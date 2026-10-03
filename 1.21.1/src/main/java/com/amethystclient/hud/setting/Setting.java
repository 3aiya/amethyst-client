package com.amethystclient.hud.setting;

/** One option shown when a module is expanded in the HUD settings. */
public abstract sealed class Setting permits BoolSetting, SliderSetting, ModeSetting {
	public final String name;

	protected Setting(String name) {
		this.name = name;
	}
}
