"""Flat JSON records for the Wolfram -> Manim data bridge.

The contract is deliberately small: a JSON list of records, each with an
``id``, a ``kind``, and the payload that kind needs. Wolfram writes the records
once; Manim only reads, validates, and maps them to mobjects.
"""

from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Any, Iterable, Mapping

import numpy as np

from .palette import KDE

POINT_KINDS = frozenset({"sampled_polyline", "parametric"})
DRAWABLE_KINDS = POINT_KINDS | {"bars", "table", "surface", "points3d"}
RECORD_KINDS = DRAWABLE_KINDS | {"scalar_series"}

Record = dict[str, Any]


def _is_number(value: Any) -> bool:
    return (
        isinstance(value, (int, float))
        and not isinstance(value, bool)
        and math.isfinite(float(value))
    )


def _style(record: Mapping[str, Any]) -> Mapping[str, Any]:
    style = record.get("style", {})
    if not isinstance(style, dict):
        raise ValueError(f"record {record.get('id')!r}: style must be an object")
    return style


def _points(record: Mapping[str, Any]) -> list[list[float]]:
    kind = record["kind"]
    points = record.get("points")
    if not isinstance(points, list) or len(points) < 2:
        raise ValueError(f"record {record['id']!r}: points must contain at least 2 points")
    allowed = (2, 3) if kind == "sampled_polyline" else (3, 4)
    for index, point in enumerate(points):
        if not isinstance(point, (list, tuple)) or len(point) not in allowed:
            raise ValueError(
                f"record {record['id']!r}: point {index} has wrong dimension"
            )
        if not all(_is_number(value) for value in point):
            raise ValueError(f"record {record['id']!r}: point {index} is not numeric")
    return points


def _values(record: Mapping[str, Any]) -> list[float]:
    values = record.get("values")
    if not isinstance(values, list) or not values:
        raise ValueError(f"record {record['id']!r}: values must be a non-empty list")
    if not all(_is_number(value) for value in values):
        raise ValueError(f"record {record['id']!r}: values must be numeric")
    return values


def _bars(record: Mapping[str, Any]) -> tuple[list[float], list[str]]:
    values = _values(record)
    names = record.get("categories", record.get("bar_names", []))
    if not isinstance(names, list) or not all(isinstance(name, str) for name in names):
        raise ValueError(f"record {record['id']!r}: categories must be strings")
    if names and len(names) != len(values):
        raise ValueError(f"record {record['id']!r}: categories and values differ in length")
    return values, names


def _table(record: Mapping[str, Any]) -> tuple[list[str], list[list[Any]]]:
    header = record.get("header", [])
    rows = record.get("rows")
    if not isinstance(header, list) or not all(isinstance(cell, str) for cell in header):
        raise ValueError(f"record {record['id']!r}: header must be a string list")
    if not isinstance(rows, list) or not rows:
        raise ValueError(f"record {record['id']!r}: rows must be a non-empty list")
    width = len(header) if header else len(rows[0]) if isinstance(rows[0], list) else 0
    if width == 0:
        raise ValueError(f"record {record['id']!r}: table needs at least one column")
    for row_index, row in enumerate(rows):
        if not isinstance(row, list) or len(row) != width:
            raise ValueError(f"record {record['id']!r}: row {row_index} has wrong length")
        if not all(isinstance(cell, (str, int, float)) and not isinstance(cell, bool) for cell in row):
            raise ValueError(f"record {record['id']!r}: row {row_index} has a non-scalar cell")
    return header, rows


def _surface(record: Mapping[str, Any]) -> tuple[list[list[float]], int, int]:
    points = record.get("points")
    shape = record.get("shape")
    if not isinstance(shape, list) or len(shape) != 2 or not all(
        isinstance(value, int) and not isinstance(value, bool) and value > 1 for value in shape
    ):
        raise ValueError(f"record {record['id']!r}: shape must be [rows, cols] > 1")
    rows, cols = shape
    if not isinstance(points, list) or len(points) != rows * cols:
        raise ValueError(f"record {record['id']!r}: points do not match shape")
    for index, point in enumerate(points):
        if not isinstance(point, (list, tuple)) or len(point) != 3:
            raise ValueError(f"record {record['id']!r}: surface point {index} is not 3D")
        if not all(_is_number(value) for value in point):
            raise ValueError(f"record {record['id']!r}: surface point {index} is not numeric")
    return points, rows, cols


