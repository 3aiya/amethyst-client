package com.amethystclient.hud;

import com.amethystclient.hud.modules.CpsModule;
import com.amethystclient.ui.AmethystTheme;
import com.amethystclient.ui.Draw;
import com.amethystclient.ui.PageScreen;
import java.util.ArrayList;
import java.util.List;

/** The Amethyst HUD: draws the enabled modules on top of the vanilla HUD. */
public final class AmethystHud {
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
		List<HudModule> shown = new ArrayList<>();
		for (HudModule module : Modules.HUD) {
			if (!module.enabled()) {
				continue;
			}
			module.measure(d, preview);
			if (module.width > 0 && module.height > 0) {
				shown.add(module);
			}
		}
		layoutStack(shown);
		for (HudModule module : shown) {
			module.render(d, theme, module.x(hudWidth), module.y(hudHeight), preview);
		}
		d.pop();
	}

	/** Stacks the shown, docked modules top-left in their order, so there are no gaps. */
	private static void layoutStack(List<HudModule> shown) {
		int y = HudModule.MARGIN;
		for (HudModule module : Modules.STACK) {
			if (module.docked() && shown.contains(module)) {
				module.placeInStack(y);
				y += module.height + HudModule.STACK_GAP;
			} else {
				module.leaveStack();
			}
		}
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
