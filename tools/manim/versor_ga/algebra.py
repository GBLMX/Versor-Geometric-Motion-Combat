"""A small dense Clifford algebra kernel for Manim scenes.

This is intentionally not a general numerical GA library: it targets the
3D algebras used by the mod (E3, PGA3, CGA3) where ``2**n <= 32``. The hot path
is simple NumPy indexing, which keeps scene code readable and fast enough for
animation frames.
"""

from __future__ import annotations

from dataclasses import dataclass
from functools import cached_property

import numpy as np


@dataclass(frozen=True)
class Signature:
    """Metric for ``n`` generators; basis blades are integer bitmasks."""

    metric: tuple[int, ...]

    def __post_init__(self) -> None:
        if not self.metric:
            raise ValueError("metric must not be empty")
        if any(m not in (-1, 0, 1) for m in self.metric):
            raise ValueError("metric entries must be -1, 0 or 1")

    @property
    def n(self) -> int:
        return len(self.metric)

    @property
    def size(self) -> int:
        return 1 << self.n

    @cached_property
    def gp_sign(self) -> np.ndarray:
        """Sign of ``blade(a) * blade(b)`` for every pair of masks."""
        out = np.zeros((self.size, self.size), dtype=np.int8)
        for a in range(self.size):
            for b in range(self.size):
                swaps = 0
                for i in range(self.n):
                    if (a >> i) & 1:
                        swaps += (b & ((1 << i) - 1)).bit_count()
                sign = -1 if swaps & 1 else 1
                common = a & b
                for i in range(self.n):
                    if (common >> i) & 1:
                        sign *= self.metric[i]
                out[a, b] = sign
        return out

    @cached_property
    def op_sign(self) -> np.ndarray:
        """Sign of ``blade(a) ^ blade(b)``; zero when the blades overlap."""
        out = np.zeros((self.size, self.size), dtype=np.int8)
        for a in range(self.size):
            for b in range(self.size):
                if a & b:
                    continue
                swaps = 0
                for i in range(self.n):
                    if (a >> i) & 1:
                        swaps += (b & ((1 << i) - 1)).bit_count()
                out[a, b] = -1 if swaps & 1 else 1
        return out

    @cached_property
    def grades(self) -> np.ndarray:
        return np.array([i.bit_count() for i in range(self.size)], dtype=np.int8)

    def scalar(self, value: float = 1.0) -> "Multivector":
        c = np.zeros(self.size)
        c[0] = value
        return Multivector(self, c)

    def blade(self, mask: int, value: float = 1.0) -> "Multivector":
        c = np.zeros(self.size)
        c[mask] = value
        return Multivector(self, c)

    def vector(self, *coords: float) -> "Multivector":
        if len(coords) != self.n:
            raise ValueError(f"expected {self.n} coordinates, got {len(coords)}")
        c = np.zeros(self.size)
        for i, value in enumerate(coords):
            c[1 << i] = value
        return Multivector(self, c)

    def multivector(self, values) -> "Multivector":
        c = np.asarray(values, dtype=float)
        if c.shape != (self.size,):
            raise ValueError(f"expected {self.size} coefficients, got {c.shape}")
        return Multivector(self, c)