def _points3d(record: Mapping[str, Any]) -> list[list[float]]:
    points = record.get("points")
    if not isinstance(points, list) or not points:
        raise ValueError(f"record {record['id']!r}: points must be a non-empty list")
    for index, point in enumerate(points):
        if not isinstance(point, (list, tuple)) or len(point) != 3:
            raise ValueError(f"record {record['id']!r}: point {index} is not 3D")
        if not all(_is_number(value) for value in point):
            raise ValueError(f"record {record['id']!r}: point {index} is not numeric")
    return points


def _check_record(record: Any, seen: set[str]) -> None:
    if not isinstance(record, dict):
        raise ValueError("each record must be a JSON object")
    record_id = record.get("id")
    if not isinstance(record_id, str) or not record_id:
        raise ValueError("each record needs a non-empty string id")
    if record_id in seen:
        raise ValueError(f"duplicate record id {record_id!r}")
    seen.add(record_id)

    kind = record.get("kind")
    if kind not in RECORD_KINDS:
        raise ValueError(f"record {record_id!r}: unsupported kind {kind!r}")
    _style(record)
    if kind in POINT_KINDS:
        _points(record)
    elif kind == "scalar_series":
        _values(record)
    elif kind == "bars":
        _bars(record)
    elif kind == "table":
        _table(record)
    elif kind == "surface":
        _surface(record)
    else:
        _points3d(record)


def validate_records(records: Any) -> None:
    """Raise ``ValueError`` when records do not follow the flat contract."""
    if not isinstance(records, list) or not records:
        raise ValueError("records must be a non-empty JSON list")
    seen: set[str] = set()
    for record in records:
        _check_record(record, seen)


def load_records(path: str | Path) -> list[Record]:
    """Load and validate a flat JSON record list."""
    with open(path, encoding="utf-8") as handle:
        records = json.load(handle)
    validate_records(records)
    return records


def record_series(record: Mapping[str, Any]) -> list[float]:
    """Return the values of a ``scalar_series`` record."""
    if record.get("kind") != "scalar_series":
        raise ValueError(f"record {record.get('id')!r} is not a scalar_series")
    return _values(record)


def series_value(record: Mapping[str, Any], progress: float) -> float:
    """Linearly sample a ``scalar_series`` at ``progress`` in ``[0, 1]``."""
    values = record_series(record)
    if len(values) == 1:
        return float(values[0])
    progress = min(1.0, max(0.0, float(progress)))
    position = progress * (len(values) - 1)
    lower = int(math.floor(position))
    upper = min(lower + 1, len(values) - 1)
    fraction = position - lower
    return float(values[lower] * (1.0 - fraction) + values[upper] * fraction)


def _scene_points(record: Mapping[str, Any]) -> list[tuple[float, ...]]:
    points = _points(record)
    if record["kind"] == "parametric":
        return [tuple(point[1:]) for point in points]
    return [tuple(point) for point in points]


def _build_curve(record: Mapping[str, Any], axes: Any, style: Mapping[str, Any], color: str, width: float):
    from manimlib import VMobject

    mobject = VMobject(color=style.get("color", color), stroke_width=float(style.get("stroke_width", width)))
    scene_points = [axes.c2p(*point) for point in _scene_points(record)]
    if style.get("smooth", True):
        mobject.set_points_smoothly(scene_points)
    else:
        mobject.set_points_as_corners(scene_points)
    return mobject


def _build_bars(record: Mapping[str, Any], style: Mapping[str, Any], color: str):
    from manimlib import BarChart

    values, names = _bars(record)
    max_value = style.get("max_value", max(values) or 1.0)
    return BarChart(
        values,
        height=float(style.get("height", 4.0)),
        width=float(style.get("width", 6.0)),
        max_value=float(max_value),
        bar_colors=[style.get("color", color), style.get("secondary_color", KDE.keyword)],
        bar_names=names,
        bar_fill_opacity=float(style.get("opacity", 0.8)),
        bar_stroke_width=float(style.get("stroke_width", 3.0)),
    )


