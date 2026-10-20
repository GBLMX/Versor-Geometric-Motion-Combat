package com.gblmx.versormotion.field;

import vsr.Calculus;
import vsr.E3;
import vsr.Field;

/**
 * Bakes particle seeds from a divergence-free velocity field.
 *
 * <p>The velocity field is the curl of a scalar stream function
 * {@code A = (0, 0, psi)}, so {@code div(v) = 0} by construction and the
 * particles circulate instead of piling up. Seeds sit on a ring around the
 * origin and are advected one bake step at a time; the resulting positions are
 * what the server sends to clients.
 */
public final class StreamBake {
	public static final int SEEDS = 10;
	public static final int STEPS = 6;
	public static final double SEED_RADIUS = 0.5;
	public static final double STEP_LENGTH = 0.35;

	private static final Field POTENTIAL = (x, y, z) ->
			E3.vec(0, -0.5 * Math.log(1.0 + x * x + z * z), 0);

	/** Curl of {@link #POTENTIAL}: a horizontal vortex around +Y. */
	public static final Field VELOCITY = (x, y, z) -> Calculus.curl(POTENTIAL, x, y, z);

	private StreamBake() {}

	public static double[] bake(double cx, double cy, double cz, int seeds, int steps) {
		if (seeds < 1 || steps < 1) {
			throw new IllegalArgumentException("seeds and steps must be positive");
		}
		double[] points = new double[seeds * steps * 3];
		int index = 0;
		for (int seed = 0; seed < seeds; seed++) {
			double angle = 2.0 * Math.PI * seed / seeds;
			double px = cx + SEED_RADIUS * Math.cos(angle);
			double py = cy;
			double pz = cz + SEED_RADIUS * Math.sin(angle);
			for (int step = 0; step < steps; step++) {
				points[index++] = px;
				points[index++] = py;
				points[index++] = pz;

				double[] v = E3.coords(VELOCITY.at(px, py, pz));
				double length = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
				if (length < 1e-9) {
					continue;
				}
				px += v[0] / length * STEP_LENGTH;
				py += v[1] / length * STEP_LENGTH;
				pz += v[2] / length * STEP_LENGTH;
			}
		}
		return points;
	}
}
