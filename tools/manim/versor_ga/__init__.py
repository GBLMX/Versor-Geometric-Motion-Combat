"""Small Clifford algebra toolkit for ManimGL scenes.

Example::

    from versor_ga import E3, PGA, CGA
    from versor_ga.animation import RotorAnimation, MotorAnimation

    rotor = E3.rotor((0, 0, 1), PI / 2)
    self.play(RotorAnimation(mobject, rotor))

The package is intentionally narrow: E3, PGA3 and CGA3, dense coefficients,
and helpers for Manim mobjects. It is not a replacement for `clifford` or
`kingdon` for general-purpose numerical work.
"""

from .algebra import Multivector, Signature, nlerp
from .palette import KDE, Palette
from .presets import (
    CGA,
    E3,
    PGA,
    CGASphere,
    E3Rotor,
    Line,
    Motor,
    PGARotor,
    Plane,
    Point,
)

__all__ = [
    "CGA",
    "CGASphere",
    "E3",
    "E3Rotor",
    "KDE",
    "Line",
    "Motor",
    "Multivector",
    "PGA",
    "PGARotor",
    "Palette",
    "Plane",
    "Point",
    "Signature",
    "nlerp",
]
