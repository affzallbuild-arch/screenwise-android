#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
kotlinc "$ROOT/app/src/main/java/com/screenwise/app/usage/UsageMath.kt" \
  "$ROOT/app/src/main/java/com/screenwise/app/util/WellnessScore.kt" \
  "$ROOT/devtools/TestMain.kt" -include-runtime -d "$OUT/test.jar"
java -jar "$OUT/test.jar"
