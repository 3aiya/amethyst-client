package com.amethystclient.packcache;

import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads a server resource pack straight into the cache folder, computing its SHA-1 while it
 * streams. The download only succeeds if the bytes hash to exactly what the server announced, so a
 * truncated or tampered download can never become the cached copy.
 */
public final class PackDownloader {
	/** Same limit vanilla applies to server resource packs. */
	private static final long MAX_PACK_SIZE = 250L * 1024 * 1024;

	private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
		Thread thread = new Thread(runnable, "AmethystClient pack download");
		thread.setDaemon(true);
		return thread;
	});

	private final HttpClient http = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(15))
			.executor(this.executor)
			.build();

	/**
	 * Starts an asynchronous download. The future completes with {@code target} when the file is
	 * written and its hash matches {@code expectedSha1}, and fails otherwise. On failure the partial
	 * file has already been deleted.
	 */
	public CompletableFuture<Path> download(URL url, String expectedSha1, Path target, Map<String, String> headers) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return this.downloadBlocking(url, expectedSha1, target, headers);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new CompletionException(e);
			} catch (URISyntaxException e) {
				throw new CompletionException(e);
			}
		}, this.executor);
	}

	private Path downloadBlocking(URL url, String expectedSha1, Path target, Map<String, String> headers)
			throws IOException, InterruptedException, URISyntaxException {
		HttpRequest.Builder request = HttpRequest.newBuilder(url.toURI())
				.timeout(Duration.ofSeconds(60))
				.GET();
		headers.forEach(request::header);

		HttpResponse<InputStream> response = this.http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
		try (InputStream in = response.body()) {
			if (response.statusCode() != 200) {
				throw new IOException("Server returned HTTP " + response.statusCode());
			}
			OptionalLong declaredLength = response.headers().firstValueAsLong("Content-Length");
			if (declaredLength.isPresent() && declaredLength.getAsLong() > MAX_PACK_SIZE) {
				throw new IOException("Pack is too large (" + declaredLength.getAsLong() + " bytes, limit " + MAX_PACK_SIZE + ")");
			}

			MessageDigest digest = PackCache.newSha1();
			long total = 0;
			try (OutputStream out = Files.newOutputStream(target)) {
				byte[] buffer = new byte[64 * 1024];
				int read;
				while ((read = in.read(buffer)) != -1) {
					total += read;
					if (total > MAX_PACK_SIZE) {
						throw new IOException("Pack exceeded the " + MAX_PACK_SIZE + " byte size limit");
					}
					digest.update(buffer, 0, read);
					out.write(buffer, 0, read);
				}
			}

			String actualSha1 = HexFormat.of().formatHex(digest.digest());
			if (!actualSha1.equals(expectedSha1)) {
				throw new IOException("SHA-1 mismatch: server announced " + expectedSha1 + " but the downloaded file is " + actualSha1);
			}
			return target;
		} catch (IOException e) {
			Files.deleteIfExists(target);
			throw e;
		}
	}

	/** The same identifying headers vanilla sends with a resource pack download; some pack hosts check them. */
	public static Map<String, String> vanillaHeaders(MinecraftClient client) {
		Map<String, String> headers = new LinkedHashMap<>();
		String version = SharedConstants.getGameVersion().name();
		headers.put("User-Agent", "Minecraft Java/" + version);
		headers.put("X-Minecraft-Username", client.getSession().getUsername());
		UUID uuid = client.getSession().getUuidOrNull();
		if (uuid != null) {
			headers.put("X-Minecraft-UUID", uuid.toString().replace("-", ""));
		}
		headers.put("X-Minecraft-Version", version);
		headers.put("X-Minecraft-Version-ID", SharedConstants.getGameVersion().id());
		return headers;
	}
}
