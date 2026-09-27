package com.amethystclient.mixin;

import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The server being joined, which only the play phase's handler exposes publicly. */
@Mixin(ClientCommonNetworkHandler.class)
public interface ClientCommonNetworkHandlerAccessor {
	@Accessor("serverInfo")
	@Nullable
	ServerInfo amethystclient$getServerInfo();
}
