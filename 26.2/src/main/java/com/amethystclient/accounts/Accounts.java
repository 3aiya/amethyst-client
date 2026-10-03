package com.amethystclient.accounts;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;

/** Adds the "Accounts" button, and the name of the current account, to the multiplayer screen. */
public final class Accounts {
	private static final int BUTTON_WIDTH = 70;

	private Accounts() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof JoinMultiplayerScreen)) {
				return;
			}
			Screens.getWidgets(screen).add(Button.builder(Component.literal("Accounts"), b -> client.gui.setScreen(new AccountsScreen(screen)))
					.bounds(width - BUTTON_WIDTH - 4, 4, BUTTON_WIDTH, 20).build());
			ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> {
				String text = "Logged in as " + client.getUser().getName();
				int x = textX(s.width, client.font.width(text), client.font.width(s.getTitle()));
				if (x >= 0) {
					graphics.text(client.font, text, x, 10, 0xFFB8B0C4, true);
				}
			});
		});
	}

	/** Beside the centered title: right of it if there's room, else left of it; -1 if neither fits. */
	private static int textX(int screenWidth, int textWidth, int titleWidth) {
		int right = screenWidth - BUTTON_WIDTH - 10 - textWidth;
		if (right >= (screenWidth + titleWidth) / 2 + 8) {
			return right;
		}
		return 6 + textWidth <= (screenWidth - titleWidth) / 2 - 8 ? 6 : -1;
	}
}
