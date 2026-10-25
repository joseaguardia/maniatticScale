#!/usr/bin/env bash
# Builds the Android app inside a podman container, so the host needs no JDK or Android SDK.
# Usage: ./build.sh [gradle tasks...]   (default: assembleDebug)
#   REBUILD_IMAGE=1 ./build.sh          forces rebuilding the build image
# The Gradle cache lives in the "openscale-gradle" volume and ~/.android is mounted so the
# debug.keystore is reused: a different keystore means the APK can't update the installed app.
set -euo pipefail

IMAGE_NAME="openscale-android-build"
GRADLE_VOLUME="openscale-gradle"
ANDROID_USER_DIR="${HOME}/.android"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(dirname "${SCRIPT_DIR}")"

if [[ $# -eq 0 ]]; then
    set -- assembleDebug
fi

if [[ "${REBUILD_IMAGE:-0}" == "1" ]] || ! podman image exists "${IMAGE_NAME}"; then
    podman build --tag "${IMAGE_NAME}" --file "${SCRIPT_DIR}/Containerfile" "${SCRIPT_DIR}"
fi

mkdir --parents "${ANDROID_USER_DIR}"
if [[ ! -f "${ANDROID_USER_DIR}/debug.keystore" ]]; then
    echo "WARNING: ${ANDROID_USER_DIR}/debug.keystore not found, a new one will be generated." >&2
    echo "         Restore your backup first if you want to update the installed app without losing data." >&2
fi

podman run --rm \
    --volume "${REPO_DIR}:/workspace:Z" \
    --volume "${GRADLE_VOLUME}:/root/.gradle" \
    --volume "${ANDROID_USER_DIR}:/root/.android:Z" \
    --workdir /workspace/android_app \
    "${IMAGE_NAME}" \
    ./gradlew "$@"

find "${SCRIPT_DIR}/app/build/outputs/apk" -name "*.apk" -printf "APK: %p\n" 2>/dev/null || true
