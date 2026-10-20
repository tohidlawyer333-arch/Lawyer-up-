#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
chmod +x gradlew
./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon --stacktrace
npm --prefix tests/web ci --ignore-scripts
npm --prefix tests/web test
