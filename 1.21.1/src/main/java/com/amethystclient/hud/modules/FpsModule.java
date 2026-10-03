package com.amethystclient.hud.modules;

import com.amethystclient.hud.Game;

public class FpsModule extends SimpleModule {
	public FpsModule() {
		super("fps", "FPS", "Frames per second", true, Position.topLeft(0));
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
