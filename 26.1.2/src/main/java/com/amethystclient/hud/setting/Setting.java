package com.amethystclient.hud.setting;

import java.util.function.BooleanSupplier;

/** One option shown when a module is expanded in the ClickGUI. */
public abstract sealed class Setting permits BoolSetting, SliderSetting, ModeSetting, ListSetting {
	public final String name;
	private BooleanSupplier visible = () -> true;

	protected Setting(String name) {
		this.name = name;
	}

	/**
	 * Only shows this setting while {@code condition} holds, e.g. a "Bar colour" option that only
	 * matters when "Durability as" is "Bar". Returns this setting, typed as the caller's variable.
	 */
	@SuppressWarnings("unchecked")
	public <T extends Setting> T showWhen(BooleanSupplier condition) {
		visible = condition;
		return (T) this;
	}

	public boolean visible() {
		return visible.getAsBoolean();
	}
}
