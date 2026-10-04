package com.amethystclient.ui;

/**
 * Smooth icons drawn as characters of the UI font (assets/amethystclient/font/ui.json,
 * textures/font/icons*.png). They're white, so the text colour tints them. Draw one with
 * {@link #draw}, which sizes it in pixels of the current transform.
 */
public final class Glyphs {
	public static final String CHART = "";
	public static final String GLOBE = "";
	public static final String CUBE = "";
	public static final String PLAYER = "";
	public static final String GEAR = "";
	public static final String SEARCH = "";
	public static final String SERVER = "";
	/** 12px cells; the rest are 24px. */
	public static final String CHECK = "";
	public static final String CROSS = "";

	/** Every glyph is 8 font units tall. */
	private static final float UNITS = 8f;

	private Glyphs() {
	}

	/** Size in pixels of the texture cell, so a glyph can be drawn texel-for-pixel. */
	public static int cell(String glyph) {
		return glyph.charAt(0) >= '' ? 12 : 24;
	}

	/** Draws {@code glyph} as a {@code size}×{@code size} square with its top-left at (x, y). */
	public static void draw(Draw d, String glyph, int x, int y, int size, int color) {
		Ui.text(d, glyph, x, y, color, size / UNITS);
	}
}
