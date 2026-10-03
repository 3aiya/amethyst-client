package com.amethystclient.hud.modules;

import com.amethystclient.hud.Game;

public class CoordsModule extends SimpleModule {
	public CoordsModule() {
		super("coords", "Coordinates", "Position and facing", true, Position.topLeft(34));
	}

	@Override
	protected String label() {
		return "XYZ";
	}

	@Override
	protected String value() {
		if (!Game.inWorld()) {
			return "0 64 0  §7N";
		}
		String facing = Game.facing();
		return (int) Math.floor(Game.x()) + " " + (int) Math.floor(Game.y()) + " " + (int) Math.floor(Game.z())
				+ "  §7" + Character.toUpperCase(facing.charAt(0));
	}
}
