package com.tpstreams.player.ui

import androidx.media3.common.util.UnstableApi
import com.tpstreams.player.AdvancedResolutionBottomSheet
import com.tpstreams.player.Captions
import com.tpstreams.player.CaptionsBottomSheet
import com.tpstreams.player.DownloadActionBottomSheet
import com.tpstreams.player.DownloadActions
import com.tpstreams.player.DownloadOptionsBottomSheet
import com.tpstreams.player.PlaybackSpeedBottomSheet
import com.tpstreams.player.PlayerSettingsBottomSheet
import com.tpstreams.player.QualityOptionsBottomSheet
import com.tpstreams.player.SettingsPanel
import com.tpstreams.player.TPStreamsPlayer

/**
 * Manages bottom sheet dialogs, user option selections, and routing to playback controllers.
 */
@UnstableApi
internal class PlayerSheetManager(
    private val settingsPanel: SettingsPanel,
    private val captions: Captions,
    private val downloadActions: DownloadActions,
    private val playerProvider: () -> TPStreamsPlayer?,
) : PlayerSettingsBottomSheet.SettingsListener,
    QualityOptionsBottomSheet.QualityOptionsListener,
    AdvancedResolutionBottomSheet.ResolutionSelectionListener,
    PlaybackSpeedBottomSheet.PlaybackSpeedListener,
    CaptionsBottomSheet.CaptionsOptionsListener,
    DownloadOptionsBottomSheet.DownloadSelectionListener {

    val settingsBottomSheet: PlayerSettingsBottomSheet by lazy {
        PlayerSettingsBottomSheet().apply {
            setSettingsListener(this@PlayerSheetManager)
        }
    }

    val qualityOptionsBottomSheet: QualityOptionsBottomSheet by lazy {
        QualityOptionsBottomSheet().apply {
            setQualityOptionsListener(this@PlayerSheetManager)
            setCurrentQuality(settingsPanel.getCurrentQuality())
        }
    }

    val advancedResolutionBottomSheet: AdvancedResolutionBottomSheet by lazy {
        AdvancedResolutionBottomSheet().apply {
            setResolutionSelectionListener(this@PlayerSheetManager)
            setSelectedResolution(settingsPanel.getCurrentQuality())
        }
    }

    val playbackSpeedBottomSheet: PlaybackSpeedBottomSheet by lazy {
        PlaybackSpeedBottomSheet().apply {
            setPlaybackSpeedListener(this@PlayerSheetManager)
            setCurrentSpeed(settingsPanel.getPlaybackSpeed())
        }
    }

    val captionsBottomSheet: CaptionsBottomSheet by lazy {
        CaptionsBottomSheet().apply {
            setCaptionsOptionsListener(this@PlayerSheetManager)
            setCurrentLanguage(captions.getCurrentCaptionLanguage())
        }
    }

    val downloadOptionsBottomSheet: DownloadOptionsBottomSheet by lazy {
        DownloadOptionsBottomSheet().apply {
            setDownloadSelectionListener(this@PlayerSheetManager)
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
    override fun onQualitySelected() = settingsPanel.showQualityOptionsBottomSheet()
    override fun onCaptionsSelected() = captions.showCaptionsBottomSheet()
    override fun onPlaybackSpeedSelected() = settingsPanel.showPlaybackSpeedBottomSheet()
    override fun onDownloadSelected() = downloadActions.onDownloadSelected()
    override fun getCurrentQuality(): String = settingsPanel.getCurrentQuality()
    override fun getCurrentCaptionStatus(): String = captions.getCurrentCaptionStatus()
    override fun getPlaybackSpeed(): Float = settingsPanel.getPlaybackSpeed()
    override fun getCurrentDownloadStatus(): String = downloadActions.getCurrentDownloadStatus()
    override fun getDownloadIcon(): Int = downloadActions.getDownloadIcon()
    override fun isDownloadEnabled(): Boolean = settingsPanel.isDownloadEnabled()

    // ── QualityOptionsBottomSheet.QualityOptionsListener ─────────────────
    override fun onAutoQualitySelected() = settingsPanel.onAutoQualitySelected()
    override fun onHigherQualitySelected() = settingsPanel.onHigherQualitySelected()
    override fun onDataSaverSelected() = settingsPanel.onDataSaverSelected()
    override fun onAdvancedSelected() = settingsPanel.showAdvancedResolutionBottomSheet()

    // ── AdvancedResolutionBottomSheet.ResolutionSelectionListener ────────
    override fun onResolutionSelected(resolution: String) = settingsPanel.onResolutionSelected(resolution)

    // ── PlaybackSpeedBottomSheet.PlaybackSpeedListener ───────────────────
    override fun onSpeedSelected(speed: Float) = settingsPanel.onSpeedSelected(speed)

    // ── CaptionsBottomSheet.CaptionsOptionsListener ──────────────────────
    override fun onCaptionsDisabled() = captions.onCaptionsDisabled()
    override fun onCaptionLanguageSelected(language: String) = captions.onCaptionLanguageSelected(language)
    override fun getCurrentCaptionLanguage(): String? = captions.getCurrentCaptionLanguage()
    override fun getPlayer(): TPStreamsPlayer? = playerProvider()

    // ── DownloadOptionsBottomSheet.DownloadSelectionListener ─────────────
    override fun onDownloadResolutionSelected(resolution: String) = downloadActions.onDownloadResolutionSelected(resolution)
}
