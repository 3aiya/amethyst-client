package com.amethystclient.mixin;

import com.amethystclient.AmethystServers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The pinned Amethyst server's row: its name centred on the card in amethyst, and no move arrows.
 * Rows can't be moved above it either.
 */
@Mixin(MultiplayerServerListWidget.ServerEntry.class)
public abstract class ServerEntryMixin {
	@Unique
	private static final int NAME_COLOR = 0xC77DFF;

	@Shadow
	@Final
	private MultiplayerScreen screen;

	@Shadow
	@Final
	private ServerInfo server;

	@Unique
	private boolean amethystclient$isPinned() {
		return AmethystServers.isPinned(screen.getServerList(), server);
	}

	/** The card's centre, kept from render's arguments for centring the name. */
	@Unique
	private int amethystclient$cardCenterX;

	@Inject(method = "render", at = @At("HEAD"))
	private void amethystclient$rememberCardCenter(DrawContext context, int index, int y, int x, int entryWidth,
			int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci) {
		amethystclient$cardCenterX = x + entryWidth / 2;
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)I"))
	private int amethystclient$drawPinnedName(DrawContext context, TextRenderer textRenderer, String name, int x, int y,
			int color, boolean shadow, Operation<Integer> original) {
		if (!amethystclient$isPinned()) {
			return original.call(context, textRenderer, name, x, y, color, shadow);
		}
		Text styled = Text.literal(name).styled(style -> style.withBold(true).withColor(NAME_COLOR));
		return context.drawText(textRenderer, styled, amethystclient$cardCenterX - textRenderer.getWidth(styled) / 2, y,
				color, shadow);
	}

	@WrapWithCondition(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V"))
	private boolean amethystclient$hideLockedArrows(DrawContext context, Identifier sprite, int x, int y, int width, int height) {
		String path = sprite.getPath();
		if (path.startsWith("server_list/move_up")) {
			return AmethystServers.canMoveUp(screen.getServerList(), server);
		}
		if (path.startsWith("server_list/move_down")) {
			return AmethystServers.canMoveDown(screen.getServerList(), server);
		}
		return true;
	}

	@Inject(method = "swapEntries", at = @At("HEAD"), cancellable = true)
	private void amethystclient$keepPinnedOnTop(int i, int j, CallbackInfo ci) {
		if (!AmethystServers.canSwap(screen.getServerList(), i, j)) {
			ci.cancel();
		}
	}
}
