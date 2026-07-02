package com.tpstreams.player

import androidx.media3.common.util.UnstableApi

@UnstableApi
class SettingsPanel(private val view: TPStreamsPlayerView) {
    private var currentQuality: String = QualityOptionsBottomSheet.QUALITY_AUTO
    private var availableResolutions: List<String> = emptyList()
    private var currentPlaybackSpeed: Float = 1.0f
    private var pendingResolutionHeight: Int? = null

    fun showSettings() {
        val activity = view.getActivity()
        if (activity != null && !view.settingsBottomSheet.isAdded) {
            view.settingsBottomSheet.show(activity.supportFragmentManager)
        }
    }
    
    fun updateAvailableResolutions() {
        val tpsPlayer = view.getPlayer() ?: return
        val availableHeights = tpsPlayer.getAvailableVideoResolutions()
        val resolutionStrings = availableHeights.map { "${it}p" }
        setAvailableResolutions(resolutionStrings)

        // If the user's selected resolution is no longer available
        // (e.g., maxAllowedResolution was lowered via setMaxResolution),
        // fall back to Auto so the UI doesn't show a stale label.
        if (currentQuality.matches(Regex("\\d+p")) && !resolutionStrings.contains(currentQuality)) {
            setCurrentQuality(QualityOptionsBottomSheet.QUALITY_AUTO)
        }
    }
    
    fun setAvailableResolutions(resolutions: List<String>) {
        this.availableResolutions = resolutions
        view.advancedResolutionBottomSheet.setAvailableResolutions(resolutions)
    }
    
    fun setCurrentQuality(quality: String) {
        this.currentQuality = quality
        view.qualityOptionsBottomSheet.setCurrentQuality(quality)
        view.advancedResolutionBottomSheet.setSelectedResolution(quality)
    }
    
    fun setPlaybackSpeed(speed: Float) {
        this.currentPlaybackSpeed = speed
        view.playbackSpeedBottomSheet.setCurrentSpeed(speed)
        view.getPlayer()?.setPlaybackSpeed(speed)
    }
    
    fun getPlaybackSpeed(): Float {
        // Get the actual current speed from the player
        val player = view.getPlayer()
        return if (player != null) {
            player.playbackParameters.speed
        } else {
            currentPlaybackSpeed
        }
    }
    
    fun getCurrentQuality(): String {
        return currentQuality
    }

    fun onAutoQualitySelected() {
        setCurrentQuality(QualityOptionsBottomSheet.QUALITY_AUTO)
        pendingResolutionHeight = null
        view.getPlayer()?.setUserResolutionPreference(Int.MAX_VALUE)
    }
    
    fun onHigherQualitySelected() {
        setCurrentQuality(QualityOptionsBottomSheet.QUALITY_HIGHER)
        pendingResolutionHeight = null
        
        // Get the highest available resolution
        val highestResolution = availableResolutions.firstOrNull()?.dropLast(1)?.toIntOrNull()
        if (highestResolution != null) {
            view.getPlayer()?.setUserResolutionPreference(highestResolution)
        } else {
            onAutoQualitySelected()
        }
    }
    
    fun onDataSaverSelected() {
        setCurrentQuality(QualityOptionsBottomSheet.QUALITY_DATA_SAVER)
        pendingResolutionHeight = null
        
        // Get the lowest available resolution
        val lowestResolution = availableResolutions.lastOrNull()?.dropLast(1)?.toIntOrNull()
        if (lowestResolution != null) {
            view.getPlayer()?.setUserResolutionPreference(lowestResolution)
        } else {
            onAutoQualitySelected()
        }
    }

    fun showQualityOptionsBottomSheet() {
        val activity = view.getActivity() ?: return
        view.qualityOptionsBottomSheet.show(activity.supportFragmentManager)
    }

    fun showAdvancedResolutionBottomSheet() {
        val activity = view.getActivity() ?: return
        view.advancedResolutionBottomSheet.show(activity.supportFragmentManager)
    }

    fun showPlaybackSpeedBottomSheet() {
        val activity = view.getActivity() ?: return
        view.playbackSpeedBottomSheet.show(activity.supportFragmentManager)
    }

    fun setPreferredResolutionHeight(height: Int) {
        setCurrentQuality("${height}p")
        pendingResolutionHeight = height
        view.getPlayer()?.setUserResolutionPreference(height)
    }

    fun onResolutionSelected(resolution: String) {
        val height = resolution.removeSuffix("p").toIntOrNull() ?: return
        setPreferredResolutionHeight(height)
    }

    fun applyPendingResolutionPreference() {
        pendingResolutionHeight?.let { height ->
            val player = view.getPlayer()
            if (player != null) {
                player.setUserResolutionPreference(height)
                pendingResolutionHeight = null
            }
        }
    }
    
    fun onSpeedSelected(speed: Float) {
        setPlaybackSpeed(speed)
    }

    fun isDownloadEnabled(): Boolean {
        val player = view.getPlayer()
        return player?.enableDownload == true && player?.isLiveStream == false ?: false
    }
} 