package com.gblmx.versormotion.net;

import com.gblmx.versormotion.VersorMotion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client camera impulse after taking damage. */
public record CameraShakePayload(float amplitude, int duration) implements CustomPacketPayload {
	public static final Type<CameraShakePayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath(VersorMotion.MOD_ID, "camera_shake"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CameraShakePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, CameraShakePayload::amplitude,
			ByteBufCodecs.VAR_INT, CameraShakePayload::duration,
			CameraShakePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
