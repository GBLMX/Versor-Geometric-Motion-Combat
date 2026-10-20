package com.gblmx.versormotion.net;

import com.gblmx.versormotion.VersorMotion;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * One baked tornado: its axis base and the funnel sample points. Clients spin
 * the point cloud around the axis and emit particles from it.
 */
public record TornadoPayload(double x, double y, double z, int duration, float[] points)
		implements CustomPacketPayload {
	private static final int MAX_POINTS = 4096;

	public static final Type<TornadoPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath(VersorMotion.MOD_ID, "tornado"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TornadoPayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeDouble(payload.x);
				buf.writeDouble(payload.y);
				buf.writeDouble(payload.z);
				buf.writeVarInt(payload.duration);
				buf.writeVarInt(payload.points.length);
				for (float point : payload.points) {
					buf.writeFloat(point);
				}
			},
			buf -> {
				double x = buf.readDouble();
				double y = buf.readDouble();
				double z = buf.readDouble();
				int duration = buf.readVarInt();
				int length = buf.readVarInt();
				if (length <= 0 || length > MAX_POINTS || length % 3 != 0) {
					throw new DecoderException("invalid tornado length " + length);
				}
				if (duration <= 0 || duration > 20 * 60) {
					throw new DecoderException("invalid tornado duration " + duration);
				}
				float[] points = new float[length];
				for (int i = 0; i < length; i++) {
					points[i] = buf.readFloat();
				}
				return new TornadoPayload(x, y, z, duration, points);
			});

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
