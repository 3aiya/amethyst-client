package com.amethystclient.ui;

/**
 * A white shape with per-pixel alpha, drawn tinted with {@link Draw#mask}: anti-aliased corners,
 * circles and shadows in one textured quad each instead of hundreds of 1px fills. Ui builds and
 * caches them; each version's Draw turns one into a texture the first time it's drawn.
 */
public final class Mask {
	/** Unique name, lowercase letters, digits and underscores (it ends up in a texture id). */
	public final String key;
	public final int width;
	public final int height;
	/** 0–255, row by row. */
	public final int[] alpha;
	/** Draw's handle for the uploaded texture, once there is one. */
	Object texture;

	Mask(String key, int width, int height, int[] alpha) {
		this.key = key;
		this.width = width;
		this.height = height;
		this.alpha = alpha;
	}

	public int alphaAt(int x, int y) {
		return alpha[y * width + x];
	}
}
