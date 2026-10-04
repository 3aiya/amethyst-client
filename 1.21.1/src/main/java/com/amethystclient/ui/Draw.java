package com.amethystclient.ui;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The only class the shared UI code draws through. Each Minecraft version has its own copy that
 * maps these calls onto that version's GUI API, so widgets, pages and HUD modules stay the same in
 * every version.
 */
public final class Draw {
	/** The UI font (Quicksand, see assets/amethystclient/font/ui.json). */
	private static final Identifier UI_FONT = Identifier.of("amethystclient", "ui");
	/** The other fonts in assets/amethystclient/font, by name (e.g. "lexend_20"). */
	private static final Map<String, Identifier> SHARP_FONTS = new HashMap<>();
	/** The styled text of recent strings, by font, so the same text isn't rebuilt every frame. */
	private static final Map<Identifier, Map<String, Text>> STYLED = new HashMap<>();

	public final DrawContext graphics;
	private final TextRenderer font = MinecraftClient.getInstance().textRenderer;
	private float alpha = 1f;

	private static final float LAYER_DEPTH = 400;

	public Draw(DrawContext graphics) {
		this.graphics = graphics;
	}

	public int width() {
		return graphics.getScaledWindowWidth();
	}

	public int height() {
		return graphics.getScaledWindowHeight();
	}

	/** Screen pixels per GUI pixel. Drawing inside {@code scale(1 / guiScale())} works in screen pixels. */
	public float guiScale() {
		return (float) MinecraftClient.getInstance().getWindow().getScaleFactor();
	}

	// ---- shapes ----

	public void fill(int x1, int y1, int x2, int y2, int color) {
		color = apply(color);
		if (x2 > x1 && y2 > y1 && color >>> 24 != 0) {
			graphics.fill(x1, y1, x2, y2, color);
		}
	}

	/** A rect fading from {@code top} to {@code bottom}. */
	public void gradient(int x1, int y1, int x2, int y2, int top, int bottom) {
		if (x2 > x1 && y2 > y1) {
			graphics.fillGradient(x1, y1, x2, y2, apply(top), apply(bottom));
		}
	}
	/**
	 * Draws the ({@code u}, {@code v}, {@code uw}×{@code vh}) part of {@code mask}, tinted with
	 * {@code color}, stretched over ({@code x}, {@code y}, {@code w}×{@code h}). Here a fill per run
	 * of equal alpha: this version's fills are drawn straight into a buffer and are cheap.
	 */
	public void mask(Mask mask, int x, int y, int w, int h, int u, int v, int uw, int vh, int color) {
		color = apply(color);
		if (w <= 0 || h <= 0 || color >>> 24 == 0) {
			return;
		}
		int alpha = color >>> 24;
		int rgb = color & 0xFFFFFF;
		int row = 0;
		while (row < h) {
			int sy = v + row * vh / h;
			// Rows stretched from the same mask row are drawn together.
			int rowEnd = row + 1;
			while (rowEnd < h && v + rowEnd * vh / h == sy) {
				rowEnd++;
			}
			int col = 0;
			while (col < w) {
				int a = mask.alphaAt(u + col * uw / w, sy);
				int end = col + 1;
				while (end < w && mask.alphaAt(u + end * uw / w, sy) == a) {
					end++;
				}
				if (a > 0) {
					graphics.fill(x + col, y + row, x + end, y + rowEnd, (alpha * a / 255) << 24 | rgb);
				}
				col = end;
			}
			row = rowEnd;
		}
	}

	// ---- text ----

	public void text(String text, int x, int y, int color) {
		color = apply(color);
		// Very low alphas are drawn fully opaque by the font renderer, so skip them.
		if (color >>> 24 >= 8) {
			graphics.drawText(font, styled(text), x, y, color, false);
		}
	}

	public int textWidth(String text) {
		return font.getWidth(styled(text));
	}

	private static Text styled(String text) {
		return styled(text, UI_FONT);
	}

	private static Text styled(String text, Identifier uiFont) {
		Map<String, Text> cache = STYLED.computeIfAbsent(uiFont, f -> new HashMap<>());
		if (cache.size() > 512) {
			cache.clear();
		}
		return cache.computeIfAbsent(text, t -> Text.literal(t).styled(style -> style.withFont(uiFont)));
	}

	/** Like {@link #text}, in another font from assets/amethystclient/font (e.g. "lexend_20"). */
	public void text(String text, int x, int y, int color, String fontName) {
		color = apply(color);
		if (color >>> 24 >= 8) {
			Identifier uiFont = font(fontName);
			graphics.drawText(font, styled(text, uiFont), x, y, color, false);
		}
	}

	public int textWidth(String text, String fontName) {
		return font.getWidth(styled(text, font(fontName)));
	}

	private static Identifier font(String name) {
		return SHARP_FONTS.computeIfAbsent(name, n -> Identifier.of("amethystclient", n));
	}

	public int lineHeight() {
		return font.fontHeight;
	}

	// ---- transform, clipping, layering ----

	public void push() {
		graphics.getMatrices().push();
	}

	public void translate(float x, float y) {
		graphics.getMatrices().translate(x, y, 0);
	}

	public void scale(float s) {
		graphics.getMatrices().scale(s, s, 1);
	}

	public void pop() {
		graphics.getMatrices().pop();
	}

	/** Clips to a rect in the current (transformed) coordinates, like newer versions do. */
	public void clip(int x1, int y1, int x2, int y2) {
		Matrix4f matrix = graphics.getMatrices().peek().getPositionMatrix();
		Vector3f from = matrix.transformPosition(new Vector3f(x1, y1, 0));
		Vector3f to = matrix.transformPosition(new Vector3f(x2, y2, 0));
		graphics.enableScissor((int) Math.floor(from.x), (int) Math.floor(from.y), (int) Math.ceil(to.x), (int) Math.ceil(to.y));
	}

	public void unclip() {
		graphics.disableScissor();
	}

	/**
	 * Starts a new layer: everything drawn after this is on top of everything before, items
	 * included. This version draws in order, but items are drawn at a higher depth, so the layer is
	 * moved above them. Only call it inside a {@link #push()}/{@link #pop()} pair.
	 */
	public void layer() {
		graphics.getMatrices().translate(0, 0, LAYER_DEPTH);
	}

	/** Multiplies the alpha of everything drawn until it's reset to 1. Used for fade-ins. */
	public void alpha(float value) {
		alpha = Math.clamp(value, 0f, 1f);
	}

	private int apply(int color) {
		if (alpha >= 1f) {
			return color;
		}
		return ((int) ((color >>> 24) * alpha) << 24) | (color & 0x00FFFFFF);
	}
}
