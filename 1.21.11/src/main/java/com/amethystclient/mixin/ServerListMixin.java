package com.amethystclient.mixin;

import com.amethystclient.AmethystServers;
import java.util.List;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
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
	private List<ServerInfo> servers;

	@Shadow
	@Final
	private List<ServerInfo> hiddenServers;

	@Inject(method = "loadFile", at = @At("TAIL"))
	private void amethystclient$pinAmethystServer(CallbackInfo ci) {
		AmethystServers.pinToTop(servers, hiddenServers);
	}

	@Inject(method = "remove", at = @At("HEAD"), cancellable = true)
	private void amethystclient$keepPinnedServer(ServerInfo serverInfo, CallbackInfo ci) {
		if (AmethystServers.isPinned((ServerList) (Object) this, serverInfo)) {
			ci.cancel();
		}
	}

	@Inject(method = "swapEntries", at = @At("HEAD"), cancellable = true)
	private void amethystclient$keepPinnedOnTop(int index1, int index2, CallbackInfo ci) {
		if (!AmethystServers.canSwap((ServerList) (Object) this, index1, index2)) {
			ci.cancel();
		}
	}

	@Inject(method = "set", at = @At("HEAD"), cancellable = true)
	private void amethystclient$keepPinnedSlot(int index, ServerInfo serverInfo, CallbackInfo ci) {
		if (index == 0 && !servers.isEmpty() && AmethystServers.isPinned((ServerList) (Object) this, servers.get(0))) {
			ci.cancel();
		}
	}
}
