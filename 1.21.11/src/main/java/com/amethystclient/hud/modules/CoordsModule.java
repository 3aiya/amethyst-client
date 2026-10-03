package com.amethystclient.hud.modules;

import com.amethystclient.hud.Game;
import com.amethystclient.hud.setting.BoolSetting;
import com.amethystclient.hud.setting.ModeSetting;
import java.util.Locale;

public class CoordsModule extends SimpleModule {
	private final BoolSetting showFacing;
	private final ModeSetting format;

	public CoordsModule() {
		super("coords", "Coordinates", "Position and facing", true, Position.topLeft(34));
		showFacing = bool("facing", "Show facing", true);
		format = mode("format", "Format", "Whole", "Whole", "Decimal");
	}

	@Override
	protected String label() {
		return "XYZ";
	}

	@Override
	protected String value() {
		boolean inWorld = Game.inWorld();
		String text = number(inWorld ? Game.x() : 0) + " " + number(inWorld ? Game.y() : 64) + " " + number(inWorld ? Game.z() : 0);
		if (showFacing.get()) {
			String facing = inWorld ? Game.facing() : "north";
			text += "  §7" + Character.toUpperCase(facing.charAt(0));
		}
		return text;
	}

	private String number(double value) {
		return format.get().equals("Decimal")
				? String.format(Locale.ROOT, "%.1f", value)
				: Integer.toString((int) Math.floor(value));
	}
}
