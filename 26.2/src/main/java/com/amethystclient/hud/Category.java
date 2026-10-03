package com.amethystclient.hud;

import com.amethystclient.ui.Glyphs;
import java.util.Locale;

/**
 * A column in the ClickGUI. Every module names its category, and the GUI builds one column per
 * category in this order, so adding a category here is all it takes to get a new column.
 */
public enum Category {
	INFO("Info", Glyphs.CHART),
	WORLD("World", Glyphs.GLOBE),
	RENDER("Render", Glyphs.CUBE),
	PLAYER("Player", Glyphs.PLAYER),
	MISC("Misc", Glyphs.GEAR);

	public final String title;
	/** A {@link Glyphs} icon. */
	public final String icon;

	Category(String title, String icon) {
		this.title = title;
		this.icon = icon;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}
}
