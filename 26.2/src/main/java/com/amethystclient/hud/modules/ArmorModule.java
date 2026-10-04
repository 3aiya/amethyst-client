package com.amethystclient.hud.modules;

import com.amethystclient.hud.Category;
import com.amethystclient.hud.Game;
import com.amethystclient.hud.HudModule;
import com.amethystclient.hud.setting.BoolSetting;
import com.amethystclient.hud.setting.ModeSetting;
import com.amethystclient.ui.AmethystTheme;
import com.amethystclient.ui.Draw;
import com.amethystclient.ui.Ui;
import java.util.List;

/** Worn armour and the held item, each with the durability left. */
public class ArmorModule extends HudModule {
	private static final int PAD = 3;
	private static final int ROW = 17;

	private final BoolSetting held;
	private final BoolSetting durability;
	private final ModeSetting durabilityFormat;
	private List<Game.Item> items = List.of();

	public ArmorModule() {
		// Bottom right, above the watermark.
		super("armor", "Armour", "Armour and held item durability", Category.PLAYER, false,
				new Position(1, 1, -MARGIN, -33));
		held = bool("held", "Held item", true);
		durability = bool("durability", "Durability", true);
		durabilityFormat = mode("durability_format", "Durability as", "Percent", "Percent", "Bar")
				.showWhen(durability::get);
	}

	private boolean showText() {
		return durability.get() && durabilityFormat.get().equals("Percent");
	}

	private boolean showBar() {
		return durability.get() && durabilityFormat.get().equals("Bar");
	}

	@Override
	public void measure(Draw d, boolean preview) {
		items = Game.inWorld() ? Game.equipment(held.get()) : List.of();
		if (items.isEmpty()) {
			// Nothing to show; in the editor it still needs a size to be dragged.
			width = preview ? Ui.chipWidth(d, "", "No armour") : 0;
			height = preview ? Ui.CHIP_HEIGHT : 0;
			return;
		}
		width = PAD * 2 + 16 + (showText() ? 4 + d.textWidth("100%") : 0);
		height = PAD * 2 + items.size() * ROW - 1;
	}

	@Override
	public void render(Draw d, AmethystTheme t, int x, int y, boolean preview) {
		if (items.isEmpty()) {
			if (preview) {
				Ui.chip(d, t, x, y, width, "", "§7No armour", backgroundAlpha(), false);
			}
			return;
		}
		Ui.box(d, x, y, width, height, Ui.RADIUS_CARD,
				AmethystTheme.withAlpha(t.bgCard(), backgroundAlpha()),
				AmethystTheme.withAlpha(t.border(), Math.max(backgroundAlpha(), 0.4f)));
		int rowY = y + PAD;
		for (Game.Item item : items) {
			Game.drawItem(d, item, x + PAD, rowY);
			if (item.durability() >= 0) {
				int color = durabilityColor(item.durability());
				if (showText()) {
					String text = item.durability() + "%";
					d.text(text, x + width - PAD - d.textWidth(text), rowY + 5, color);
				} else if (showBar()) {
					d.fill(x + PAD + 2, rowY + 15, x + PAD + 14, rowY + 16, 0xFF000000);
					d.fill(x + PAD + 2, rowY + 15, x + PAD + 2 + Math.round(item.durability() * 12 / 100f), rowY + 16, color);
				}
			}
			rowY += ROW;
		}
	}

	private static int durabilityColor(int percent) {
		if (percent > 50) {
			return AmethystTheme.SUCCESS_TEXT;
		}
		return percent > 20 ? AmethystTheme.WARNING : AmethystTheme.DANGER;
	}
}
