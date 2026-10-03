package com.amethystclient.hud;

import com.amethystclient.hud.modules.ArmorModule;
import com.amethystclient.hud.modules.CoordsModule;
import com.amethystclient.hud.modules.CpsModule;
import com.amethystclient.hud.modules.FpsModule;
import com.amethystclient.hud.modules.KeystrokesModule;
import com.amethystclient.hud.modules.PingModule;
import com.amethystclient.ui.AmethystTheme;
import com.amethystclient.ui.Draw;
import com.amethystclient.ui.PageScreen;
import java.util.List;

/** The Amethyst HUD: draws the enabled modules on top of the vanilla HUD. */
public final class AmethystHud {
	public static final List<HudModule> MODULES = List.of(
			new FpsModule(),
			new PingModule(),
			new CoordsModule(),
			new CpsModule(),
			new ArmorModule(),
			new KeystrokesModule());

	private AmethystHud() {
	}

	/** Called at the end of the vanilla HUD every frame. */
	public static void render(Draw d) {
		CpsModule.update();
		if (Game.hudHidden() || !Game.inWorld() || PageScreen.isOpen(HudSettingsPage.class)) {
			// The settings page draws the modules itself, so they can be dragged.
			return;
		}
		renderModules(d, false);
	}

	/** Draws every enabled module at its saved position, scaled by the HUD scale. */
	static void renderModules(Draw d, boolean preview) {
		float scale = scale();
		int hudWidth = hudWidth(d);
		int hudHeight = hudHeight(d);
		AmethystTheme theme = AmethystTheme.current();
		d.push();
		d.scale(scale);
		for (HudModule module : MODULES) {
			if (!module.enabled()) {
				continue;
			}
			module.measure(d, preview);
			if (module.width > 0 && module.height > 0) {
				module.render(d, theme, module.x(hudWidth), module.y(hudHeight), preview);
			}
		}
		d.pop();
	}

	static float scale() {
		return (float) HudConfig.get().scale;
	}

	static int hudWidth(Draw d) {
		return (int) (d.width() / scale());
	}

	static int hudHeight(Draw d) {
		return (int) (d.height() / scale());
	}
}
