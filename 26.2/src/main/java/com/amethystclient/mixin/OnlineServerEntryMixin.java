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
 * The pinned Amethyst server's row: an amethyst tint, a styled name with an "OFFICIAL" badge, and
 * no move arrows. Rows can't be moved above it either.
 */
@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class OnlineServerEntryMixin {
	@Unique
	private static final int BACKGROUND_TOP = 0x449D4EDD;
	@Unique
	private static final int BACKGROUND_BOTTOM = 0x149D4EDD;
	@Unique
	private static final int NAME_COLOR = 0xD8B4FE;
	@Unique
	private static final int BADGE_COLOR = 0xFF7B2CBF;
	@Unique
	private static final Component BADGE = Component.literal("OFFICIAL");

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

	@Inject(method = "extractContent", at = @At("HEAD"))
	private void amethystclient$drawPinnedBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
			float a, CallbackInfo ci) {
		if (amethystclient$isPinned()) {
			ServerSelectionList.OnlineServerEntry self = (ServerSelectionList.OnlineServerEntry) (Object) this;
			graphics.fillGradient(self.getContentX() - 1, self.getContentY() - 1, self.getContentRight() + 1,
					self.getContentBottom() + 1, BACKGROUND_TOP, BACKGROUND_BOTTOM);
		}
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
		graphics.text(font, styled, x, y, color);
		int badgeX = x + font.width(styled) + 6;
		graphics.fill(badgeX, y - 1, badgeX + font.width(BADGE) + 6, y + 9, BADGE_COLOR);
		graphics.text(font, BADGE, badgeX + 3, y, 0xFFFFFFFF, false);
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
