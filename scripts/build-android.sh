#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME to an Android SDK directory}"
java -version
go version
bash gradlew -PaumArm64 testOssDebugUnitTest lintOssDebug assembleOssDebug --no-daemon --stacktrace
apk_dir=app/build/outputs/apk/oss/debug
mapfile -t apks < <(find "$apk_dir" -maxdepth 1 -name '*arm64-v8a*-debug.apk' -type f)
test "${#apks[@]}" -eq 1
python3 scripts/inspect-apk.py "${apks[0]}"
sha256sum "${apks[0]}" | tee "$apk_dir/SHA256SUMS"
