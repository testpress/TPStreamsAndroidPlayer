package com.tpstreams.player.data

import org.json.JSONObject

/**
 * Data class representing parsed asset information from the TPStreams API response.
 * This class holds the essential playback information for both regular videos and live streams.
 *
 * @property mediaUrl The URL to the media content (HLS or DASH)
 * @property enableDrm Whether DRM protection is enabled for this asset
 * @property thumbnailUrl URL to the thumbnail image (empty for active live streams)
 * @property videoObj The video JSON object containing additional metadata like tracks and thumbnails (null for active live streams)
 * @property isLiveStream Whether this asset is an active live stream (not recorded)
 */
data class AssetInfo(
    val mediaUrl: String,
    val enableDrm: Boolean,
    val thumbnailUrl: String,
    val videoObj: JSONObject?,
    val isLiveStream: Boolean = false
)
