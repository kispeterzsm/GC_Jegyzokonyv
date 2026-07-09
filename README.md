# Jegyzőkönyv

Offline Android field-report builder. Pick an HTML template, take photos with the
camera, append text blocks, export to PDF, and share — all without internet,
accounts, or cloud storage.

Package: `hu.gc.jegyzokonyv` · Min SDK 26 · Kotlin + Jetpack Compose

## APK Download

Latest debug APK:
[`app-debug.apk`](https://github.com/kispeterzsm/GC_Jegyzokonyv/releases/download/v0.5.8/app-debug.apk)

Install it on a connected Android device with:

```sh
adb install -r app-debug.apk
```

## Building

This repository ships the Gradle wrapper scripts and wrapper jar. Build with:

```sh
./build-pk.sh debug                  # build APK at app/build/outputs/apk/debug/
./gradlew :app:installDebug          # install on connected device/emulator
adb shell am start -n hu.gc.jegyzokonyv/.MainActivity
```

Requirements: JDK 17, Android SDK with platform 34 installed, a device or
emulator running Android 8.0 (API 26) or newer.

## Testing

Run the local quality gate without a phone:

```sh
./build-pk.sh debug
./gradlew :app:testDebugUnitTest :app:lintDebug
```
