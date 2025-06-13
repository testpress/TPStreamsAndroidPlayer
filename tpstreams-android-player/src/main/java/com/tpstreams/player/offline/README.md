# TPStreams Download Functionality

This package provides offline playback functionality for the TPStreams Player library.

## Features

- Download videos for offline playback
- Quality selection for downloads
- Download progress tracking
- Manage and play downloaded videos

## Usage

### Initialize the Download Manager

Initialize the download manager in your application's `onCreate()` method:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TPStreamsPlayer.init("your-organization-id", applicationContext)
        // The download manager is initialized automatically when TPStreamsPlayer.init is called
    }
}
```

### Using the Built-in Download UI

The SDK provides a built-in UI for downloading and managing videos:

```kotlin
// The download option is automatically available in the player settings bottom sheet
// when using TPStreamsPlayerView
```

### Integrating Downloads into Your App

If you want to integrate download functionality directly into your app's UI, you can use the `DownloadUtils` class:

#### Check if a Video is Downloaded

```kotlin
val isDownloaded = DownloadUtils.isDownloaded(context, contentId)
```

#### Start a Download

```kotlin
// Start a download with quality selection dialog
DownloadUtils.startDownload(context, videoUrl, contentId)

// Start a download with a specific quality
DownloadUtils.startDownload(context, videoUrl, contentId, quality = "720p")
```

#### Get Download Progress

```kotlin
val progress = DownloadUtils.getDownloadPercentage(context, contentId)
```

#### Delete a Download

```kotlin
DownloadUtils.deleteDownload(context, contentId)
```

#### Get All Downloads

```kotlin
// Get raw download objects
val downloads = DownloadUtils.getDownloads(context)

// Get simplified download items
val downloadItems = DownloadUtils.getDownloadItems(context)
```

#### Show the Download List Activity

```kotlin
// To programmatically show the built-in download list activity
DownloadUtils.showDownloadListActivity(context)
```

### Playing Downloaded Videos

To play a downloaded video:

```kotlin
// Check if the video is downloaded first
if (DownloadUtils.isDownloaded(context, contentId)) {
    // Create player
    val player = TPStreamsPlayer.create(context, contentId, "", true)
    
    // Play the downloaded content
    DownloadUtils.playOfflineContent(player, contentId)
    
    // Or with a player view
    playerView.player = player
    DownloadUtils.playOfflineContent(playerView, contentId)
}
```

## Managing Video Metadata

When implementing your own download list UI, you'll likely want to display more information about each video than just the content ID. Here's how to manage video metadata:

### Storing Metadata with Downloads

```kotlin
// Create a metadata class for your videos
data class VideoMetadata(
    val contentId: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String,
    val duration: Long
)

// Store metadata when starting a download
val metadata = VideoMetadata(
    contentId = "video123",
    title = "My Video",
    description = "This is a great video",
    thumbnailUrl = "https://example.com/thumbnail.jpg",
    duration = 120000 // in milliseconds
)

// Save to SharedPreferences or a database
val gson = Gson()
val json = gson.toJson(metadata)
sharedPreferences.edit().putString("metadata_$contentId", json).apply()

// Retrieve metadata when displaying downloads
val json = sharedPreferences.getString("metadata_$contentId", null)
val metadata = gson.fromJson(json, VideoMetadata::class.java)
```

## Notes

- Downloaded videos are stored in the app's internal storage and are not accessible to other apps
- Downloads are automatically removed when the app is uninstalled
- The download functionality requires the `INTERNET` permission, which is already included in the SDK
- For large video files, consider showing a notification to the user when downloads complete
- You may want to implement download restrictions based on network type (e.g., only download on Wi-Fi)
- Remember to clean up metadata when a download is deleted 