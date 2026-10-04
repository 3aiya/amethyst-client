package com.amethystclient.ui;

import java.util.HashMap;
import java.util.Map;

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
		round(d, x, y, w, h, r, color, true, true);
	}

	/** Like {@link #round}, with only the top and/or bottom corners rounded. */
	public static void round(Draw d, int x, int y, int w, int h, int r, int color, boolean top, boolean bottom) {
		r = Math.min(r, Math.min(w, h) / 2);
		int topRows = top ? r : 0;
		int bottomRows = bottom ? r : 0;
		for (int i = 0; i < topRows; i++) {
			int in = inset(r, i);
			d.fill(x + in, y + i, x + w - in, y + i + 1, color);
		}
		for (int i = 0; i < bottomRows; i++) {
			int in = inset(r, i);
			d.fill(x + in, y + h - i - 1, x + w - in, y + h - i, color);
		}
		d.fill(x, y + topRows, x + w, y + h - bottomRows, color);
	}

	/**
	 * A small pixel icon: one string per row, '#' = filled. Runs of filled pixels are drawn as one
	 * rect each.
	 */
	public static void icon(Draw d, String[] rows, int x, int y, int color) {
		for (int row = 0; row < rows.length; row++) {
			String line = rows[row];
			int start = -1;
			for (int col = 0; col <= line.length(); col++) {
				boolean filled = col < line.length() && line.charAt(col) == '#';
				if (filled && start < 0) {
					start = col;
				} else if (!filled && start >= 0) {
					d.fill(x + start, y + row, x + col, y + row + 1, color);
					start = -1;
				}
			}
		}
	}

	/** Text drawn at {@code scale} with its top-left corner at ({@code x}, {@code y}). */
	public static void text(Draw d, String text, float x, float y, int color, float scale) {
		d.push();
		d.translate(x, y);
		d.scale(scale);
		d.text(text, 0, 0, color);
		d.pop();
	}

	/** The sizes each font family comes in: font/<family>_<oversample×10>.json. */
	private static final int[] SHARP_SIZES = {10, 13, 16, 20, 25, 30, 40};

	/** The size of {@code family} rasterized closest to {@code scale} pixels per unit. */
	private static String sharpFont(String family, float scale) {
		int wanted = Math.round(scale * 10);
		int best = SHARP_SIZES[0];
		for (int size : SHARP_SIZES) {
			if (Math.abs(size - wanted) < Math.abs(best - wanted)) {
				best = size;
			}
		}
		return family + "_" + best;
	}

	/**
	 * Text in a font family from assets/amethystclient/font ("lexend", "inter_medium", ...), for
	 * drawing in screen pixels (1 unit = 1 pixel). Uses the size rasterized closest to
	 * {@code scale}, so the glyphs aren't shrunk from a much bigger bitmap and stay sharp.
	 */
	public static void sharpText(Draw d, String family, String text, float x, float y, int color, float scale) {
		d.push();
		d.translate(x, y);
		d.scale(scale);
		d.text(text, 0, 0, color, sharpFont(family, scale));
		d.pop();
	}

	/** Width of {@link #sharpText}. */
	public static int sharpWidth(Draw d, String family, String text, float scale) {
		return (int) Math.ceil(d.textWidth(text, sharpFont(family, scale)) * scale);
	}

	/** {@code text} cut down with "..." so its {@link #sharpText} fits in {@code maxWidth} pixels. */
	public static String sharpClip(Draw d, String family, String text, int maxWidth, float scale) {
		if (sharpWidth(d, family, text, scale) <= maxWidth) {
			return text;
		}
		String dots = "...";
		int end = text.length();
		while (end > 0 && sharpWidth(d, family, text.substring(0, end) + dots, scale) > maxWidth) {
			end--;
		}
		return text.substring(0, end) + dots;
	}

	public static int width(Draw d, String text, float scale) {
		return (int) Math.ceil(d.textWidth(text) * scale);
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

	// ---- anti-aliased shapes (for drawing in screen pixels) ----

	private static final Map<Integer, float[][]> CORNERS = new HashMap<>();

	/**
	 * How much of each pixel of a top-left corner of radius {@code r} the arc covers (4×4
	 * samples): {@code [row][column]}, 0–1.
	 */
	private static float[][] corner(int r) {
		return CORNERS.computeIfAbsent(r, radius -> {
			float[][] coverage = new float[radius][radius];
			int n = 4;
			for (int i = 0; i < radius; i++) {
				for (int j = 0; j < radius; j++) {
					int inside = 0;
					for (int a = 0; a < n; a++) {
						for (int b = 0; b < n; b++) {
							double dx = radius - (j + (a + 0.5) / n);
							double dy = radius - (i + (b + 0.5) / n);
							if (dx * dx + dy * dy <= radius * radius) {
								inside++;
							}
						}
					}
					coverage[i][j] = inside / (float) (n * n);
				}
			}
			return coverage;
		});
	}

	/**
	 * A rounded rect with anti-aliased corners: edge pixels get the part of the colour's alpha the
	 * arc covers. Meant for screen-pixel drawing, where 1 unit is 1 pixel. Each pixel is drawn once.
	 */
	public static void smooth(Draw d, int x, int y, int w, int h, int r, int color) {
		smooth(d, x, y, w, h, r, color, true, true);
	}

	/** Like {@link #smooth}, with only the top and/or bottom corners rounded. */
	public static void smooth(Draw d, int x, int y, int w, int h, int r, int color, boolean top, boolean bottom) {
		r = Math.min(r, Math.min(w, h) / 2);
		if (r <= 0) {
			d.fill(x, y, x + w, y + h, color);
			return;
		}
		float[][] coverage = corner(r);
		for (int i = 0; i < r; i++) {
			if (top) {
				smoothRow(d, coverage[i], x, y + i, w, color);
			}
			if (bottom) {
				smoothRow(d, coverage[i], x, y + h - 1 - i, w, color);
			}
		}
		d.fill(x, y + (top ? r : 0), x + w, y + h - (bottom ? r : 0), color);
	}

	private static void smoothRow(Draw d, float[] coverage, int x, int y, int w, int color) {
		int full = coverage.length;
		for (int j = 0; j < coverage.length; j++) {
			if (coverage[j] >= 0.999f) {
				full = j;
				break;
			}
		}
		int alpha = color >>> 24;
		for (int j = 0; j < full; j++) {
			if (coverage[j] > 0.02f) {
				int c = (Math.round(alpha * coverage[j]) << 24) | (color & 0x00FFFFFF);
				d.fill(x + j, y, x + j + 1, y + 1, c);
				d.fill(x + w - j - 1, y, x + w - j, y + 1, c);
			}
		}
		d.fill(x + full, y, x + w - full, y + 1, color);
	}

	/** An anti-aliased filled circle of diameter {@code size}. */
	public static void circle(Draw d, int x, int y, int size, int color) {
		smooth(d, x, y, size, size, size / 2, color);
	}

	/**
	 * A soft shadow under a {@link #smooth} rect, spread over {@code size} pixels. The layers are
	 * drawn with {@link #round} (one rect per row): each is only a few percent opaque, so
	 * anti-aliasing them is invisible, and per-pixel corners on every layer made the menu crawl.
	 * At most 8 layers are stacked, each stepping over a band of the spread.
	 */
	public static void smoothShadow(Draw d, int x, int y, int w, int h, int r, int size, float strength) {
		if (size <= 0) {
			return;
		}
		int layers = Math.min(size, 8);
		int color = AmethystTheme.withAlpha(0xFF000000, strength / layers);
		for (int l = layers; l >= 1; l--) {
			int i = Math.round((float) l * size / layers);
			round(d, x - i, y - i + size / 2, w + i * 2, h + i * 2, r + i, color);
		}
	}

	/** A left-to-right gradient, drawn one 1px column at a time (keep it narrow or short-lived). */
	public static void hGradient(Draw d, int x, int y, int w, int h, int from, int to) {
		for (int i = 0; i < w; i++) {
			d.fill(x + i, y, x + i + 1, y + h, AmethystTheme.lerp(from, to, w > 1 ? (float) i / (w - 1) : 0f));
		}
	}

	/** A top-to-bottom gradient, drawn one 1px row at a time. */
	public static void vGradient(Draw d, int x, int y, int w, int h, int from, int to) {
		for (int i = 0; i < h; i++) {
			d.fill(x, y + i, x + w, y + i + 1, AmethystTheme.lerp(from, to, h > 1 ? (float) i / (h - 1) : 0f));
		}
	}

	/**
	 * A soft drop shadow behind a rounded rect: {@code size} stacked, growing translucent rects,
	 * shifted 2px down. {@code strength} is the darkness right under the edge.
	 */
	public static void shadow(Draw d, int x, int y, int w, int h, int r, int size, float strength) {
		int color = AmethystTheme.withAlpha(0xFF000000, strength / size);
		for (int i = size; i >= 1; i--) {
			round(d, x - i, y - i + 2, w + i * 2, h + i * 2, r + i, color);
		}
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
