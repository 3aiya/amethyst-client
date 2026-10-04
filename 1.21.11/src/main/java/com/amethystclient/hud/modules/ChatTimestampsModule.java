package com.amethystclient.hud.modules;

import com.amethystclient.hud.Category;
import com.amethystclient.hud.ClientModule;
import com.amethystclient.hud.setting.BoolSetting;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Puts the time in front of every chat message (see ChatTweaks). */
public class ChatTimestampsModule extends ClientModule {
	private final BoolSetting twentyFour;
	private final BoolSetting seconds;

	public ChatTimestampsModule() {
		super("chat_timestamps", "Chat Timestamps", "Shows when each chat message arrived", Category.SERVER, false);
		twentyFour = bool("24h", "24-hour clock", true);
		seconds = bool("seconds", "Seconds", false);
	}

	/** "[14:05] ", "[2:05 PM] ", ... */
	public String prefix() {
		String pattern = (twentyFour.get() ? "HH:mm" : "h:mm") + (seconds.get() ? ":ss" : "") + (twentyFour.get() ? "" : " a");
		return "[" + LocalTime.now().format(DateTimeFormatter.ofPattern(pattern)) + "] ";
	}
}
