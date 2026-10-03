package com.amethystclient.ui;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * The only class the shared UI code draws through. Each Minecraft version has its own copy that
 * maps these calls onto that version's GUI API, so widgets, pages and HUD modules stay the same in
 * every version.
 */
public final class Draw {
	/** The UI font (Quicksand, see assets/amethystclient/font/ui.json). */
	private static final StyleSpriteSource UI_FONT = new StyleSpriteSource.Font(Identifier.of("amethystclient", "ui"));
	/** The other fonts in assets/amethystclient/font, by name (e.g. "lexend_20"). */
	private static final Map<String, StyleSpriteSource> SHARP_FONTS = new HashMap<>();

	public final DrawContext graphics;
	private final TextRenderer font = MinecraftClient.getInstance().textRenderer;
	private float alpha = 1f;

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

	private static Text styled(String text, StyleSpriteSource uiFont) {
		return Text.literal(text).styled(style -> style.withFont(uiFont));
	}

	/** Like {@link #text}, in another font from assets/amethystclient/font (e.g. "lexend_20"). */
	public void text(String text, int x, int y, int color, String fontName) {
		color = apply(color);
		if (color >>> 24 >= 8) {
			StyleSpriteSource uiFont = font(fontName);
			graphics.drawText(font, styled(text, uiFont), x, y, color, false);
		}
	}

	public int textWidth(String text, String fontName) {
		return font.getWidth(styled(text, font(fontName)));
	}

	private static StyleSpriteSource font(String name) {
		return SHARP_FONTS.computeIfAbsent(name, n -> new StyleSpriteSource.Font(Identifier.of("amethystclient", n)));
	}

	public int lineHeight() {
		return font.fontHeight;
	}

	// ---- transform, clipping, layering ----

	public void push() {
		graphics.getMatrices().pushMatrix();
	}

	public void translate(float x, float y) {
		graphics.getMatrices().translate(x, y);
	}

	public void scale(float s) {
		graphics.getMatrices().scale(s, s);
	}

	public void pop() {
		graphics.getMatrices().popMatrix();
	}

	public void clip(int x1, int y1, int x2, int y2) {
		graphics.enableScissor(x1, y1, x2, y2);
	}

	public void unclip() {
		graphics.disableScissor();
	}

	/** Starts a new layer: everything drawn after this is on top of everything before, text included. */
	public void layer() {
		graphics.createNewRootLayer();
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
