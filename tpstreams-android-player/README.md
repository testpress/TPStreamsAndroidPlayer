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

The player includes a settings icon in the top-right corner that opens a bottom sheet with options like Quality, Captions, and Playback Speed. The player handles these settings internally, so you don't need to implement any additional code to use this feature.

If you need to programmatically open the settings menu:

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

## Extending Settings Functionality

To extend or customize the settings functionality, you can subclass `TPStreamsPlayerView` and override the settings handler methods:

```kotlin
class CustomPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TPStreamsPlayerView(context, attrs, defStyleAttr) {

    override fun onQualitySelected() {
        super.onQualitySelected()
        // Your custom quality selection logic
    }

    override fun onCaptionsSelected() {
        super.onCaptionsSelected()
        // Your custom captions selection logic
    }

    override fun onPlaybackSpeedSelected() {
        super.onPlaybackSpeedSelected()
        // Your custom playback speed selection logic
    }
}
```

## Troubleshooting

### Settings Icon Not Working

If the settings icon click is not working:

1. Make sure your activity extends `FragmentActivity` or `AppCompatActivity`
2. Check that you're using the correct layout file (`exo_player_view.xml`)
3. Try using the `showSettings()` method directly on the player view

### BottomSheet Not Showing

If the bottom sheet doesn't appear:

1. Check logcat for any error messages
2. Make sure your activity's theme includes Material Design components

## Features

- DRM support
- Custom player controls
- Settings menu in top-right corner (YouTube style)
- Quality selection
- Caption/subtitle support
- Playback speed control 