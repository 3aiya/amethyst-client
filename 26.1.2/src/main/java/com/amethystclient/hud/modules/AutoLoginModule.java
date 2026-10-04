package com.amethystclient.hud.modules;

import com.amethystclient.hud.Category;
import com.amethystclient.hud.ClientModule;

/** Turns AutoLogin on or off. */
public class AutoLoginModule extends ClientModule {
	public AutoLoginModule() {
		super("auto_login", "Auto Login", "Logs you in with your saved password", Category.SERVER, true);
	}
}
