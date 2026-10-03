package com.amethystclient.hud;

import com.amethystclient.hud.setting.BoolSetting;
import com.amethystclient.hud.setting.ListSetting;
import com.amethystclient.hud.setting.ModeSetting;
import com.amethystclient.hud.setting.Setting;
import com.amethystclient.hud.setting.SliderSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;

/**
 * Anything that shows up as a row in the ClickGUI. A module has an id (its config key), a name, a
 * description (the GUI's tooltip), a {@link Category} (its column), settings and an optional
 * keybind that toggles it.
 *
 * <p>To add a module: extend this (or {@link HudModule} for something drawn on the HUD), declare
 * its settings in the constructor with {@link #bool}, {@link #slider}, {@link #mode} and
 * {@link #list}, and add an instance to {@link Modules#ALL}. The GUI, config and keybinds pick it
 * up from there.
 */
public abstract class ClientModule {
	public final String id;
	public final String name;
	public final String description;
	public final Category category;
	final boolean enabledByDefault;

	/** Shown under the module when it's expanded in the ClickGUI, in this order. */
	public final List<Setting> settingsList = new ArrayList<>();

	protected ClientModule(String id, String name, String description, Category category, boolean enabledByDefault) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
		this.enabledByDefault = enabledByDefault;
	}

	public HudConfig.Module settings() {
		return HudConfig.get().module(this);
	}

	public boolean enabled() {
		return settings().enabled;
	}

	public void setEnabled(boolean enabled) {
		HudConfig.Module settings = settings();
		if (settings.enabled == enabled) {
			return;
		}
		settings.enabled = enabled;
		if (enabled) {
			onEnable();
		} else {
			onDisable();
		}
	}

	public void toggle() {
		setEnabled(!enabled());
	}

	/** GLFW key code that toggles this module, or 0 for none. */
	public int keybind() {
		return Math.max(0, settings().key);
	}

	public void setKeybind(int key) {
		settings().key = Math.max(0, key);
	}

	// ---- hooks ----

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	/** Called every client tick while in a world, whether the module is enabled or not. */
	public void tick() {
	}

	/** Called on the first tick after joining a world or server, before {@link #tick}. */
	public void onJoinWorld() {
	}

	// ---- settings, saved in this module's config values ----

	protected BoolSetting bool(String key, String name, boolean defaultValue) {
		return add(new BoolSetting(name,
				() -> settings().values.get(key) instanceof Boolean b ? b : defaultValue,
				value -> settings().values.put(key, value)));
	}

	protected SliderSetting slider(String key, String name, double min, double max, double step, double defaultValue,
			DoubleFunction<String> format) {
		return add(new SliderSetting(name, min, max, step,
				() -> settings().values.get(key) instanceof Number n ? Math.clamp(n.doubleValue(), min, max) : defaultValue,
				value -> settings().values.put(key, value), format));
	}

	/** One of {@code options}. */
	protected ModeSetting mode(String key, String name, String defaultValue, String... options) {
		return add(new ModeSetting(name, List.of(options),
				() -> settings().values.get(key) instanceof String s ? s : defaultValue,
				value -> settings().values.put(key, value)));
	}

	/** Any number of {@code options}; {@code defaults} are on at first. */
	protected ListSetting list(String key, String name, List<String> defaults, String... options) {
		return add(new ListSetting(name, List.of(options),
				() -> settings().values.get(key) instanceof List<?> saved
						? saved.stream().filter(String.class::isInstance).map(String.class::cast).toList()
						: defaults,
				value -> settings().values.put(key, value)));
	}

	private <T extends Setting> T add(T setting) {
		settingsList.add(setting);
		return setting;
	}
}
