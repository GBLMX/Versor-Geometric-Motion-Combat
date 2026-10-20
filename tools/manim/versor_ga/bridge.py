"""Flat JSON records for the Wolfram -> Manim data bridge.

The contract is deliberately small: a JSON list of records, each with an
``id``, a ``kind``, and either ``points`` or ``values``. Wolfram writes the
records once; Manim only reads, validates, and maps them to mobjects.
"""

from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Any, Iterable, Mapping

from .palette import KDE

POINT_KINDS = frozenset({"sampled_polyline", "parametric"})
RECORD_KINDS = POINT_KINDS | {"scalar_series"}

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
    if kind == "scalar_series":
        _values(record)
    else:
        _points(record)


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


def build_mobject(
    record: Mapping[str, Any],
    axes: Any,
    *,
    default_color: str = KDE.text,
    default_stroke_width: float = 4.0,
):
    """Map one point-backed record to a ManimGL ``VMobject``.

    ``axes`` may be a 2D ``Axes`` or a ``ThreeDAxes``; ``c2p`` receives the
    point dimension unchanged.
    """
    from manimlib import VMobject

    if record.get("kind") not in POINT_KINDS:
        raise ValueError(f"record {record.get('id')!r} has no drawable points")
    style = _style(record)
    mobject = VMobject(
        color=style.get("color", default_color),
        stroke_width=float(style.get("stroke_width", default_stroke_width)),
    )
    scene_points = [axes.c2p(*point) for point in _scene_points(record)]
    if style.get("smooth", True):
        mobject.set_points_smoothly(scene_points)
    else:
        mobject.set_points_as_corners(scene_points)
    return mobject


def build_mobjects(
    records: Iterable[Mapping[str, Any]],
    axes: Any,
    **kwargs: Any,
) -> dict[str, Any]:
    """Build every drawable record, keyed by ``id``; skip scalar series."""
    return {
        record["id"]: build_mobject(record, axes, **kwargs)
        for record in records
        if record.get("kind") in POINT_KINDS
    }


__all__ = [
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
