import vsr.Motion;

/** Prints position, velocity, acceleration and jerk of a point under a screw motion. */
public final class KinematicsDemo {
    public static void main(String[] args) {
        // spin about z (omega = 2) while translating along z (v = 1)
        Motion motion = Motion.screw(0, 0, 1, 2.0, 0, 0, 1);

        System.out.println("  t     position                 velocity              acceleration          jerk");
        for (int i = 0; i <= 4; i++) {
            double t = i * 0.25;
            double[] p = motion.position(t, 1, 0, 0);
            double[] v = motion.velocity(t, 1, 0, 0);
            double[] a = motion.acceleration(t, 1, 0, 0);
            double[] j = motion.jerk(t, 1, 0, 0);
            System.out.printf("%5.2f  (%6.3f %6.3f %6.3f)  (%6.3f %6.3f %6.3f)  (%6.3f %6.3f %6.3f)  (%6.3f %6.3f %6.3f)%n",
                    t, p[0], p[1], p[2], v[0], v[1], v[2], a[0], a[1], a[2], j[0], j[1], j[2]);
        }
    }
}
