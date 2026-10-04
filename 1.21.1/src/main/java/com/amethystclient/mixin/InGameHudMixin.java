package com.amethystclient.mixin;

import com.amethystclient.AmethystServers;
import com.amethystclient.hud.AmethystHud;
import com.amethystclient.hud.Modules;
import com.amethystclient.scoreboard.StyledScoreboard;
import com.amethystclient.ui.Draw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
	/**
	 * Hide Scoreboard hides the sidebar; otherwise Styled Scoreboard draws it as the Amethyst
	 * panels (on Amethyst Community servers, or everywhere if set so).
	 */
	@Inject(
			method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
			at = @At("HEAD"),
			cancellable = true)
	private void amethystclient$styledSidebar(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
		if (Modules.HIDE_SCOREBOARD.enabled()) {
			ci.cancel();
		} else if (Modules.STYLED_SCOREBOARD.replaces(AmethystServers.isAmethystServer(MinecraftClient.getInstance()))) {
			StyledScoreboard.render(context, objective);
			ci.cancel();
		}
	}

	@Inject(
			method = "renderTitleAndSubtitle(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
			at = @At("HEAD"),
			cancellable = true)
	private void amethystclient$hideTitles(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		if (Modules.HIDE_TITLES.enabled()) {
			ci.cancel();
		}
	}

	/** The Amethyst HUD modules, on top of the vanilla HUD. */
	@Inject(
			method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
			at = @At("TAIL"))
	private void amethystclient$hud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		AmethystHud.render(new Draw(context));
	}
}
