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
 * The pinned Amethyst server's row: an amethyst tint, a styled name with an "OFFICIAL" badge, and
 * no move arrows. Rows can't be moved above it either.
 */
@Mixin(MultiplayerServerListWidget.ServerEntry.class)
public abstract class ServerEntryMixin {
	@Unique
	private static final int BACKGROUND_TOP = 0x449D4EDD;
	@Unique
	private static final int BACKGROUND_BOTTOM = 0x149D4EDD;
	@Unique
	private static final int NAME_COLOR = 0xD8B4FE;
	@Unique
	private static final int BADGE_COLOR = 0xFF7B2CBF;
	@Unique
	private static final Text BADGE = Text.literal("OFFICIAL");

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

	@Inject(method = "render", at = @At("HEAD"))
	private void amethystclient$drawPinnedBackground(DrawContext context, int index, int y, int x, int entryWidth,
			int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci) {
		if (amethystclient$isPinned()) {
			context.fillGradient(x - 1, y - 1, x + entryWidth + 1, y + entryHeight + 1, BACKGROUND_TOP, BACKGROUND_BOTTOM);
		}
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)I"))
	private int amethystclient$drawPinnedName(DrawContext context, TextRenderer textRenderer, String name, int x, int y,
			int color, boolean shadow, Operation<Integer> original) {
		if (!amethystclient$isPinned()) {
			return original.call(context, textRenderer, name, x, y, color, shadow);
		}
		Text styled = Text.literal(name).styled(style -> style.withBold(true).withColor(NAME_COLOR));
		int end = context.drawText(textRenderer, styled, x, y, color, shadow);
		int badgeX = x + textRenderer.getWidth(styled) + 6;
		context.fill(badgeX, y - 1, badgeX + textRenderer.getWidth(BADGE) + 6, y + 9, BADGE_COLOR);
		context.drawText(textRenderer, BADGE, badgeX + 3, y, 0xFFFFFF, false);
		return end;
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
