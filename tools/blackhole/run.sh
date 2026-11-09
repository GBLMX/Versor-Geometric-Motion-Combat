#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/jbrsdk-25.0.3}"
JAVAC="$JAVA_HOME/bin/javac"
JAVA="$JAVA_HOME/bin/java"

if [[ ! -x "$JAVAC" || ! -x "$JAVA" ]]; then
  echo "JDK 25 not found at $JAVA_HOME; set JAVA_HOME to a JDK 25 install." >&2
  exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# Compile the reusable GA core plus this single-file renderer. No Minecraft or
# external dependencies are involved.
mapfile -t SOURCES < <(find "$ROOT/lib/versor-java/src/main/java" -name '*.java' -print)
"$JAVAC" --release 25 -d "$TMP" "${SOURCES[@]}" "$ROOT/tools/blackhole/BlackHole.java"

cd "$ROOT"
"$JAVA" -cp "$TMP" BlackHole "$@"
