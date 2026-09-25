package com.amethystclient.presence;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The game mode the player is in behind the Amethyst proxy, e.g. "Survival". The client sends an
 * empty mode to ask for it; the AmethystPresence proxy plugin answers with the current one.
 */
public record ModePayload(String mode) implements CustomPacketPayload {
	public static final Type<ModePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("amethyst", "presence"));
	public static final StreamCodec<ByteBuf, ModePayload> CODEC = ByteBufCodecs.STRING_UTF8.map(ModePayload::new, ModePayload::mode);

	@Override
	public Type<ModePayload> type() {
		return TYPE;
	}
}
