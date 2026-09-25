package com.amethystclient.presence;

import com.amethystclient.AmethystServers;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/** Works out what the player is doing each tick and hands it to {@link DiscordPresence}. */
public final class PresenceTracker {
	private static volatile String mode;
	private static ClientLevel lastLevel;

	private PresenceTracker() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(ModePayload.TYPE, ModePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ModePayload.TYPE, ModePayload.CODEC);
		ClientPlayNetworking.registerGlobalReceiver(ModePayload.TYPE, (payload, context) ->
				mode = payload.mode().isBlank() ? null : payload.mode());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> mode = null);
		ClientTickEvents.END_CLIENT_TICK.register(PresenceTracker::tick);
		DiscordPresence.start();
	}

	private static void tick(Minecraft client) {
		ClientLevel level = client.level;
		boolean amethyst = level != null && AmethystServers.isAmethystServer(client);
		if (level != lastLevel) {
			lastLevel = level;
			// A new level means we joined, the proxy moved us to another server, or we changed
			// dimension: ask the proxy which mode we're in now.
			if (amethyst) {
				ClientPlayNetworking.send(new ModePayload(""));
			}
		}

		if (level == null) {
			DiscordPresence.set("In the menus", null);
		} else if (client.hasSingleplayerServer()) {
			DiscordPresence.set("Singleplayer", null);
		} else if (amethyst) {
			String details = mode == null ? "Amethyst Community" : "Amethyst Community - " + mode;
			DiscordPresence.set(details, AmethystServers.DISPLAY_ADDRESS);
		} else {
			// Other servers' addresses aren't ours to share.
			DiscordPresence.set("Multiplayer", null);
		}
	}
}
