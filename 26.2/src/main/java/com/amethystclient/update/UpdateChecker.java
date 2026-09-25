package com.amethystclient.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks GitHub for a newer release of this Minecraft version, downloads it in the background,
 * and swaps it into the mods folder the next time the game is closed - see the notes above
 * {@link #launchApplyScript}. The running jar can't be replaced while the JVM has it open, so the
 * swap itself happens in a small script launched from a JVM shutdown hook, after this process exits.
 */
public final class UpdateChecker {
	private static final Logger LOGGER = LoggerFactory.getLogger("amethystclient/update");
	private static final String MOD_ID = "amethystclient";
	private static final String REPO = "3aiya/amethyst-client";
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
	private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(2);

	private static volatile String pendingVersion;
	private static volatile boolean shutdownHookRegistered;

	private UpdateChecker() {
	}

	/** The version we've already downloaded and queued to apply on next launch, if any. */
	public static String pendingVersion() {
		return pendingVersion;
	}

	public static void checkAsync() {
		Thread thread = new Thread(UpdateChecker::check, "amethystclient-update-check");
		thread.setDaemon(true);
		thread.start();
	}

	private static void check() {
		try {
			ModContainer self = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow();
			String currentVersion = self.getMetadata().getVersion().getFriendlyString();
			String mcVersion = FabricLoader.getInstance()
					.getModContainer("minecraft")
					.orElseThrow()
					.getMetadata()
					.getVersion()
					.getFriendlyString();

			HttpClient http = HttpClient.newHttpClient();
			String body = get(http, "https://api.github.com/repos/" + REPO + "/releases");
			if (body == null) {
				return;
			}

			String suffix = "-mc" + mcVersion;
			for (JsonElement element : JsonParser.parseString(body).getAsJsonArray()) {
				JsonObject release = element.getAsJsonObject();
				String tag = release.get("tag_name").getAsString();
				if (!tag.endsWith(suffix) || !tag.startsWith("v")) {
					continue;
				}

				String remoteVersion = tag.substring(1, tag.length() - suffix.length());
				if (!isNewer(remoteVersion, currentVersion)) {
					return;
				}

				JsonArray assets = release.getAsJsonArray("assets");
				String assetUrl = null;
				String assetName = null;
				for (JsonElement a : assets) {
					JsonObject asset = a.getAsJsonObject();
					String name = asset.get("name").getAsString();
					if (name.endsWith(".jar") && !name.endsWith("-sources.jar")) {
						assetUrl = asset.get("browser_download_url").getAsString();
						assetName = name;
						break;
					}
				}
				if (assetUrl == null) {
					LOGGER.warn("Release {} has no jar asset, skipping update", tag);
					return;
				}

				if (download(http, assetUrl, assetName, self)) {
					pendingVersion = remoteVersion;
					LOGGER.info("Amethyst Client {} downloaded, will apply on next launch", remoteVersion);
				}
				return;
			}
		} catch (Exception e) {
			LOGGER.warn("Amethyst Client update check failed", e);
		}
	}

	private static String get(HttpClient http, String url) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.header("Accept", "application/vnd.github+json")
				.header("User-Agent", "amethystclient-updater")
				.timeout(REQUEST_TIMEOUT)
				.GET()
				.build();
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			LOGGER.warn("Update check request to {} failed: HTTP {}", url, response.statusCode());
			return null;
		}
		return response.body();
	}

	private static boolean download(HttpClient http, String url, String assetName, ModContainer self)
			throws IOException, InterruptedException {
		Path updateDir = FabricLoader.getInstance().getGameDir().resolve("mods").resolve("amethystclient-updates");
		Files.createDirectories(updateDir);
		Path pending = updateDir.resolve("pending.jar");

		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.header("User-Agent", "amethystclient-updater")
				.timeout(DOWNLOAD_TIMEOUT)
				.GET()
				.build();
		HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(pending));
		if (response.statusCode() / 100 != 2) {
			Files.deleteIfExists(pending);
			LOGGER.warn("Update download failed: HTTP {}", response.statusCode());
			return false;
		}

		// The currently running jar - it can't be deleted until this JVM exits and releases it.
		Path currentJar = self.getOrigin().getPaths().get(0);
		Path targetJar = currentJar.resolveSibling(assetName);
		writeApplyScript(updateDir, currentJar, pending, targetJar);
		return true;
	}

	/**
	 * Writes a batch script that waits for this process to exit, then deletes the old jar and moves
	 * the downloaded one into its place, and queues it to run from a shutdown hook. Windows keeps this
	 * jar file open for as long as the JVM that loaded it is alive, so the swap can only happen after
	 * this process has actually exited - a separate, detached process is the only thing that can wait
	 * for that and then touch the file.
	 */
	private static void writeApplyScript(Path updateDir, Path currentJar, Path pending, Path targetJar)
			throws IOException {
		long pid = ProcessHandle.current().pid();
		String script = String.format(Locale.ROOT, """
				@echo off
				:wait
				tasklist /FI "PID eq %d" 2>NUL | find /I "%d" >NUL
				if not errorlevel 1 (
				  timeout /t 1 /nobreak >NUL
				  goto wait
				)
				del /f /q "%s"
				move /y "%s" "%s" >NUL
				del /f /q "%%~f0"
				""", pid, pid, currentJar, pending, targetJar);
		Path scriptPath = updateDir.resolve("apply-update.bat");
		Files.writeString(scriptPath, script, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

		if (!shutdownHookRegistered) {
			shutdownHookRegistered = true;
			Runtime.getRuntime().addShutdownHook(new Thread(() -> launchApplyScript(scriptPath)));
		}
	}

	/** Launches the swap script detached, so it keeps running after this JVM has fully exited. */
	private static void launchApplyScript(Path scriptPath) {
		try {
			new ProcessBuilder("cmd", "/c", "start", "\"amethystclient-update\"", "/min", scriptPath.toString())
					.directory(scriptPath.getParent().toFile())
					.start();
		} catch (IOException e) {
			LOGGER.warn("Failed to launch Amethyst Client update script", e);
		}
	}

	private static boolean isNewer(String remote, String current) {
		int[] r = parseVersion(remote);
		int[] c = parseVersion(current);
		for (int i = 0; i < 3; i++) {
			if (r[i] != c[i]) {
				return r[i] > c[i];
			}
		}
		return false;
	}

	/** "1.0.2" -> [1, 0, 2]. Anything after the third number (pre-release/build tags) is ignored. */
	private static int[] parseVersion(String version) {
		String[] parts = version.split("[.+-]");
		int[] out = new int[3];
		for (int i = 0; i < 3 && i < parts.length; i++) {
			try {
				out[i] = Integer.parseInt(parts[i]);
			} catch (NumberFormatException ignored) {
				out[i] = 0;
			}
		}
		return out;
	}
}
