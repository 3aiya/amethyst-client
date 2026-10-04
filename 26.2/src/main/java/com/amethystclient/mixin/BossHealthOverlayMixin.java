package com.amethystclient.mixin;

import com.amethystclient.hud.Modules;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void amethystclient$hideBossBar(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (Modules.HIDE_BOSS_BAR.enabled()) {
			ci.cancel();
		}
	}
}
