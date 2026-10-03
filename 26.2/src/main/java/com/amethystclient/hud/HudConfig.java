package com.amethystclient.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** HUD and appearance settings, saved as config/amethystclient/hud.json. */
public final class HudConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("AmethystClient/Hud");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("amethystclient").resolve("hud.json");

	private static HudConfig instance;

	/** Name of an {@link com.amethystclient.ui.AmethystTheme}. */
	public String theme = "Pink";
	/** Alpha of the HUD panels' background, so the world shows through (guide: 70–80%). */
	public double backgroundOpacity = 0.75;
	public double scale = 1.0;
	public boolean reduceMotion;
	public Map<String, Module> modules = new LinkedHashMap<>();
	/** Where each settings panel was dragged to: {x, y} in GUI pixels. */
	public Map<String, int[]> panels = new LinkedHashMap<>();

	public static final class Module {
		public boolean enabled;
		/** The screen point the module is pinned to: 0 = left/top, 0.5 = centre, 1 = right/bottom. */
		public double anchorX;
		public double anchorY;
		/** Pixel offset from that point, in HUD (scaled) pixels. */
		public int offsetX;
		public int offsetY;
		/** The module's own settings (booleans, numbers and mode names), by key. */
		public Map<String, Object> values = new LinkedHashMap<>();

		Module(boolean enabled, HudModule.Position position) {
			this.enabled = enabled;
			set(position);
		}

		public void set(HudModule.Position position) {
			anchorX = position.anchorX();
			anchorY = position.anchorY();
			offsetX = position.offsetX();
			offsetY = position.offsetY();
		}
	}

	public static HudConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	/** This module's settings, created with the module's defaults the first time. */
	public Module module(HudModule module) {
		return modules.computeIfAbsent(module.id, id -> new Module(module.enabledByDefault, module.defaultPosition));
	}

	private static HudConfig load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				HudConfig config = GSON.fromJson(reader, HudConfig.class);
				if (config != null) {
					if (config.modules == null) {
						config.modules = new LinkedHashMap<>();
					}
					if (config.panels == null) {
						config.panels = new LinkedHashMap<>();
					}
					for (Module module : config.modules.values()) {
						if (module.values == null) {
							module.values = new LinkedHashMap<>();
						}
					}
					config.scale = Math.clamp(config.scale, 0.5, 2.0);
					config.backgroundOpacity = Math.clamp(config.backgroundOpacity, 0.0, 1.0);
					return config;
				}
			} catch (IOException | RuntimeException e) {
				LOGGER.warn("Couldn't read {}, using the defaults", FILE, e);
			}
		}
		return new HudConfig();
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			LOGGER.warn("Couldn't save {}", FILE, e);
		}
	}
}
