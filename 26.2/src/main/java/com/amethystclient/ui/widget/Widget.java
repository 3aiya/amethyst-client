package com.amethystclient.ui.widget;

import com.amethystclient.ui.AmethystTheme;
import com.amethystclient.ui.Draw;

/**
 * Base for the Amethyst widgets. Pages lay widgets out every frame by setting {@link #x}, {@link #y},
 * {@link #width} and {@link #height}, so a widget only keeps its own state (pressed, animations).
 */
public abstract class Widget {
	public int x;
	public int y;
	public int width;
	public int height;
	public boolean visible = true;
	public boolean enabled = true;
	protected boolean pressed;

	public Widget at(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		return this;
	}

	public boolean contains(double mx, double my) {
		return visible && mx >= x && mx < x + width && my >= y && my < y + height;
	}

	protected boolean hovered(double mx, double my) {
		return enabled && contains(mx, my);
	}

	public abstract void render(Draw d, AmethystTheme t, int mx, int my);

	/** Left click. Returns true when the widget used it. */
	public boolean mouseClicked(double mx, double my) {
		if (!enabled || !contains(mx, my)) {
			return false;
		}
		pressed = true;
		onPress(mx, my);
		return true;
	}

	public void mouseDragged(double mx, double my) {
	}

	public void mouseReleased(double mx, double my) {
		if (pressed) {
			pressed = false;
			if (enabled && contains(mx, my)) {
				onClick();
			}
		}
	}

	/** On mouse down. */
	protected void onPress(double mx, double my) {
	}

	/** On mouse up over the widget, after a press on it. */
	protected void onClick() {
	}
}
