"""E3, PGA3 and CGA3 presets for scene authoring.

The presets keep the geometric data a scene needs (axis/angle, translation,
line endpoints, plane normal/offset, sphere centre/radius) next to the
multivector, so animation helpers do not have to reverse-engineer it.
"""

from __future__ import annotations

from dataclasses import dataclass
from math import cos, sin, tau

import numpy as np

from .algebra import Multivector, Signature
from .palette import KDE

E3_SIG = Signature((1, 1, 1))
PGA_SIG = Signature((0, 1, 1, 1))
CGA_SIG = Signature((1, 1, 1, 1, -1))


def _unit(v) -> np.ndarray:
    a = np.asarray(v, dtype=float)
    n = np.linalg.norm(a)
    if n < 1e-15:
        raise ValueError("zero vector")
    return a / n


def _basis(normal: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    n = _unit(normal)
    helper = np.array([0.0, 0.0, 1.0])
    if abs(float(np.dot(n, helper))) > 0.9:
        helper = np.array([1.0, 0.0, 0.0])
    u = _unit(np.cross(n, helper))
    v = np.cross(n, u)
    return u, v


def _coords3(mv: Multivector) -> np.ndarray:
    return np.array([mv[1], mv[2], mv[4]], dtype=float)


@dataclass(frozen=True)
class E3Rotor:
    mv: Multivector
    axis: np.ndarray
    angle: float

    def at(self, t: float) -> "E3Rotor":
        return E3.rotor(self.axis, self.angle * t)

    def apply(self, point) -> np.ndarray:
        p = np.asarray(point, dtype=float)
        image = self.mv.sandwich(E3.vector(*p))
        return _coords3(image)


@dataclass(frozen=True)
class PGARotor:
    mv: Multivector
    axis: np.ndarray
    angle: float

    def at(self, t: float) -> "PGARotor":
        return PGA.rotor(self.axis, self.angle * t)

    def apply(self, point) -> np.ndarray:
        p = np.asarray(point, dtype=float)
        return PGA.point_coords(self.mv.sandwich(PGA.point(*p).mv))


@dataclass(frozen=True)
class Motor:
    mv: Multivector
    rotor: PGARotor
    translation: np.ndarray

    def at(self, t: float) -> "Motor":
        r = self.rotor.at(t)
        return PGA.motor(r, self.translation * t)

    def apply(self, point) -> np.ndarray:
        p = np.asarray(point, dtype=float)
        return PGA.point_coords(self.mv.sandwich(PGA.point(*p).mv))


@dataclass(frozen=True)
class Point:
    coords: np.ndarray
    mv: Multivector

    def to_mobject(self, radius: float = 0.05, color: str = KDE.point, **kwargs):
        from manimlib import Sphere

        return Sphere(radius=radius, color=color, **kwargs).move_to(self.coords)


@dataclass(frozen=True)
class Line:
    start: np.ndarray
    end: np.ndarray

    def to_mobject(self, color: str = KDE.line, width: float = 0.04, extend: float = 0.0, **kwargs):
        from manimlib import Line3D

        d = self.end - self.start
        n = np.linalg.norm(d)
        if n < 1e-15:
            raise ValueError("line endpoints coincide")
        offset = _unit(d) * extend
        return Line3D(self.start - offset, self.end + offset, width=width, color=color, **kwargs)

    def moved(self, fn) -> "Line":
        return Line(np.asarray(fn(self.start)), np.asarray(fn(self.end)))


@dataclass(frozen=True)
class Plane:
    normal: np.ndarray
    offset: float
    mv: Multivector

    def to_mobject(
        self,
        color: str = KDE.plane,
        opacity: float = 0.22,
        extent: float = 2.0,
        resolution: int = 2,
        **kwargs,
    ):
        from manimlib import ParametricSurface

        n = _unit(self.normal)
        u, v = _basis(n)
        origin = -self.offset * n

        def surface(s: float, t: float):
            return origin + s * u + t * v

        return ParametricSurface(
            surface,
            u_range=(-extent, extent),
            v_range=(-extent, extent),
            resolution=(resolution, resolution),
            color=color,
            opacity=opacity,
            **kwargs,
        )


@dataclass(frozen=True)
class CGASphere:
    center: np.ndarray
    radius: float
    mv: Multivector

    def to_mobject(
        self,
        color: str = KDE.sphere,
        opacity: float = 0.28,
        resolution: tuple[int, int] = (32, 16),
        **kwargs,
    ):
        from manimlib import Sphere

        return Sphere(
            radius=self.radius,
            color=color,
            opacity=opacity,
            resolution=resolution,
            **kwargs,
        ).move_to(self.center)


class E3:
    SIG = E3_SIG
    e1 = SIG.blade(1)
    e2 = SIG.blade(2)
    e3 = SIG.blade(4)

    @staticmethod
    def vector(x: float, y: float, z: float) -> Multivector:
        return E3.SIG.vector(x, y, z)

    @staticmethod
    def rotor(axis, angle: float) -> E3Rotor:
        a = _unit(axis)
        b = E3.SIG.blade(6, a[0]).add(
            E3.SIG.blade(5, -a[1])
        ).add(E3.SIG.blade(3, a[2]))
        mv = E3.SIG.scalar(cos(angle / 2)).sub(b * sin(angle / 2))
        return E3Rotor(mv, a, angle)

    @staticmethod
    def rotate(point, rotor) -> np.ndarray:
        return rotor.apply(point)


class PGA:
    SIG = PGA_SIG
    POINT_E123 = 14
    POINT_E012 = 7
    POINT_E013 = 11
    POINT_E023 = 13
    E01 = 3
    E02 = 5
    E03 = 9
    E23 = 12
    E13 = 10
    E12 = 6

    @staticmethod
    def point(x: float, y: float, z: float) -> Point:
        mv = (
            PGA.SIG.blade(PGA.POINT_E123, 1.0)
            .add(PGA.SIG.blade(PGA.POINT_E012, -z))
            .add(PGA.SIG.blade(PGA.POINT_E013, y))
            .add(PGA.SIG.blade(PGA.POINT_E023, -x))
        )
        return Point(np.array([x, y, z], dtype=float), mv)

    @staticmethod
    def point_coords(mv: Multivector) -> np.ndarray:
        return np.array([-mv[PGA.POINT_E023], mv[PGA.POINT_E013], -mv[PGA.POINT_E012]])

    @staticmethod
    def line(p1: Point | np.ndarray, p2: Point | np.ndarray) -> Line:
        a = np.asarray(p1.coords if isinstance(p1, Point) else p1, dtype=float)
        b = np.asarray(p2.coords if isinstance(p2, Point) else p2, dtype=float)
        return Line(a, b)

    @staticmethod
    def plane(normal, offset: float) -> Plane:
        n = np.asarray(normal, dtype=float)
        length = float(np.linalg.norm(n))
        if length < 1e-15:
            raise ValueError("zero plane normal")
        unit = n / length
        d = float(offset) / length
        mv = PGA.SIG.vector(d, unit[0], unit[1], unit[2])
        return Plane(unit, d, mv)

    @staticmethod
    def translator(tx: float, ty: float, tz: float) -> Multivector:
        return PGA.SIG.scalar(1.0).sub(
            PGA.SIG.blade(PGA.E01, 0.5 * tx)
            .add(PGA.SIG.blade(PGA.E02, 0.5 * ty))
            .add(PGA.SIG.blade(PGA.E03, 0.5 * tz))
        )

    @staticmethod
    def rotor(axis, angle: float) -> PGARotor:
        a = _unit(axis)
        b = (
            PGA.SIG.blade(PGA.E23, a[0])
            .sub(PGA.SIG.blade(PGA.E13, a[1]))
            .add(PGA.SIG.blade(PGA.E12, a[2]))
        )
        mv = PGA.SIG.scalar(cos(angle / 2)).sub(b * sin(angle / 2))
        return PGARotor(mv, a, angle)

    @staticmethod
    def motor(rotor: PGARotor, translation) -> Motor:
        t = np.asarray(translation, dtype=float)
        mv = PGA.translator(*t).gp(rotor.mv)
        return Motor(mv, rotor, t)


class CGA:
    SIG = CGA_SIG
    E1, E2, E3, E4, E5 = 1, 2, 4, 8, 16
    NINF = SIG.blade(E4, 1.0).add(SIG.blade(E5, 1.0))
    N0 = SIG.blade(E5, 0.5).sub(SIG.blade(E4, 0.5))

    @staticmethod
    def point(x: float, y: float, z: float) -> Point:
        half = 0.5 * (x * x + y * y + z * z)
        mv = (
            CGA.SIG.vector(x, y, z, 0.0, 0.0)
            .add(CGA.NINF * half)
            .add(CGA.N0)
        )
        return Point(np.array([x, y, z], dtype=float), mv)

    @staticmethod
    def point_coords(mv: Multivector) -> np.ndarray:
        w = mv[CGA.E5] - mv[CGA.E4]
        if abs(w) < 1e-12:
            raise ValueError("point at infinity")
        return np.array([mv[CGA.E1], mv[CGA.E2], mv[CGA.E3]]) / w

    @staticmethod
    def sphere(cx: float, cy: float, cz: float, radius: float) -> CGASphere:
        p = CGA.point(cx, cy, cz)
        mv = p.mv.sub(CGA.NINF * (0.5 * radius * radius))
        return CGASphere(np.array([cx, cy, cz], dtype=float), float(radius), mv)

    @staticmethod
    def sphere_coords(mv: Multivector) -> tuple[np.ndarray, float]:
        w = mv[CGA.E5] - mv[CGA.E4]
        if abs(w) < 1e-12:
            raise ValueError("sphere at infinity")
        c = np.array([mv[CGA.E1], mv[CGA.E2], mv[CGA.E3]]) / w
        r2 = float(c @ c - (mv[CGA.E4] + mv[CGA.E5]) / w)
        return c, float(np.sqrt(max(0.0, r2)))

    @staticmethod
    def plane(nx: float, ny: float, nz: float, offset: float) -> Plane:
        n = np.array([nx, ny, nz], dtype=float)
        length = float(np.linalg.norm(n))
        if length < 1e-15:
            raise ValueError("zero plane normal")
        unit = n / length
        d = float(offset) / length
        mv = CGA.SIG.vector(unit[0], unit[1], unit[2], 0.0, 0.0).add(CGA.NINF * (-d))
        return Plane(unit, d, mv)
