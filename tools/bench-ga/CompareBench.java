package vsr;

import java.util.Locale;
import java.util.Random;

/**
 * Java side of the cross-library GA benchmark.
 *
 * <p>Compiled in package {@code vsr} so it can build dense multivectors with the
 * package-private array constructor. It measures the same three workloads as
 * {@code bench.py}: dense geometric product, one rotor sandwich, and a batch of
 * 1000 rotor sandwiches.</p>
 */
public final class CompareBench {
    private static final int WARMUP = 3;
    private static final int ROUNDS = 5;
    private static final long TARGET_NANOS = 20_000_000L;
    private static final long MAX_ITERS = 1L << 24;
    private static volatile double sink;

    private CompareBench() {}

    public static void main(String[] args) {
        System.out.println("lang,library,operation,ns_per_elem,note");
        Signature sig = E3.SIG;
        Random rnd = new Random(42);
        Multivector a = dense(sig, rnd);
        Multivector b = dense(sig, rnd);
        Multivector rotor = E3.rotor(0, 0, 1, 0.7);
        Multivector vector = E3.vec(1, 2, 3);

        measure("versor-java", "gp", "Cl(3,0) dense", 1,
                () -> a.gp(b).get(0));
        measure("versor-java", "sandwich", "Cl(3,0) rotor on vector", 1,
                () -> rotor.sandwich(vector).get(0));

        int n = 1000;
        Multivector[] points = new Multivector[n];
        for (int i = 0; i < n; i++) {
            points[i] = dense(sig, rnd);
        }
        MultivectorBatch batch = MultivectorBatch.of(points);
        measure("versor-java", "batch_sandwich", "1000 dense points, per point", n,
                () -> batch.sandwich(rotor).get(0, n - 1));

        Signature cga = Signature.cl(4, 1);
        Multivector ca = dense(cga, rnd);
        Multivector cb = dense(cga, rnd);
        Multivector cgaRotor = Multivector.scalar(cga, Math.cos(0.35))
                .sub(Multivector.blade(cga, 0b11, Math.sin(0.35)));
        measure("versor-java", "gp_cga", "Cl(4,1) dense", 1,
                () -> ca.gp(cb).get(0));
        measure("versor-java", "sandwich_cga", "Cl(4,1) rotor on dense", 1,
                () -> cgaRotor.sandwich(cb).get(0));
    }

    private static void measure(String library, String operation, String note,
                                int elements, Op op) {
        long iters = 1;
        double dummy = 0;
        while (true) {
            long t0 = System.nanoTime();
            for (long i = 0; i < iters; i++) dummy += op.apply();
            long dt = System.nanoTime() - t0;
            if (dt >= TARGET_NANOS || iters >= MAX_ITERS) break;
            iters <<= 1;
        }
        sink += dummy;
        for (int w = 0; w < WARMUP; w++) run(op, iters);

        double best = Double.MAX_VALUE;
        for (int r = 0; r < ROUNDS; r++) {
            double ns = run(op, iters) / (double) iters / elements;
            if (ns < best) best = ns;
        }
        System.out.printf(Locale.ROOT, "java,%s,%s,%.3f,%s (iters=%d)%n",
                library, operation, best, note, iters);
    }

    private static double run(Op op, long iters) {
        double sum = 0;
        long t0 = System.nanoTime();
        for (long i = 0; i < iters; i++) sum += op.apply();
        long dt = System.nanoTime() - t0;
        sink += sum;
        return dt;
    }

    private static Multivector dense(Signature sig, Random rnd) {
        double[] c = new double[sig.size];
        for (int i = 0; i < c.length; i++) c[i] = rnd.nextDouble() * 2 - 1;
        return new Multivector(sig, c);
    }

    @FunctionalInterface
    private interface Op {
        double apply();
    }
}
