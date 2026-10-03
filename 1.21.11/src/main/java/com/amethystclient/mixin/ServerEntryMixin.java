package com.amethystclient.mixin;

import com.amethystclient.AmethystServers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.pipeline.RenderPipeline;
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

	@WrapOperation(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V"))
	private void amethystclient$drawPinnedName(DrawContext context, TextRenderer textRenderer, String name, int x, int y,
			int color, Operation<Void> original) {
		if (!amethystclient$isPinned()) {
			original.call(context, textRenderer, name, x, y, color);
			return;
		}
		Text styled = Text.literal(name).styled(style -> style.withBold(true).withColor(NAME_COLOR));
		int centerX = ((MultiplayerServerListWidget.ServerEntry) (Object) this).getContentMiddleX();
		context.drawTextWithShadow(textRenderer, styled, centerX - textRenderer.getWidth(styled) / 2, y, color);
	}

	@WrapWithCondition(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V"))
	private boolean amethystclient$hideLockedArrows(DrawContext context, RenderPipeline pipeline, Identifier sprite, int x,
			int y, int width, int height) {
		String path = sprite.getPath();
		if (path.startsWith("server_list/move_up")) {
			return AmethystServers.canMoveUp(screen.getServerList(), server);
		}
		if (path.startsWith("server_list/move_down")) {
			return AmethystServers.canMoveDown(screen.getServerList(), server);
		}
		return true;
	}

	/** Hovering a hidden "move up" arrow mustn't highlight it or show the hand cursor. */
	@WrapOperation(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screen/multiplayer/MultiplayerServerListWidget$ServerEntry;isBottomLeft(III)Z"))
	private boolean amethystclient$noLockedMoveUpHover(MultiplayerServerListWidget.ServerEntry entry, int x, int y,
			int size, Operation<Boolean> original) {
		return AmethystServers.canMoveUp(screen.getServerList(), server) && original.call(entry, x, y, size);
	}

	/** Same for a hidden "move down" arrow. */
	@WrapOperation(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screen/multiplayer/MultiplayerServerListWidget$ServerEntry;isTopLeft(III)Z"))
	private boolean amethystclient$noLockedMoveDownHover(MultiplayerServerListWidget.ServerEntry entry, int x, int y,
			int size, Operation<Boolean> original) {
		return AmethystServers.canMoveDown(screen.getServerList(), server) && original.call(entry, x, y, size);
	}

	@Inject(method = "swapEntries", at = @At("HEAD"), cancellable = true)
	private void amethystclient$keepPinnedOnTop(int i, int j, CallbackInfo ci) {
		if (!AmethystServers.canSwap(screen.getServerList(), i, j)) {
			ci.cancel();
		}
	}
}
