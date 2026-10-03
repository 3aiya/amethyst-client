package com.amethystclient.mixin;

import com.amethystclient.AmethystServers;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class OnlineServerEntryMixin {
	@Unique
	private static final int NAME_COLOR = 0xC77DFF;

	@Shadow
	@Final
	private JoinMultiplayerScreen screen;

	@Shadow
	@Final
	private ServerData serverData;

	@Unique
	private boolean amethystclient$isPinned() {
		return AmethystServers.isPinned(screen.getServers(), serverData);
	}

	@WrapOperation(method = "extractContent", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
	private void amethystclient$drawPinnedName(GuiGraphicsExtractor graphics, Font font, String name, int x, int y,
			int color, Operation<Void> original) {
		if (!amethystclient$isPinned()) {
			original.call(graphics, font, name, x, y, color);
			return;
		}
		Component styled = Component.literal(name).withStyle(style -> style.withBold(true).withColor(NAME_COLOR));
		int centerX = ((ServerSelectionList.OnlineServerEntry) (Object) this).getContentXMiddle();
		graphics.text(font, styled, centerX - font.width(styled) / 2, y, color);
	}

	@WrapWithCondition(method = "extractContent", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private boolean amethystclient$hideLockedArrows(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite,
			int x, int y, int width, int height) {
		String path = sprite.getPath();
		if (path.startsWith("server_list/move_up")) {
			return AmethystServers.canMoveUp(screen.getServers(), serverData);
		}
		if (path.startsWith("server_list/move_down")) {
			return AmethystServers.canMoveDown(screen.getServers(), serverData);
		}
		return true;
	}

	/** Hovering a hidden "move up" arrow mustn't highlight it or show the hand cursor. */
	@WrapOperation(method = "extractContent", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/multiplayer/ServerSelectionList$OnlineServerEntry;mouseOverTopLeftQuarter(III)Z"))
	private boolean amethystclient$noLockedMoveUpHover(ServerSelectionList.OnlineServerEntry entry, int x, int y, int size,
			Operation<Boolean> original) {
		return AmethystServers.canMoveUp(screen.getServers(), serverData) && original.call(entry, x, y, size);
	}

	/** Same for a hidden "move down" arrow. */
	@WrapOperation(method = "extractContent", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/multiplayer/ServerSelectionList$OnlineServerEntry;mouseOverBottomLeftQuarter(III)Z"))
	private boolean amethystclient$noLockedMoveDownHover(ServerSelectionList.OnlineServerEntry entry, int x, int y, int size,
			Operation<Boolean> original) {
		return AmethystServers.canMoveDown(screen.getServers(), serverData) && original.call(entry, x, y, size);
	}

	@Inject(method = "swap", at = @At("HEAD"), cancellable = true)
	private void amethystclient$keepPinnedOnTop(int currentIndex, int newIndex, CallbackInfo ci) {
		if (!AmethystServers.canSwap(screen.getServers(), currentIndex, newIndex)) {
			ci.cancel();
		}
	}
}
