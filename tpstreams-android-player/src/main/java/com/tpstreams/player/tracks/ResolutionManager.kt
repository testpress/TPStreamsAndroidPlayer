package com.tpstreams.player.tracks

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

/**
 * Manages video resolution constraints, quality settings, and bitrate queries for a player instance.
 */
@OptIn(UnstableApi::class)
internal class ResolutionManager(
    private val exoPlayer: ExoPlayer,
    private val trackSelector: DefaultTrackSelector,
) {

    private var maxAllowedResolution: Int = Int.MAX_VALUE
    private var userPreferredResolution: Int = Int.MAX_VALUE

    fun getAvailableVideoResolutions(): List<Int> {
        val resolutions = mutableSetOf<Int>()

        val mappedTrackInfo = trackSelector.currentMappedTrackInfo ?: return emptyList()
        for (rendererIndex in 0 until mappedTrackInfo.rendererCount) {
            if (mappedTrackInfo.getRendererType(rendererIndex) != C.TRACK_TYPE_VIDEO) continue
            val trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex)
            for (groupIndex in 0 until trackGroups.length) {
                val group = trackGroups.get(groupIndex)
                for (trackIndex in 0 until group.length) {
                    val format = group.getFormat(trackIndex)
                    if (format.height != Format.NO_VALUE && format.height <= maxAllowedResolution) {
                        resolutions.add(format.height)
                    }
                }
            }
        }

        return resolutions.sortedDescending()
    }

    fun getResolutionBitrates(): Map<String, Int> {
        val mappedTrackInfo = trackSelector.currentMappedTrackInfo ?: return emptyMap()
        val resolutionBitrateMap = mutableMapOf<Int, Int>()

        for (rendererIndex in 0 until mappedTrackInfo.rendererCount) {
            if (mappedTrackInfo.getRendererType(rendererIndex) != C.TRACK_TYPE_VIDEO) continue
            val trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex)
            for (groupIndex in 0 until trackGroups.length) {
                val group = trackGroups.get(groupIndex)
                for (trackIndex in 0 until group.length) {
                    val format = group.getFormat(trackIndex)
                    if (format.height != Format.NO_VALUE &&
                        format.bitrate != Format.NO_VALUE &&
                        format.height <= maxAllowedResolution
                    ) {
                        resolutionBitrateMap[format.height] = format.bitrate
                    }
                }
            }
        }

        val result = resolutionBitrateMap.mapKeys { (height, _) -> "${height}p" }
        Log.d(TAG, "Resolution-bitrate map: $result")
        return result
    }

    fun setMaxResolution(height: Int) {
        Log.d(TAG, "Setting hard max video height to $height")
        maxAllowedResolution = height
        applyConstraints()
    }

    fun setUserResolutionPreference(height: Int) {
        Log.d(TAG, "User preferred max video height set to $height")
        userPreferredResolution = height
        applyConstraints()
    }

    private fun applyConstraints() {
        val effectiveMax = minOf(maxAllowedResolution, userPreferredResolution)
        val parametersBuilder = trackSelector.buildUponParameters()

        if (effectiveMax == Int.MAX_VALUE) {
            parametersBuilder.clearVideoSizeConstraints()
        } else {
            parametersBuilder.setMaxVideoSize(Int.MAX_VALUE, effectiveMax)
        }

        trackSelector.parameters = parametersBuilder.build()
    }

    private companion object {
        private const val TAG = "ResolutionManager"
    }
}
