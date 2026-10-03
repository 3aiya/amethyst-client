package com.amethystclient.ui;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/**
 * The only class the shared UI code draws through. Each Minecraft version has its own copy that
 * maps these calls onto that version's GUI API, so widgets, pages and HUD modules stay the same in
 * every version.
 */
public final class Draw {
	/** The UI font (Quicksand, see assets/amethystclient/font/ui.json). */
	private static final FontDescription UI_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("amethystclient", "ui"));
	/** The other fonts in assets/amethystclient/font, by name (e.g. "lexend_20"). */
	private static final Map<String, FontDescription> SHARP_FONTS = new HashMap<>();

	public final GuiGraphicsExtractor graphics;
	private final Font font = Minecraft.getInstance().font;
	private float alpha = 1f;

	public Draw(GuiGraphicsExtractor graphics) {
		this.graphics = graphics;
	}

	public int width() {
		return graphics.guiWidth();
	}

	public int height() {
		return graphics.guiHeight();
	}

	/** Screen pixels per GUI pixel. Drawing inside {@code scale(1 / guiScale())} works in screen pixels. */
	public float guiScale() {
		return (float) Minecraft.getInstance().getWindow().getGuiScale();
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
			graphics.text(font, styled(text), x, y, color, false);
		}
	}

	public int textWidth(String text) {
		return font.width(styled(text));
	}

	private static Component styled(String text) {
		return styled(text, UI_FONT);
	}

	private static Component styled(String text, FontDescription uiFont) {
		return Component.literal(text).withStyle(style -> style.withFont(uiFont));
	}

	/** Like {@link #text}, in another font from assets/amethystclient/font (e.g. "lexend_20"). */
	public void text(String text, int x, int y, int color, String fontName) {
		color = apply(color);
		if (color >>> 24 >= 8) {
			FontDescription uiFont = font(fontName);
			graphics.text(font, styled(text, uiFont), x, y, color, false);
		}
	}

	public int textWidth(String text, String fontName) {
		return font.width(styled(text, font(fontName)));
	}

	private static FontDescription font(String name) {
		return SHARP_FONTS.computeIfAbsent(name, n -> new FontDescription.Resource(Identifier.fromNamespaceAndPath("amethystclient", n)));
	}

	public int lineHeight() {
		return font.lineHeight;
	}

	// ---- transform, clipping, layering ----

	public void push() {
		graphics.pose().pushMatrix();
	}

	public void translate(float x, float y) {
		graphics.pose().translate(x, y);
	}

	public void scale(float s) {
		graphics.pose().scale(s, s);
	}

	public void pop() {
		graphics.pose().popMatrix();
	}

	public void clip(int x1, int y1, int x2, int y2) {
		graphics.enableScissor(x1, y1, x2, y2);
	}

	public void unclip() {
		graphics.disableScissor();
	}

	/** Starts a new layer: everything drawn after this is on top of everything before, text included. */
	public void layer() {
		graphics.nextStratum();
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
