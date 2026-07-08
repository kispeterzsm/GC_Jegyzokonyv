#!/usr/bin/env bash
set -euo pipefail

# Usage: ./build-apk.sh [debug|release]   (default: debug)

cd "$(dirname "$0")"

VARIANT="${1:-debug}"

case "$VARIANT" in
    debug)   TASK="assembleDebug";   OUT="app/build/outputs/apk/debug/app-debug.apk" ;;
    release) TASK="assembleRelease"; OUT="app/build/outputs/apk/release/app-release-unsigned.apk" ;;
    *) echo "Unknown variant: $VARIANT (use debug or release)" >&2; exit 1 ;;
esac

# Gradle 8.9 supports JDK 8–22. Pick a compatible JVM, preferring 21 → 17 → 11.
if [[ -z "${JAVA_HOME:-}" ]] || ! "${JAVA_HOME}/bin/java" -version 2>&1 | grep -qE 'version "(1\.8|11|17|21)'; then
    for candidate in \
        "$HOME/.local/share/jdks/java-21-openjdk" \
        "$HOME/.local/share/jdks/java-17-openjdk" \
        "$HOME/.local/share/jdks/java-11-openjdk" \
        /usr/lib/jvm/java-21-openjdk \
        /usr/lib/jvm/java-17-openjdk \
        /usr/lib/jvm/java-11-openjdk \
        /usr/lib/jvm/default-java; do
        if [[ -x "$candidate/bin/java" ]]; then
            export JAVA_HOME="$candidate"
            break
        fi
    done
fi

if [[ -z "${ANDROID_HOME:-}" ]]; then
    for candidate in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" /opt/android-sdk; do
        if [[ -d "$candidate" ]]; then
            export ANDROID_HOME="$candidate"
            export ANDROID_SDK_ROOT="$candidate"
            break
        fi
    done
fi

echo "==> JAVA_HOME=${JAVA_HOME:-<unset>}"
echo "==> ANDROID_HOME=${ANDROID_HOME:-<unset>}"
echo "==> Running ./gradlew $TASK"
./gradlew "$TASK"

if [[ -f "$OUT" ]]; then
    echo
    echo "APK built: $OUT"
    ls -lh "$OUT"
else
    echo "Build finished but expected APK not found at: $OUT" >&2
    exit 1
fi
