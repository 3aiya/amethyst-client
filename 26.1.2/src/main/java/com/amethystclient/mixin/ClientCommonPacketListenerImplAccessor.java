package com.amethystclient.mixin;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The server being joined, which only the play phase's listener exposes publicly. */
@Mixin(ClientCommonPacketListenerImpl.class)
public interface ClientCommonPacketListenerImplAccessor {
	@Accessor("serverData")
	@Nullable
	ServerData amethystclient$getServerData();
}
