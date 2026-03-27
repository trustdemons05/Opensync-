#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR/.."

VERSION_NAME=$(grep -E 'versionName\s*=\s*".*"' android-alarm-helper/app/build.gradle.kts | sed -E 's/.*"([^"]+)".*/\1/' | head -n1)
if [ -z "$VERSION_NAME" ]; then
  echo "Could not detect versionName" >&2
  exit 1
fi

TAG="alarm-v${VERSION_NAME}"

git add android-alarm-helper/app/build.gradle.kts .github/workflows/android-alarm-helper-release.yml android-alarm-helper/README.md android-alarm-helper/scripts

if ! git diff --cached --quiet; then
  git commit -m "Release prep ${TAG}"
fi

git push origin android-alarm-helper

if git rev-parse -q --verify "refs/tags/${TAG}" >/dev/null; then
  git tag -d "$TAG"
fi

git tag -a "$TAG" -m "Android Alarm Helper ${VERSION_NAME}"
git push origin "$TAG"

echo "Pushed ${TAG}"
