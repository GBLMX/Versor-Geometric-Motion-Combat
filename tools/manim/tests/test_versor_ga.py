from __future__ import annotations

import math
import unittest

import numpy as np

from versor_ga import CGA, E3, PGA, nlerp
from versor_ga.animation import MotorAnimation, RotorAnimation


class AlgebraTests(unittest.TestCase):
    def test_e3_basis_product(self):
        self.assertTrue(np.allclose((E3.e1 * E3.e2).coef, E3.SIG.blade(3).coef))
        self.assertTrue(np.allclose((E3.e2 * E3.e1).coef, -E3.SIG.blade(3).coef))
        self.assertAlmostEqual((E3.e1 * E3.e1).coef[0], 1.0)

    def test_e3_rotor_quarter_turn(self):
        rotor = E3.rotor((0, 0, 1), math.pi / 2)
        self.assertTrue(np.allclose(rotor.apply((1, 0, 0)), (0, 1, 0), atol=1e-12))

    def test_e3_slerp(self):
        a = E3.rotor((0, 0, 1), 0.0).mv
        b = E3.rotor((0, 0, 1), math.pi / 2).mv
        mid = nlerp(a, b, 0.5)
        self.assertAlmostEqual(mid.norm(), 1.0)

    def test_pga_point_roundtrip(self):
        p = PGA.point(1, -2, 3)
        self.assertTrue(np.allclose(PGA.point_coords(p.mv), (1, -2, 3)))

    def test_pga_motor(self):
        motor = PGA.motor(PGA.rotor((0, 0, 1), math.pi / 2), (1, 0, 0))
        self.assertTrue(np.allclose(motor.apply((1, 0, 0)), (1, 1, 0), atol=1e-12))

    def test_pga_plane_normalized(self):
        plane = PGA.plane((0, 0, 2), -2)
        self.assertTrue(np.allclose(plane.normal, (0, 0, 1)))
        self.assertAlmostEqual(plane.offset, -1.0)

    def test_cga_sphere_roundtrip(self):
        sphere = CGA.sphere(1, 2, 3, 4)
        center, radius = CGA.sphere_coords(sphere.mv)
        self.assertTrue(np.allclose(center, (1, 2, 3)))
        self.assertAlmostEqual(radius, 4.0)

    def test_cga_point_roundtrip(self):
        p = CGA.point(1, -2, 3)
        self.assertTrue(np.allclose(CGA.point_coords(p.mv), (1, -2, 3)))


class ManimAdapterTests(unittest.TestCase):
    def test_mobjects(self):
        from manimlib import Line3D, ParametricSurface, Sphere

        point = PGA.point(0, 0, 0).to_mobject()
        line = PGA.line((0, 0, 0), (1, 0, 0)).to_mobject()
        plane = PGA.plane((0, 0, 1), 0).to_mobject()
        sphere = CGA.sphere(0, 0, 0, 1).to_mobject()
        self.assertIsInstance(point, Sphere)
        self.assertIsInstance(line, Line3D)
        self.assertIsInstance(plane, ParametricSurface)
        self.assertIsInstance(sphere, Sphere)

    def test_rotor_animation(self):
        from manimlib import Line3D, ORIGIN

        line = Line3D(ORIGIN, np.array([1.0, 0.0, 0.0]))
        anim = RotorAnimation(line, E3.rotor((0, 0, 1), math.pi / 2))
        anim.begin()
        anim.interpolate(1.0)
        anim.finish()
        self.assertTrue(np.allclose(line.get_end()[:2], (0, 1), atol=1e-9))

    def test_motor_animation(self):
        from manimlib import Line3D, ORIGIN

        line = Line3D(ORIGIN, np.array([1.0, 0.0, 0.0]))
        motor = PGA.motor(PGA.rotor((0, 0, 1), math.pi / 2), (1, 0, 0))
        anim = MotorAnimation(line, motor)
        anim.begin()
        anim.interpolate(1.0)
        anim.finish()
        self.assertTrue(np.allclose(line.get_end()[:2], (1, 1), atol=1e-9))


if __name__ == "__main__":
    unittest.main()
