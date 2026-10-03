package com.amethystclient.mixin;

import com.amethystclient.AmethystServers;
import java.util.List;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the Amethyst server pinned to the top of the multiplayer list. */
@Mixin(ServerList.class)
public abstract class ServerListMixin {
	@Shadow
	@Final
	private List<ServerData> serverList;

	@Shadow
	@Final
	private List<ServerData> hiddenServerList;

	@Inject(method = "load", at = @At("TAIL"))
	private void amethystclient$pinAmethystServer(CallbackInfo ci) {
		AmethystServers.pinToTop(serverList, hiddenServerList);
	}

	@Inject(method = "remove", at = @At("HEAD"), cancellable = true)
	private void amethystclient$keepPinnedServer(ServerData thing, CallbackInfo ci) {
		if (AmethystServers.isPinned((ServerList) (Object) this, thing)) {
			ci.cancel();
		}
	}

	@Inject(method = "swap", at = @At("HEAD"), cancellable = true)
	private void amethystclient$keepPinnedOnTop(int a, int b, CallbackInfo ci) {
		if (!AmethystServers.canSwap((ServerList) (Object) this, a, b)) {
			ci.cancel();
		}
	}

	@Inject(method = "replace", at = @At("HEAD"), cancellable = true)
	private void amethystclient$keepPinnedSlot(int id, ServerData data, CallbackInfo ci) {
		if (id == 0 && !serverList.isEmpty() && AmethystServers.isPinned((ServerList) (Object) this, serverList.get(0))) {
			ci.cancel();
		}
	}
}
