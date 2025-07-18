# TPStreams Android Player

A powerful Android SDK for seamless integration with TPStreams video platform, featuring DRM support and a modern implementation using ExoPlayer.

## Features

- 🎥 Built on ExoPlayer with full TPStreams platform integration
- 🔒 Widevine DRM support
- 🚀 Asynchronous video metadata fetching
- 📱 Modern Android development practices (Jetpack Compose, Coroutines)
- 🎮 Simple playback controls
- ⚡ Adaptive streaming (DASH) support
- 💾 Offline video downloads and management

## Requirements

- Android API level 21+ (Android 5.0 or higher)
- Kotlin 1.9.0+
- Android Studio Arctic Fox or higher

## Installation

Add the following to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.testpress:TPStreamsAndroidPlayer:1.0.0")
}
```

## Usage

1. Initialize the SDK (do this once, typically in your Application class):

```kotlin
TPStreamsSDK.init("your_organization_id")
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

3. Use with TPStreamsPlayerView:

```kotlin
// XML layout
<com.tpstreams.player.TPStreamsPlayerView
    android:id="@+id/player_view"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:layout_constraintDimensionRatio="16:9"
    app:layout_constraintTop_toTopOf="parent" />

// In your activity/fragment
binding.playerView.player = player
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

## Download Functionality

### 1. Manage Downloads

```kotlin
// Get download client instance
val downloadClient = DownloadClient.getInstance(context)

// Check download status
val isDownloaded = downloadClient.isDownloaded(assetId)
val isDownloading = downloadClient.isDownloading(assetId)
val isPaused = downloadClient.isPaused(assetId)

// Control downloads
downloadClient.pauseDownload(assetId)
downloadClient.resumeDownload(assetId)
downloadClient.removeDownload(assetId)
```

### 2. Display Download List

```kotlin
// Get all download items
val downloadItems = downloadClient.getAllDownloadItems()

// Each DownloadItem contains:
// - assetId: String - Unique identifier for the asset
// - title: String - Title of the video
// - thumbnailUrl: String - URL to the thumbnail image
// - totalBytes: Long - Total size in bytes
// - downloadedBytes: Long - Downloaded bytes so far
// - progressPercentage: Int - Download progress (0-100)
// - state: Int - Download state (COMPLETED, DOWNLOADING, PAUSED, etc.)
```

### 3. Example: Display Downloads in RecyclerView

```kotlin
class DownloadsAdapter(
    private val items: List<DownloadItem>,
    private val downloadClient: DownloadClient
) : RecyclerView.Adapter<DownloadsAdapter.ViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_download, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.titleTextView.text = item.title
        holder.progressBar.progress = item.progressPercentage
        
        // Load thumbnail
        Glide.with(holder.itemView)
            .load(item.thumbnailUrl)
            .into(holder.thumbnailImageView)
            
        // Set status text based on state
        val statusText = when (item.state) {
            DownloadState.COMPLETED -> "Downloaded"
            DownloadState.DOWNLOADING -> "${item.progressPercentage}%"
            DownloadState.PAUSED -> "Paused"
            else -> "Queued"
        }
        holder.statusTextView.text = statusText
        
        // Setup action buttons
        when (item.state) {
            DownloadState.COMPLETED -> {
                holder.actionButton.text = "Delete"
                holder.actionButton.setOnClickListener {
                    downloadClient.removeDownload(item.assetId)
                }
            }
            DownloadState.DOWNLOADING -> {
                holder.actionButton.text = "Pause"
                holder.actionButton.setOnClickListener {
                    downloadClient.pauseDownload(item.assetId)
                }
            }
            DownloadState.PAUSED -> {
                holder.actionButton.text = "Resume"
                holder.actionButton.setOnClickListener {
                    downloadClient.resumeDownload(item.assetId)
                }
            }
        }
    }
    
    override fun getItemCount() = items.size
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.title_text)
        val thumbnailImageView: ImageView = view.findViewById(R.id.thumbnail_image)
        val progressBar: ProgressBar = view.findViewById(R.id.progress_bar)
        val statusTextView: TextView = view.findViewById(R.id.status_text)
        val actionButton: Button = view.findViewById(R.id.action_button)
    }
}
```

### 4. Listen for Download Changes

```kotlin
// Add listener to update UI when downloads change
downloadClient.addListener(object : DownloadClient.Listener {
    override fun onDownloadsChanged() {
        // Refresh your UI with the latest download information
        val updatedDownloads = downloadClient.getAllDownloadItems()
        downloadsAdapter.updateItems(updatedDownloads)
    }
})

// Don't forget to remove the listener when no longer needed
override fun onDestroy() {
    super.onDestroy()
    downloadClient.removeListener(listener)
}
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
