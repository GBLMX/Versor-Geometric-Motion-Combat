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
  uv run --frozen --no-config python - <<'PY'
from versor_ga.bridge import load_records, record_series

records = load_records("bridge_data/data/demo.json")
ids = [record["id"] for record in records]
assert ids == ["sin_curve", "phase_shifted", "slope"], ids
assert len(records[0]["points"]) == 501
assert len(records[1]["points"]) == 501
assert len(record_series(records[2])) == 501
print("wolfram bridge: 3 records, 501 samples each")
PY
else
  echo "wolframscript not found; using checked-in bridge data"
fi

uv run --frozen --no-config python -m unittest discover -s tests -v
uv run --frozen --no-config manimgl "$HERE/versor_scene.py" VersorRotor \
  -l -w --video_dir "$HERE/videos"
uv run --frozen --no-config manimgl "$HERE/bridge_scene.py" JsonBridgeScene \
  -l -w --video_dir "$HERE/videos"

if [ ! -s "$HERE/videos/VersorRotor.mp4" ]; then
  echo "missing render: $HERE/videos/VersorRotor.mp4" >&2
  exit 1
fi
if [ ! -s "$HERE/videos/JsonBridgeScene.mp4" ]; then
  echo "missing render: $HERE/videos/JsonBridgeScene.mp4" >&2
  exit 1
fi
echo "render ok: $HERE/videos/VersorRotor.mp4"
echo "render ok: $HERE/videos/JsonBridgeScene.mp4"
