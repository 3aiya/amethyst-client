package com.amethystclient.mixin;

import com.amethystclient.scoreboard.StyledScoreboard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
	/** On Amethyst Community servers, draw the styled sidebar instead of the vanilla one. */
	@Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
	private void amethystclient$styledSidebar(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
		if (StyledScoreboard.isStyledServer(Minecraft.getInstance())) {
			StyledScoreboard.extract(graphics, objective);
			ci.cancel();
		}
	}
}
