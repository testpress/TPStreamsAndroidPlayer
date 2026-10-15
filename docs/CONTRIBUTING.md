# Contributing to TPStreams Android Player

Welcome to the **TPStreams Android Player SDK** contributor documentation. This repository contains the Android player library built on Media3 / ExoPlayer as well as the sample integration app.

---

## Documentation Sections

To keep things focused and easy to navigate, the documentation is divided into the following guides:

| Guide | Description |
| :--- | :--- |
| 🚀 **[Setup Guide](SETUP.md)** | Prerequisites (Android Studio), project cloning, Gradle sync, and running the sample app. |
| 🧪 **[Testing Guide](TESTING.md)** | Instructions for running unit tests, Android instrumentation tests, and lint checks. |
| 📦 **[Release Guide](RELEASE.md)** | Versioning rules, pre-release checklist, triggering GitHub Actions, and JitPack distribution. |
| 🛠️ **[Troubleshooting Guide](trouble-shooting.md)** | Player error code reference and troubleshooting tips. |

---

## Quick Architecture Overview

- **`:tpstreams-android-player`**: The core Android SDK library built on Media3 / ExoPlayer. Provides video playback, Widevine DRM handling, offline downloads, telemetry/diagnostics, and player UI controls.
- **`:app`**: The sample Android application demonstrating SDK integration using Jetpack Compose.
- **`gradle/gradle-mvn-build-packages.gradle`**: Maven publication configuration for publishing to local Maven and JitPack.

---

## Documentation Updates

When introducing new APIs, configuration options, or behavior changes:
- Update the public developer documentation in the **[`streams-docs`](https://github.com/testpress/streams-docs)** repository alongside your code changes.

