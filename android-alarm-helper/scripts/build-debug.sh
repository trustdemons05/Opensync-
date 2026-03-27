#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

export JAVA_HOME="${JAVA_HOME:-/home/node/.openclaw/workspace/.toolchains/jdk-17}"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/home/node/.openclaw/workspace/.android-sdk}"

if [ ! -f "$ROOT_DIR/local.properties" ]; then
  echo "sdk.dir=$ANDROID_SDK_ROOT" > "$ROOT_DIR/local.properties"
fi

./gradlew assembleDebug

echo "\nBuilt APK:"
ls -lh "$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
