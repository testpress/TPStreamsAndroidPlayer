# Local Setup Guide

This guide covers local environment setup and getting started with the **TPStreams Android Player SDK**.

---

## 1. Prerequisites

- **[Android Studio](https://developer.android.com/studio)** (Giraffe / Hedgehog or newer recommended).

> **Note**: Android Studio bundles everything needed—including the **JDK** (Embedded JBR 17), the **Android SDK**, and build tools. You do not need to install Java or the Android SDK separately.

---

## 2. Setup Steps

1. **Clone the repository**:
   ```bash
   git clone git@github.com:testpress/TPStreamsAndroidPlayer.git
   cd TPStreamsAndroidPlayer
   ```

2. **Open in Android Studio**:
   - Launch **Android Studio**.
   - Select **Open** and select the `TPStreamsAndroidPlayer` folder.

3. **Sync Gradle**:
   - Android Studio will automatically resolve dependencies and sync the project.
   - If prompted or needed, click **File > Sync Project with Gradle Files**.

4. **Run the Sample App**:
   - In the run configurations dropdown at the top, select **`:app`**.
   - Select a running emulator or physical device.
   - Click the green play button (**Run ▶**) to compile and launch the sample application.

---

## 3. Project Architecture

This repository is structured as a multi-module Gradle project:

- **`:tpstreams-android-player`**: The core Android SDK library built on Media3 / ExoPlayer. Provides video playback, Widevine DRM handling, offline downloads, telemetry/diagnostics, and custom player UI controls.
- **`:app`**: The sample Android application demonstrating SDK integration using Jetpack Compose.
- **`gradle/gradle-mvn-build-packages.gradle`**: Maven publication configuration for publishing to local Maven and JitPack.
