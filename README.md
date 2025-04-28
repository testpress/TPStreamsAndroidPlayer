# TPStreams Android Player

A powerful Android SDK for seamless integration with TPStreams video platform, featuring DRM support and a modern implementation using ExoPlayer.

## Features

- 🎥 Built on ExoPlayer with full TPStreams platform integration
- 🔒 Widevine DRM support
- 🚀 Asynchronous video metadata fetching
- 📱 Modern Android development practices (Jetpack Compose, Coroutines)
- 🎮 Simple playback controls
- ⚡ Adaptive streaming (DASH) support

## Requirements

- Android API level 21+ (Android 5.0 or higher)
- Kotlin 1.9.0+
- Android Studio Arctic Fox or higher

## Installation

Add the following to your root `build.gradle.kts`:

```kotlin
allprojects {
    repositories {
        // ... other repositories
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.testpress:tpstreams-android-player:1.0.0")
}
```

## Usage

1. Initialize the SDK (do this once, typically in your Application class):

```kotlin
TPStreamsPlayer.init("your_organization_id")
```

2. Create a player instance:

```kotlin
val player = TPStreamsPlayer.create(
    context = context,
    assetId = "your_asset_id",
    accessToken = "your_access_token",
    shouldAutoPlay = false // Optional, defaults to true
)
```

3. Use with Jetpack Compose:

```kotlin
AndroidView(
    modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16 / 9f),
    factory = { context ->
        PlayerView(context).apply {
            this.player = player
            useController = true
        }
    }
)
```

4. Basic playback control:

```kotlin
// Start playback
player.play()

// Pause playback
player.pause()

// Release resources when done
player.release()
```

## Example App

Check out the included example app in the `app` module for a complete implementation example using Jetpack Compose.

## License

```
Copyright (c) 2025 Testpress

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
