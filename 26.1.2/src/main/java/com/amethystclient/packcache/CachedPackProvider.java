package com.amethystclient.packcache;

import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.function.Consumer;

import static com.amethystclient.packcache.ServerPackManager.LOGGER;

/**
 * Adds the active cached pack to the client's resource pack list. It is registered on the client
 * {@code PackRepository} by {@code PackRepositoryMixin}.
 *
 * <p>The game calls {@link #loadPacks} every time it rescans packs, which it does at the start of
 * every resource reload, including the first load at game startup. So applying a different pack
 * only takes two steps: change {@link ServerPackManager#getActivePack()}, then call
 * {@code Minecraft.reloadResourcePacks()}.
 */
public final class CachedPackProvider implements RepositorySource {
	/**
	 * Same position vanilla uses for server packs:
	 * - required: always enabled, without being listed in options.txt.
	 * - TOP: overrides the player's own resource packs, as a server pack normally does.
	 * - fixed: pinned in the pack screen and never saved to options.txt.
	 */
	private static final PackSelectionConfig POSITION = new PackSelectionConfig(true, Pack.Position.TOP, true);

	@Override
	public void loadPacks(Consumer<Pack> profileAdder) {
		PackCache.CachedPack pack = ServerPackManager.get().getActivePack();
		if (pack == null) {
			return;
		}

		PackLocationInfo info = new PackLocationInfo(
				"amethystclient/cached/" + pack.serverKey(),
				Component.literal("Cached server pack (" + pack.serverKey() + ")"),
				PackSource.SERVER,
				Optional.empty()
		);
		Pack profile = Pack.readMetaAndCreate(info, new FilePackResources.FileResourcesSupplier(pack.zip()), PackType.CLIENT_RESOURCES, POSITION);
		if (profile == null) {
			// Metadata could not be read (e.g. the file vanished after verification). Skip it; the next join re-checks.
			LOGGER.warn("[cache] Could not read pack metadata of cached pack {}; it will not be applied", pack.zip());
			return;
		}
		profileAdder.accept(profile);
	}
}
