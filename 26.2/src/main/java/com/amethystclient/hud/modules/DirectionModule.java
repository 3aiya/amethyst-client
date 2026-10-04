package com.amethystclient.hud.modules;

import com.amethystclient.hud.Category;
import com.amethystclient.hud.Game;
import com.amethystclient.hud.setting.BoolSetting;
import com.amethystclient.hud.setting.ModeSetting;
import java.util.Locale;

/** The direction you're facing, and which axis that walks along. */
public class DirectionModule extends SimpleModule {
	private final ModeSetting style;
	private final BoolSetting axis;

	public DirectionModule() {
		super("direction", "Direction", "The way you're facing", Category.WORLD, false, Position.stacked(7));
		style = mode("style", "Style", "Full", "Full", "Short");
		axis = bool("axis", "Show axis", true);
	}

	@Override
	protected String label() {
		return "FACING";
	}

	@Override
	protected String value() {
		String facing = Game.inWorld() ? Game.facing() : "north";
		String name = style.get().equals("Short")
				? facing.substring(0, 1).toUpperCase(Locale.ROOT)
				: Character.toUpperCase(facing.charAt(0)) + facing.substring(1);
		if (!axis.get()) {
			return name;
		}
		String direction = switch (facing) {
			case "north" -> "-Z";
			case "south" -> "+Z";
			case "east" -> "+X";
			case "west" -> "-X";
			default -> "";
		};
		return name + " §7" + direction;
	}

	@Override
	protected String widthSample() {
		String name = style.get().equals("Short") ? "W" : "North";
		return axis.get() ? name + " §7-X" : name;
	}
}
