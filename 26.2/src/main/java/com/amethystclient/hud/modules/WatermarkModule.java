package com.amethystclient.hud.modules;

import com.amethystclient.hud.Category;
import com.amethystclient.hud.Game;
import com.amethystclient.hud.HudModule;
import com.amethystclient.hud.setting.BoolSetting;
import com.amethystclient.hud.setting.ModeSetting;
import com.amethystclient.hud.setting.SliderSetting;
import com.amethystclient.ui.AmethystTheme;
import com.amethystclient.ui.Draw;
import com.amethystclient.ui.Ui;

/**
 * The Amethyst Community logo (or "Amethyst Client" in the accent) on screen. Only drawn on the
 * Amethyst servers; the HUD editor always shows it so it can be placed.
 */
public class WatermarkModule extends HudModule {
	private static final int PAD = 5;

	/** assets/amethystclient/textures/gui/watermark.png and its size in pixels. */
	private static final String LOGO = "gui/watermark";
	private static final int LOGO_WIDTH = 512;
	private static final int LOGO_HEIGHT = 182;

	private final ModeSetting style;
	private final SliderSetting size;
	private final SliderSetting opacity;
	private final BoolSetting background;
	private final BoolSetting fps;

	public WatermarkModule() {
		// A new id so it's on by default for configs saved while the old watermark was off.
		super("server_watermark", "Watermark", "The Amethyst Community logo, on Amethyst servers only",
				Category.RENDER, true, new Position(1, 1, -MARGIN, -MARGIN));
		style = mode("style", "Style", "Logo", "Logo", "Text");
		size = slider("size", "Size", 40, 200, 5, 80, v -> (int) v + "px")
				.showWhen(this::logo);
		opacity = slider("opacity", "Opacity", 0.1, 1, 0.05, 0.85, v -> Math.round(v * 100) + "%")
				.showWhen(this::logo);
		background = bool("background", "Background", true).showWhen(() -> !logo());
		fps = bool("fps", "Show FPS", false).showWhen(() -> !logo());
	}

	private boolean logo() {
		return style.get().equals("Logo");
	}

	private String suffix() {
		return fps.get() ? "  §7" + Game.fps() + " fps" : "";
	}

	@Override
	public void measure(Draw d, boolean preview) {
		if (!preview && !Game.onAmethystServer()) {
			width = 0;
			height = 0;
		} else if (logo()) {
			width = (int) size.get();
			height = Math.round(width * (float) LOGO_HEIGHT / LOGO_WIDTH);
		} else {
			width = d.textWidth("Amethyst Client") + d.textWidth(fps.get() ? "  000 fps" : "") + PAD * 2;
			height = Ui.CHIP_HEIGHT;
		}
	}

	@Override
	public void render(Draw d, AmethystTheme t, int x, int y, boolean preview) {
		if (logo()) {
			d.alpha((float) opacity.get());
			d.image(LOGO, x, y, width, height, LOGO_WIDTH, LOGO_HEIGHT);
			d.alpha(1f);
			return;
		}
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
