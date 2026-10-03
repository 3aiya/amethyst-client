package com.amethystclient.hud.setting;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ModeSetting extends Setting {
	public final List<String> options;
	private final Supplier<String> getter;
	private final Consumer<String> setter;

	public ModeSetting(String name, List<String> options, Supplier<String> getter, Consumer<String> setter) {
		super(name);
		this.options = options;
		this.getter = getter;
		this.setter = setter;
	}

	public String get() {
		String value = getter.get();
		return options.contains(value) ? value : options.getFirst();
	}

	public void set(String option) {
		setter.accept(option);
	}
}
