package com.amethystclient.mixin;

import com.amethystclient.AmethystServers;
import com.amethystclient.scoreboard.StyledScoreboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
	/** On Amethyst Community servers, draw the styled sidebar instead of the vanilla one. */
	@Inject(
			method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
			at = @At("HEAD"),
			cancellable = true)
	private void amethystclient$styledSidebar(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
		if (AmethystServers.isAmethystServer(MinecraftClient.getInstance())) {
			StyledScoreboard.render(context, objective);
			ci.cancel();
		}
	}
}
