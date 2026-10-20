package com.gblmx.versormotion.net;

import com.gblmx.versormotion.VersorMotion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent when a player starts a dodge roll. Only the state transition travels;
 * clients animate locally from the receipt tick and the duration.
 */
public record RollPayload(int entityId, int duration) implements CustomPacketPayload {
	public static final Type<RollPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath(VersorMotion.MOD_ID, "roll"));

	public static final StreamCodec<RegistryFriendlyByteBuf, RollPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, RollPayload::entityId,
			ByteBufCodecs.VAR_INT, RollPayload::duration,
			RollPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
