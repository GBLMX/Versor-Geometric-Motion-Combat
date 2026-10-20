package com.gblmx.versormotion.field;

import com.gblmx.versormotion.net.Payloads;
import com.gblmx.versormotion.net.TornadoPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Server-side trigger for a baked tornado funnel. */
public final class Tornado {
	/** How long clients keep simulating and rendering the funnel, in ticks. */
	public static final int TICKS = 200;

	private Tornado() {}

	/** @return number of baked funnel points sent to the clients */
	public static int spawn(ServerLevel level, Vec3 base) {
		double[] baked = TornadoBake.bake(base.x, base.y, base.z);
		float[] points = new float[baked.length];
		for (int i = 0; i < baked.length; i++) {
			points[i] = (float) baked[i];
		}
		Payloads.broadcast(level, new TornadoPayload(base.x, base.y, base.z, TICKS, points));
		return baked.length / 3;
	}
}
