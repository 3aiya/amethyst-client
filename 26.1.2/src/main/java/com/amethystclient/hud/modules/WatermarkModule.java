package com.amethystclient.hud.modules;

import com.amethystclient.hud.Category;
import com.amethystclient.hud.Game;
import com.amethystclient.hud.HudModule;
import com.amethystclient.hud.setting.BoolSetting;
import com.amethystclient.ui.AmethystTheme;
import com.amethystclient.ui.Draw;
import com.amethystclient.ui.Ui;

/** "Amethyst Client" in the accent, optionally with the FPS next to it. */
public class WatermarkModule extends HudModule {
	private static final int PAD = 5;

	private final BoolSetting background;
	private final BoolSetting fps;

	public WatermarkModule() {
		super("watermark", "Watermark", "The client's name on screen", Category.CLIENT, false,
				new Position(0.5, 0, 0, MARGIN));
		background = bool("background", "Background", true);
		fps = bool("fps", "Show FPS", false);
	}

	private String suffix() {
		return fps.get() ? "  §7" + Game.fps() + " fps" : "";
	}

	@Override
	public void measure(Draw d, boolean preview) {
		width = d.textWidth("Amethyst Client") + d.textWidth(fps.get() ? "  000 fps" : "") + PAD * 2;
		height = Ui.CHIP_HEIGHT;
	}

	@Override
	public void render(Draw d, AmethystTheme t, int x, int y, boolean preview) {
		if (background.get()) {
			Ui.box(d, x, y, width, height, Ui.RADIUS_BUTTON,
					AmethystTheme.withAlpha(t.bgCard(), backgroundAlpha()),
					AmethystTheme.withAlpha(t.border(), Math.max(backgroundAlpha(), 0.4f)));
			// Accent line along the top, like the ClickGUI headers.
			d.fill(x + 3, y, x + width - 3, y + 1, t.accent());
		}
		int ty = y + (height - d.lineHeight()) / 2 + 1;
		int w = Ui.wordmark(d, t, x + PAD, ty);
		d.text(suffix(), x + PAD + w, ty, AmethystTheme.TEXT_SECONDARY);
	}
}
