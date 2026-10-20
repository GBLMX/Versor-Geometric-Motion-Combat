# GA cross-library benchmark

Compares `versor-java` with two numeric PyPI libraries on the same Cl(3,0)
workloads:

- dense geometric product;
- one rotor sandwich on a vector;
- 1000 rotor sandwiches, reported **per element**.

## Run

```bash
cd tools/bench-ga
./run.sh
```

The script builds `lib/versor-java`, compiles `CompareBench.java`, creates the
pinned Python environment, and writes `results.csv`.

## Method

- Java: JMH-style manual harness, 3 warmups, best of 5 rounds, calibrated
  iteration count, `System.nanoTime()`, default JVM.
- Python: same calibration/warmup/best-of-5 with `time.perf_counter_ns()`.
- Rotor: `cos(0.35) - sin(0.35) e12`; vector: `e1 + 2 e2 + 3 e3`.
- Batch: 1000 dense random multivectors, one vectorized call per iteration for
  `clifford.MVArray` and `kingdon` broadcasting; `MultivectorBatch.sandwich` for
  `versor-java`.
- Results are **ns per element**; lower is better.

## Caveats

- This is a microbenchmark, not an application benchmark. Python per-call
  overhead is part of the single-operation numbers; batch numbers are the fairer
  throughput comparison.
- `galgebra` is symbolic (SymPy) and is intentionally excluded from numeric
  timings.
- `pygafro`/`gafropy` are CGA/robotics-specific; their APIs do not expose the
  same dense Cl(3,0) primitives, so they are excluded.
- Do not compare absolute numbers across machines. Compare the same run's
  relative ordering, and rerun with your JVM/Python versions.
- `clifford`/`kingdon` may use NumPy, Numba, or code generation internally;
  `versor-java` uses JVM JIT and its own dense/sparse/batch kernels.
