from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from versor_ga.bridge import (
    build_mobject,
    build_mobjects,
    load_records,
    series_value,
    validate_records,
)


def record(**overrides):
    data = {
        "id": "curve",
        "kind": "sampled_polyline",
        "style": {"color": "#FF6A00", "stroke_width": 5},
        "points": [[0.0, 0.0], [1.0, 1.0], [2.0, 0.0]],
    }
    data.update(overrides)
    return data


class BridgeTests(unittest.TestCase):
    def test_load_valid_records(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "data.json"
            path.write_text(json.dumps([record()]), encoding="utf-8")
            loaded = load_records(path)
        self.assertEqual(loaded[0]["id"], "curve")

    def test_rejects_duplicate_ids(self):
        with self.assertRaisesRegex(ValueError, "duplicate"):
            validate_records([record(), record()])

    def test_rejects_unknown_kind(self):
        with self.assertRaisesRegex(ValueError, "unsupported kind"):
            validate_records([record(kind="polygon")])

    def test_rejects_non_numeric_point(self):
        with self.assertRaisesRegex(ValueError, "not numeric"):
            validate_records([record(points=[[0, 0], [1, "x"]])])

    def test_build_mobject_uses_style(self):
        from manimlib import Axes

        axes = Axes(x_range=(0, 2, 1), y_range=(-1, 1, 1), width=4, height=4)
        mobject = build_mobject(record(), axes)
        self.assertEqual(mobject.get_color(), "#FF6A00")
        self.assertAlmostEqual(mobject.get_stroke_width(), 5.0)

    def test_parametric_drops_parameter(self):
        from manimlib import Axes

        data = record(
            kind="parametric",
            points=[[0.0, 0.0, 0.0], [0.5, 1.0, 1.0], [1.0, 0.0, 0.0]],
        )
        axes = Axes(x_range=(0, 1, 1), y_range=(-1, 1, 1), width=4, height=4)
        mobject = build_mobject(data, axes)
        self.assertGreater(mobject.get_num_points(), 0)

    def test_build_mobjects_skips_series(self):
        from manimlib import Axes

        axes = Axes(x_range=(0, 2, 1), y_range=(-1, 1, 1), width=4, height=4)
        mobjects = build_mobjects(
            [record(), record(id="series", kind="scalar_series", values=[0, 1])],
            axes,
        )
        self.assertEqual(list(mobjects), ["curve"])

    def test_bars_builds_chart(self):
        from manimlib import BarChart

        data = record(
            id="bars",
            kind="bars",
            values=[1.0, 2.0],
            categories=["a", "b"],
            points=None,
            style={"color": "#00A8FF"},
        )
        self.assertIsInstance(build_mobject(data, None), BarChart)

    def test_table_builds_group(self):
        from manimlib import VGroup

        data = record(
            id="table",
            kind="table",
            header=["a", "b"],
            rows=[[1, "x"], [2, "y"]],
            points=None,
            style={"font_size": 20},
        )
        table = build_mobject(data, None)
        self.assertIsInstance(table, VGroup)
        self.assertGreater(len(table.submobjects), 0)

    def test_surface_rejects_bad_shape(self):
        data = record(
            id="surface",
            kind="surface",
            shape=[2, 2],
            points=[[0, 0, 0]],
        )
        with self.assertRaisesRegex(ValueError, "shape"):
            validate_records([data])

    def test_surface_builds_parametric_surface(self):
        from manimlib import ParametricSurface, ThreeDAxes

        points = [
            [0.0, 0.0, 0.0],
            [0.0, 1.0, 0.0],
            [1.0, 0.0, 0.0],
            [1.0, 1.0, 1.0],
        ]
        data = record(
            id="surface",
            kind="surface",
            shape=[2, 2],
            points=points,
        )
        axes = ThreeDAxes(
            x_range=(0, 1, 1),
            y_range=(0, 1, 1),
            z_range=(0, 1, 1),
            width=4,
            height=4,
            depth=4,
        )
        self.assertIsInstance(build_mobject(data, axes), ParametricSurface)

    def test_points3d_builds_group(self):
        from manimlib import Group, ThreeDAxes

        data = record(
            id="scatter",
            kind="points3d",
            points=[[0, 0, 0], [1, 1, 1]],
        )
        axes = ThreeDAxes(
            x_range=(0, 1, 1),
            y_range=(0, 1, 1),
            z_range=(0, 1, 1),
            width=4,
            height=4,
            depth=4,
        )
        self.assertIsInstance(build_mobject(data, axes), Group)

    def test_series_value_interpolates(self):
        series = record(id="series", kind="scalar_series", values=[0.0, 10.0])
        self.assertAlmostEqual(series_value(series, 0.25), 2.5)
        self.assertAlmostEqual(series_value(series, -1.0), 0.0)
        self.assertAlmostEqual(series_value(series, 2.0), 10.0)


if __name__ == "__main__":
    unittest.main()
