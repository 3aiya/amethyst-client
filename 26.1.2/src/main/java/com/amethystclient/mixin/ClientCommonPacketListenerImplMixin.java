package com.amethystclient.mixin;

import com.amethystclient.packcache.ServerPackManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts the server's resource pack packets. ClientCommonPacketListenerImpl is shared by the
 * configuration and play phases, so this covers packs sent while joining and packs sent later.
 *
 * <p>Both handlers start with {@code PacketUtils.ensureRunningOnSameThread(...)}. On the network
 * thread that call reschedules the packet onto the render thread and aborts, so injecting right
 * after it guarantees our logic only runs once, on the render thread.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin {
	private static final String FORCE_MAIN_THREAD =
			"Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V";

	@Shadow
	@Final
	protected Minecraft minecraft;

	@Shadow
	@Final
	protected Connection connection;

	@Shadow
	@Final
	@Nullable
	protected ServerData serverData;

	/**
	 * Runs before vanilla looks at the pack (before the confirmation screen or download). If the
	 * mod handles the pack (hash matched, or it is downloading the new version itself), vanilla is
	 * cancelled. Otherwise vanilla continues untouched.
	 */
	@Inject(method = "handleResourcePackPush", at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD, shift = At.Shift.AFTER), cancellable = true)
	private void amethystclient$onResourcePackSend(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
		ClientCommonPacketListenerImpl self = (ClientCommonPacketListenerImpl) (Object) this;
		// Fallback path: re-invoking the handler runs vanilla's normal flow for this packet.
		Runnable vanillaHandler = () -> self.handleResourcePackPush(packet);

		if (ServerPackManager.get().onPackPush(packet, this.connection, this.serverData, this.minecraft, vanillaHandler)) {
			ci.cancel();
		}
	}

	/** Observes pack removals so the next push of the same pack is evaluated again. Vanilla still runs. */
	@Inject(method = "handleResourcePackPop", at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD, shift = At.Shift.AFTER))
	private void amethystclient$onResourcePackRemove(ClientboundResourcePackPopPacket packet, CallbackInfo ci) {
		ServerPackManager.get().onPackRemove(packet, this.connection);
	}
}
