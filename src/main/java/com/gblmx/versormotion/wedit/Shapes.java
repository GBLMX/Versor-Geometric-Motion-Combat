package com.gblmx.versormotion.wedit;

/**
 * Integer shape tests for the WorldEdit-style brushes. All offsets are block
 * offsets from the shape center; the cylinder spans {@code height} blocks
 * centered on {@code dy = 0}.
 */
public final class Shapes {
	private Shapes() {}

	public static boolean sphere(int dx, int dy, int dz, int radius) {
		int r2 = radius * radius;
		return dx * dx + dy * dy + dz * dz <= r2;
	}

	/** One-block-thick shell; for {@code radius = 1} this is the 6 face neighbors. */
	public static boolean hollowSphere(int dx, int dy, int dz, int radius) {
		int outer = radius * radius;
		int inner = (radius - 1) * (radius - 1);
		int distance = dx * dx + dy * dy + dz * dz;
		return distance <= outer && distance > inner;
	}

	public static boolean cylinder(int dx, int dy, int dz, int radius, int height) {
		int low = -(height / 2);
		int high = low + height - 1;
		return dx * dx + dz * dz <= radius * radius && dy >= low && dy <= high;
	}

	public static boolean hollowCylinder(int dx, int dy, int dz, int radius, int height) {
		int low = -(height / 2);
		int high = low + height - 1;
		int horizontal = dx * dx + dz * dz;
		return horizontal <= radius * radius && horizontal > (radius - 1) * (radius - 1)
				&& dy >= low && dy <= high;
	}
}
