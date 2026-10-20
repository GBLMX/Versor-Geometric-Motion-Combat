"""Manim animations driven by rotors and motors."""

from __future__ import annotations

import numpy as np
from manimlib import Animation, ORIGIN


class GATransform(Animation):
    """Animate a mobject through a time-varying point map.

    ``point_map_at(t)`` must return a callable ``point -> point``. The original
    mobject is copied once, so each frame transforms the same base geometry
    instead of accumulating drift.
    """

    def __init__(self, mobject, point_map_at, **kwargs):
        self.base = mobject.copy()
        self.point_map_at = point_map_at
        super().__init__(mobject, **kwargs)

    def interpolate(self, alpha: float) -> None:
        fn = self.point_map_at(alpha)
        transformed = self.base.copy().apply_function(
            lambda p: np.asarray(fn(p), dtype=float)
        )
        self.mobject.become(transformed)


class RotorAnimation(GATransform):
    """Rotate a mobject with an E3/PGA rotor, about ``about_point``."""

    def __init__(self, mobject, rotor, about_point=ORIGIN, **kwargs):
        self.rotor = rotor
        self.about = np.asarray(about_point, dtype=float)

        def map_at(t: float):
            r = rotor.at(t)
            return lambda p: r.apply(np.asarray(p, dtype=float) - self.about) + self.about

        super().__init__(mobject, map_at, **kwargs)


class MotorAnimation(GATransform):
    """Apply a PGA motor ``T * R`` to a mobject over time."""

    def __init__(self, mobject, motor, **kwargs):
        self.motor = motor
        super().__init__(mobject, lambda t: motor.at(t).apply, **kwargs)
