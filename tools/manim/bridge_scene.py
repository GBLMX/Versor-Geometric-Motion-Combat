"""Render the flat Wolfram -> JSON -> ManimGL bridge demo.

Generate the data from ``tools/manim`` with::

    wolframscript -file bridge_data/generate_data.wls

Then render::

    uv run --frozen --no-config manimgl bridge_scene.py JsonBridgeScene -l -w --video_dir videos
"""

import os

from manimlib import *

from versor_ga import KDE
from versor_ga.bridge import build_mobjects, load_records, series_value

HERE = os.path.dirname(os.path.abspath(__file__))
RECORDS = load_records(os.path.join(HERE, "bridge_data", "data", "demo.json"))
CURVES = [
    record
    for record in RECORDS
    if record["kind"] == "sampled_polyline"
    and all(len(point) == 2 for point in record["points"])
]


class JsonBridgeScene(Scene):
    def construct(self):
        self.camera.background_color = KDE.background

        axes = Axes(
            x_range=(-5, 5, 1),
            y_range=(-2.5, 2.5, 1),
            width=10,
            height=6,
            axis_config=dict(stroke_color=KDE.axes, stroke_width=2),
        )
        axes.to_edge(DOWN, buff=0.5)

        title = Text(
            "Wolfram -> JSON -> ManimGL",
            font_size=36,
            color=KDE.command,
        ).to_edge(UP, buff=0.25)
        subtitle = Text(
            "flat records | axes.c2p | ShowCreation",
            font_size=20,
            color=KDE.comment,
        ).next_to(title, DOWN, buff=0.08)

        self.play(ShowCreation(axes), FadeIn(title), FadeIn(subtitle))

        curves = build_mobjects(CURVES, axes)
        self.play(*[ShowCreation(curve) for curve in curves.values()], run_time=1.5)

        series = next(record for record in RECORDS if record["kind"] == "scalar_series")
        caption = Text("slope", font_size=28, color=KDE.text).to_corner(UR, buff=0.45)
        value = DecimalNumber(
            series_value(series, 0.0),
            num_decimal_places=3,
            font_size=28,
            color=KDE.number,
        )
        value.next_to(caption, DOWN, buff=0.08, aligned_edge=RIGHT)

        progress = ValueTracker(0.0)
        value.add_updater(lambda mob: mob.set_value(series_value(series, progress.get_value())))
        self.add(caption, value)
        self.play(progress.animate.set_value(1.0), run_time=2.0)
        self.wait(0.5)
