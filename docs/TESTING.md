# Testing Guide

This guide details how to run unit tests, instrumented tests, and lint analysis in the **TPStreams Android Player SDK**.

---

## 1. Unit Tests

Unit tests execute on the local JVM and verify player components, listeners, models, and business logic without requiring an emulator or physical device.

### Running via Gradle CLI

```bash
# Run unit tests for the SDK library
./gradlew :tpstreams-android-player:testDebugUnitTest

# Run all unit tests across all modules
./gradlew test
```

### Running via Android Studio
1. In the Project window, navigate to `tpstreams-android-player > src > test`.
2. Right-click on the `test` directory (or a specific test file).
3. Click **Run 'Tests in 'test''** (`Ctrl+Shift+R` on macOS, `Ctrl+Shift+F10` on Windows/Linux).

---

## 2. Android Instrumentation Tests

Instrumentation tests run on a connected physical device or Android Virtual Device (AVD). They validate player rendering, DRM session lifecycle, and media decoder integration under real OS conditions.

### Running via Gradle CLI

Ensure an emulator is booted or a physical device is connected via ADB (`adb devices`), then run:

```bash
# Run instrumentation tests for the SDK library
./gradlew :tpstreams-android-player:connectedAndroidTest

# Run all instrumentation tests across all modules
./gradlew connectedAndroidTest
```

### Running via Android Studio
1. Ensure your emulator or physical device is connected and selected.
2. In the Project window, navigate to `tpstreams-android-player > src > androidTest`.
3. Right-click on the `androidTest` folder (or specific test class).
4. Click **Run 'Tests in 'androidTest''**.

---

## 3. Code Quality & Lint

To check for Android API compatibility, deprecations, and potential defects:

```bash
# Run Android Lint across all modules
./gradlew lint

# Run lint only on the SDK library
./gradlew :tpstreams-android-player:lint
```

HTML lint reports are generated under:
`tpstreams-android-player/build/reports/lint-results.html`

---

## 4. Pre-Release Sanity Checklist

Before opening a release PR or tagging a release:
- [ ] Ensure all unit tests pass: `./gradlew :tpstreams-android-player:testDebugUnitTest`
- [ ] Run Android instrumentation tests on a test device/emulator: `./gradlew :tpstreams-android-player:connectedAndroidTest`
- [ ] Verify sample app launches without errors: `./gradlew assembleDebug`
- [ ] Check lint warnings: `./gradlew lint`
