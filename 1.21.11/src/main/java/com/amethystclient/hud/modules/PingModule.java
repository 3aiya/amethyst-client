package com.amethystclient.hud.modules;

import com.amethystclient.hud.Category;
import com.amethystclient.hud.Game;

public class PingModule extends SimpleModule {
	public PingModule() {
		super("ping", "Ping", "Your latency to the server", Category.INFO, false, Position.stacked(1));
	}

	@Override
	protected String label() {
		return "PING";
	}

	@Override
	protected String value() {
		int ping = Game.ping();
		return switch (ping) {
			case -1 -> "Local";
			case -2 -> "--";
			default -> ping + " ms";
		};
	}

	@Override
	protected String widthSample() {
		return "000 ms";
	}
}
