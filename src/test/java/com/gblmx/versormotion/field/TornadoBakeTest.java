package com.gblmx.versormotion.field;

import java.util.Arrays;

import vsr.E3;

/** Standalone checks for the tornado field and streamline bake. */
public final class TornadoBakeTest {
	private static int checks;
	private static int failures;

	public static void main(String[] args) {
		if (run() > 0) {
			System.exit(1);
		}
	}

	public static int run() {
		checks = 0;
		failures = 0;

		int count = TornadoBake.SEEDS * TornadoBake.STEPS * 3;
		double[] points = TornadoBake.bake(10, 20, 30);
		check("point count", points.length == count);

		boolean finite = true;
		for (double value : points) {
			finite &= Double.isFinite(value);
		}
		check("finite points", finite);

		// Deterministic and centered on the requested base.
		check("bake is deterministic", Arrays.equals(points, TornadoBake.bake(10, 20, 30)));
		double[] shifted = TornadoBake.bake(11, 20, 30);
		boolean follows = true;
		for (int i = 0; i < points.length; i += 3) {
			follows &= Math.abs(shifted[i] - points[i] - 1.0) < 1e-9;
		}
		check("bake follows the base", follows);

		// Seeds sit on the ground ring.
		boolean onRing = true;
		for (int seed = 0; seed < TornadoBake.SEEDS; seed++) {
			int i = seed * TornadoBake.STEPS * 3;
			double dx = points[i] - 10.0;
			double dz = points[i + 2] - 30.0;
			onRing &= Math.abs(Math.hypot(dx, dz) - TornadoBake.SEED_RADIUS) < 1e-9;
			onRing &= Math.abs(points[i + 1] - 20.0) < 1e-9;
		}
		check("seeds on the ground ring", onRing);

		// Every streamline rises, stays inside the envelope and winds around.
		boolean rises = true;
		boolean inside = true;
		boolean boundedRadius = true;
		boolean winds = true;
		boolean reachesTop = false;
		for (int seed = 0; seed < TornadoBake.SEEDS; seed++) {
			double previousY = Double.NEGATIVE_INFINITY;
			double turn = 0;
			double previousAngle = Double.NaN;
			for (int step = 0; step < TornadoBake.STEPS; step++) {
				int i = (seed * TornadoBake.STEPS + step) * 3;
				double y = points[i + 1] - 20.0;
				double r = Math.hypot(points[i] - 10.0, points[i + 2] - 30.0);
				rises &= y >= previousY - 1e-9;
				inside &= y >= 0 && y <= TornadoBake.HEIGHT + 1e-9;
				boundedRadius &= r > 0 && r < TornadoBake.TOP_RADIUS + 1.5;
				if (y >= TornadoBake.HEIGHT - 1e-6) {
					reachesTop = true;
				}
				double angle = Math.atan2(points[i + 2] - 30.0, points[i] - 10.0);
				if (Double.isFinite(previousAngle)) {
					double delta = angle - previousAngle;
					while (delta > Math.PI) {
						delta -= 2 * Math.PI;
					}
					while (delta < -Math.PI) {
						delta += 2 * Math.PI;
					}
					turn += delta;
				}
				previousAngle = angle;
				previousY = y;
			}
			winds &= Math.abs(turn) > Math.PI;
		}
		check("streamlines rise", rises);
		check("streamlines stay inside", inside);
		check("radius bounded", boundedRadius);
		check("streamlines wind around the axis", winds);
		check("reaches the top", reachesTop);

		// Field properties: counter-clockwise swirl around +Y, inward, updraft.
		double[] tangent = E3.coords(TornadoBake.VELOCITY.at(1.1, 0, 0));
		check("swirls counter-clockwise", tangent[2] > 0.1 && tangent[0] < 0);
		double[] core = E3.coords(TornadoBake.VELOCITY.at(0, 0, 0));
		check("updraft on the axis",
				core[1] > TornadoBake.UPDRAFT && Math.abs(core[0]) < 1e-9 && Math.abs(core[2]) < 1e-9);
		boolean alwaysRises = true;
		for (double[] sample : new double[][]{{0.5, 0, 0}, {-1.4, 3, 2}, {2.0, 8.0, 0}}) {
			alwaysRises &= E3.coords(TornadoBake.VELOCITY.at(sample[0], sample[1], sample[2]))[1] > 0;
		}
		check("always rises", alwaysRises);

		// The funnel flares with height.
		check("base radius", Math.abs(TornadoBake.funnelRadius(0) - TornadoBake.BASE_RADIUS) < 1e-9);
		check("top radius",
				Math.abs(TornadoBake.funnelRadius(TornadoBake.HEIGHT) - TornadoBake.TOP_RADIUS) < 1e-9);
		check("radius grows", TornadoBake.funnelRadius(4) > TornadoBake.funnelRadius(2));

		System.out.println(failures == 0
				? "OK: " + checks + " tornado checks passed"
				: "FAILED: " + failures + "/" + checks + " tornado checks");
		return failures;
	}

	private static void check(String name, boolean condition) {
		checks++;
		if (!condition) {
			failures++;
			System.out.println("FAIL " + name);
		}
	}
}
