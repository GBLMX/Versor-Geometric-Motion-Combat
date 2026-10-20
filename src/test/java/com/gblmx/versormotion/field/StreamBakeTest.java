package com.gblmx.versormotion.field;

import vsr.Calculus;
import vsr.E3;

/** Standalone checks for the divergence-free particle bake. */
public final class StreamBakeTest {
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

		double[] points = StreamBake.bake(10, 20, 30, StreamBake.SEEDS, StreamBake.STEPS);
		check("point count", points.length == StreamBake.SEEDS * StreamBake.STEPS * 3);
		for (int i = 0; i < points.length; i += 3) {
			check("height preserved", Math.abs(points[i + 1] - 20.0) < 1e-9);
		}

		for (int seed = 0; seed < StreamBake.SEEDS; seed++) {
			int i = seed * StreamBake.STEPS * 3;
			double dx = points[i] - 10.0;
			double dz = points[i + 2] - 30.0;
			check("seed on ring", Math.abs(Math.hypot(dx, dz) - StreamBake.SEED_RADIUS) < 1e-9);
		}

		check("streamlines move", points[0] != points[3] || points[2] != points[5]);

		double[] velocity = E3.coords(StreamBake.VELOCITY.at(0.5, 0, 0));
		check("vortex swirls around +Y",
				velocity[2] < -0.1 && Math.abs(velocity[1]) < 1e-9);
		for (double[] sample : new double[][]{{0.5, 0, 0}, {-0.7, 0.3, 0}, {1.4, -0.9, 2}}) {
			double div = Calculus.divergence(StreamBake.VELOCITY, sample[0], sample[1], sample[2]);
			check("divergence-free", Math.abs(div) < 1e-6);
		}
		check("rejects zero seeds", throwsIllegal(() ->
				StreamBake.bake(0, 0, 0, 0, StreamBake.STEPS)));

		// Baking is deterministic and centered on the requested origin.
		double[] again = StreamBake.bake(10, 20, 30, StreamBake.SEEDS, StreamBake.STEPS);
		check("bake is deterministic", java.util.Arrays.equals(points, again));
		double[] shifted = StreamBake.bake(11, 20, 30, StreamBake.SEEDS, StreamBake.STEPS);
		check("bake follows the center", Math.abs(shifted[0] - points[0] - 1.0) < 1e-9);

		System.out.println(failures == 0
				? "OK: " + checks + " field checks passed"
				: "FAILED: " + failures + "/" + checks + " field checks");
		return failures;
	}

	private static boolean throwsIllegal(Runnable runnable) {
		try {
			runnable.run();
			return false;
		} catch (IllegalArgumentException e) {
			return true;
		}
	}

	private static void check(String name, boolean condition) {
		checks++;
		if (!condition) {
			failures++;
			System.out.println("FAIL " + name);
		}
	}
}
