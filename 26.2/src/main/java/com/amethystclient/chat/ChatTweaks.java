package com.amethystclient.chat;

import com.amethystclient.hud.Modules;
import com.amethystclient.hud.modules.HideJoinLeaveModule;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** The Server column's chat modules: Hide Join/Leave here, Chat Timestamps via ChatComponentMixin. */
public final class ChatTweaks {
	private ChatTweaks() {
	}

	public static void register() {
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || !isHiddenJoinLeave(message));
	}

	private static boolean isHiddenJoinLeave(Component message) {
		HideJoinLeaveModule module = Modules.HIDE_JOIN_LEAVE;
		if (!module.enabled()) {
			return false;
		}
		return message.getContents() instanceof TranslatableContents translatable
				&& HideJoinLeaveModule.TRANSLATION_KEYS.contains(translatable.getKey())
				|| module.hides(message.getString());
	}

	/** {@code message} with the time in front when Chat Timestamps is on. */
	public static Component timestamped(Component message) {
		if (!Modules.CHAT_TIMESTAMPS.enabled()) {
			return message;
		}
		return Component.empty()
				.append(Component.literal(Modules.CHAT_TIMESTAMPS.prefix()).withStyle(ChatFormatting.GRAY))
				.append(message);
	}
}
