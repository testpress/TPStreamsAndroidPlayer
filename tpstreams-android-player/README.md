# TPStreams Android Player

This is a custom video player for Android based on ExoPlayer.

## Usage

### Basic Setup

```kotlin
// Initialize the player
TPStreamsPlayer.init("your_organization_id")

// Create the player
val player = TPStreamsPlayer.create(
    context,
    "your_asset_id",
    "your_access_token"
)

// Set the player to a PlayerView
playerView.player = player
```

### Settings Menu

The player includes a settings icon in the top-right corner that opens a bottom sheet with options like Quality, Captions, and Playback Speed.

To implement custom behavior for these settings:

```kotlin
playerView.setSettingsListener(object : PlayerSettingsBottomSheet.SettingsListener {
    override fun onQualitySelected() {
        // Handle quality selection
        // Example: Show quality options dialog
    }

    override fun onCaptionsSelected() {
        // Handle captions selection
        // Example: Show available subtitle tracks
    }

    override fun onPlaybackSpeedSelected() {
        // Handle playback speed selection
        // Example: Show speed options (0.5x, 1x, 1.5x, 2x)
    }
})
```

You can also trigger the settings menu programmatically:

```kotlin
playerView.showSettings()
```

## Customizing the UI

If you need to adjust the position of the settings icon (for example, to account for a notch or status bar), you can modify the padding in the layout file:

```xml
<!-- In tpstreams_player_control_view.xml -->
<FrameLayout
    android:id="@+id/exo_top_bar"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_gravity="top"
    android:paddingTop="24dp"> <!-- Adjust this value as needed -->
    
    <!-- Settings icon -->
</FrameLayout>
```

## Troubleshooting

### Settings Icon Not Working

If the settings icon click is not working:

1. Make sure your activity extends `FragmentActivity` or `AppCompatActivity`
2. Check that you're using the correct layout file (`exo_player_view.xml`)
3. Try using the `showSettings()` method directly on the player view

### BottomSheet Not Showing

If the bottom sheet doesn't appear:

1. Ensure you've set a settings listener on the player view
2. Check logcat for any error messages
3. Make sure your activity's theme includes Material Design components

## Features

- DRM support
- Custom player controls
- Settings menu in top-right corner (YouTube style)
- Quality selection
- Caption/subtitle support
- Playback speed control 