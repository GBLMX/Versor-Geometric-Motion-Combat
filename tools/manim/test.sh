#!/bin/sh
set -eu

HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT=$(CDPATH= cd -- "$HERE/../.." && pwd)
VERSOR="$ROOT/lib/versor-java"

echo "== versor-java checks =="
"$VERSOR/build.sh"

echo "== versor-java -> JSON =="
mkdir -p "$HERE/bridge/out" "$HERE/data" "$HERE/videos"
javac --release 17 -Xlint:all -cp "$VERSOR/out" -d "$HERE/bridge/out" \
  "$HERE/bridge/vsr/bridge/VersorManimDump.java"
java -cp "$VERSOR/out:$HERE/bridge/out" vsr.bridge.VersorManimDump \
  "$HERE/data/versor-rotor.json"

echo "== manim render =="
cd "$HERE"
uv sync --frozen --no-config

if command -v wolframscript >/dev/null 2>&1; then
  echo "== Wolfram -> flat JSON =="
  wolframscript -file bridge_data/generate_data.wls
  wolframscript -file bridge_data/zeta.wls
  uv run --frozen --no-config python - <<'PY'
from versor_ga.bridge import load_records, record_series

records = load_records("bridge_data/data/demo.json")
ids = [record["id"] for record in records]
assert ids == [
    "sin_curve",
    "phase_shifted",
    "slope",
    "bars",
    "table",
    "surface",
    "space_curve",
    "scatter",
], ids
assert len(records[0]["points"]) == 501
assert len(records[1]["points"]) == 501
assert len(record_series(records[2])) == 501
assert len(records[3]["values"]) == 3
assert len(records[4]["rows"]) == 3
assert records[5]["shape"] == [21, 21]
assert len(records[5]["points"]) == 441
assert len(records[6]["points"]) == 121
assert len(records[7]["points"]) == 25

zeta = load_records("bridge_data/data/zeta.json")[0]
assert zeta["id"] == "zeta_critical"
assert len(zeta["points"]) == 1001
assert zeta["points"][0][0] == 0.0
assert len(zeta["points"][0]) == 3
print("wolfram bridge: 8 demo records + 1001 zeta samples validated")
PY
else
  echo "wolframscript not found; using checked-in bridge data"
fi

uv run --frozen --no-config python -m unittest discover -s tests -v
uv run --frozen --no-config manimgl "$HERE/versor_scene.py" VersorRotor \
  -l -w --video_dir "$HERE/videos"
uv run --frozen --no-config manimgl "$HERE/bridge_scene.py" JsonBridgeScene \
  -l -w --video_dir "$HERE/videos"
uv run --frozen --no-config manimgl "$HERE/bridge_showcase.py" BridgeChartsScene \
  -l -w --video_dir "$HERE/videos"
uv run --frozen --no-config manimgl "$HERE/bridge_showcase.py" Bridge3DScene \
  -l -w --video_dir "$HERE/videos"
uv run --frozen --no-config manimgl "$HERE/zeta_scene.py" ZetaScene \
  -l -w --video_dir "$HERE/videos"

if [ ! -s "$HERE/videos/VersorRotor.mp4" ]; then
  echo "missing render: $HERE/videos/VersorRotor.mp4" >&2
  exit 1
fi
if [ ! -s "$HERE/videos/JsonBridgeScene.mp4" ]; then
  echo "missing render: $HERE/videos/JsonBridgeScene.mp4" >&2
  exit 1
fi
if [ ! -s "$HERE/videos/BridgeChartsScene.mp4" ]; then
  echo "missing render: $HERE/videos/BridgeChartsScene.mp4" >&2
  exit 1
fi
if [ ! -s "$HERE/videos/Bridge3DScene.mp4" ]; then
  echo "missing render: $HERE/videos/Bridge3DScene.mp4" >&2
  exit 1
fi
if [ ! -s "$HERE/videos/ZetaScene.mp4" ]; then
  echo "missing render: $HERE/videos/ZetaScene.mp4" >&2
  exit 1
fi
echo "render ok: $HERE/videos/VersorRotor.mp4"
echo "render ok: $HERE/videos/JsonBridgeScene.mp4"
echo "render ok: $HERE/videos/BridgeChartsScene.mp4"
echo "render ok: $HERE/videos/Bridge3DScene.mp4"
echo "render ok: $HERE/videos/ZetaScene.mp4"
