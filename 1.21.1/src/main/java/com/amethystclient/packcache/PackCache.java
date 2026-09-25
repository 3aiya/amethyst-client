package com.amethystclient.packcache;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import static com.amethystclient.packcache.ServerPackManager.LOGGER;

/**
 * On-disk storage for cached server resource packs.
 *
 * <pre>
 * .minecraft/resourcepack-cache/
 * ├── last-server.txt                  key of the server whose pack is applied at launch
 * ├── play.example.com/
 * │   ├── hash.txt                     SHA-1 of the current pack for this server
 * │   └── pack-&lt;sha1&gt;.zip             the pack itself
 * └── 192.168.1.20_25566/
 *     └── ...
 * </pre>
 *
 * The zip file name contains the hash instead of a fixed {@code pack.zip}. The zip currently in use
 * stays open inside the resource manager, and Windows won't let you overwrite an open file. A new
 * version is therefore written next to the old one, {@code hash.txt} is switched over to it, and
 * the old zip is deleted once the resource reload has released it.
 *
 * <p>Every read path treats a missing, unreadable or inconsistent file as "no cache". The caller
 * then falls back to downloading the pack, which regenerates the folder.
 */
public final class PackCache {
	private static final Pattern SHA1 = Pattern.compile("[0-9a-f]{40}");
	private static final String HASH_FILE = "hash.txt";
	private static final String LAST_SERVER_FILE = "last-server.txt";
	private static final String ZIP_PREFIX = "pack-";

	/** A verified cached pack: the zip exists, its SHA-1 equals {@code sha1}, and it contains a pack.mcmeta. */
	public record CachedPack(String serverKey, String sha1, Path zip) {
	}

	private final Path root;

	public PackCache(Path root) {
		this.root = root;
	}

	public static boolean isSha1(String hash) {
		return SHA1.matcher(hash).matches();
	}

	/**
	 * Turns a server-list address into a folder name that is safe to use and stable over time.
	 * The default port is removed, so {@code example.com} and {@code example.com:25565} share one cache.
	 * Anything that is not a safe file name character is replaced with {@code _}.
	 */
	public static String serverKey(String address) {
		String normalized = address.trim().toLowerCase(Locale.ROOT);
		if (normalized.endsWith(":25565")) {
			normalized = normalized.substring(0, normalized.length() - ":25565".length());
		}
		String key = normalized.replaceAll("[^a-z0-9._-]", "_");
		// Never produce "", "." or ".." - those would resolve to the cache root or its parent.
		if (key.isEmpty() || key.chars().allMatch(c -> c == '.')) {
			key = "_" + key;
		}
		return key;
	}

