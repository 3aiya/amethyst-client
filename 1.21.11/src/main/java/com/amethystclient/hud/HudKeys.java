package com.amethystclient.hud;

import com.amethystclient.ui.PageScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * The "Open/close HUD settings" key (Right Shift by default, rebindable in Controls), and the
 * per-tick module update (module keybinds, {@link ClientModule#tick}).
 */
public final class HudKeys {
	private static KeyBinding open;
	/** When the menu last closed on its own key, from System.nanoTime(). */
	private static long closedByKeyAt = System.nanoTime() - 1_000_000_000L;

	private HudKeys() {
	}

	public static void register() {
		open = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.amethystclient.hud_settings",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				KeyBinding.Category.create(Identifier.of("amethystclient", "main"))));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			Modules.tick();
			while (open.wasPressed()) {
				// The press that just closed the menu must not open it again.
				boolean justClosed = System.nanoTime() - closedByKeyAt < 250_000_000L;
				if (!justClosed && client.player != null && client.currentScreen == null) {
					PageScreen.open(new HudSettingsPage());
				}
			}
		});
	}

	/**
	 * The GLFW key code the menu key is bound to, or -1 (unbound or a mouse button). The menu
	 * closes on it itself, since key bindings don't fire while a screen is open.
	 */
	public static int openKey() {
		InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(open);
		return key.getCategory() == InputUtil.Type.KEYSYM ? key.getCode() : -1;
	}

	/**
	 * Called when the menu closes on its key. Once the menu is gone, that same press can still
	 * reach the key mapping, so clicks right after it are ignored.
	 */
	public static void onClosedByKey() {
		closedByKeyAt = System.nanoTime();
	}
}
