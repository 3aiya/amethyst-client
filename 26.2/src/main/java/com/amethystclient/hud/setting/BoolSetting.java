package com.amethystclient.hud.setting;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class BoolSetting extends Setting {
	private final Supplier<Boolean> getter;
	private final Consumer<Boolean> setter;

	public BoolSetting(String name, Supplier<Boolean> getter, Consumer<Boolean> setter) {
		super(name);
		this.getter = getter;
		this.setter = setter;
	}

	public boolean get() {
		return getter.get();
	}

	public void toggle() {
		setter.accept(!get());
	}
}
