package com.amethystclient.hud.modules;

import com.amethystclient.hud.Category;
import com.amethystclient.hud.ClientModule;
import com.amethystclient.hud.setting.BoolSetting;

/** Turns the Discord Rich Presence (see PresenceTracker) on or off. */
public class DiscordPresenceModule extends ClientModule {
	private final BoolSetting mode;
	private final BoolSetting address;

	public DiscordPresenceModule() {
		super("discord_presence", "Discord Presence", "Shows what you're playing on your Discord profile",
				Category.SERVER, true);
		mode = bool("mode", "Show mode", true);
		address = bool("address", "Show server address", true);
	}

	/** Show the mode (Survival, Hub, ...) on Amethyst servers. */
	public boolean showMode() {
		return mode.get();
	}

	public boolean showAddress() {
		return address.get();
	}
}
