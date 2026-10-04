package com.amethystclient.chat;

import com.amethystclient.hud.Modules;
import com.amethystclient.hud.modules.HideJoinLeaveModule;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;

/** The Server column's chat modules: Hide Join/Leave here, Chat Timestamps via ChatHudMixin. */
public final class ChatTweaks {
	private ChatTweaks() {
	}

	public static void register() {
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || !isHiddenJoinLeave(message));
	}

	private static boolean isHiddenJoinLeave(Text message) {
		HideJoinLeaveModule module = Modules.HIDE_JOIN_LEAVE;
		if (!module.enabled()) {
			return false;
		}
		return message.getContent() instanceof TranslatableTextContent translatable
				&& HideJoinLeaveModule.TRANSLATION_KEYS.contains(translatable.getKey())
				|| module.hides(message.getString());
	}

	/** {@code message} with the time in front when Chat Timestamps is on. */
	public static Text timestamped(Text message) {
		if (!Modules.CHAT_TIMESTAMPS.enabled()) {
			return message;
		}
		return Text.empty()
				.append(Text.literal(Modules.CHAT_TIMESTAMPS.prefix()).formatted(Formatting.GRAY))
				.append(message);
	}
}
