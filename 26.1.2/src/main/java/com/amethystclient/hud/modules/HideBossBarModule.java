package com.amethystclient.hud.modules;

import com.amethystclient.hud.Category;
import com.amethystclient.hud.ClientModule;

public class HideBossBarModule extends ClientModule {
	public HideBossBarModule() {
		super("hide_boss_bar", "Hide Boss Bar", "Hides the boss bars at the top of the screen", Category.SERVER, false);
	}
}