	/**
	 * Loads and fully verifies the cached pack for a server. The zip's SHA-1 is recomputed rather
	 * than trusting hash.txt, so a truncated or modified file is caught here and never applied.
	 *
	 * @return the verified pack, or {@code null} if there is no usable cache (corrupt caches are deleted)
	 */
	@Nullable
	public CachedPack load(String serverKey) {
		Path dir = this.root.resolve(serverKey);
		Path hashFile = dir.resolve(HASH_FILE);
		if (!Files.isRegularFile(hashFile)) {
			return null;
		}

		try {
			String hash = Files.readString(hashFile, StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
			if (!isSha1(hash)) {
				LOGGER.warn("[cache] {} for '{}' does not contain a valid SHA-1; discarding cache", HASH_FILE, serverKey);
				this.invalidate(serverKey);
				return null;
			}

			Path zip = dir.resolve(zipName(hash));
			if (!Files.isRegularFile(zip)) {
				LOGGER.warn("[cache] {} for '{}' points to {} which is missing; discarding cache", HASH_FILE, serverKey, zip.getFileName());
				this.invalidate(serverKey);
				return null;
			}

			String actual = sha1(zip);
			if (!actual.equals(hash)) {
				LOGGER.warn("[cache] Cached pack for '{}' is corrupted (expected SHA-1 {}, file is {}); discarding cache", serverKey, hash, actual);
				this.invalidate(serverKey);
				return null;
			}

			if (!looksLikeResourcePack(zip)) {
				LOGGER.warn("[cache] Cached pack for '{}' is not a valid resource pack zip; discarding cache", serverKey);
				this.invalidate(serverKey);
				return null;
			}

			return new CachedPack(serverKey, hash, zip);
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("[cache] Could not read cached pack for '{}'; discarding cache", serverKey, e);
			this.invalidate(serverKey);
			return null;
		}
	}

	/** Returns the temporary file a new download for this server should be written to, creating folders as needed. */
	public Path prepareDownload(String serverKey, String sha1) throws IOException {
		Path dir = this.root.resolve(serverKey);
		Files.createDirectories(dir);
		return dir.resolve(zipName(sha1) + ".part");
	}

	/**
	 * Promotes a finished, hash-verified download into the cache. The zip is moved into place
	 * first and hash.txt is written afterwards. If the game dies in between, hash.txt still
	 * points to the previous zip, which is only deleted later, so the cache stays consistent.
	 */
	public CachedPack store(String serverKey, String sha1, Path downloadedPart) throws IOException {
		if (!looksLikeResourcePack(downloadedPart)) {
			Files.deleteIfExists(downloadedPart);
			throw new IOException("Downloaded file is not a valid resource pack (no pack.mcmeta at the zip root)");
		}

		Path dir = this.root.resolve(serverKey);
		Files.createDirectories(dir);
		Path zip = dir.resolve(zipName(sha1));
		moveReplacing(downloadedPart, zip);
		writeAtomically(dir.resolve(HASH_FILE), sha1);
		return new CachedPack(serverKey, sha1, zip);
	}

	/**
	 * Deletes old pack versions and leftover partial downloads for {@code keep}'s server.
	 * Files that are still locked (Windows) are skipped silently and retried next time.
	 */
	public void deleteStaleFiles(CachedPack keep) {
		Path dir = this.root.resolve(keep.serverKey());
		if (!Files.isDirectory(dir)) {
			return;
		}

		String keepName = keep.zip().getFileName().toString();
		try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, ZIP_PREFIX + "*")) {
			for (Path file : files) {
				if (!file.getFileName().toString().equals(keepName)) {
					try {
						Files.deleteIfExists(file);
						LOGGER.debug("[cache] Deleted stale file {}", file);
					} catch (IOException ignored) {
						// Still held open by the old resource pack; it will be cleaned up on a later run.
					}
				}
			}
		} catch (IOException e) {
			LOGGER.debug("[cache] Could not clean up {}", dir, e);
		}
	}

	/** Drops the cache for a server (best effort). The next join treats the server as never seen. */
	public void invalidate(String serverKey) {
		Path dir = this.root.resolve(serverKey);
		try {
			Files.deleteIfExists(dir.resolve(HASH_FILE));
		} catch (IOException e) {
			LOGGER.debug("[cache] Could not delete {} for '{}'", HASH_FILE, serverKey, e);
		}
		if (Files.isDirectory(dir)) {
			try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, ZIP_PREFIX + "*")) {
				for (Path file : files) {
					try {
						Files.deleteIfExists(file);
					} catch (IOException ignored) {
						// Locked; harmless since hash.txt is gone, cleaned up later.
					}
				}
			} catch (IOException ignored) {
			}
		}
	}

	@Nullable
	public String readLastServer() {
		Path file = this.root.resolve(LAST_SERVER_FILE);
		try {
			if (!Files.isRegularFile(file)) {
				return null;
			}
			String key = Files.readString(file, StandardCharsets.UTF_8).trim();
			// Re-sanitize: the file is user-editable and must not escape the cache root.
			return key.isEmpty() ? null : serverKey(key);
		} catch (IOException e) {
			LOGGER.warn("[cache] Could not read {}", LAST_SERVER_FILE, e);
			return null;
		}
	}

	public void writeLastServer(String serverKey) {
		try {
			Files.createDirectories(this.root);
			writeAtomically(this.root.resolve(LAST_SERVER_FILE), serverKey);
		} catch (IOException e) {
			LOGGER.warn("[cache] Could not write {}", LAST_SERVER_FILE, e);
		}
	}

	private static String zipName(String sha1) {
		return ZIP_PREFIX + sha1 + ".zip";
	}

	static MessageDigest newSha1() {
		try {
			return MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-1 is not available in this JVM", e);
		}
	}

	private static String sha1(Path file) throws IOException {
		MessageDigest digest = newSha1();
		try (InputStream in = Files.newInputStream(file)) {
			byte[] buffer = new byte[64 * 1024];
			int read;
			while ((read = in.read(buffer)) != -1) {
				digest.update(buffer, 0, read);
			}
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	/** Cheap structural check: the file is a readable zip and has pack.mcmeta at its root, like vanilla requires. */
	private static boolean looksLikeResourcePack(Path zip) {
		try (ZipFile zipFile = new ZipFile(zip.toFile())) {
			return zipFile.getEntry("pack.mcmeta") != null;
		} catch (IOException e) {
			return false;
		}
	}

	private static void writeAtomically(Path target, String content) throws IOException {
		Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
		Files.writeString(tmp, content, StandardCharsets.UTF_8);
		moveReplacing(tmp, target);
	}

	private static void moveReplacing(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
