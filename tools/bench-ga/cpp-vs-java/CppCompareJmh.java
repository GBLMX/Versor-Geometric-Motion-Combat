package vsr;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
public class CppCompareJmh {
    static final int K = 16;
    static final int B = 1024;
    static final int[] E3_VEC = {1, 2, 4};
    static final int[] PGA_POINT = {7, 11, 13, 14};

    Multivector[] e3a, e3b;
    Multivector[] points, motors;
    MultivectorBatch e3Batch, pgaBatch;
    Multivector e3Rotor, pgaMotor;
    int idx;

    @Setup
    public void setup() {
        Random rnd = new Random(42);
        e3a = new Multivector[K];
        e3b = new Multivector[K];
        points = new Multivector[K];
        motors = new Multivector[K];
        for (int i = 0; i < K; i++) {
            e3a[i] = E3.vec(rnd.nextDouble() * 2 - 1, rnd.nextDouble() * 2 - 1, rnd.nextDouble() * 2 - 1);
            e3b[i] = E3.vec(rnd.nextDouble() * 2 - 1, rnd.nextDouble() * 2 - 1, rnd.nextDouble() * 2 - 1);
            points[i] = P3.point(rnd.nextDouble() * 2 - 1, rnd.nextDouble() * 2 - 1, rnd.nextDouble() * 2 - 1);
            motors[i] = P3.motor(P3.rotor(rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble() * 2),
                    P3.translator(rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble()));
        }
        Multivector[] e3Values = new Multivector[B];
        Multivector[] pointValues = new Multivector[B];
        for (int i = 0; i < B; i++) {
            e3Values[i] = E3.vec(rnd.nextDouble() * 2 - 1, rnd.nextDouble() * 2 - 1, rnd.nextDouble() * 2 - 1);
            pointValues[i] = P3.point(rnd.nextDouble() * 2 - 1, rnd.nextDouble() * 2 - 1, rnd.nextDouble() * 2 - 1);
        }
        e3Batch = MultivectorBatch.of(e3Values);
        pgaBatch = MultivectorBatch.of(pointValues);
        e3Rotor = E3.rotor(rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble() * 2);
        pgaMotor = P3.motor(P3.rotor(rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble() * 2),
                P3.translator(rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble()));
    }

    @Benchmark
    public void e3Gp(Blackhole bh) {
        int i = idx++ & 15;
        Multivector c = e3a[i].gp(e3b[(i * 7) & 15]);
        for (int m = 0; m < c.sig.size; m++) bh.consume(c.get(m));
    }

    @Benchmark
    public void pgaSpin(Blackhole bh) {
        int i = idx++ & 15;
        Multivector q = motors[(i * 3) & 15].sandwich(points[i]);
        for (int m : PGA_POINT) bh.consume(q.get(m));
    }

    @Benchmark
    @OperationsPerInvocation(B)
    public void e3BatchSandwich(Blackhole bh) {
        MultivectorBatch out = e3Batch.sandwich(e3Rotor);
        for (int j = 0; j < B; j++) for (int m : E3_VEC) bh.consume(out.get(m, j));
    }

    @Benchmark
    @OperationsPerInvocation(B)
    public void pgaBatchSandwich(Blackhole bh) {
        MultivectorBatch out = pgaBatch.sandwich(pgaMotor);
        for (int j = 0; j < B; j++) for (int m : PGA_POINT) bh.consume(out.get(m, j));
    }
}
