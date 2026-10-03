package com.amethystclient.ui.widget;

import com.amethystclient.ui.AmethystTheme;
import com.amethystclient.ui.Draw;
import com.amethystclient.ui.Ui;

/** A flat button with rounded corners: accent (primary), red (danger) or card-coloured (neutral). */
public class FlatButton extends Widget {
	public enum Variant { PRIMARY, DANGER, NEUTRAL }

	public String label;
	private final Variant variant;
	private final Runnable action;

	public FlatButton(String label, Variant variant, Runnable action) {
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

		// Flat and simple: the fill changes on hover, and pressing nudges it down 1px.
		int offset = down ? 1 : 0;
		Ui.round(d, x, y + offset, width, height, Ui.RADIUS_BUTTON, fill);
		if (variant == Variant.NEUTRAL) {
			Ui.outline(d, x, y + offset, width, height, Ui.RADIUS_BUTTON, hover ? t.borderLight() : t.border());
		}
		Ui.centered(d, label, x + width / 2, y + offset + (height - d.lineHeight()) / 2 + 1, text);
	}

	@Override
	protected void onClick() {
		action.run();
	}
}
