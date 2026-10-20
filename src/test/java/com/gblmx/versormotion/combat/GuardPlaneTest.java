package com.gblmx.versormotion.combat;

/** Standalone checks for the shield guard plane. */
public final class GuardPlaneTest {
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

		double[] front = ArcSweep.bake(0, 0, 0, 0, 3.0, ArcSweep.SAMPLES);
		check("plane ahead is crossed", GuardPlane.intersect(
				front, 0, 0, 2, 0, 0, 1, -2, 3.0) != null);
		GuardPlane.Hit hit = GuardPlane.intersect(front, 0, 0, 2, 0, 0, 1, -2, 3.0);
		check("hit lies on the plane", hit != null && Math.abs(hit.z() - 2.0) < 1e-9);
		check("hit lies in the swing plane", hit != null && Math.abs(hit.y()) < 1e-9);
		check("outside reach misses", GuardPlane.intersect(
				front, 0, 0, 2, 0, 0, 1, -2, 0.5) == null);
		check("plane beyond the arc misses", GuardPlane.intersect(
				front, 0, 0, 5, 0, 0, 1, -5, 6.0) == null);
		check("plane behind the arc misses", GuardPlane.intersect(
				front, 0, 0, 0.5, 0, 0, 1, -0.5, 6.0) == null);
		check("perpendicular plane is crossed", GuardPlane.intersect(
				front, 0, 0, 3, 1, 0, 0, 0, 3.0) != null);
		check("side plane at the arc end", GuardPlane.intersect(
				ArcSweep.bake(0, 0, 0, Math.PI / 2, 3.0, ArcSweep.SAMPLES),
				0, 0, 0, 0, 0, 1, 0, 3.0) != null);
		check("rejects degenerate arc", throwsIllegal(() ->
				GuardPlane.intersect(new double[3], 0, 0, 0, 0, 0, 1, 0, 1)));

		System.out.println(failures == 0
				? "OK: " + checks + " guard checks passed"
				: "FAILED: " + failures + "/" + checks + " guard checks");
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
