"""Render a rotor path computed by versor-java.

Run ``./test.sh`` first; it regenerates ``data/versor-rotor.json`` with
``bridge/VersorManimDump.java``. This scene only interpolates that file and
draws it with ManimGL.
"""

import json
import os

import numpy as np

from manimlib import *

from versor_ga import KDE

HERE = os.path.dirname(os.path.abspath(__file__))
with open(os.path.join(HERE, "data", "versor-rotor.json")) as fh:
    DATA = json.load(fh)

T = np.array([sample["t"] for sample in DATA["samples"]])
ANGLE = np.array([sample["angle"] for sample in DATA["samples"]])
X = np.array([sample["x"] for sample in DATA["samples"]])
Y = np.array([sample["y"] for sample in DATA["samples"]])


class VersorRotor(Scene):
    """e1 under a 90-degree E3 rotor, sampled by versor-java."""

    def construct(self):
        self.camera.background_color = KDE.background
        title = Text(
            "versor-java  E3.rotor",
            font_size=42,
            color=KDE.command,
        ).to_edge(UP, buff=0.35)
        subtitle = Text(
            "61 samples | R = cos(theta/2) - sin(theta/2) e12",
            font_size=24,
            color=KDE.comment,
        ).next_to(title, DOWN, buff=0.12)
        self.play(FadeIn(title), FadeIn(subtitle))

        axes = Axes(
            x_range=(0, 1.25, 0.25),
            y_range=(0, 1.25, 0.25),
            width=6.0,
            height=6.0,
            axis_config=dict(stroke_color=KDE.axes, stroke_width=2),
        )
        axes.to_edge(DOWN, buff=0.55)
        axes.shift(RIGHT * 0.55)
        x_label = Text("x", font_size=28, color=KDE.text).next_to(axes.get_x_axis().get_end(), RIGHT)
        y_label = Text("y", font_size=28, color=KDE.text).next_to(axes.get_y_axis().get_end(), UP)
        self.play(ShowCreation(axes), FadeIn(x_label), FadeIn(y_label))

        theta = ValueTracker(0.0)

        def arrow():
            p = theta.get_value()
            x = float(np.interp(p, T, X))
            y = float(np.interp(p, T, Y))
            return Arrow(
                axes.c2p(0, 0),
                axes.c2p(x, y),
                buff=0,
                thickness=6,
                color=KDE.vector,
            )

        arrow_mob = always_redraw(arrow)
        self.add(arrow_mob)

        angle_caption = Text("theta =", font_size=30, color=KDE.text).to_corner(UR, buff=0.45)
        angle_value = DecimalNumber(
            0,
            num_decimal_places=1,
            font_size=30,
            color=KDE.number,
        )
        angle_value.add_updater(
            lambda m: m.set_value(float(np.interp(theta.get_value(), T, ANGLE)) * 180 / np.pi)
        )
        angle_group = VGroup(angle_caption, angle_value).arrange(RIGHT, buff=0.1)
        angle_group.to_corner(UR, buff=0.45)

        norm_caption = Text("|v| =", font_size=30, color=KDE.text)
        norm_value = DecimalNumber(
            1,
            num_decimal_places=3,
            font_size=30,
            color=KDE.number,
        )
        norm_value.add_updater(
            lambda m: m.set_value(
                float(np.hypot(
                    np.interp(theta.get_value(), T, X),
                    np.interp(theta.get_value(), T, Y),
                ))
            )
        )
        norm_group = VGroup(norm_caption, norm_value).arrange(RIGHT, buff=0.1)
        norm_group.next_to(angle_group, DOWN, buff=0.18, aligned_edge=LEFT)
        self.add(angle_group, norm_group)

        self.play(theta.animate.set_value(1.0), run_time=4.0, rate_func=smooth)
        self.wait(1.0)
