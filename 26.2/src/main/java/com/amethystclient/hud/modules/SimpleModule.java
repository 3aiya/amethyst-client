package com.amethystclient.hud.modules;

import com.amethystclient.hud.Category;
import com.amethystclient.hud.HudModule;
import com.amethystclient.hud.setting.BoolSetting;
import com.amethystclient.ui.AmethystTheme;
import com.amethystclient.ui.Draw;
import com.amethystclient.ui.Ui;

/** A module that is a single info chip: an uppercase label and a value. */
public abstract class SimpleModule extends HudModule {
	private final BoolSetting showLabel;

	protected SimpleModule(String id, String name, String description, Category category, boolean enabledByDefault,
			Position defaultPosition) {
		super(id, name, description, category, enabledByDefault, defaultPosition);
		showLabel = bool("label", "Show label", true);
	}

	private String shownLabel() {
		return showLabel.get() ? label() : "";
	}

	protected abstract String label();

	protected abstract String value();

	/** Values that change length (numbers) get padded so the chip doesn't jitter in width. */
	protected String widthSample() {
		return value();
	}

	@Override
	public void measure(Draw d, boolean preview) {
		String sample = widthSample();
		String value = value();
		width = Ui.chipWidth(d, shownLabel(), d.textWidth(sample) >= d.textWidth(value) ? sample : value);
		height = Ui.CHIP_HEIGHT;
	}

	@Override
	public void render(Draw d, AmethystTheme t, int x, int y, boolean preview) {
		Ui.chip(d, t, x, y, width, shownLabel(), value(), backgroundAlpha(), false);
	}
}
