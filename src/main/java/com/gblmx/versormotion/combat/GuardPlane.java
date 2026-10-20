package com.gblmx.versormotion.combat;

import vsr.Multivector;
import vsr.P3;

/**
 * Parry test: does the baked blade arc cross the defender's guard plane
 * within shield reach?
 *
 * <p>The guard plane is built from the defender's eye position and look
 * vector ({@code n.x + d = 0}, the same convention as {@link P3#plane}), and
 * the crossing point is the PGA meet of that plane with the arc segment. A
 * crossing only counts when it lands inside both the shield radius and the
 * segment; the signed-distance test selects the segment before the meet so
 * parallel arcs never reach {@link P3#pointCoords}.
 */
public final class GuardPlane {
	/** Shield coverage radius around the defender's eye. */
	public static final double REACH = 1.15;

	public record Hit(double x, double y, double z) {}

	private GuardPlane() {}

	public static Hit intersect(double[] arc,
			double ox, double oy, double oz,
			double nx, double ny, double nz, double d, double reach) {
		if (arc.length < 6 || arc.length % 3 != 0) {
			throw new IllegalArgumentException("arc must contain at least two xyz triples");
		}
		Multivector plane = P3.plane(nx, ny, nz, d);
		for (int i = 0; i + 5 < arc.length; i += 3) {
			double ax = arc[i], ay = arc[i + 1], az = arc[i + 2];
			double bx = arc[i + 3], by = arc[i + 4], bz = arc[i + 5];
			double sa = nx * ax + ny * ay + nz * az + d;
			double sb = nx * bx + ny * by + nz * bz + d;
			if (!(sa * sb < 0)) {
				continue;
			}

			double[] point;
			try {
				Multivector line = P3.line(P3.point(ax, ay, az), P3.point(bx, by, bz));
				point = P3.pointCoords(P3.meetPlaneLine(plane, line));
			} catch (IllegalArgumentException degenerate) {
				continue;
			}

			double dx = point[0] - ox;
			double dy = point[1] - oy;
			double dz = point[2] - oz;
			if (dx * dx + dy * dy + dz * dz <= reach * reach) {
				return new Hit(point[0], point[1], point[2]);
			}
		}
		return null;
	}
}
