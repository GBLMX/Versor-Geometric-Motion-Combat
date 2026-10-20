#!/bin/sh
set -eu

HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT=$(CDPATH= cd -- "$HERE/../.." && pwd)
VERSOR="$ROOT/lib/versor-java"

mkdir -p "$HERE/java-out"

echo "== build versor-java =="
"$VERSOR/build.sh"

echo "== Java benchmark =="
javac --release 17 -Xlint:all -cp "$VERSOR/out" -d "$HERE/java-out" \
  "$HERE/CompareBench.java"
java -Xmx4g -cp "$VERSOR/out:$HERE/java-out" vsr.CompareBench > "$HERE/java.csv"

echo "== Python benchmark =="
cd "$HERE"
uv sync --frozen --no-config
uv run --frozen --no-config python bench.py > "$HERE/python.csv"

{
  head -n 1 "$HERE/java.csv"
  tail -n +2 "$HERE/java.csv"
  tail -n +2 "$HERE/python.csv"
} > "$HERE/results.csv"

echo "== results (ns per element) =="
cat "$HERE/results.csv"
