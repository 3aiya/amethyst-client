package com.amethystclient.hud;

import com.amethystclient.hud.modules.ArmorModule;
import com.amethystclient.hud.modules.ClockModule;
import com.amethystclient.hud.modules.CoordsModule;
import com.amethystclient.hud.modules.CpsModule;
import com.amethystclient.hud.modules.DirectionModule;
import com.amethystclient.hud.modules.FpsModule;
import com.amethystclient.hud.modules.KeystrokesModule;
import com.amethystclient.hud.modules.MemoryModule;
import com.amethystclient.hud.modules.ModuleListModule;
import com.amethystclient.hud.modules.PingModule;
import com.amethystclient.hud.modules.SessionTimeModule;
import com.amethystclient.hud.modules.SpeedModule;
import com.amethystclient.hud.modules.WatermarkModule;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every module, in the order they're listed in their ClickGUI column (and drawn on the HUD, so
 * later ones are on top). Register new modules here.
 */
public final class Modules {
	public static final List<ClientModule> ALL = List.of(
			// Info
			new FpsModule(),
			new PingModule(),
			new CpsModule(),
			new MemoryModule(),
			// World
			new CoordsModule(),
			new DirectionModule(),
			new SpeedModule(),
			// Render
			new WatermarkModule(),
			new ModuleListModule(),
			// Player
			new ArmorModule(),
			new KeystrokesModule(),
			// Misc
			new ClockModule(),
			new SessionTimeModule());

	/** The modules that draw on the HUD. */
	public static final List<HudModule> HUD = ALL.stream()
			.filter(HudModule.class::isInstance)
			.map(HudModule.class::cast)
			.toList();

	/** Which bound keys were down last tick, so a held key toggles only once. */
	private static Map<Integer, Boolean> keysDown = new HashMap<>();
	private static boolean wasInWorld;

	private Modules() {
	}

	public static List<ClientModule> in(Category category) {
		return ALL.stream().filter(module -> module.category == category).toList();
	}

	/** Called at the end of every client tick: keybinds, then each module's {@link ClientModule#tick}. */
	public static void tick() {
		if (!Game.inWorld()) {
			keysDown.clear();
			wasInWorld = false;
			return;
		}
		if (!wasInWorld) {
			wasInWorld = true;
			for (ClientModule module : ALL) {
				module.onJoinWorld();
			}
		}
		Map<Integer, Boolean> now = new HashMap<>();
		for (ClientModule module : ALL) {
			if (module.keybind() > 0) {
				now.computeIfAbsent(module.keybind(), Game::physicalKeyDown);
			}
		}
		// Keys pressed while a screen (chat, inventory, the GUI) is open don't toggle anything.
		if (!Game.screenOpen()) {
			boolean changed = false;
			for (ClientModule module : ALL) {
				int key = module.keybind();
				if (key > 0 && now.get(key) && !keysDown.getOrDefault(key, true)) {
					module.toggle();
					changed = true;
				}
			}
			if (changed) {
				HudConfig.get().save();
			}
		}
		keysDown = now;
		for (ClientModule module : ALL) {
			module.tick();
		}
	}
}
