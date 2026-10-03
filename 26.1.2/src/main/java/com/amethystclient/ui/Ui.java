package com.amethystclient.ui;

/**
 * Shared drawing building blocks from the UI guide (§4–§6): rounded rects, cards, glows, section
 * titles, info chips. Radii are in GUI pixels (about half the launcher's CSS pixels).
 */
public final class Ui {
	public static final int RADIUS_BUTTON = 3;
	public static final int RADIUS_CARD = 4;
	public static final int RADIUS_PANEL = 6;

	private Ui() {
	}

	// ---- rounded shapes ----

	/** Horizontal inset of row {@code i} (0 = outermost) of a corner with radius {@code r}. */
	private static int inset(int r, int i) {
		if (i >= r) {
			return 0;
		}
		double dy = r - i - 0.5;
		return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
	}

	/** A filled rectangle with rounded corners. Each pixel is drawn once, so translucent colours work. */
	public static void round(Draw d, int x, int y, int w, int h, int r, int color) {
		r = Math.min(r, Math.min(w, h) / 2);
		for (int i = 0; i < r; i++) {
			int in = inset(r, i);
			d.fill(x + in, y + i, x + w - in, y + i + 1, color);
			d.fill(x + in, y + h - i - 1, x + w - in, y + h - i, color);
		}
		d.fill(x, y + r, x + w, y + h - r, color);
	}

	/**
	 * A 1px rounded outline: the pixels of {@code round(r)} that aren't in the shape inset by 1px
	 * with radius {@code r - 1}. Each pixel is drawn once.
	 */
	public static void outline(Draw d, int x, int y, int w, int h, int r, int color) {
		r = Math.min(r, Math.min(w, h) / 2);
		int ir = Math.max(0, r - 1);
		d.fill(x + inset(r, 0), y, x + w - inset(r, 0), y + 1, color);
		d.fill(x + inset(r, 0), y + h - 1, x + w - inset(r, 0), y + h, color);
		for (int i = 1; i < r; i++) {
			int outer = inset(r, i);
			int inner = 1 + inset(ir, i - 1);
			d.fill(x + outer, y + i, x + inner, y + i + 1, color);
			d.fill(x + w - inner, y + i, x + w - outer, y + i + 1, color);
			d.fill(x + outer, y + h - i - 1, x + inner, y + h - i, color);
			d.fill(x + w - inner, y + h - i - 1, x + w - outer, y + h - i, color);
		}
		int from = Math.max(1, r);
		d.fill(x, y + from, x + 1, y + h - from, color);
		d.fill(x + w - 1, y + from, x + w, y + h - from, color);
	}

	/** A filled rounded rect with a 1px border, without the fill showing under the border. */
	public static void box(Draw d, int x, int y, int w, int h, int r, int fill, int border) {
		round(d, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
		outline(d, x, y, w, h, r, border);
	}

	/** Card (§5.3): bg-card, 1px border; on hover bg-card-hover and an accent-50% border. */
	public static void card(Draw d, AmethystTheme t, int x, int y, int w, int h, boolean hovered) {
		box(d, x, y, w, h, RADIUS_CARD,
				hovered ? t.bgCardHover() : t.bgCard(),
				hovered ? AmethystTheme.withAlpha(t.accent(), 0.5f) : t.border());
	}

	// ---- text ----

	/** Section title: a plain muted label. Returns its height. */
	public static int sectionTitle(Draw d, AmethystTheme t, String label, int x, int y) {
		d.text(label, x, y, AmethystTheme.TEXT_MUTED);
		return d.lineHeight();
	}

	/** "Amethyst" in zinc-400 + " Client" in the accent (§5.18). Returns its width. */
	public static int wordmark(Draw d, AmethystTheme t, int x, int y) {
		String first = "Amethyst";
		String second = " Client";
		d.text(first, x, y, AmethystTheme.TEXT_LABEL);
		d.text(second, x + d.textWidth(first), y, t.accent());
		return d.textWidth(first) + d.textWidth(second);
	}

	public static void centered(Draw d, String text, int centerX, int y, int color) {
		d.text(text, centerX - d.textWidth(text) / 2, y, color);
	}

	/** {@code text} cut down with "..." so it fits in {@code maxWidth}. */
	public static String clip(Draw d, String text, int maxWidth) {
		if (d.textWidth(text) <= maxWidth) {
			return text;
		}
		String dots = "...";
		int end = text.length();
		while (end > 0 && d.textWidth(text.substring(0, end)) + d.textWidth(dots) > maxWidth) {
			end--;
		}
		return text.substring(0, end) + dots;
	}

	// ---- info chip (§5.8) ----

	public static final int CHIP_HEIGHT = 15;
	public static final int CHIP_PAD = 5;

	public static int chipWidth(Draw d, String label, String value) {
		int w = CHIP_PAD * 2 + d.textWidth(value);
		if (!label.isEmpty()) {
			w += d.textWidth(label) + 4;
		}
		return w;
	}

	/**
	 * A HUD info chip: translucent bg-card, 1px border, uppercase muted label, then the value in
	 * bold-white. {@code highlighted} gives the accent-tinted "currently active" variant.
	 */
	public static void chip(Draw d, AmethystTheme t, int x, int y, int w, String label, String value,
			float bgAlpha, boolean highlighted) {
		int fill = highlighted
				? AmethystTheme.lerp(AmethystTheme.withAlpha(t.bgCard(), bgAlpha), t.accent(), 0.10f)
				: AmethystTheme.withAlpha(t.bgCard(), bgAlpha);
		int border = highlighted ? AmethystTheme.withAlpha(t.accent(), 0.3f) : AmethystTheme.withAlpha(t.border(), Math.max(bgAlpha, 0.4f));
		box(d, x, y, w, CHIP_HEIGHT, RADIUS_BUTTON, fill, border);
		int tx = x + CHIP_PAD;
		int ty = y + (CHIP_HEIGHT - d.lineHeight()) / 2 + 1;
		if (!label.isEmpty()) {
			d.text(label, tx, ty, highlighted ? t.accent() : AmethystTheme.TEXT_MUTED);
			tx += d.textWidth(label) + 4;
		}
		d.text(value, tx, ty, AmethystTheme.TEXT_HEADLINE);
	}
}
