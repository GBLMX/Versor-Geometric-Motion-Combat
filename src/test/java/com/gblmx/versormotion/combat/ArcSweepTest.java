package com.gblmx.versormotion.combat;

/** Standalone checks for the swing arc geometry ({@code ./gradlew combatTest}). */
public final class ArcSweepTest {
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
		testShape();
		testYaw();
		testEndpoints();
		testHitAlongSegment();
		testMiss();
		testRejectsInvalidInput();
		System.out.println(failures == 0
				? "OK: " + checks + " arc checks passed"
				: "FAILED: " + failures + "/" + checks + " arc checks");
		return failures;
	}

	private static void testShape() {
		double[] arc = ArcSweep.bake(10.0, 20.0, 30.0, 0.0, 3.0, ArcSweep.SAMPLES);
		check("sample count", arc.length == ArcSweep.SAMPLES * 3);
		for (int i = 0; i < arc.length; i += 3) {
			double dx = arc[i] - 10.0;
			double dy = arc[i + 1] - 20.0;
			double dz = arc[i + 2] - 30.0;
			check("height preserved", Math.abs(dy) < 1e-9);
			check("radius preserved", Math.abs(Math.hypot(dx, dz) - 3.0) < 1e-9);
		}
	}

	private static void testYaw() {
		double[] forward = ArcSweep.bake(0, 0, 0, 0.0, 4.0, 3);
		near("yaw 0 faces +Z", forward[5], 4.0);
		near("yaw 0 has no X", forward[3], 0.0);

		double[] west = ArcSweep.bake(0, 0, 0, Math.PI / 2, 4.0, 3);
		near("yaw 90 faces -X", west[3], -4.0);
		near("yaw 90 has no Z", west[5], 0.0);
	}

	private static void testEndpoints() {
		double[] arc = ArcSweep.bake(0, 0, 0, 0.0, 2.0, 3);
		double expectedX = 2.0 * Math.sin(ArcSweep.HALF_ANGLE);
		double expectedZ = 2.0 * Math.cos(ArcSweep.HALF_ANGLE);
		near("start X", arc[0], -expectedX);
		near("start Z", arc[2], expectedZ);
		near("end X", arc[6], expectedX);
		near("end Z", arc[8], expectedZ);
	}

	private static void testHitAlongSegment() {
		double[] dense = ArcSweep.bake(0, 0, 0, 0.0, 3.0, ArcSweep.SAMPLES);
		check("hit at arc centre",
				ArcSweep.hits(dense, ArcSweep.BLADE_RADIUS, -0.2, -1, 2.8, 0.2, 1, 3.2));
		check("thin target touching the blade",
				ArcSweep.hits(dense, ArcSweep.BLADE_RADIUS, 2.95, -0.1, 0.9, 3.05, 0.1, 1.1));
		check("hit at the arc end",
				ArcSweep.hits(dense, ArcSweep.BLADE_RADIUS, 2.5, -1, 1.0, 3.0, 1, 1.6));

		// Two samples leave only the chord between the endpoints; a target at the
		// chord midpoint is farther than the blade radius from every sample, so
		// only the segment test can catch it.
		double[] sparse = ArcSweep.bake(0, 0, 0, 0.0, 3.0, 2);
		double chordZ = 3.0 * Math.cos(ArcSweep.HALF_ANGLE);
		check("tunnelling between samples is caught",
				ArcSweep.hits(sparse, ArcSweep.BLADE_RADIUS,
						-0.1, -1, chordZ - 0.1, 0.1, 1, chordZ + 0.1));
	}

	private static void testMiss() {
		double[] arc = ArcSweep.bake(0, 0, 0, 0.0, 3.0, ArcSweep.SAMPLES);
		check("behind the attacker misses",
				!ArcSweep.hits(arc, ArcSweep.BLADE_RADIUS, -0.2, -1, -3.2, 0.2, 1, -2.8));
		check("outside the radius misses",
				!ArcSweep.hits(arc, ArcSweep.BLADE_RADIUS, -0.2, -1, 5.0, 0.2, 1, 5.4));
		check("above the arc misses",
				!ArcSweep.hits(arc, ArcSweep.BLADE_RADIUS, -0.2, 3.0, 2.8, 0.2, 4.0, 3.2));
	}

	private static void testRejectsInvalidInput() {
		check("rejects one sample", throwsIllegal(() -> ArcSweep.bake(0, 0, 0, 0, 3, 1)));
		check("rejects zero radius", throwsIllegal(() -> ArcSweep.bake(0, 0, 0, 0, 0, 8)));
		check("rejects short arc", throwsIllegal(() ->
				ArcSweep.hits(new double[3], 0.5, -1, -1, -1, 1, 1, 1)));
	}

	private static boolean throwsIllegal(Runnable runnable) {
		try {
			runnable.run();
			return false;
		} catch (IllegalArgumentException e) {
			return true;
		}
	}

	private static void near(String name, double actual, double expected) {
		check(name, Math.abs(actual - expected) < 1e-9);
	}

	private static void check(String name, boolean condition) {
		checks++;
		if (!condition) {
			failures++;
			System.out.println("FAIL " + name);
		}
	}
}
