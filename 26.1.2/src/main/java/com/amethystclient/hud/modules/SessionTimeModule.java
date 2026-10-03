package com.amethystclient.hud.modules;

import com.amethystclient.hud.Category;
import java.util.Locale;

/** How long you've been in the current world or server. */
public class SessionTimeModule extends SimpleModule {
	private long joinedAt = System.currentTimeMillis();

	public SessionTimeModule() {
		super("session", "Session Time", "Time since you joined this world or server", Category.INFO, false,
				Position.topLeft(102));
	}

	@Override
	public void onJoinWorld() {
		joinedAt = System.currentTimeMillis();
	}

	@Override
	protected String label() {
		return "SESSION";
	}

	@Override
	protected String value() {
		long seconds = (System.currentTimeMillis() - joinedAt) / 1000;
		long hours = seconds / 3600;
		long minutes = seconds / 60 % 60;
		return hours > 0
				? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds % 60)
				: String.format(Locale.ROOT, "%d:%02d", minutes, seconds % 60);
	}

	@Override
	protected String widthSample() {
		return value().replaceAll("[0-9]", "0");
	}
}
