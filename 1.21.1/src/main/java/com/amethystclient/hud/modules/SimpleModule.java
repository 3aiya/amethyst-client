package com.amethystclient.hud.modules;

import com.amethystclient.hud.HudModule;
import com.amethystclient.ui.AmethystTheme;
import com.amethystclient.ui.Draw;
import com.amethystclient.ui.Ui;

/** A module that is a single info chip: an uppercase label and a value. */
public abstract class SimpleModule extends HudModule {
	protected SimpleModule(String id, String name, String description, boolean enabledByDefault, Position defaultPosition) {
		super(id, name, description, enabledByDefault, defaultPosition);
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
		width = Ui.chipWidth(d, label(), d.textWidth(sample) >= d.textWidth(value) ? sample : value);
		height = Ui.CHIP_HEIGHT;
	}

	@Override
	public void render(Draw d, AmethystTheme t, int x, int y, boolean preview) {
		Ui.chip(d, t, x, y, width, label(), value(), backgroundAlpha(), false);
	}
}
