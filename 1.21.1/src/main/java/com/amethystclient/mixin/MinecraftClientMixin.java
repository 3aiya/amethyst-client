package com.amethystclient.mixin;

import com.amethystclient.packcache.ServerPackManager;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	/**
	 * On a failed resource reload, vanilla disables every optional pack and retries. Our pack is
	 * "required", so it would survive and could fail the retry too. Drop it before the retry runs.
	 */
	@Inject(method = "onResourceReloadFailure", at = @At("HEAD"))
	private void amethystclient$dropCachedPackOnReloadFailure(CallbackInfo ci) {
		ServerPackManager.get().onResourceReloadFailure();
	}
}
