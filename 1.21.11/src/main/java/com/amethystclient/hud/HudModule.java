package com.amethystclient.hud;

import com.amethystclient.ui.AmethystTheme;
import com.amethystclient.ui.Draw;

/**
 * A module that draws on the HUD. It's pinned to a point on screen (a corner, an edge's middle or
 * the centre) with a pixel offset, so it stays in place relative to that point when the window or
 * GUI scale changes. Enabled = shown.
 */
public abstract class HudModule extends ClientModule {
	/** Gap between a module and the screen edge at the default positions. */
	public static final int MARGIN = 4;

	/** See {@link HudConfig.Module}. */
	public record Position(double anchorX, double anchorY, int offsetX, int offsetY) {
		public static final Position NONE = new Position(0, 0, 0, 0);

		public static Position topLeft(int offsetY) {
			return new Position(0, 0, MARGIN, MARGIN + offsetY);
		}
	}

	final Position defaultPosition;

	/** Size from the last {@link #measure} call, in HUD (scaled) pixels. */
	public int width;
	public int height;

	protected HudModule(String id, String name, String description, Category category, boolean enabledByDefault,
			Position defaultPosition) {
		super(id, name, description, category, enabledByDefault);
		this.defaultPosition = defaultPosition;
	}

	/**
	 * Sets {@link #width} and {@link #height}. {@code preview} is true in the layout editor, where a
	 * module with nothing to show (no armour, ...) still needs a size so it can be dragged.
	 */
	public abstract void measure(Draw d, boolean preview);

	public abstract void render(Draw d, AmethystTheme t, int x, int y, boolean preview);

	/** Left edge on a HUD of {@code hudWidth} (scaled) pixels, kept on screen. */
	public int x(int hudWidth) {
		HudConfig.Module s = settings();
		int x = (int) Math.round(s.anchorX * (hudWidth - width)) + s.offsetX;
		return Math.clamp(x, 0, Math.max(0, hudWidth - width));
	}

	public int y(int hudHeight) {
		HudConfig.Module s = settings();
		int y = (int) Math.round(s.anchorY * (hudHeight - height)) + s.offsetY;
		return Math.clamp(y, 0, Math.max(0, hudHeight - height));
	}

	/** Moves the module to ({@code x}, {@code y}), pinning it to the nearest third of the screen. */
	public void moveTo(int x, int y, int hudWidth, int hudHeight) {
		double anchorX = anchor(x + width / 2.0, hudWidth);
		double anchorY = anchor(y + height / 2.0, hudHeight);
		settings().set(new Position(anchorX, anchorY,
				x - (int) Math.round(anchorX * (hudWidth - width)),
				y - (int) Math.round(anchorY * (hudHeight - height))));
	}

	private static double anchor(double center, int size) {
		double f = center / size;
		return f < 1 / 3.0 ? 0 : f > 2 / 3.0 ? 1 : 0.5;
	}

	public void resetPosition() {
		settings().set(defaultPosition);
	}

	protected static float backgroundAlpha() {
		return (float) HudConfig.get().backgroundOpacity;
	}
}
