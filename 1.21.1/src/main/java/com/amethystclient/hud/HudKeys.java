package com.amethystclient.hud;

import com.amethystclient.ui.PageScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** The "Open HUD settings" key (Right Shift by default, rebindable in Controls). */
public final class HudKeys {
	private HudKeys() {
	}

	public static void register() {
		KeyBinding open = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.amethystclient.hud_settings",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				"key.category.amethystclient.main"));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (open.wasPressed()) {
				if (client.player != null && client.currentScreen == null) {
					PageScreen.open(new HudSettingsPage());
				}
			}
		});
	}
}
