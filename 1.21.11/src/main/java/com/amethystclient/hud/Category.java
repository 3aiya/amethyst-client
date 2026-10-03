package com.amethystclient.hud;

import com.amethystclient.ui.Icons;

/**
 * A column in the ClickGUI. Every module names its category, and the GUI builds one column per
 * category in this order, so adding a category here is all it takes to get a new column.
 */
public enum Category {
	INFO("Info", Icons.CHART),
	WORLD("World", Icons.COMPASS),
	PLAYER("Player", Icons.PLAYER),
	CLIENT("Client", Icons.GEAR);

	public final String title;
	public final String[] icon;

	Category(String title, String[] icon) {
		this.title = title;
		this.icon = icon;
	}

	/** Key under which the column's position is saved. */
	public String id() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}
}
