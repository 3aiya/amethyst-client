package com.amethystclient;

import com.amethystclient.packcache.ServerPackManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class AmethystClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Launch step: load and verify the last server's cached pack. The game's first resource load,
		// which runs after mod initialization, then includes it (see CachedPackProvider).
		ServerPackManager.get().initialize();

		// When the play phase starts, make sure the active cached pack belongs to this server.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
				ServerPackManager.get().onJoinedServer(handler.getConnection(), handler.getServerData(), client));
	}
}
