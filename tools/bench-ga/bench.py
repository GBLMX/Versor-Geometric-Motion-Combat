"""Python side of the cross-library GA benchmark.

Workloads match ``CompareBench.java`` exactly:
  * dense geometric product in Cl(3,0);
  * one rotor sandwich on a vector;
  * 1000 rotor sandwiches, reported per element.

Run through ``run.sh`` so the Java and Python sides use the same workload.
"""

from __future__ import annotations

import time

import numpy as np
import clifford
import kingdon
from clifford import Cl, MVArray

WARMUP = 3
ROUNDS = 5
TARGET_NS = 20_000_000
BATCH = 1000
RNG = np.random.default_rng(42)


def timed(fn, elements: int = 1) -> float:
    iters = 1
    while True:
        t0 = time.perf_counter_ns()
        for _ in range(iters):
            fn()
        dt = time.perf_counter_ns() - t0
        if dt >= TARGET_NS or iters >= (1 << 24):
            break
        iters <<= 1

    for _ in range(WARMUP):
        for _ in range(iters):
            fn()

    best = float("inf")
    for _ in range(ROUNDS):
        t0 = time.perf_counter_ns()
        for _ in range(iters):
            fn()
        dt = time.perf_counter_ns() - t0
        best = min(best, dt / iters / elements)
    return best


def row(library: str, operation: str, ns: float, note: str) -> None:
    print(f"python,{library},{operation},{ns:.3f},{note}")


def clifford_bench() -> None:
    layout, blades = Cl(3, 0)
    a = layout.MultiVector(RNG.normal(size=8))
    b = layout.MultiVector(RNG.normal(size=8))
    theta = 0.7
    rotor = layout.MultiVector(
        np.array([np.cos(theta / 2), 0, 0, 0, -np.sin(theta / 2), 0, 0, 0])
    )
    vector = blades["e1"]

    row("clifford", "gp", timed(lambda: a * b), "Cl(3,0) dense")
    row("clifford", "sandwich", timed(lambda: rotor * vector * ~rotor),
        "Cl(3,0) rotor on vector")

    points = MVArray([layout.MultiVector(RNG.normal(size=8)) for _ in range(BATCH)])
    row("clifford", "batch_sandwich",
        timed(lambda: rotor * points * ~rotor, BATCH),
        f"{BATCH} dense points, per point")

    layout5, blades5 = Cl(4, 1)
    a5 = layout5.MultiVector(RNG.normal(size=32))
    b5 = layout5.MultiVector(RNG.normal(size=32))
    rotor5 = np.cos(0.35) - np.sin(0.35) * blades5["e12"]
    row("clifford", "gp_cga", timed(lambda: a5 * b5), "Cl(4,1) dense")
    row("clifford", "sandwich_cga", timed(lambda: rotor5 * b5 * ~rotor5),
        "Cl(4,1) rotor on dense")


def kingdon_bench() -> None:
    alg = kingdon.Algebra(3, 0, 0)
    keys = tuple(alg.canon2bin.values())
    e12 = alg.blades["e12"].keys()[0]

    def mv(values_by_mask):
        return alg.multivector(
            values=[values_by_mask.get(key, 0.0) for key in keys],
            keys=keys,
        )

    a = mv(dict(zip(range(8), RNG.normal(size=8))))
    b = mv(dict(zip(range(8), RNG.normal(size=8))))
    theta = 0.7
    rotor = mv({0: np.cos(theta / 2), e12: -np.sin(theta / 2)})
    vector = mv({1: 1.0, 2: 2.0, 4: 3.0})

    row("kingdon", "gp", timed(lambda: a * b), "Cl(3,0) dense")
    row("kingdon", "sandwich", timed(lambda: rotor * vector * ~rotor),
        "Cl(3,0) rotor on vector")

    batch_values = {key: RNG.normal(size=BATCH) for key in keys}
    points = alg.multivector(
        values=[batch_values[key] for key in keys],
        keys=keys,
    )
    row("kingdon", "batch_sandwich",
        timed(lambda: rotor * points * ~rotor, BATCH),
        f"{BATCH} dense points, per point")

    alg5 = kingdon.Algebra(4, 1, 0)
    keys5 = tuple(alg5.canon2bin.values())
    e12_5 = alg5.blades["e12"].keys()[0]

    def mv5(values_by_mask):
        return alg5.multivector(
            values=[values_by_mask.get(key, 0.0) for key in keys5],
            keys=keys5,
        )

    a5 = mv5(dict(zip(keys5, RNG.normal(size=32))))
    b5 = mv5(dict(zip(keys5, RNG.normal(size=32))))
    rotor5 = mv5({0: np.cos(0.35), e12_5: -np.sin(0.35)})
    row("kingdon", "gp_cga", timed(lambda: a5 * b5), "Cl(4,1) dense")
    row("kingdon", "sandwich_cga", timed(lambda: rotor5 * b5 * ~rotor5),
        "Cl(4,1) rotor on dense")


def main() -> None:
    print("lang,library,operation,ns_per_elem,note")
    print(f"# clifford {clifford.__version__}, kingdon {kingdon.__version__}, "
          f"numpy {np.__version__}")
    clifford_bench()
    kingdon_bench()


if __name__ == "__main__":
    main()
