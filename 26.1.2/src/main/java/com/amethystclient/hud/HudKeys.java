package com.amethystclient.hud;

import com.amethystclient.ui.PageScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * The "Open/close HUD settings" key (Right Shift by default, rebindable in Controls), and the
 * per-tick module update (module keybinds, {@link ClientModule#tick}).
 */
public final class HudKeys {
	private static KeyMapping open;
	/** When the menu last closed on its own key, from System.nanoTime(). */
	private static long closedByKeyAt = System.nanoTime() - 1_000_000_000L;

	private HudKeys() {
	}

	public static void register() {
		open = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.amethystclient.hud_settings",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				KeyMapping.Category.register(Identifier.fromNamespaceAndPath("amethystclient", "main"))));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			Modules.tick();
			while (open.consumeClick()) {
				// The press that just closed the menu must not open it again.
				boolean justClosed = System.nanoTime() - closedByKeyAt < 250_000_000L;
				if (!justClosed && client.player != null && client.screen == null) {
					PageScreen.open(new HudSettingsPage());
				}
			}
		});
	}

	/**
	 * The GLFW key code the menu key is bound to, or -1 (unbound or a mouse button). The menu
	 * closes on it itself, since key mappings don't fire while a screen is open.
	 */
	public static int openKey() {
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(open);
		return key.getType() == InputConstants.Type.KEYSYM ? key.getValue() : -1;
	}

	/**
	 * Called when the menu closes on its key. Once the menu is gone, that same press can still
	 * reach the key mapping, so clicks right after it are ignored.
	 */
	public static void onClosedByKey() {
		closedByKeyAt = System.nanoTime();
	}
}
