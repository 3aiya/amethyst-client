package com.amethystclient.hud.modules;

import com.amethystclient.hud.Category;
import com.amethystclient.hud.setting.ModeSetting;

/** How much of the Java heap the game is using. */
public class MemoryModule extends SimpleModule {
	private static final long MB = 1024 * 1024;

	private final ModeSetting format;

	public MemoryModule() {
		super("memory", "Memory", "RAM used by the game", Category.INFO, false, Position.stacked(5));
		format = mode("format", "Show as", "Percent", "Percent", "Used / Max", "Used");
	}

	@Override
	protected String label() {
		return "MEM";
	}

	@Override
	protected String value() {
		Runtime runtime = Runtime.getRuntime();
		long used = (runtime.totalMemory() - runtime.freeMemory()) / MB;
		long max = runtime.maxMemory() / MB;
		return switch (format.get()) {
			case "Used / Max" -> used + "/" + max + " MB";
			case "Used" -> used + " MB";
			default -> Math.round(100.0 * used / max) + "%";
		};
	}

	@Override
	protected String widthSample() {
		return switch (format.get()) {
			case "Used / Max" -> "0000/0000 MB";
			case "Used" -> "0000 MB";
			default -> "00%";
		};
	}
}
