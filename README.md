# Jegyzőkönyv

Offline Android field-report builder. Pick an HTML template, take photos with the
camera, append text blocks, export to PDF, and share — all without internet,
accounts, or cloud storage.

Package: `hu.gc.jegyzokonyv` · Min SDK 26 · Kotlin + Jetpack Compose

## APK Download

Latest debug APK:
[`app-debug.apk`](https://github.com/kispeterzsm/GC_Jegyzokonyv/releases/download/v0.1.1-debug/app-debug.apk)

Install it on a connected Android device with:

```sh
adb install -r app-debug.apk
```

## Building

This repository ships the Gradle wrapper scripts (`gradlew`, `gradlew.bat`) but
**not** the wrapper jar binary. On first checkout, generate it once with a
locally installed Gradle (any 8.x will do):

```sh
gradle wrapper --gradle-version 8.9
```

Then:

```sh
./build-apk.sh debug                 # build APK at app/build/outputs/apk/debug/
./gradlew :app:installDebug          # install on connected device/emulator
adb shell am start -n hu.gc.jegyzokonyv/.MainActivity
```

Requirements: JDK 17, Android SDK with platform 34 installed, a device or
emulator running Android 8.0 (API 26) or newer.
