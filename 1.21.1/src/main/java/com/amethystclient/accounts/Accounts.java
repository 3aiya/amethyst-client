package com.amethystclient.accounts;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.text.Text;

/** Adds the "Accounts" button, and the name of the current account, to the multiplayer screen. */
public final class Accounts {
	private static final int BUTTON_WIDTH = 70;

	private Accounts() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof MultiplayerScreen)) {
				return;
			}
			Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Accounts"), b -> client.setScreen(new AccountsScreen(screen)))
					.dimensions(width - BUTTON_WIDTH - 4, 4, BUTTON_WIDTH, 20).build());
			ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, delta) -> {
				String text = "Logged in as " + client.getSession().getUsername();
				int x = textX(s.width, client.textRenderer.getWidth(text), client.textRenderer.getWidth(s.getTitle()));
				if (x >= 0) {
					graphics.drawText(client.textRenderer, text, x, 10, 0xFFB8B0C4, true);
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
