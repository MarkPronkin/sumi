#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || "$1" != emulator-* ]]; then
  printf 'Usage: %s emulator-5554\nChoose one emulator explicitly. This script never targets connected physical phones.\n' "$0" >&2
  exit 2
fi

export ANDROID_SERIAL="$1"
cd "$(dirname "$0")/.."
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest
