package com.amethystclient.hud.setting;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Like {@link ModeSetting}, but any number of options can be on at once. */
public final class ListSetting extends Setting {
	public final List<String> options;
	private final Supplier<List<String>> getter;
	private final Consumer<List<String>> setter;

	public ListSetting(String name, List<String> options, Supplier<List<String>> getter, Consumer<List<String>> setter) {
		super(name);
		this.options = options;
		this.getter = getter;
		this.setter = setter;
	}

	public boolean has(String option) {
		return getter.get().contains(option);
	}

	public void toggle(String option) {
		Set<String> selected = new LinkedHashSet<>(getter.get());
		if (!selected.remove(option)) {
			selected.add(option);
		}
		// Kept in the options' order, so the saved list doesn't depend on click order.
		setter.accept(options.stream().filter(selected::contains).toList());
	}
}
