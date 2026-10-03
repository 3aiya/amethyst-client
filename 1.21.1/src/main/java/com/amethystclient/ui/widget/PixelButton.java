package com.amethystclient.ui.widget;

import com.amethystclient.ui.AmethystTheme;
import com.amethystclient.ui.Draw;
import com.amethystclient.ui.Ui;

/**
 * The launcher's chunky button (§5.1): 1px dark border and a 2px dark "ledge" underneath. Pressing
 * moves it down onto the ledge like a physical key; the primary variant lifts on hover and has an
 * accent glow underneath.
 */
public class PixelButton extends Widget {
	public enum Variant { PRIMARY, DANGER, NEUTRAL }

	private static final int LEDGE = 2;

	public String label;
	private final Variant variant;
	private final Runnable action;

	public PixelButton(String label, Variant variant, Runnable action) {
		this.label = label;
		this.variant = variant;
		this.action = action;
	}

	@Override
	public void render(Draw d, AmethystTheme t, int mx, int my) {
		if (!visible) {
			return;
		}
		boolean hover = hovered(mx, my);
		boolean down = pressed && hover;
		int fill;
		int text;
		switch (variant) {
			case PRIMARY -> {
				fill = down ? t.accentPressed() : hover ? t.accentHover() : t.accent();
				text = t.accentFg();
			}
			case DANGER -> {
				fill = hover ? AmethystTheme.DANGER_HOVER : AmethystTheme.DANGER;
				text = AmethystTheme.WHITE;
			}
			default -> {
				fill = hover ? t.bgCardHover() : t.bgCard();
				text = t.accent();
			}
		}
		if (!enabled) {
			fill = AmethystTheme.disabled(fill);
			text = AmethystTheme.disabled(text);
		}

		// The face sits on top of the ledge; pressed drops it 2px onto it, primary hover lifts it 1px.
		int faceHeight = height - LEDGE;
		int offset = down ? LEDGE : hover && variant == Variant.PRIMARY ? -1 : 0;
		if (variant == Variant.PRIMARY && enabled && !down) {
			Ui.glow(d, x, y + 4, width, faceHeight, t.accent(), 5, hover ? 0.5f : 0.35f);
		}
		if (!down) {
			Ui.round(d, x, y + LEDGE + offset, width, faceHeight, Ui.RADIUS_BUTTON, AmethystTheme.LEDGE);
		}
		Ui.box(d, x, y + offset, width, faceHeight, Ui.RADIUS_BUTTON, fill, AmethystTheme.LEDGE);
		String shown = "§l" + label.toUpperCase();
		Ui.centered(d, shown, x + width / 2, y + offset + (faceHeight - d.lineHeight()) / 2 + 1, text);
	}

	@Override
	protected void onClick() {
		action.run();
	}
}
