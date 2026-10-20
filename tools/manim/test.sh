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
uv run --frozen --no-config python -m unittest discover -s tests -v
uv run --frozen --no-config manimgl "$HERE/versor_scene.py" VersorRotor \
  -l -w --video_dir "$HERE/videos"

if [ ! -s "$HERE/videos/VersorRotor.mp4" ]; then
  echo "missing render: $HERE/videos/VersorRotor.mp4" >&2
  exit 1
fi
echo "render ok: $HERE/videos/VersorRotor.mp4"
