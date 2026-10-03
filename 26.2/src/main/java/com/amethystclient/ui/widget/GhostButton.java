package com.amethystclient.ui.widget;

import com.amethystclient.ui.AmethystTheme;
import com.amethystclient.ui.Draw;
import com.amethystclient.ui.Motion;
import com.amethystclient.ui.Ui;

/** Secondary action (§5.2): transparent, 1px border-light, zinc-300 text; border and text turn accent on hover. */
public class GhostButton extends Widget {
	public String label;
	private final Runnable action;
	private final Motion hover = new Motion(0f, 150);

	public GhostButton(String label, Runnable action) {
		this.label = label;
		this.action = action;
	}

	@Override
	public void render(Draw d, AmethystTheme t, int mx, int my) {
		if (!visible) {
			return;
		}
		hover.set(hovered(mx, my) ? 1f : 0f);
		float h = hover.get();
		int border = AmethystTheme.lerp(t.borderLight(), t.accent(), h);
		int text = AmethystTheme.lerp(AmethystTheme.TEXT_SECONDARY, t.accent(), h);
		if (!enabled) {
			border = AmethystTheme.disabled(border);
			text = AmethystTheme.TEXT_FAINT;
		}
		int dy = pressed && hovered(mx, my) ? 1 : 0;
		Ui.outline(d, x, y + dy, width, height, Ui.RADIUS_BUTTON, border);
		Ui.centered(d, label, x + width / 2, y + dy + (height - d.lineHeight()) / 2 + 1, text);
	}

	@Override
	protected void onClick() {
		action.run();
	}
}
