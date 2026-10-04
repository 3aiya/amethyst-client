package com.amethystclient.hud.modules;

import com.amethystclient.hud.Category;
import com.amethystclient.hud.setting.BoolSetting;
import com.amethystclient.hud.setting.ModeSetting;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** The real-world time. */
public class ClockModule extends SimpleModule {
	private final ModeSetting format;
	private final BoolSetting seconds;

	public ClockModule() {
		super("clock", "Clock", "Your computer's time", Category.MISC, false, Position.stacked(4));
		format = mode("format", "Format", "24h", "24h", "12h");
		seconds = bool("seconds", "Seconds", false);
	}

	private String pattern() {
		String time = seconds.get() ? "mm:ss" : "mm";
		return format.get().equals("12h") ? "h:" + time + " a" : "HH:" + time;
	}

	@Override
	protected String label() {
		return "TIME";
	}

	@Override
	protected String value() {
		return LocalTime.now().format(DateTimeFormatter.ofPattern(pattern(), Locale.ROOT));
	}

	@Override
	protected String widthSample() {
		return value().replaceAll("[0-9]", "0");
	}
}
