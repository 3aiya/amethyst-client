package com.amethystclient.mixin;

import com.amethystclient.hud.Modules;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossBarHud.class)
public abstract class BossBarHudMixin {
	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true)
	private void amethystclient$hideBossBar(DrawContext context, CallbackInfo ci) {
		if (Modules.HIDE_BOSS_BAR.enabled()) {
			ci.cancel();
		}
	}
}
