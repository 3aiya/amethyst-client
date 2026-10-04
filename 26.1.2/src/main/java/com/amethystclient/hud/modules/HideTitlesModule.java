package com.amethystclient.hud.modules;

import com.amethystclient.hud.Category;
import com.amethystclient.hud.ClientModule;

public class HideTitlesModule extends ClientModule {
	public HideTitlesModule() {
		super("hide_titles", "Hide Titles", "Hides the big titles and subtitles servers show", Category.SERVER, false);
	}
}
