#!/usr/bin/env bash
set -euo pipefail

# Usage: ./release-apk.sh <version>
# Example: ./release-apk.sh 0.2.1
#
# Builds the debug APK, updates app version + README download link,
# commits and pushes to GitHub, then creates a GitHub release with app-debug.apk.

cd "$(dirname "$0")"

VERSION="${1:-}"
if [[ -z "$VERSION" ]]; then
    echo "Usage: $0 <version>" >&2
    echo "Example: $0 0.2.1" >&2
    exit 1
fi

if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Invalid version: $VERSION (expected format: x.y.z, e.g. 0.2.1)" >&2
    exit 1
fi

if ! command -v gh >/dev/null 2>&1; then
    echo "GitHub CLI (gh) is required." >&2
    exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
    echo "GitHub CLI is not authenticated. Run: gh auth login" >&2
    exit 1
fi

if [[ -n "$(git status --porcelain)" ]]; then
    echo "Working tree is not clean. Commit/stash your changes first." >&2
    git status --short
    exit 1
fi

TAG="v${VERSION}"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
APK_URL="https://github.com/kispeterzsm/GC_Jegyzokonyv/releases/download/${TAG}/app-debug.apk"

if git rev-parse "$TAG" >/dev/null 2>&1 || gh release view "$TAG" --repo kispeterzsm/GC_Jegyzokonyv >/dev/null 2>&1; then
    echo "Tag/release already exists: $TAG" >&2
    exit 1
fi

CURRENT_CODE=$(grep -E '^[[:space:]]*versionCode = [0-9]+' app/build.gradle.kts | head -1 | sed -E 's/[^0-9]*([0-9]+).*/\1/')
if [[ -z "$CURRENT_CODE" ]]; then
    echo "Could not find current versionCode in app/build.gradle.kts" >&2
    exit 1
fi
NEXT_CODE=$((CURRENT_CODE + 1))

python3 - "$VERSION" "$NEXT_CODE" "$APK_URL" <<'PY'
import re
import sys
from pathlib import Path

version, code, apk_url = sys.argv[1:]

gradle = Path("app/build.gradle.kts")
text = gradle.read_text()
text, code_count = re.subn(r'versionCode = \d+', f'versionCode = {code}', text, count=1)
text, name_count = re.subn(r'versionName = "[^"]+"', f'versionName = "{version}"', text, count=1)
if code_count != 1 or name_count != 1:
    raise SystemExit("Failed to update versionCode/versionName in app/build.gradle.kts")
gradle.write_text(text)

readme = Path("README.md")
text = readme.read_text()
text, link_count = re.subn(
    r'https://github\.com/kispeterzsm/GC_Jegyzokonyv/releases/download/[^/)]+/app-debug\.apk',
    apk_url,
    text,
    count=1,
)
if link_count != 1:
    raise SystemExit("Failed to update APK download link in README.md")
readme.write_text(text)
PY

echo "==> Building debug APK for $VERSION"
./build-apk.sh debug

echo "==> Committing version bump"
git add README.md app/build.gradle.kts
git commit -m "Release ${TAG}"

echo "==> Pushing master"
git push origin master

echo "==> Creating GitHub release ${TAG}"
gh release create "$TAG" "$APK_PATH" \
    --repo kispeterzsm/GC_Jegyzokonyv \
    --title "$TAG" \
    --notes "Debug APK build for version ${VERSION}." \
    --target master

echo
echo "Release complete:"
echo "  ${APK_URL}"
