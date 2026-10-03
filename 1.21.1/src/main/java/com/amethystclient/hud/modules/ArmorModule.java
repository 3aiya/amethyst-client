package com.amethystclient.hud.modules;

import com.amethystclient.hud.Game;
import com.amethystclient.hud.HudModule;
import com.amethystclient.ui.AmethystTheme;
import com.amethystclient.ui.Draw;
import com.amethystclient.ui.Ui;
import java.util.List;

/** Worn armour and the held item, each with the durability left. */
public class ArmorModule extends HudModule {
	private static final int PAD = 3;
	private static final int ROW = 17;
	private static final String SAMPLE = "100%";

	private List<Game.Item> items = List.of();

	public ArmorModule() {
		super("armor", "Armour", "Armour and held item durability", true, new Position(1, 0.5, -MARGIN, 0));
	}

	@Override
	public void measure(Draw d, boolean preview) {
		items = Game.inWorld() ? Game.equipment() : List.of();
		if (items.isEmpty()) {
			// Nothing to show; in the editor it still needs a size to be dragged.
			width = preview ? Ui.chipWidth(d, "", "No armour") : 0;
			height = preview ? Ui.CHIP_HEIGHT : 0;
			return;
		}
		width = PAD * 2 + 16 + 4 + d.textWidth(SAMPLE);
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
				String text = item.durability() + "%";
				d.text(text, x + width - PAD - d.textWidth(text), rowY + 5, durabilityColor(item.durability()));
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
