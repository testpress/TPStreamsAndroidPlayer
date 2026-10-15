# Release Guidelines & Procedure

This document details the release process, versioning rules, and GitHub Actions release automation for the **TPStreams Android Player SDK**.

---

## 1. Versioning Rules

- This project adheres to **[Semantic Versioning](https://semver.org/spec/v2.0.0.html)** (`MAJOR.MINOR.PATCH`).
- The SDK version is managed in **`gradle.properties`** via the `VERSION_NAME` property:
  ```properties
  VERSION_NAME=1.2.17
  ```

---

## 2. Step-by-Step Release Checklist

### Step 1: Prepare the Release PR
1. **Update `gradle.properties`**:
   Increment `VERSION_NAME` (e.g., from `1.2.16` to `1.2.17`).
2. **Update `CHANGELOG.md`**:
   Add a new release section following [Keep a Changelog](https://keepachangelog.com/en/1.1.0/):
   ```markdown
   ## [1.2.17] - 2026-10-15

   ### Added
   - Description of added features...

   ### Fixed
   - Description of bug fixes...
   ```
   > **Important**: The automated release script uses a regex parser matching `## [version]` in `CHANGELOG.md`. Keep this exact format so release notes are extracted accurately for the GitHub Release.
3. **Run Pre-Release Sanity Tests**:
   - Run unit tests: `./gradlew :tpstreams-android-player:testDebugUnitTest`
   - Run Android instrumentation tests on an emulator/device: `./gradlew :tpstreams-android-player:connectedAndroidTest`
4. **Update Public Docs (`streams-docs`)**:
   - If new APIs, configuration options, or features were added or changed, submit corresponding documentation updates to the **[`streams-docs`](https://github.com/testpress/streams-docs)** repository.
5. **Commit, Open PR & Merge**:
   - Commit changes, open a PR targeting `main`, ensure all CI checks pass, and merge.

---

### Step 2: Trigger the Release Workflow
1. Navigate to the GitHub repository **Actions** tab.
2. Select the **Release library** workflow (`.github/workflows/release.yml`).
3. Click **Run workflow** on the `main` branch.
4. **What `release.yml` executes**:
   - Validates `VERSION_NAME` in `gradle.properties`.
   - Verifies the Git tag does not already exist.
   - Runs unit tests (`:tpstreams-android-player:testDebugUnitTest`).
   - Builds the release AAR (`:tpstreams-android-player:bundleReleaseAar`).
   - Creates a **Sentry release** (`TPStreamsAndroidPlayer@<version>`) and associates commits.
   - Authenticates via GitHub App credentials (`RELEASE_APP_ID`) to create and push an annotated Git tag `refs/tags/<version>`.

> **Note**: For developers, the manual release steps end here. Everything downstream is fully automated.

---

## 3. Automated Downstream Pipeline

Pushing the Git tag automatically triggers the distribution pipeline:

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
   - Because `release.yml` creates the tag using a **GitHub App token** (`RELEASE_APP_TOKEN`), GitHub fires the standard `push: tags` event.
   - `publish-release.yml` receives this event.
   - It builds the test APK (`./gradlew assembleDebug -PisTestApk=true -PVERSION_NAME=<version>`).
   - It parses `CHANGELOG.md` to extract the release notes under `## [<version>]`.
   - It creates the GitHub Release, attaches the test APK, and publishes the notes.

2. **JitPack Distribution**:
   - JitPack detects the new tag and compiles the library according to [.jitpack.yml](../.jitpack.yml).
   - Downstream consumers can immediately import the new version:
     ```kotlin
     implementation("com.github.testpress:TPStreamsAndroidPlayer:<VERSION>")
     ```

---

## 4. Manual Fallback / Standalone Test APK Builds

If a GitHub Release or test APK needs to be generated without running the full release pipeline, [.github/workflows/publish-release.yml](../.github/workflows/publish-release.yml) can be triggered manually via `workflow_dispatch` with custom `ref` or `version` parameters.
