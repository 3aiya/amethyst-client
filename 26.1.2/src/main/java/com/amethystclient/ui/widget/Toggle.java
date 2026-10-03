package com.amethystclient.ui.widget;

import com.amethystclient.ui.AmethystTheme;
import com.amethystclient.ui.Draw;
import com.amethystclient.ui.Motion;
import com.amethystclient.ui.Ui;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Toggle switch (§5.5): fully round track, accent when on, white knob sliding over ~150ms. */
public class Toggle extends Widget {
	public static final int WIDTH = 22;
	public static final int HEIGHT = 12;

	private final Supplier<Boolean> getter;
	private final Consumer<Boolean> setter;
	private final Motion position;

	public Toggle(Supplier<Boolean> getter, Consumer<Boolean> setter) {
		this.getter = getter;
		this.setter = setter;
		this.position = new Motion(getter.get() ? 1f : 0f, 150);
		this.width = WIDTH;
		this.height = HEIGHT;
	}

	@Override
	public void render(Draw d, AmethystTheme t, int mx, int my) {
		if (!visible) {
			return;
		}
		position.set(getter.get() ? 1f : 0f);
		float p = position.get();
		int track = AmethystTheme.lerp(t.borderLight(), t.accent(), p);
		if (!enabled) {
			track = AmethystTheme.disabled(track);
		}
		Ui.round(d, x, y, WIDTH, HEIGHT, HEIGHT / 2, track);
		int knob = HEIGHT - 2;
		int kx = x + 1 + Math.round(p * (WIDTH - 2 - knob));
		Ui.round(d, kx, y + 2, knob, knob, knob / 2, 0x40000000);
		Ui.round(d, kx, y + 1, knob, knob, knob / 2, AmethystTheme.WHITE);
	}

	@Override
	protected void onClick() {
		setter.accept(!getter.get());
	}
}
