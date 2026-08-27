package com.tpstreams.player.ui

import androidx.media3.common.util.UnstableApi
import com.tpstreams.player.AdvancedResolutionBottomSheet
import com.tpstreams.player.CaptionsBottomSheet
import com.tpstreams.player.DownloadActionBottomSheet
import com.tpstreams.player.DownloadOptionsBottomSheet
import com.tpstreams.player.PlaybackSpeedBottomSheet
import com.tpstreams.player.PlayerSettingsBottomSheet
import com.tpstreams.player.QualityOptionsBottomSheet
import com.tpstreams.player.TPStreamsPlayer

/**
 * Interface contract for settings actions required by PlayerSheetManager.
 */
@UnstableApi
internal interface SettingsActions {
    fun showQualityOptionsBottomSheet()
    fun showPlaybackSpeedBottomSheet()
    fun showAdvancedResolutionBottomSheet()
    fun onAutoQualitySelected()
    fun onHigherQualitySelected()
    fun onDataSaverSelected()
    fun onResolutionSelected(resolution: String)
    fun onSpeedSelected(speed: Float)
    fun getCurrentQuality(): String
    fun getPlaybackSpeed(): Float
    fun isDownloadEnabled(): Boolean
}

/**
 * Interface contract for captions actions required by PlayerSheetManager.
 */
@UnstableApi
internal interface CaptionsActions {
    fun showCaptionsBottomSheet()
    fun onCaptionsDisabled()
    fun onCaptionLanguageSelected(language: String)
    fun getCurrentCaptionLanguage(): String?
    fun getCurrentCaptionStatus(): String
}

/**
 * Interface contract for download actions required by PlayerSheetManager.
 */
@UnstableApi
internal interface DownloadUiActions : DownloadOptionsBottomSheet.DownloadSelectionListener {
    fun onDownloadSelected()
    fun getCurrentDownloadStatus(): String
    fun getDownloadIcon(): Int
    fun deleteCurrentDownload()
    fun pauseCurrentDownload()
    fun resumeCurrentDownload()
}

/**
 * Manages bottom sheet dialogs, user option selections, and routing to playback controllers.
 */
@UnstableApi
internal class PlayerSheetManager(
    private val settingsActions: SettingsActions,
    private val captionsActions: CaptionsActions,
    private val downloadActions: DownloadUiActions,
    private val playerProvider: () -> TPStreamsPlayer?,
) : PlayerSettingsBottomSheet.SettingsListener,
    QualityOptionsBottomSheet.QualityOptionsListener,
    AdvancedResolutionBottomSheet.ResolutionSelectionListener,
    PlaybackSpeedBottomSheet.PlaybackSpeedListener,
    CaptionsBottomSheet.CaptionsOptionsListener {

    val settingsBottomSheet: PlayerSettingsBottomSheet by lazy {
        PlayerSettingsBottomSheet().apply {
            setSettingsListener(this@PlayerSheetManager)
        }
    }

    val qualityOptionsBottomSheet: QualityOptionsBottomSheet by lazy {
        QualityOptionsBottomSheet().apply {
            setQualityOptionsListener(this@PlayerSheetManager)
            setCurrentQuality(settingsActions.getCurrentQuality())
        }
    }

    val advancedResolutionBottomSheet: AdvancedResolutionBottomSheet by lazy {
        AdvancedResolutionBottomSheet().apply {
            setResolutionSelectionListener(this@PlayerSheetManager)
            setSelectedResolution(settingsActions.getCurrentQuality())
        }
    }

    val playbackSpeedBottomSheet: PlaybackSpeedBottomSheet by lazy {
        PlaybackSpeedBottomSheet().apply {
            setPlaybackSpeedListener(this@PlayerSheetManager)
            setCurrentSpeed(settingsActions.getPlaybackSpeed())
        }
    }

    val captionsBottomSheet: CaptionsBottomSheet by lazy {
        CaptionsBottomSheet().apply {
            setCaptionsOptionsListener(this@PlayerSheetManager)
            setCurrentLanguage(captionsActions.getCurrentCaptionLanguage())
        }
    }

    val downloadOptionsBottomSheet: DownloadOptionsBottomSheet by lazy {
        DownloadOptionsBottomSheet().apply {
            setDownloadSelectionListener(downloadActions)
        }
    }

    val downloadActionBottomSheet: DownloadActionBottomSheet by lazy {
        DownloadActionBottomSheet().apply {
            setDownloadActionListener(object : DownloadActionBottomSheet.DownloadActionListener {
                override fun onDeleteDownloadConfirmed() {
                    downloadActions.deleteCurrentDownload()
                }

                override fun onPauseDownloadConfirmed() {
                    downloadActions.pauseCurrentDownload()
                }

                override fun onResumeDownloadConfirmed() {
                    downloadActions.resumeCurrentDownload()
                }

                override fun onCancelDownloadConfirmed() {
                    downloadActions.deleteCurrentDownload()
                }
            })
        }
    }

    // ── PlayerSettingsBottomSheet.SettingsListener ──────────────────────
    override fun onQualitySelected() = settingsActions.showQualityOptionsBottomSheet()
    override fun onCaptionsSelected() = captionsActions.showCaptionsBottomSheet()
    override fun onPlaybackSpeedSelected() = settingsActions.showPlaybackSpeedBottomSheet()
    override fun onDownloadSelected() = downloadActions.onDownloadSelected()
    override fun getCurrentQuality(): String = settingsActions.getCurrentQuality()
    override fun getCurrentCaptionStatus(): String = captionsActions.getCurrentCaptionStatus()
    override fun getPlaybackSpeed(): Float = settingsActions.getPlaybackSpeed()
    override fun getCurrentDownloadStatus(): String = downloadActions.getCurrentDownloadStatus()
    override fun getDownloadIcon(): Int = downloadActions.getDownloadIcon()
    override fun isDownloadEnabled(): Boolean = settingsActions.isDownloadEnabled()

    // ── QualityOptionsBottomSheet.QualityOptionsListener ─────────────────
    override fun onAutoQualitySelected() = settingsActions.onAutoQualitySelected()
    override fun onHigherQualitySelected() = settingsActions.onHigherQualitySelected()
    override fun onDataSaverSelected() = settingsActions.onDataSaverSelected()
    override fun onAdvancedSelected() = settingsActions.showAdvancedResolutionBottomSheet()

    // ── AdvancedResolutionBottomSheet.ResolutionSelectionListener ────────
    override fun onResolutionSelected(resolution: String) = settingsActions.onResolutionSelected(resolution)

    // ── PlaybackSpeedBottomSheet.PlaybackSpeedListener ───────────────────
    override fun onSpeedSelected(speed: Float) = settingsActions.onSpeedSelected(speed)

    // ── CaptionsBottomSheet.CaptionsOptionsListener ──────────────────────
    override fun onCaptionsDisabled() = captionsActions.onCaptionsDisabled()
    override fun onCaptionLanguageSelected(language: String) = captionsActions.onCaptionLanguageSelected(language)
    override fun getCurrentCaptionLanguage(): String? = captionsActions.getCurrentCaptionLanguage()
    override fun getPlayer(): TPStreamsPlayer? = playerProvider()
}
