package com.amethystclient.ui.widget;

import com.amethystclient.ui.AmethystTheme;
import com.amethystclient.ui.Draw;
import com.amethystclient.ui.Ui;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A row of chips where one is selected (preset chips, §5.6/§5.8): the selected one gets the
 * highlighted look (accent 10% fill, accent 30% border, accent text).
 */
public class ChipSelect extends Widget {
	private static final int GAP = 4;

	private final List<String> options;
	private final Supplier<String> getter;
	private final Consumer<String> setter;
	/** Optional colour swatch per option, 0 for none. */
	private final List<Integer> swatches;

	public ChipSelect(List<String> options, List<Integer> swatches, Supplier<String> getter, Consumer<String> setter) {
		this.options = options;
		this.swatches = swatches;
		this.getter = getter;
		this.setter = setter;
		this.height = 16;
	}

	private int chipWidth() {
		return (width - GAP * (options.size() - 1)) / options.size();
	}

	@Override
	public void render(Draw d, AmethystTheme t, int mx, int my) {
		int cw = chipWidth();
		for (int i = 0; i < options.size(); i++) {
			String option = options.get(i);
			int cx = x + i * (cw + GAP);
			boolean selected = option.equalsIgnoreCase(getter.get());
			boolean hover = enabled && mx >= cx && mx < cx + cw && my >= y && my < y + height;
			int fill = selected ? AmethystTheme.withAlpha(t.accent(), 0.10f) : hover ? t.bgCardHover() : t.bgElevated();
			int border = selected ? AmethystTheme.withAlpha(t.accent(), 0.30f) : hover ? t.borderLight() : t.border();
			Ui.box(d, cx, y, cw, height, Ui.RADIUS_BUTTON, fill, border);
			int swatch = swatches.isEmpty() ? 0 : swatches.get(i);
			int textWidth = d.textWidth(option) + (swatch != 0 ? 9 : 0);
			int tx = cx + (cw - textWidth) / 2;
			int ty = y + (height - d.lineHeight()) / 2 + 1;
			if (swatch != 0) {
				Ui.round(d, tx, ty, 6, 6, 3, swatch);
				tx += 9;
			}
			d.text(option, tx, ty, selected ? t.accent() : hover ? AmethystTheme.TEXT_HOVER : AmethystTheme.TEXT_LABEL);
		}
	}

	@Override
	protected void onPress(double mx, double my) {
		int cw = chipWidth();
		int i = (int) ((mx - x) / (cw + GAP));
		if (i >= 0 && i < options.size() && mx - x - i * (cw + GAP) < cw) {
			setter.accept(options.get(i));
		}
	}
}
