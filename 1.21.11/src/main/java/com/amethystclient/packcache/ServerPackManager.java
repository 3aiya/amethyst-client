package com.amethystclient.packcache;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackRemoveS2CPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The brain of the mod. Decides, for every server resource pack push, whether the pack the client
 * already has is still correct, needs replacing, or should be left to vanilla.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li><b>Launch</b> ({@link #initialize()}): read {@code last-server.txt}, verify that server's
 *       cached zip, and make it the active pack <i>before</i> the game's first resource load, so it
 *       is applied as part of normal startup with no extra reload.</li>
 *   <li><b>Join</b> ({@link #onPackPush}): compare the SHA-1 in the server's packet with the active
 *       pack. If they match, reply "loaded" at once and do nothing else. If they differ, download
 *       the new version, cache it and reload.</li>
 *   <li><b>Anything unusual</b> (no hash, player disabled server packs, download failed, ...): hand
 *       the packet back to vanilla, so the player gets the normal prompt/download/reload flow.</li>
 * </ol>
 *
 * <p>Threading: every public method runs on the render thread. The mixins inject after vanilla's
 * {@code forceMainThread} call, and Fabric's connection events fire on the render thread. Only the
 * HTTP transfer runs elsewhere, and its result is posted back with {@code client.execute}. That's
 * why the mutable fields below need no locking.
 */
public final class ServerPackManager {
	public static final Logger LOGGER = LoggerFactory.getLogger("AmethystClient/PackCache");

	private static final ServerPackManager INSTANCE = new ServerPackManager();

	public static ServerPackManager get() {
		return INSTANCE;
	}

	private final PackDownloader downloader = new PackDownloader();

	@Nullable
	private PackCache cache;

	/**
	 * The cached pack currently injected into the client's resource list, or null for none.
	 * {@link CachedPackProvider} reads this on every resource rescan.
	 */
	@Nullable
	private volatile PackCache.CachedPack activePack;

	/**
	 * Pack IDs we decided to hand back to vanilla. The fallback re-invokes vanilla's
	 * {@code onResourcePackSend}, which passes through our mixin again. This set tells the
	 * mixin to let that one call through untouched.
	 */
	private final Set<UUID> passToVanilla = new HashSet<>();

	// ---- Per-connection state (reset whenever a new ClientConnection is seen) ----
	@Nullable
	private ClientConnection sessionConnection;
	/** The pack UUID this mod manages on the current connection. Additional packs go to vanilla. */
	@Nullable
	private UUID sessionPackId;
	/** Hash currently being downloaded on this connection, so a re-sent packet doesn't start a second download. */
	@Nullable
	private String sessionDownloadingSha1;

	private ServerPackManager() {
	}

	// ------------------------------------------------------------------------------------------
	// Step 1: launch
	// ------------------------------------------------------------------------------------------

	/**
	 * Sets up the cache and, if possible, activates the last server's pack. This is called from
	 * {@code onInitializeClient}, which Fabric runs before the client's first resource pack scan,
	 * so the cached pack loads with the rest of the startup resources.
	 * Safe to call more than once.
	 */
	public synchronized void initialize() {
		if (this.cache != null) {
			return;
		}
		this.cache = new PackCache(FabricLoader.getInstance().getGameDir().resolve("resourcepack-cache"));

		String lastServer = this.cache.readLastServer();
		if (lastServer == null) {
			LOGGER.info("[launch] No cached server pack from a previous session; starting with normal resources");
			return;
		}

		PackCache.CachedPack pack = this.cache.load(lastServer);
		if (pack == null) {
			LOGGER.info("[launch] Cached pack for '{}' is missing or corrupted; it will be downloaded normally on the next join", lastServer);
			return;
		}

		this.activePack = pack;
		this.cache.deleteStaleFiles(pack);
		LOGGER.info("[launch] APPLIED FROM LAUNCH CACHE: pack for '{}' (sha1 {})", pack.serverKey(), pack.sha1());
	}

	@Nullable
	public PackCache.CachedPack getActivePack() {
		// The resource manager can be built before our entrypoint runs; make sure the launch cache is loaded first.
		this.initialize();
		return this.activePack;
	}

	// ------------------------------------------------------------------------------------------
	// Step 2: the server pushes a pack
	// ------------------------------------------------------------------------------------------

	/**
	 * Called by the mixin for every resource pack push, before vanilla's accept/download logic.
	 *
	 * @param vanillaHandler re-runs vanilla's handler for this packet (used for fallbacks)
	 * @return {@code true} if this mod handled the packet and vanilla must be skipped
	 */
	public boolean onPackPush(ResourcePackSendS2CPacket packet, ClientConnection connection, @Nullable ServerInfo serverInfo,
			MinecraftClient client, Runnable vanillaHandler) {
		this.initialize();
		UUID packId = packet.id();

		// This is our own fallback re-entering. Let vanilla run exactly as it normally would.
		if (this.passToVanilla.remove(packId)) {
			return false;
		}

		this.beginSessionIfNew(connection);

		// ---- Cases we deliberately leave to vanilla ----
		if (serverInfo == null) {
			LOGGER.info("[join] Pack push without server info (singleplayer/LAN); using vanilla handling");
			return false;
		}
		if (serverInfo.getResourcePackPolicy() == ServerInfo.ResourcePackPolicy.DISABLED) {
			// The player explicitly turned server packs off for this server; respect it (vanilla declines or disconnects).
			LOGGER.info("[join] Server resource packs are disabled for '{}' in the server list; using vanilla handling", serverInfo.address);
			return false;
		}

		String serverSha1 = packet.hash().trim().toLowerCase(Locale.ROOT);
		if (!PackCache.isSha1(serverSha1)) {
			// Without a hash there is no way to know if the pack changed short of downloading it.
			LOGGER.info("[join] Server did not send a valid SHA-1 (got '{}'); cannot cache, using vanilla handling", packet.hash());
			return false;
		}

		URL url = parseUrl(packet.url());
		if (url == null) {
			return false; // Vanilla replies INVALID_URL.
		}

		if (this.sessionPackId != null && !this.sessionPackId.equals(packId)) {
			LOGGER.info("[join] Server sent an additional pack ({}); only one pack per server is cached, using vanilla handling for it", packId);
			return false;
		}
		this.sessionPackId = packId;

		String serverKey = PackCache.serverKey(serverInfo.address);
		PackCache.CachedPack current = this.activePack;

		// ---- Case A: the pack applied at launch (or earlier) is exactly this version ----
		if (current != null && current.serverKey().equals(serverKey) && current.sha1().equals(serverSha1)) {
			LOGGER.info("[join] HASH MATCH for '{}' (sha1 {}): pack already applied, nothing to do", serverKey, serverSha1);
			// Tell the server the pack is loaded, as vanilla would after a successful download and reload.
			sendStatus(connection, packId, ResourcePackStatusC2SPacket.Status.ACCEPTED);
			sendStatus(connection, packId, ResourcePackStatusC2SPacket.Status.DOWNLOADED);
			sendStatus(connection, packId, ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED);
			return true;
		}

		// A download of this exact version is already running (the server re-sent the packet); it will report back.
		if (serverSha1.equals(this.sessionDownloadingSha1)) {
			return true;
		}

		// ---- Case B: not active, but the correct version is on disk (e.g. last launch was for another server) ----
		PackCache.CachedPack onDisk = this.cache.load(serverKey);
		if (onDisk != null && onDisk.sha1().equals(serverSha1)) {
			LOGGER.info("[join] HASH MATCH with disk cache for '{}' (sha1 {}): applying cached copy, no download", serverKey, serverSha1);
			sendStatus(connection, packId, ResourcePackStatusC2SPacket.Status.ACCEPTED);
			sendStatus(connection, packId, ResourcePackStatusC2SPacket.Status.DOWNLOADED);
			this.activate(onDisk, client).thenAccept(ok -> sendStatus(connection, packId,
					ok ? ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED : ResourcePackStatusC2SPacket.Status.FAILED_RELOAD));
			return true;
		}

		// ---- Case C: first join or the server updated its pack, so download it ----
		LOGGER.info("[join] HASH CHANGED for '{}': cached {} vs server {}; downloading {}",
				serverKey, onDisk == null ? "<none>" : onDisk.sha1(), serverSha1, url);
		Path target;
		try {
			target = this.cache.prepareDownload(serverKey, serverSha1);
		} catch (IOException e) {
			LOGGER.warn("[download] Could not create cache folder for '{}'; using vanilla handling", serverKey, e);
			this.fallBackToVanilla(serverKey, packId, connection, vanillaHandler);
			return true;
		}

		sendStatus(connection, packId, ResourcePackStatusC2SPacket.Status.ACCEPTED);
		this.sessionDownloadingSha1 = serverSha1;

		this.downloader.download(url, serverSha1, target, PackDownloader.vanillaHeaders(client))
				.whenCompleteAsync((file, error) -> {
					boolean sameSession = connection == this.sessionConnection;
					if (sameSession) {
						this.sessionDownloadingSha1 = null;
					}

					if (error != null) {
						// Unwrap CompletionException / UncheckedIOException to the actual reason.
						Throwable cause = error;
						while (cause.getCause() != null) {
							cause = cause.getCause();
						}
						LOGGER.warn("[download] Download of pack for '{}' failed ({}); falling back to vanilla handling", serverKey, cause.toString());
						if (sameSession) {
							this.fallBackToVanilla(serverKey, packId, connection, vanillaHandler);
						}
						return;
					}

					PackCache.CachedPack stored;
					try {
						stored = this.cache.store(serverKey, serverSha1, file);
					} catch (IOException e) {
						LOGGER.warn("[download] Could not save pack for '{}' to cache; falling back to vanilla handling", serverKey, e);
						if (sameSession) {
							this.fallBackToVanilla(serverKey, packId, connection, vanillaHandler);
						}
						return;
					}
					LOGGER.info("[download] Downloaded and cached pack for '{}' (sha1 {})", serverKey, serverSha1);

					if (!sameSession || !connection.isOpen()) {
						// Player left while downloading. The file is verified and cached, so the next join/launch uses it.
						this.cache.writeLastServer(serverKey);
						LOGGER.info("[download] Disconnected before the download finished; the pack will be used on the next join");
						return;
					}

					sendStatus(connection, packId, ResourcePackStatusC2SPacket.Status.DOWNLOADED);
					this.activate(stored, client).thenAccept(ok -> {
						if (ok) {
							LOGGER.info("[download] DOWNLOADED + APPLIED new pack version for '{}' (sha1 {})", serverKey, serverSha1);
						}
						sendStatus(connection, packId,
								ok ? ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED : ResourcePackStatusC2SPacket.Status.FAILED_RELOAD);
					});
				}, client);
		return true;
	}

	/** The server withdrew its pack. Forget the session's pack ID so that a later push is checked again. */
	public void onPackRemove(ResourcePackRemoveS2CPacket packet, ClientConnection connection) {
		this.beginSessionIfNew(connection);
		Optional<UUID> removed = packet.id();
		if (this.sessionPackId != null && (removed.isEmpty() || removed.get().equals(this.sessionPackId))) {
			// The cached pack stays loaded. Many plugins send "remove all" right before re-sending the same
			// pack, and dropping it here would cause a pointless reload.
			LOGGER.info("[join] Server removed its pack; the cached copy stays loaded until the server pushes a different version");
			this.sessionPackId = null;
		}
	}

	// ------------------------------------------------------------------------------------------
	// Server switching / failure handling
	// ------------------------------------------------------------------------------------------

	/**
	 * Called when the play phase begins on a server. If the active pack belongs to a different
	 * server, swap in this server's cached pack, or drop the pack if this server has none cached.
	 * This keeps one server's pack from showing on another server, including servers that never
	 * send a pack. Singleplayer/LAN (no server info) keeps the current pack as is.
	 */
	public void onJoinedServer(ClientConnection connection, @Nullable ServerInfo serverInfo, MinecraftClient client) {
		this.initialize();
		this.beginSessionIfNew(connection);
		if (serverInfo == null || this.sessionPackId != null) {
			return; // Singleplayer, or a pack push for this server is already being handled.
		}

		String serverKey = PackCache.serverKey(serverInfo.address);
		PackCache.CachedPack current = this.activePack;
		if (current != null && current.serverKey().equals(serverKey)) {
			return;
		}

		PackCache.CachedPack own = this.cache.load(serverKey);
		if (own != null) {
			LOGGER.info("[join] Switched to '{}': applying its cached pack (sha1 {}) until the server confirms the version", serverKey, own.sha1());
			this.activate(own, client);
		} else if (current != null) {
			LOGGER.info("[join] '{}' has no cached pack; removing the pack of '{}' so it does not apply here", serverKey, current.serverKey());
			this.activate(null, client);
		}
	}

	/**
	 * Called at the start of vanilla's resource-reload-failure handling. Vanilla then re-enables only
	 * "required" packs and reloads. Our pack is required, so a broken cached pack would fail every
	 * retry. Drop it and delete it from the cache so the next join re-downloads it.
	 */
	public void onResourceReloadFailure() {
		PackCache.CachedPack pack = this.activePack;
		if (pack == null) {
			return;
		}
		LOGGER.error("[reload] Resource reload failed while the cached pack for '{}' was active; disabling it and clearing its cache", pack.serverKey());
		this.activePack = null;
		if (this.cache != null) {
			this.cache.invalidate(pack.serverKey());
		}
	}

	// ------------------------------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------------------------------

	/**
	 * Makes {@code pack} the active pack (null removes it) and reloads resources.
	 *
	 * @return a future that completes with {@code true} if the reload finished with the pack still applied
	 */
	private CompletableFuture<Boolean> activate(@Nullable PackCache.CachedPack pack, MinecraftClient client) {
		this.activePack = pack;
		if (pack != null) {
			this.cache.writeLastServer(pack.serverKey());
		}
		return client.reloadResources().handle((ignored, error) -> {
			// onResourceReloadFailure() clears activePack if the reload failed.
			boolean ok = error == null && this.activePack == pack;
			if (ok && pack != null) {
				this.cache.deleteStaleFiles(pack); // The old zip is no longer open, so it can be deleted now.
			}
			return ok;
		});
	}

	/** Re-runs vanilla's handler for this packet, so the player gets the normal prompt/download/reload flow. */
	private void fallBackToVanilla(String serverKey, UUID packId, ClientConnection connection, Runnable vanillaHandler) {
		if (!connection.isOpen()) {
			return;
		}
		// An outdated cached version must not remain underneath vanilla's copy of the new one.
		// Vanilla's own reload (when its download completes) rescans and drops it.
		PackCache.CachedPack current = this.activePack;
		if (current != null && current.serverKey().equals(serverKey)) {
			this.activePack = null;
		}
		this.passToVanilla.add(packId);
		vanillaHandler.run();
	}

	private void beginSessionIfNew(ClientConnection connection) {
		if (this.sessionConnection != connection) {
			this.sessionConnection = connection;
			this.sessionPackId = null;
			this.sessionDownloadingSha1 = null;
			this.passToVanilla.clear();
		}
	}

	private static void sendStatus(ClientConnection connection, UUID packId, ResourcePackStatusC2SPacket.Status status) {
		if (connection.isOpen()) {
			connection.send(new ResourcePackStatusC2SPacket(packId, status));
		}
	}

	/** Mirrors vanilla's URL validation: only http(s) URLs are accepted. */
	@Nullable
	private static URL parseUrl(String url) {
		try {
			URL parsed = new URI(url).toURL();
			String protocol = parsed.getProtocol();
			return "http".equals(protocol) || "https".equals(protocol) ? parsed : null;
		} catch (Exception e) {
			return null;
		}
	}
}
