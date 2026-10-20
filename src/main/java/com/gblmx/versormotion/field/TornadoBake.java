package com.gblmx.versormotion.field;

import vsr.E3;
import vsr.Field;

/**
 * Bakes a tornado funnel from an analytic Rankine-vortex velocity field.
 *
 * <p>The field combines a Rankine swirl (solid-body core, {@code 1/r} halo),
 * radial inflow and an updraft concentrated on the axis:
 *
 * <pre>
 *   v_t = V_CORE * (r &lt;= r0 ? r/r0 : r0/r)
 *   v_r = -INFLOW * v_t
 *   v_y = UPDRAFT * exp(-(r/r0)^2) + RISE
 * </pre>
 *
 * <p>{@code r0(y)} widens with height, so integrating the normalized field
 * from seeds on the ground ring produces the flared, spiralling streamlines a
 * funnel is made of. The result is deterministic and ready to ship to clients
 * as one point list; the client only rotates it.
 */
public final class TornadoBake {
	public static final int SEEDS = 8;
	public static final int STEPS = 44;
	/** Height of the funnel, in blocks. */
	public static final double HEIGHT = 9.0;
	/** Funnel radius at the ground and at the top. */
	public static final double BASE_RADIUS = 0.45;
	public static final double TOP_RADIUS = 2.6;
	/** Seed radius on the ground; streamlines start just outside the core. */
	public static final double SEED_RADIUS = 1.1;
	/** Peak tangential speed, in blocks per second. */
	public static final double CORE_SPEED = 4.0;
	/** Radial inflow as a fraction of the tangential speed. */
	public static final double INFLOW = 0.15;
	/** Updraft on the axis, in blocks per second. */
	public static final double UPDRAFT = 14.0;
	/** Background updraft outside the core, in blocks per second. */
	public static final double RISE = 3.0;
	/** Arc length of one integration step. */
	public static final double STEP_LENGTH = 0.3;
	/** Streamlines are kept outside this fraction of the funnel radius. */
	public static final double CORE_FLOOR = 0.35;

	/** Funnel radius at height {@code y} above the base. */
	public static double funnelRadius(double y) {
		double t = Math.clamp(y / HEIGHT, 0.0, 1.0);
		return BASE_RADIUS + (TOP_RADIUS - BASE_RADIUS) * t * t;
	}

	/** Velocity at local coordinates: funnel axis at the origin, base at y=0. */
	public static final Field VELOCITY = (x, y, z) -> {
		double r = Math.hypot(x, z);
		double r0 = funnelRadius(y);
		double tangential = r < 1e-9 ? 0.0 : CORE_SPEED * Math.min(1.0, r / r0);
		double tx = r < 1e-9 ? 0.0 : -z / r;
		double tz = r < 1e-9 ? 0.0 : x / r;
		double rx = r < 1e-9 ? 0.0 : x / r;
		double rz = r < 1e-9 ? 0.0 : z / r;
		double inflow = -INFLOW * tangential;
		double updraft = UPDRAFT * Math.exp(-(r * r) / (r0 * r0)) + RISE;
		return E3.vec(tangential * tx + inflow * rx, updraft, tangential * tz + inflow * rz);
	};

	private TornadoBake() {}

	/**
	 * Integrates {@link #SEEDS} streamlines from the ground ring and returns
	 * {@code SEEDS * STEPS} absolute points as {@code x, y, z} triples.
	 *
	 * <p>Streamlines stop rising at {@link #HEIGHT}; their remaining samples
	 * stay on the cap so the point count is fixed and the payload stays simple.
	 */
	public static double[] bake(double cx, double cy, double cz) {
		double[] points = new double[SEEDS * STEPS * 3];
		int index = 0;
		for (int seed = 0; seed < SEEDS; seed++) {
			double angle = 2.0 * Math.PI * seed / SEEDS;
			double px = SEED_RADIUS * Math.cos(angle);
			double py = 0;
			double pz = SEED_RADIUS * Math.sin(angle);
			for (int step = 0; step < STEPS; step++) {
				points[index++] = cx + px;
				points[index++] = cy + py;
				points[index++] = cz + pz;

				double[] v = E3.coords(VELOCITY.at(px, py, pz));
				double length = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
				if (length < 1e-9) {
					continue;
				}
				px += v[0] / length * STEP_LENGTH;
				py = Math.min(HEIGHT, py + v[1] / length * STEP_LENGTH);
				pz += v[2] / length * STEP_LENGTH;

				double r = Math.hypot(px, pz);
				double floor = CORE_FLOOR * funnelRadius(py);
				if (r < floor) {
					double scale = floor / Math.max(r, 1e-9);
					px *= scale;
					pz *= scale;
				}
			}
		}
		return points;
	}
}
