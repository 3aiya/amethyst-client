package com.amethystclient.presence;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * The game mode the player is in behind the Amethyst proxy, e.g. "Survival". The client sends an
 * empty mode to ask for it; the AmethystPresence proxy plugin answers with the current one.
 */
public record ModePayload(String mode) implements CustomPayload {
	public static final CustomPayload.Id<ModePayload> ID = new CustomPayload.Id<>(Identifier.of("amethyst", "presence"));
	public static final PacketCodec<ByteBuf, ModePayload> CODEC = PacketCodecs.STRING.xmap(ModePayload::new, ModePayload::mode);

	@Override
	public CustomPayload.Id<ModePayload> getId() {
		return ID;
	}
}
