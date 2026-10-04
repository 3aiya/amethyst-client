package com.amethystclient;

import com.amethystclient.accounts.Accounts;
import com.amethystclient.autologin.AutoLogin;
import com.amethystclient.chat.ChatTweaks;
import com.amethystclient.hud.HudKeys;
import com.amethystclient.packcache.ServerPackManager;
import com.amethystclient.presence.PresenceTracker;
import com.amethystclient.update.UpdateChecker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.chat.Component;

public class AmethystClient implements ClientModInitializer {
	private static boolean updateAnnounced;

	@Override
	public void onInitializeClient() {
		// Launch step: load and verify the last server's cached pack. The game's first resource load,
		// which runs after mod initialization, then includes it (see CachedPackProvider).
		ServerPackManager.get().initialize();

		// Look for a newer release in the background; see UpdateChecker for how it's applied.
		UpdateChecker.checkAsync();

		// Discord Rich Presence, including the mode (Survival, Hub, ...) on Amethyst servers.
		PresenceTracker.register();

		// Answers the auth plugin's login/register prompt with the saved password.
		AutoLogin.register();

		// The account manager, opened from the multiplayer screen.
		Accounts.register();

		// The HUD modules and their settings page (Right Shift).
		HudKeys.register();

		// The Server column's chat modules (Hide Join/Leave, Chat Timestamps).
		ChatTweaks.register();

		// When the play phase starts, make sure the active cached pack belongs to this server.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ServerPackManager.get().onJoinedServer(handler.getConnection(), handler.getServerData(), client);

			String version = UpdateChecker.pendingVersion();
			if (version != null && !updateAnnounced) {
				updateAnnounced = true;
				client.gui.hud.getChat().addClientSystemMessage(Component.literal(
						"§b[Amethyst Client] §fUpdate v" + version + " downloaded - restart the game to apply it."));
			}
		});
	}
}
