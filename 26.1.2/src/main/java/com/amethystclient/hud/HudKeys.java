package com.amethystclient.hud;

import com.amethystclient.ui.PageScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** The "Open HUD settings" key (Right Shift by default, rebindable in Controls). */
public final class HudKeys {
	private HudKeys() {
	}

	public static void register() {
		KeyMapping open = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.amethystclient.hud_settings",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				KeyMapping.Category.register(Identifier.fromNamespaceAndPath("amethystclient", "main"))));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (open.consumeClick()) {
				if (client.player != null && client.screen == null) {
					PageScreen.open(new HudSettingsPage());
				}
			}
		});
	}
}
