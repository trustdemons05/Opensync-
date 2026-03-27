#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

"$ROOT_DIR/scripts/build-debug.sh"

mkdir -p "$ROOT_DIR/releases"
STAMP="$(date -u +%Y%m%d-%H%M%S)"
OUT="$ROOT_DIR/releases/koi-alarm-helper-debug-$STAMP.apk"
cp "$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk" "$OUT"
sha256sum "$OUT" | tee "$OUT.sha256"

echo "\nRelease artifact created:"
echo "$OUT"
