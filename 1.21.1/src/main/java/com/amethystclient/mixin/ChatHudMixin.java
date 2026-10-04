package com.amethystclient.mixin;

import com.amethystclient.chat.ChatTweaks;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
	/** Chat Timestamps: every message that reaches the chat, player or system, goes through here. */
	@ModifyVariable(
			method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
			at = @At("HEAD"),
			argsOnly = true)
	private Text amethystclient$timestamp(Text message) {
		return ChatTweaks.timestamped(message);
	}
}
