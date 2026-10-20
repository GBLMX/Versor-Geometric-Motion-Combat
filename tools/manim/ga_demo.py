"""Short demo of the versor_ga authoring helpers.

Render:
    uv run --frozen --no-config manimgl ga_demo.py VersorGADemo -l -w --video_dir videos
"""

import numpy as np
from manimlib import *

from versor_ga import CGA, E3, KDE, PGA
from versor_ga.animation import MotorAnimation, RotorAnimation


class VersorGADemo(ThreeDScene):
    def construct(self):
        self.camera.background_color = KDE.background
        self.frame.reorient(70, -50)
        self.frame.set_height(8.0)

        axes = ThreeDAxes(
            x_range=(-2, 2, 1),
            y_range=(-2, 2, 1),
            z_range=(-1, 2, 1),
            width=4,
            height=4,
            depth=3,
            axis_config=dict(stroke_color=KDE.axes, stroke_width=2),
        )
        self.add(axes)

        title = (
            Text("versor_ga", font_size=34, color=KDE.command)
            .fix_in_frame()
            .to_corner(UL, buff=0.35)
        )
        subtitle = Text(
            "E3 rotor  |  PGA motor  |  CGA sphere",
            font_size=20,
            color=KDE.comment,
        ).fix_in_frame().next_to(title, DOWN, buff=0.08)
        self.add(title, subtitle)

        # E3: rotate a vector with a 90-degree rotor.
        arrow = Line3D(ORIGIN, np.array([1.5, 0.0, 0.0]), width=0.05, color=KDE.vector)
        self.play(ShowCreation(arrow), run_time=0.8)
        self.play(
            RotorAnimation(arrow, E3.rotor((0, 0, 1), PI / 2)),
            run_time=2.0,
        )

        # PGA: a point, a line, and a motor moving the line.
        p = PGA.point(0, 0, 1)
        q = PGA.point(2, 2, 1)
        line = PGA.line(p, q)
        line_mob = line.to_mobject(color=KDE.line)
        plane = PGA.plane((0, 0, 1), -1)
        self.play(
            ShowCreation(plane.to_mobject(color=KDE.plane, extent=2.2)),
            ShowCreation(line_mob),
            ShowCreation(p.to_mobject(color=KDE.point)),
            run_time=1.0,
        )
        motor = PGA.motor(PGA.rotor((0, 0, 1), PI / 2), (1, 0, 0))
        self.play(MotorAnimation(line_mob, motor), run_time=2.0)

        # CGA: a sphere encoded as a conformal multivector.
        sphere = CGA.sphere(0, 0, 1, 1.2)
        self.play(ShowCreation(sphere.to_mobject(color=KDE.sphere)), run_time=1.2)
        self.wait(1.0)