def _build_table(record: Mapping[str, Any], style: Mapping[str, Any], color: str):
    from manimlib import Line, Text, VGroup

    header, rows = _table(record)
    all_rows = ([header] if header else []) + rows
    font_size = float(style.get("font_size", 24.0))
    padding = float(style.get("padding", 0.14))
    text_color = style.get("color", color)
    header_color = style.get("header_color", KDE.command)
    grid_color = style.get("grid_color", KDE.axes)

    cells = [
        [
            Text(
                str(cell),
                font_size=font_size,
                color=header_color if (header and row_index == 0) else text_color,
            )
            for cell in row
        ]
        for row_index, row in enumerate(all_rows)
    ]
    widths = [max(cell.get_width() for cell in column) + 2 * padding for column in zip(*cells)]
    heights = [max(cell.get_height() for cell in row) + 2 * padding for row in cells]
    total_width = sum(widths)
    total_height = sum(heights)
    x_edges = [-total_width / 2 + sum(widths[:index]) for index in range(len(widths) + 1)]
    y_edges = [total_height / 2 - sum(heights[:index]) for index in range(len(heights) + 1)]

    for row_index, row in enumerate(cells):
        for column_index, cell in enumerate(row):
            cell.move_to(
                np.array(
                    [
                        (x_edges[column_index] + x_edges[column_index + 1]) / 2,
                        (y_edges[row_index] + y_edges[row_index + 1]) / 2,
                        0.0,
                    ]
                )
            )

    grid = VGroup(
        *[
            Line(
                np.array([x, y_edges[-1], 0.0]),
                np.array([x, y_edges[0], 0.0]),
                color=grid_color,
                stroke_width=1.0,
            )
            for x in x_edges
        ],
        *[
            Line(
                np.array([x_edges[0], y, 0.0]),
                np.array([x_edges[-1], y, 0.0]),
                color=grid_color,
                stroke_width=1.0,
            )
            for y in y_edges
        ],
    )
    return VGroup(grid, *[cell for row in cells for cell in row])


def _build_surface(record: Mapping[str, Any], axes: Any, style: Mapping[str, Any], color: str):
    from manimlib import ParametricSurface

    points, rows, cols = _surface(record)
    grid = [np.asarray(axes.c2p(*point), dtype=float) for point in points]

    def uv_func(u: float, v: float) -> np.ndarray:
        x = u * (cols - 1)
        y = v * (rows - 1)
        column = min(int(x), cols - 2)
        row = min(int(y), rows - 2)
        tx = x - column
        ty = y - row
        p00 = grid[row * cols + column]
        p10 = grid[row * cols + column + 1]
        p01 = grid[(row + 1) * cols + column]
        p11 = grid[(row + 1) * cols + column + 1]
        return (
            p00 * (1 - tx) * (1 - ty)
            + p10 * tx * (1 - ty)
            + p01 * (1 - tx) * ty
            + p11 * tx * ty
        )

    return ParametricSurface(
        uv_func,
        u_range=(0, 1),
        v_range=(0, 1),
        resolution=(max(2, cols - 1), max(2, rows - 1)),
        color=style.get("color", color),
        opacity=float(style.get("opacity", 0.8)),
    )


def _build_points3d(record: Mapping[str, Any], axes: Any, style: Mapping[str, Any], color: str):
    from manimlib import Group, Sphere

    points = _points3d(record)
    radius = float(style.get("radius", 0.06))
    opacity = float(style.get("opacity", 1.0))
    return Group(
        *[
            Sphere(
                radius=radius,
                color=style.get("color", color),
                opacity=opacity,
                resolution=(8, 4),
            ).move_to(axes.c2p(*point))
            for point in points
        ]
    )


def build_mobject(
    record: Mapping[str, Any],
    axes: Any,
    *,
    default_color: str = KDE.text,
    default_stroke_width: float = 4.0,
):
    """Map one drawable record to a ManimGL mobject.

    Point-backed records accept a 2D ``Axes`` or a ``ThreeDAxes``. ``surface``
    and ``points3d`` require a ``ThreeDAxes``; ``table`` ignores ``axes``.
    """
    kind = record.get("kind")
    if kind not in DRAWABLE_KINDS:
        raise ValueError(f"record {record.get('id')!r} has no drawable payload")
    style = _style(record)
    if kind in POINT_KINDS:
        return _build_curve(record, axes, style, default_color, default_stroke_width)
    if kind == "bars":
        return _build_bars(record, style, KDE.line)
    if kind == "table":
        return _build_table(record, style, default_color)
    if kind == "surface":
        return _build_surface(record, axes, style, KDE.blue)
    return _build_points3d(record, axes, style, KDE.point)


def build_mobjects(
    records: Iterable[Mapping[str, Any]],
    axes: Any,
    **kwargs: Any,
) -> dict[str, Any]:
    """Build every drawable record, keyed by ``id``; skip scalar series."""
    return {
        record["id"]: build_mobject(record, axes, **kwargs)
        for record in records
        if record.get("kind") in DRAWABLE_KINDS
    }


__all__ = [
    "DRAWABLE_KINDS",
    "POINT_KINDS",
    "RECORD_KINDS",
    "Record",
    "build_mobject",
    "build_mobjects",
    "load_records",
    "record_series",
    "series_value",
    "validate_records",
]
