package com.amethystclient.mixin;

import com.amethystclient.packcache.ServerPackManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.s2c.common.ResourcePackRemoveS2CPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts the server's resource pack packets. ClientCommonNetworkHandler is shared by the
 * configuration and play phases, so this covers packs sent while joining and packs sent later.
 *
 * <p>Both handlers start with {@code NetworkThreadUtils.forceMainThread(...)}. On the network
 * thread that call reschedules the packet onto the render thread and aborts, so injecting right
 * after it guarantees our logic only runs once, on the render thread.
 */
@Mixin(ClientCommonNetworkHandler.class)
public abstract class ClientCommonNetworkHandlerMixin {
	private static final String FORCE_MAIN_THREAD =
			"Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/util/thread/ThreadExecutor;)V";

	@Shadow
	@Final
	protected MinecraftClient client;

	@Shadow
	@Final
	protected ClientConnection connection;

	@Shadow
	@Final
	@Nullable
	protected ServerInfo serverInfo;

	/**
	 * Runs before vanilla looks at the pack (before the confirmation screen or download). If the
	 * mod handles the pack (hash matched, or it is downloading the new version itself), vanilla is
	 * cancelled. Otherwise vanilla continues untouched.
	 */
	@Inject(method = "onResourcePackSend", at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD, shift = At.Shift.AFTER), cancellable = true)
	private void amethystclient$onResourcePackSend(ResourcePackSendS2CPacket packet, CallbackInfo ci) {
		ClientCommonNetworkHandler self = (ClientCommonNetworkHandler) (Object) this;
		// Fallback path: re-invoking the handler runs vanilla's normal flow for this packet.
		Runnable vanillaHandler = () -> self.onResourcePackSend(packet);

		if (ServerPackManager.get().onPackPush(packet, this.connection, this.serverInfo, this.client, vanillaHandler)) {
			ci.cancel();
		}
	}

	/** Observes pack removals so the next push of the same pack is evaluated again. Vanilla still runs. */
	@Inject(method = "onResourcePackRemove", at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD, shift = At.Shift.AFTER))
	private void amethystclient$onResourcePackRemove(ResourcePackRemoveS2CPacket packet, CallbackInfo ci) {
		ServerPackManager.get().onPackRemove(packet, this.connection);
	}
}
