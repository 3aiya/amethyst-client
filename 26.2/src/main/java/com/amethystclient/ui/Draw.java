package com.amethystclient.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The only class the shared UI code draws through. Each Minecraft version has its own copy that
 * maps these calls onto that version's GUI API, so widgets, pages and HUD modules stay the same in
 * every version.
 */
public final class Draw {
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
			graphics.text(font, text, x, y, color, false);
		}
	}

	public int textWidth(String text) {
		return font.width(text);
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
