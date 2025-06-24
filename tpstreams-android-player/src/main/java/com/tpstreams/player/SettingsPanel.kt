package com.tpstreams.player

class SettingsPanel(private val view: TPStreamsPlayerView) {
    private var currentQuality: String = QualityOptionsBottomSheet.QUALITY_AUTO
    private var availableResolutions: List<String> = emptyList()
    private var currentPlaybackSpeed: Float = 1.0f

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
    
        // Let the trackSelector handle it automatically (no constraints)
        val player = view.getPlayer()
        val params = player?.getTrackSelector()?.buildUponParameters()
            ?.clearVideoSizeConstraints()
            ?.build()
        if (params != null) player.getTrackSelector().parameters = params
    }
    
    fun onHigherQualitySelected() {
        setCurrentQuality(QualityOptionsBottomSheet.QUALITY_HIGHER)
        
        // Get the highest available resolution
        val highestResolution = availableResolutions.firstOrNull()?.dropLast(1)?.toIntOrNull()
        if (highestResolution != null) {
            view.getPlayer()?.setVideoResolution(highestResolution)
        } else {
            onAutoQualitySelected()
        }
    }
    
    fun onDataSaverSelected() {
        setCurrentQuality(QualityOptionsBottomSheet.QUALITY_DATA_SAVER)
        
        // Get the lowest available resolution
        val lowestResolution = availableResolutions.lastOrNull()?.dropLast(1)?.toIntOrNull()
        if (lowestResolution != null) {
            view.getPlayer()?.setVideoResolution(lowestResolution)
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

    fun onResolutionSelected(resolution: String) {
        setCurrentQuality(resolution)
    
        val height = resolution.dropLast(1).toIntOrNull()
        if (height != null) {
            view.getPlayer()?.setVideoResolution(height)
        }
    }
    
    fun onSpeedSelected(speed: Float) {
        setPlaybackSpeed(speed)
    }

    fun isDownloadVisible(): Boolean {
        val player = view.getPlayer()
        return player?.isDownloadEnabled() ?: false
    }
} 