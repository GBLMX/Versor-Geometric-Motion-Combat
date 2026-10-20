package com.gblmx.versormotion.field;

import com.gblmx.versormotion.net.FieldBurstPayload;
import com.gblmx.versormotion.net.Payloads;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Server-side trigger for a baked vortex burst. */
public final class FieldBurst {
	public static final int DURATION_TICKS = 10;

	private FieldBurst() {}

	/** @return number of baked particle positions sent to the clients */
	public static int burst(ServerLevel level, Vec3 center) {
		double[] baked = StreamBake.bake(center.x, center.y, center.z,
				StreamBake.SEEDS, StreamBake.STEPS);
		float[] points = new float[baked.length];
		for (int i = 0; i < baked.length; i++) {
			points[i] = (float) baked[i];
		}
		Payloads.broadcast(level, new FieldBurstPayload(DURATION_TICKS, points));
		return baked.length / 3;
	}
}