class Multivector:
    """Immutable dense multivector over a :class:`Signature`."""

    __slots__ = ("sig", "coef")

    def __init__(self, sig: Signature, coef) -> None:
        c = np.asarray(coef, dtype=float)
        if c.shape != (sig.size,):
            raise ValueError(f"expected {sig.size} coefficients, got {c.shape}")
        self.sig = sig
        self.coef = c

    def __getitem__(self, mask: int) -> float:
        return float(self.coef[mask])

    def __add__(self, other) -> "Multivector":
        if isinstance(other, Multivector):
            self._check(other)
            return Multivector(self.sig, self.coef + other.coef)
        return Multivector(self.sig, self.coef + float(other))

    def __radd__(self, other) -> "Multivector":
        return self.__add__(other)

    def add(self, other) -> "Multivector":
        return self.__add__(other)

    def __sub__(self, other) -> "Multivector":
        if isinstance(other, Multivector):
            self._check(other)
            return Multivector(self.sig, self.coef - other.coef)
        return Multivector(self.sig, self.coef - float(other))

    def sub(self, other) -> "Multivector":
        return self.__sub__(other)

    def scale(self, value: float) -> "Multivector":
        return self * value

    def __rsub__(self, other) -> "Multivector":
        return Multivector(self.sig, float(other) - self.coef)

    def __neg__(self) -> "Multivector":
        return Multivector(self.sig, -self.coef)

    def __mul__(self, other) -> "Multivector":
        if isinstance(other, Multivector):
            return self.gp(other)
        return Multivector(self.sig, self.coef * float(other))

    def __rmul__(self, other) -> "Multivector":
        return self.__mul__(other)

    def __invert__(self) -> "Multivector":
        return self.reverse()

    def __repr__(self) -> str:
        terms = []
        for mask, value in enumerate(self.coef):
            if abs(value) > 1e-14:
                terms.append(f"{value:g}*{mask}")
        return "Multivector(" + " + ".join(terms) + ")" if terms else "Multivector(0)"

    def _check(self, other: "Multivector") -> None:
        if self.sig != other.sig:
            raise ValueError("multivectors belong to different algebras")

    def gp(self, other: "Multivector") -> "Multivector":
        self._check(other)
        out = np.zeros(self.sig.size)
        left = np.flatnonzero(self.coef)
        right = np.flatnonzero(other.coef)
        signs = self.sig.gp_sign
        for i in left:
            ai = self.coef[i]
            for j in right:
                out[i ^ j] += ai * other.coef[j] * signs[i, j]
        return Multivector(self.sig, out)

    def op(self, other: "Multivector") -> "Multivector":
        self._check(other)
        out = np.zeros(self.sig.size)
        left = np.flatnonzero(self.coef)
        right = np.flatnonzero(other.coef)
        signs = self.sig.op_sign
        for i in left:
            ai = self.coef[i]
            for j in right:
                out[i | j] += ai * other.coef[j] * signs[i, j]
        return Multivector(self.sig, out)

    def ip(self, other: "Multivector") -> "Multivector":
        """Left contraction ``A ⌋ B``."""
        return self._grade_of_product(other, lambda ga, gb: gb - ga)

    def rc(self, other: "Multivector") -> "Multivector":
        """Right contraction ``A ⌊ B``."""
        return self._grade_of_product(other, lambda ga, gb: ga - gb)

    def _grade_of_product(self, other: "Multivector", target) -> "Multivector":
        self._check(other)
        out = np.zeros(self.sig.size)
        left = np.flatnonzero(self.coef)
        right = np.flatnonzero(other.coef)
        signs = self.sig.gp_sign
        grades = self.sig.grades
        for i in left:
            for j in right:
                grade = target(grades[i], grades[j])
                if 0 <= grade <= self.sig.n:
                    out[i ^ j] += self.coef[i] * other.coef[j] * signs[i, j]
        return Multivector(self.sig, out)

    def grade(self, k: int) -> "Multivector":
        out = np.zeros(self.sig.size)
        mask = self.sig.grades == k
        out[mask] = self.coef[mask]
        return Multivector(self.sig, out)

    def reverse(self) -> "Multivector":
        signs = np.array(
            [(-1) ** (g * (g - 1) // 2) for g in self.sig.grades],
            dtype=float,
        )
        return Multivector(self.sig, self.coef * signs)

    def sandwich(self, x: "Multivector") -> "Multivector":
        return self.gp(x).gp(self.reverse())

    def norm2(self) -> float:
        return float((self * self.reverse()).coef[0])

    def norm(self) -> float:
        return float(np.sqrt(abs(self.norm2())))

    def unit(self) -> "Multivector":
        n = self.norm()
        if n < 1e-15:
            raise ValueError("cannot normalize a null multivector")
        return self * (1.0 / n)

    def is_zero(self, eps: float = 1e-12) -> bool:
        return bool(np.max(np.abs(self.coef)) < eps)


def nlerp(a: Multivector, b: Multivector, t: float) -> Multivector:
    """Normalized linear interpolation for rotors/motors on the same algebra."""
    a._check(b)
    return Multivector(a.sig, (1.0 - t) * a.coef + t * b.coef).unit()
