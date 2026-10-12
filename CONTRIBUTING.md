# Contributing & Release Guidelines

This guide covers local environment setup, architecture, development practices, and the release process for the **TPStreams Android Player SDK** team.

---

## 1. Architecture Overview

This repository is organized as a multi-module Gradle project:

- **`:tpstreams-android-player`**: The core Android SDK library built on Media3 / ExoPlayer. Provides video playback, Widevine DRM handling, offline downloads, telemetry/diagnostics, and player UI controls.
- **`:app`**: The sample Android application demonstrating SDK integration using Jetpack Compose.
- **`gradle/gradle-mvn-build-packages.gradle`**: Maven publication configuration for publishing to local Maven and JitPack.

---

## 2. Local Setup & Environment

### Prerequisites
- **Java Development Kit (JDK)**: **JDK 17** (Temurin distribution recommended; aligned with CI and JitPack).
- **Android Studio**: Arctic Fox or newer (Giraffe/Hedgehog+ recommended).
- **Android SDK**:
  - `compileSdk`: **35**
  - `minSdk`: **21**
  - `targetSdk`: **35**
- **Kotlin**: 1.9.0+

### Setup Steps
1. Clone the repository:
   ```bash
   git clone git@github.com:testpress/TPStreamsAndroidPlayer.git
   cd TPStreamsAndroidPlayer
   ```
2. Open the project in **Android Studio**.
3. Ensure Android Studio is using **JDK 17**:
   - `Settings / Preferences` > `Build, Execution, Deployment` > `Build Tools` > `Gradle` > `Gradle JDK` -> Select **JDK 17**.
4. Sync Gradle files.

---

## 3. Useful Gradle Commands

All common tasks can be run from the root directory using the Gradle wrapper (`./gradlew`):

| Task | Command | Description |
| :--- | :--- | :--- |
| **Run Unit Tests** | `./gradlew :tpstreams-android-player:testDebugUnitTest` | Runs all library unit tests. |
| **Run All Tests** | `./gradlew test` | Runs unit tests across all modules. |
| **Lint Checks** | `./gradlew lint` | Runs Android Lint analysis across modules. |
| **Build Sample App** | `./gradlew assembleDebug` | Builds the debug APK of the sample application. |
| **Build Library AAR** | `./gradlew :tpstreams-android-player:bundleReleaseAar` | Builds the release `.aar` bundle for the SDK. |
| **Publish to Maven Local** | `./gradlew :tpstreams-android-player:publishReleasePublicationToMavenLocal` | Publishes the library to `~/.m2/repository` for testing against local apps. |
| **Clean Build & Publish** | `./gradlew cleanBuildPublish` | Cleans, builds, and publishes to Maven Local. |

---

## 4. Release Guidelines & Procedure

Releases are published through an automated GitHub Actions pipeline.

### Versioning Rules
- This project adheres to **[Semantic Versioning](https://semver.org/spec/v2.0.0.html)** (`MAJOR.MINOR.PATCH`).
- Version is tracked in **`gradle.properties`** via the `VERSION_NAME` property.

---

### Step-by-Step Release Checklist

#### Step 1: Update Version & Changelog (on a release PR)
1. **Update `gradle.properties`**:
   Increment `VERSION_NAME` (e.g., change `VERSION_NAME=1.2.16` to `VERSION_NAME=1.2.17`).
2. **Update `CHANGELOG.md`**:
   Add a new section for the release adhering to [Keep a Changelog](https://keepachangelog.com/en/1.1.0/):
   ```markdown
   ## [1.2.17] - 2026-10-12

   ### Added
   - Description of added features...

   ### Fixed
   - Description of bug fixes...
   ```
   > **Important**: The automated release script uses a regex parser on `CHANGELOG.md` matching `## [version]`. Keeping this exact formatting ensures release notes are automatically extracted for the GitHub release.
3. **Commit and Merge**:
   Open a PR, ensure all CI checks pass, and merge into `main`.

---

#### Step 2: Trigger the Release Workflow
1. Go to the GitHub repository **Actions** tab.
2. Select the **Release library** workflow (`.github/workflows/release.yml`).
3. Click **Run workflow** on the `main` branch.
4. **What `release.yml` does**:
   - Validates `VERSION_NAME` in `gradle.properties`.
   - Ensures the Git tag does not already exist.
   - Runs unit tests (`:tpstreams-android-player:testDebugUnitTest`).
   - Builds release AAR (`:tpstreams-android-player:bundleReleaseAar`).
   - Creates a **Sentry release** (`TPStreamsAndroidPlayer@<version>`) and associates commits.
   - Authenticates using GitHub App credentials (`RELEASE_APP_ID`) to create and push an annotated Git tag `refs/tags/<version>`.

> **Note**: For developers, the manual release process ends here. Everything below runs automatically.

---

### What Happens Automatically After `release.yml` Tags the Commit

`release.yml` does **not** call `publish-release.yml` directly. Instead, pushing the Git tag triggers the downstream pipeline:

```
[Developer triggers release.yml on main]
              │
              ▼
   (Runs tests & builds AAR)
              │
              ▼
    [Pushes Git Tag X.Y.Z] (via GitHub App token)
              │
              ├─────────────────────────────────────────┐
              ▼                                         ▼
[Git tag push triggers publish-release.yml]    [JitPack builds library]
  • Compiles test APK                            • Distributes AAR via
  • Extracts release notes from CHANGELOG.md       com.github.testpress
  • Creates GitHub Release & attaches APK
```

1. **Triggering `publish-release.yml` (`on: push: tags: ['[0-9]*']`)**:
   - Because `release.yml` pushes the tag using a **GitHub App token** (`RELEASE_APP_TOKEN`) rather than the default `GITHUB_TOKEN`, GitHub fires the standard `push: tags` event.
   - `publish-release.yml` receives this tag push event.
   - It builds the test APK (`./gradlew assembleDebug -PisTestApk=true -PVERSION_NAME=<version>`).
   - It parses `CHANGELOG.md` to extract the notes under `## [<version>]`.
   - It creates the GitHub Release, attaches the test APK, and publishes the notes.

2. **JitPack Distribution**:
   - JitPack detects the new Git tag and compiles the library according to [.jitpack.yml](.jitpack.yml).
   - Clients can then immediately consume the new version:
     ```kotlin
     implementation("com.github.testpress:TPStreamsAndroidPlayer:<VERSION>")
     ```

---

### Manual Fallback / Standalone Test APK Builds
If a GitHub Release or test APK needs to be generated without running the full release pipeline, [.github/workflows/publish-release.yml](.github/workflows/publish-release.yml) can be triggered manually via `workflow_dispatch` with custom `ref` or `version` inputs.

