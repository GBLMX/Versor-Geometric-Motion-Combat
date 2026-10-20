package com.gblmx.versormotion.combat;

import vsr.E3;
import vsr.Motion;

/**
 * Blade sweep geometry shared by the server adjudicator and the client
 * presentation layer.
 *
 * <p>The blade is a circular arc at eye height: a horizontal reach vector is
 * rotated about world +Y from {@code -HALF_ANGLE} to {@code +HALF_ANGLE} with a
 * PGA screw motion ({@link Motion#rotation}) and then translated to the eye.
 * Baking samples the arc once per swing; the flat xyz array is also what the
 * network payload carries, so the client trail and its particles show exactly
 * the volume the server tested.
 */
public final class ArcSweep {
	public static final int SAMPLES = 48;
	public static final double HALF_ANGLE = Math.toRadians(65.0);
	/** Thickness of the swept blade in blocks, matching a sword's edge. */
	public static final double BLADE_RADIUS = 0.55;
	public static final double MIN_RADIUS = 2.0;
	public static final double MAX_RADIUS = 6.0;

	private ArcSweep() {}

	/**
	 * Bakes {@code samples} points along the swing arc.
	 *
	 * @param eyeX, eyeY, eyeZ  arc centre, the attacker's eye position
	 * @param yawRadians        vanilla yaw in radians (0 faces +Z, +90 faces -X)
	 * @param radius            blade reach in blocks
	 * @return flat xyz triples, ordered along the swing
	 */
	public static double[] bake(double eyeX, double eyeY, double eyeZ,
			double yawRadians, double radius, int samples) {
		if (samples < 2) {
			throw new IllegalArgumentException("samples must be >= 2");
		}
		if (!Double.isFinite(radius) || radius <= 0) {
			throw new IllegalArgumentException("radius must be finite and positive");
		}
		if (!Double.isFinite(yawRadians)) {
			throw new IllegalArgumentException("yaw must be finite");
		}

		double reachX = -Math.sin(yawRadians) * radius;
		double reachZ = Math.cos(yawRadians) * radius;
		double[] start = E3.coords(E3.rotate(
				E3.vec(reachX, 0, reachZ), E3.rotor(0, 1, 0, -HALF_ANGLE)));
		Motion sweep = Motion.rotation(0, 1, 0, 2 * HALF_ANGLE);

		double[] points = new double[samples * 3];
		for (int i = 0; i < samples; i++) {
			double[] p = sweep.position(i / (samples - 1.0), start[0], 0, start[2]);
			points[i * 3] = eyeX + p[0];
			points[i * 3 + 1] = eyeY + p[1];
			points[i * 3 + 2] = eyeZ + p[2];
		}
		return points;
	}

	/** @return whether any arc segment passes within {@code bladeRadius} of the box */
	public static boolean hits(double[] arc, double bladeRadius,
			double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		if (arc.length < 6 || arc.length % 3 != 0) {
			throw new IllegalArgumentException("arc must contain at least two xyz triples");
		}
		double limit = bladeRadius * bladeRadius;
		for (int i = 0; i + 5 < arc.length; i += 3) {
			if (segmentDistanceSq(arc, i, i + 3,
					minX, minY, minZ, maxX, maxY, maxZ) <= limit) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Squared distance between the arc segment {@code a..b} and an axis-aligned
	 * box. The distance to a convex box along a segment is convex in the
	 * segment parameter, so a ternary search finds the minimum without
	 * subdividing (the blade therefore cannot tunnel between samples).
	 */
	public static double segmentDistanceSq(double[] arc, int a, int b,
			double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		double ax = arc[a], ay = arc[a + 1], az = arc[a + 2];
		double dx = arc[b] - ax;
		double dy = arc[b + 1] - ay;
		double dz = arc[b + 2] - az;

		double low = 0.0;
		double high = 1.0;
		for (int i = 0; i < 32; i++) {
			double m1 = low + (high - low) / 3.0;
			double m2 = high - (high - low) / 3.0;
			double d1 = pointDistanceSq(ax + dx * m1, ay + dy * m1, az + dz * m1,
					minX, minY, minZ, maxX, maxY, maxZ);
			double d2 = pointDistanceSq(ax + dx * m2, ay + dy * m2, az + dz * m2,
					minX, minY, minZ, maxX, maxY, maxZ);
			if (d1 <= d2) {
				high = m2;
			} else {
				low = m1;
			}
		}
		double t = (low + high) / 2.0;
		return pointDistanceSq(ax + dx * t, ay + dy * t, az + dz * t,
				minX, minY, minZ, maxX, maxY, maxZ);
	}

	public static double pointDistanceSq(double px, double py, double pz,
			double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		double dx = px - Math.max(minX, Math.min(maxX, px));
		double dy = py - Math.max(minY, Math.min(maxY, py));
		double dz = pz - Math.max(minZ, Math.min(maxZ, pz));
		return dx * dx + dy * dy + dz * dz;
	}

	public static float[] toFloats(double[] arc) {
		float[] floats = new float[arc.length];
		for (int i = 0; i < arc.length; i++) {
			floats[i] = (float) arc[i];
		}
		return floats;
	}
}
