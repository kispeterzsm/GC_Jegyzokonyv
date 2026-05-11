# Jegyzőkönyv

Offline Android field-report builder. Pick an HTML template, take photos with the
camera, append text blocks, export to PDF, and share — all without internet,
accounts, or cloud storage.

Package: `hu.gc.jegyzokonyv` · Min SDK 26 · Kotlin + Jetpack Compose

See [`PLAN.md`](./PLAN.md) for the full product vision.

## Building

This repository ships the Gradle wrapper scripts (`gradlew`, `gradlew.bat`) but
**not** the wrapper jar binary. On first checkout, generate it once with a
locally installed Gradle (any 8.x will do):

```sh
gradle wrapper --gradle-version 8.9
```

Then:

```sh
./gradlew :app:assembleDebug         # build APK at app/build/outputs/apk/debug/
./gradlew :app:installDebug          # install on connected device/emulator
adb shell am start -n hu.gc.jegyzokonyv/.MainActivity
```

Requirements: JDK 17, Android SDK with platform 34 installed, a device or
emulator running Android 8.0 (API 26) or newer.

## Status

Phase 1 MVP. See `PLAN.md` for the roadmap. Currently implemented:

- Single bundled "Helyszíni szemle" inspection template
- Create draft from template
- Take photo (CameraX), append photo block with caption
- Append freestanding text blocks
- Live HTML preview (WebView)
- Autosaved HTML on disk (source of truth)
- Export PDF via WebView print adapter
- Share PDF via Android share sheet
- Draft list with delete

Not yet implemented (later phases): DOCX export, template editor, block
reorder/edit, image downscaling, encryption.
