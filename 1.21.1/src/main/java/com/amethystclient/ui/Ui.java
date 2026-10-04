package com.amethystclient.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntBinaryOperator;

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

	/**
	 * Like {@link #round}, with only the top and/or bottom corners rounded. Corner rows with the
	 * same inset are drawn as one rect, and straight ones join the middle.
	 */
	public static void round(Draw d, int x, int y, int w, int h, int r, int color, boolean top, boolean bottom) {
		r = Math.min(r, Math.min(w, h) / 2);
		int curved = 0;
		while (curved < r && inset(r, curved) > 0) {
			curved++;
		}
		int topRows = top ? curved : 0;
		int bottomRows = bottom ? curved : 0;
		for (int i = 0; i < curved; ) {
			int in = inset(r, i);
			int j = i + 1;
			while (j < curved && inset(r, j) == in) {
				j++;
			}
			if (top) {
				d.fill(x + in, y + i, x + w - in, y + j, color);
			}
			if (bottom) {
				d.fill(x + in, y + h - j, x + w - in, y + h - i, color);
			}
			i = j;
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
	 * with radius {@code r - 1}. Each pixel is drawn once; corner rows that match are drawn as one
	 * rect, and once a row is just the 1px side the side lines take over.
	 */
	public static void outline(Draw d, int x, int y, int w, int h, int r, int color) {
		r = Math.min(r, Math.min(w, h) / 2);
		int ir = Math.max(0, r - 1);
		int edge = inset(r, 0);
		d.fill(x + edge, y, x + w - edge, y + 1, color);
		d.fill(x + edge, y + h - 1, x + w - edge, y + h, color);
		int side = 1;
		while (side < r && (inset(r, side) > 0 || inset(ir, side - 1) > 0)) {
			side++;
		}
		for (int i = 1; i < side; ) {
			int outer = inset(r, i);
			int inner = 1 + inset(ir, i - 1);
			int j = i + 1;
			while (j < side && inset(r, j) == outer && 1 + inset(ir, j - 1) == inner) {
				j++;
			}
			d.fill(x + outer, y + i, x + inner, y + j, color);
			d.fill(x + w - inner, y + i, x + w - outer, y + j, color);
			d.fill(x + outer, y + h - j, x + inner, y + h - i, color);
			d.fill(x + w - inner, y + h - j, x + w - outer, y + h - i, color);
			i = j;
		}
		d.fill(x, y + side, x + 1, y + h - side, color);
		d.fill(x + w - 1, y + side, x + w, y + h - side, color);
	}

	/** A filled rounded rect with a 1px border, without the fill showing under the border. */
	public static void box(Draw d, int x, int y, int w, int h, int r, int fill, int border) {
		round(d, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
		outline(d, x, y, w, h, r, border);
	}

	// ---- anti-aliased shapes (for drawing in screen pixels) ----

	private static final Map<String, Mask> MASKS = new HashMap<>();

	private static Mask mask(String key, int width, int height, IntBinaryOperator alphaAt) {
		return MASKS.computeIfAbsent(key, k -> {
			int[] alpha = new int[width * height];
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					alpha[y * width + x] = alphaAt.applyAsInt(x, y);
				}
			}
			return new Mask(k, width, height, alpha);
		});
	}

	/** An anti-aliased filled circle of diameter {@code size}; its quarters are rounded corners. */
	private static Mask disc(int size) {
		return mask("disc_" + size, size, size, (x, y) -> {
			float radius = size / 2f;
			int inside = 0;
			int n = 4;
			for (int a = 0; a < n; a++) {
				for (int b = 0; b < n; b++) {
					double dx = x + (a + 0.5) / n - radius;
					double dy = y + (b + 0.5) / n - radius;
					if (dx * dx + dy * dy <= radius * radius) {
						inside++;
					}
				}
			}
			int alpha = Math.round(255f * inside / (n * n));
			// Like the old per-pixel corners: barely-covered pixels are left out.
			return alpha <= 5 ? 0 : alpha;
		});
	}

	/** 256×1, alpha 0 to 255 left to right. */
	private static Mask ramp() {
		return mask("ramp", 256, 1, (x, y) -> x);
	}

	/**
	 * A rounded rect with anti-aliased corners: edge pixels get the part of the colour's alpha the
	 * arc covers. Meant for screen-pixel drawing, where 1 unit is 1 pixel. Each pixel is drawn once.
	 */
	public static void smooth(Draw d, int x, int y, int w, int h, int r, int color) {
		smooth(d, x, y, w, h, r, color, true, true);
	}

	/**
	 * Like {@link #smooth}, with only the top and/or bottom corners rounded. Each corner is one
	 * quad of a {@link #disc} mask, so a whole rect is at most 7 draws.
	 */
	public static void smooth(Draw d, int x, int y, int w, int h, int r, int color, boolean top, boolean bottom) {
		r = Math.min(r, Math.min(w, h) / 2);
		if (r <= 0) {
			d.fill(x, y, x + w, y + h, color);
			return;
		}
		Mask disc = disc(2 * r);
		if (top) {
			d.mask(disc, x, y, r, r, 0, 0, r, r, color);
			d.mask(disc, x + w - r, y, r, r, r, 0, r, r, color);
			d.fill(x + r, y, x + w - r, y + r, color);
		}
		if (bottom) {
			d.mask(disc, x, y + h - r, r, r, 0, r, r, r, color);
			d.mask(disc, x + w - r, y + h - r, r, r, r, r, r, r, color);
			d.fill(x + r, y + h - r, x + w - r, y + h, color);
		}
		d.fill(x, y + (top ? r : 0), x + w, y + h - (bottom ? r : 0), color);
	}

	/** An anti-aliased filled circle of diameter {@code size}. */
	public static void circle(Draw d, int x, int y, int size, int color) {
		if (size > 0) {
			d.mask(disc(size), x, y, size, size, 0, 0, size, size, color);
		}
	}

	/**
	 * A soft shadow under a {@link #smooth} rect, spread over {@code size} pixels: up to 8 stacked
	 * translucent {@link #round} layers, each stepping over a band of the spread. The stack is
	 * rendered once into a mask and drawn as 9 pieces (corners, stretched edges, centre).
	 */
	public static void smoothShadow(Draw d, int x, int y, int w, int h, int r, int size, float strength) {
		if (size <= 0) {
			return;
		}
		int layers = Math.min(size, 8);
		int layerColor = AmethystTheme.withAlpha(0xFF000000, strength / layers);
		int ox = x - size;
		int oy = y - size + size / 2;
		int ow = w + size * 2;
		int oh = h + size * 2;
		int c = size + r + 1;
		if (ow <= c * 2 || oh <= c * 2) {
			// Too small to slice: draw the layers.
			for (int l = layers; l >= 1; l--) {
				int i = Math.round((float) l * size / layers);
				round(d, x - i, y - i + size / 2, w + i * 2, h + i * 2, r + i, layerColor);
			}
			return;
		}
		Mask m = shadowMask(r, size, layers, layerColor >>> 24);
		int far = c + 1;
		int black = 0xFF000000;
		d.mask(m, ox, oy, c, c, 0, 0, c, c, black);
		d.mask(m, ox + ow - c, oy, c, c, far, 0, c, c, black);
		d.mask(m, ox, oy + oh - c, c, c, 0, far, c, c, black);
		d.mask(m, ox + ow - c, oy + oh - c, c, c, far, far, c, c, black);
		d.mask(m, ox + c, oy, ow - c * 2, c, c, 0, 1, c, black);
		d.mask(m, ox + c, oy + oh - c, ow - c * 2, c, c, far, 1, c, black);
		d.mask(m, ox, oy + c, c, oh - c * 2, 0, c, c, 1, black);
		d.mask(m, ox + ow - c, oy + c, c, oh - c * 2, far, c, c, 1, black);
		d.fill(ox + c, oy + c, ox + ow - c, oy + oh - c, m.alphaAt(c, c) << 24);
	}

	/**
	 * The layers of {@link #smoothShadow} composited over a rect just big enough for its corners:
	 * {@code 2c + 1} square with {@code c = size + r + 1}, so the middle row and column are the
	 * straight edges.
	 */
	private static Mask shadowMask(int r, int size, int layers, int layerAlpha) {
		int c = size + r + 1;
		int n = c * 2 + 1;
		return mask("shadow_" + r + "_" + size + "_" + layers + "_" + layerAlpha, n, n, (px, py) -> {
			float a = 0f;
			for (int l = layers; l >= 1; l--) {
				int i = Math.round((float) l * size / layers);
				if (inRound(px, py, size - i, size - i, n - (size - i) * 2, n - (size - i) * 2, r + i)) {
					a += layerAlpha / 255f * (1f - a);
				}
			}
			return Math.round(a * 255);
		});
	}

	/** Whether pixel (px, py) is one {@link #round} would fill. */
	private static boolean inRound(int px, int py, int x, int y, int w, int h, int r) {
		if (px < x || py < y || px >= x + w || py >= y + h) {
			return false;
		}
		r = Math.min(r, Math.min(w, h) / 2);
		int row = Math.min(py - y, y + h - 1 - py);
		int in = row < r ? inset(r, row) : 0;
		return px >= x + in && px < x + w - in;
	}

	/** A left-to-right gradient. */
	public static void hGradient(Draw d, int x, int y, int w, int h, int from, int to) {
		if (w <= 0 || h <= 0) {
			return;
		}
		if (from >>> 24 == 0xFF && to >>> 24 == 0xFF) {
			// Opaque: "to" faded in over "from" with a 0–255 alpha ramp is the same blend.
			d.fill(x, y, x + w, y + h, from);
			d.mask(ramp(), x, y, w, h, 0, 0, 256, 1, to);
			return;
		}
		int bands = Math.min(w, 32);
		for (int b = 0; b < bands; b++) {
			int x1 = x + w * b / bands;
			int x2 = x + w * (b + 1) / bands;
			d.fill(x1, y, x2, y + h, AmethystTheme.lerp(from, to, bands > 1 ? (float) b / (bands - 1) : 0f));
		}
	}

	/** A top-to-bottom gradient. */
	public static void vGradient(Draw d, int x, int y, int w, int h, int from, int to) {
		if (w > 0 && h > 0) {
			d.gradient(x, y, x + w, y + h, from, to);
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
