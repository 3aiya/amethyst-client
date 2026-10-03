package com.amethystclient.hud.setting;

import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

public final class SliderSetting extends Setting {
	public final double min;
	public final double max;
	public final double step;
	private final Supplier<Double> getter;
	private final Consumer<Double> setter;
	private final DoubleFunction<String> format;

	public SliderSetting(String name, double min, double max, double step, Supplier<Double> getter, Consumer<Double> setter,
			DoubleFunction<String> format) {
		super(name);
		this.min = min;
		this.max = max;
		this.step = step;
		this.getter = getter;
		this.setter = setter;
		this.format = format;
	}

	public double get() {
		return getter.get();
	}

	/** 0–1 position of the current value. */
	public double fraction() {
		return (get() - min) / (max - min);
	}

	public void setFraction(double fraction) {
		double value = min + Math.clamp(fraction, 0, 1) * (max - min);
		setter.accept(Math.clamp(Math.round(value / step) * step, min, max));
	}

	public String display() {
		return format.apply(get());
	}
}
