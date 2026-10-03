package com.amethystclient.ui.widget;

import com.amethystclient.ui.AmethystTheme;
import com.amethystclient.ui.Draw;
import com.amethystclient.ui.Ui;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

/**
 * Slider (§5.6): round track in bg-elevated, accent fill and thumb, the value on the right. The
 * widget's {@link #width} covers the track and the value text.
 */
public class Slider extends Widget {
	private static final int VALUE_WIDTH = 34;
	private static final int THUMB = 8;

	private final double min;
	private final double max;
	private final double step;
	private final Supplier<Double> getter;
	private final Consumer<Double> setter;
	private final DoubleFunction<String> format;

	public Slider(double min, double max, double step, Supplier<Double> getter, Consumer<Double> setter,
			DoubleFunction<String> format) {
		this.min = min;
		this.max = max;
		this.step = step;
		this.getter = getter;
		this.setter = setter;
		this.format = format;
		this.height = 10;
	}

	private int trackWidth() {
		return width - VALUE_WIDTH;
	}

	@Override
	public void render(Draw d, AmethystTheme t, int mx, int my) {
		if (!visible) {
			return;
		}
		double frac = (getter.get() - min) / (max - min);
		int tw = trackWidth();
		int cy = y + height / 2;
		Ui.round(d, x, cy - 2, tw, 4, 2, t.bgElevated());
		int filled = (int) Math.round(frac * (tw - THUMB)) + THUMB / 2;
		Ui.round(d, x, cy - 2, filled, 4, 2, t.accent());
		int tx = x + filled - THUMB / 2;
		boolean active = pressed || hovered(mx, my);
		if (active) {
			Ui.round(d, tx - 2, cy - THUMB / 2 - 2, THUMB + 4, THUMB + 4, THUMB / 2 + 2, AmethystTheme.withAlpha(t.accent(), 0.25f));
		}
		Ui.round(d, tx, cy - THUMB / 2, THUMB, THUMB, THUMB / 2, active ? t.accentHover() : t.accent());
		String value = format.apply(getter.get());
		d.text(value, x + width - d.textWidth(value), cy - d.lineHeight() / 2 + 1, AmethystTheme.TEXT_PRIMARY);
	}

	@Override
	public boolean contains(double mx, double my) {
		return visible && mx >= x - 2 && mx < x + trackWidth() + 2 && my >= y - 2 && my < y + height + 2;
	}

	@Override
	protected void onPress(double mx, double my) {
		update(mx);
	}

	@Override
	public void mouseDragged(double mx, double my) {
		if (pressed) {
			update(mx);
		}
	}

	private void update(double mx) {
		double frac = Math.clamp((mx - x - THUMB / 2.0) / (trackWidth() - THUMB), 0, 1);
		double value = min + frac * (max - min);
		value = Math.round(value / step) * step;
		setter.accept(Math.clamp(value, min, max));
	}
}
