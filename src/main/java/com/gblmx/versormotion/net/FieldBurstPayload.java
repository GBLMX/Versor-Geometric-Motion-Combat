package com.gblmx.versormotion.net;

import com.gblmx.versormotion.VersorMotion;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-baked field particles. Clients emit the samples in order over the
 * given duration.
 */
public record FieldBurstPayload(int duration, float[] points) implements CustomPacketPayload {
	private static final int MAX_POINTS = 4096;

	public static final Type<FieldBurstPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath(VersorMotion.MOD_ID, "field_burst"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FieldBurstPayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeVarInt(payload.duration);
				buf.writeVarInt(payload.points.length);
				for (float point : payload.points) {
					buf.writeFloat(point);
				}
			},
			buf -> {
				int duration = buf.readVarInt();
				int length = buf.readVarInt();
				if (length <= 0 || length > MAX_POINTS || length % 3 != 0) {
					throw new DecoderException("invalid field burst length " + length);
				}
				float[] points = new float[length];
				for (int i = 0; i < length; i++) {
					points[i] = buf.readFloat();
				}
				return new FieldBurstPayload(duration, points);
			});

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
