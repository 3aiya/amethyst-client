package com.amethystclient.mixin;

import com.amethystclient.chat.ChatTweaks;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	/** Chat Timestamps: every message that reaches the chat, player or system, goes through here. */
	@ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true)
	private Component amethystclient$timestamp(Component message) {
		return ChatTweaks.timestamped(message);
	}
}
