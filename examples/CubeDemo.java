import vsr.Multivector;
import vsr.P3;

/** Rigidly transforms a cube with a PGA motor and prints the vertices. */
public final class CubeDemo {
    public static void main(String[] args) {
        Multivector motor = P3.motor(
                P3.rotor(0, 1, 0, Math.PI / 3),   // 60 deg about y
                P3.translator(2, 0, 0.5));

        double[][] cube = {
                {-1, -1, -1}, {1, -1, -1}, {1, 1, -1}, {-1, 1, -1},
                {-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}
        };
        for (double[] v : cube) {
            Multivector p = P3.point(v[0], v[1], v[2]);
            double[] q = P3.pointCoords(P3.apply(motor, p));
            System.out.printf("(%5.2f %5.2f %5.2f) -> (%6.3f %6.3f %6.3f)%n",
                    v[0], v[1], v[2], q[0], q[1], q[2]);
        }
    }
}
