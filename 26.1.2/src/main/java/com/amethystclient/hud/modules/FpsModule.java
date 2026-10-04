package com.amethystclient.hud.modules;

import com.amethystclient.hud.Category;
import com.amethystclient.hud.Game;

public class FpsModule extends SimpleModule {
	public FpsModule() {
		super("fps", "FPS", "Frames per second", Category.INFO, false, Position.stacked(0));
	}

	@Override
	protected String label() {
		return "FPS";
	}

	@Override
	protected String value() {
		return Integer.toString(Game.fps());
	}

	@Override
	protected String widthSample() {
		return "000";
	}
}
