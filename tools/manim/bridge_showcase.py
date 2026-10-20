"""Showcase flat bridge records for bars, tables, and 3D functions.

Render::

    uv run --frozen --no-config manimgl bridge_showcase.py BridgeChartsScene -l -w --video_dir videos
    uv run --frozen --no-config manimgl bridge_showcase.py Bridge3DScene -l -w --video_dir videos
"""

import os

from manimlib import *

from versor_ga import KDE
from versor_ga.bridge import build_mobject, load_records

HERE = os.path.dirname(os.path.abspath(__file__))
RECORDS = load_records(os.path.join(HERE, "bridge_data", "data", "demo.json"))
BY_ID = {record["id"]: record for record in RECORDS}


class BridgeChartsScene(Scene):
    """Bar chart and table from the same flat record list."""

    def construct(self):
        self.camera.background_color = KDE.background

        title = Text(
            "Wolfram data | bars + table",
            font_size=36,
            color=KDE.command,
        ).to_edge(UP, buff=0.3)
        subtitle = Text(
            "records -> BarChart / VGroup table",
            font_size=20,
            color=KDE.comment,
        ).next_to(title, DOWN, buff=0.08)
        self.play(FadeIn(title), FadeIn(subtitle))

        bars = build_mobject(BY_ID["bars"], None)
        bars.scale(0.8).to_edge(LEFT, buff=0.7).shift(DOWN * 0.25)

        table = build_mobject(BY_ID["table"], None)
        table.set_width(4.6).to_edge(RIGHT, buff=0.5).shift(DOWN * 0.25)

        self.play(ShowCreation(bars), run_time=1.2)
        self.play(FadeIn(table, shift=UP * 0.2), run_time=1.2)
        self.wait(0.8)


class Bridge3DScene(ThreeDScene):
    """Height field, space curve, and scatter points from Wolfram."""

    def construct(self):
        self.camera.background_color = KDE.background
        self.frame.reorient(65, -48)
        self.frame.set_height(7.5)

        axes = ThreeDAxes(
            x_range=(-2, 2, 1),
            y_range=(-2, 2, 1),
            z_range=(-1.5, 1.5, 1),
            width=6,
            height=6,
            depth=4,
            axis_config=dict(stroke_color=KDE.axes, stroke_width=2),
        )
        title = Text(
            "z = sin(x) cos(y)",
            font_size=32,
            color=KDE.command,
        ).fix_in_frame().to_corner(UL, buff=0.3)
        subtitle = Text(
            "surface + space curve + scatter",
            font_size=18,
            color=KDE.comment,
        ).fix_in_frame().next_to(title, DOWN, buff=0.06)

        self.play(FadeIn(axes), FadeIn(title), FadeIn(subtitle), run_time=0.8)

        surface = build_mobject(BY_ID["surface"], axes)
        curve = build_mobject(BY_ID["space_curve"], axes)
        scatter = build_mobject(BY_ID["scatter"], axes)

        self.play(ShowCreation(surface), run_time=2.0)
        self.play(ShowCreation(curve), FadeIn(scatter), run_time=1.5)
        self.frame.add_ambient_rotation(0.15 * DEG)
        self.wait(2.0)
