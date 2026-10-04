package com.amethystclient.hud.modules;

import com.amethystclient.hud.Category;
import com.amethystclient.hud.ClientModule;

public class HideScoreboardModule extends ClientModule {
	public HideScoreboardModule() {
		super("hide_scoreboard", "Hide Scoreboard", "Hides the sidebar scoreboard", Category.SERVER, false);
	}
}
