package com.tpstreams.player.ui

import androidx.media3.common.util.UnstableApi
import com.tpstreams.player.Captions
import com.tpstreams.player.DownloadActions
import com.tpstreams.player.SettingsPanel
import com.tpstreams.player.TPStreamsPlayerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
class PlayerSheetManagerTest {

    companion object {
        private fun createMockView(): TPStreamsPlayerView {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
            val unsafe = theUnsafeField.get(null)
            val allocateMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
            return allocateMethod.invoke(unsafe, TPStreamsPlayerView::class.java) as TPStreamsPlayerView
        }
    }

    private class FakeSettingsPanel : SettingsPanel(createMockView()) {
        var showQualityCalled = false
        var showSpeedCalled = false
        var showAdvancedCalled = false
        var autoQualityCalled = false
        var higherQualityCalled = false
        var dataSaverCalled = false
        var selectedResolution: String? = null
        var selectedSpeed: Float? = null

        var qualityValue = "Auto"
        var speedValue = 1.25f
        var downloadEnabledValue = true

        override fun showQualityOptionsBottomSheet() { showQualityCalled = true }
        override fun showPlaybackSpeedBottomSheet() { showSpeedCalled = true }
        override fun showAdvancedResolutionBottomSheet() { showAdvancedCalled = true }
        override fun onAutoQualitySelected() { autoQualityCalled = true }
        override fun onHigherQualitySelected() { higherQualityCalled = true }
        override fun onDataSaverSelected() { dataSaverCalled = true }
        override fun onResolutionSelected(resolution: String) { selectedResolution = resolution }
        override fun onSpeedSelected(speed: Float) { selectedSpeed = speed }
        override fun getCurrentQuality(): String = qualityValue
        override fun getPlaybackSpeed(): Float = speedValue
        override fun isDownloadEnabled(): Boolean = downloadEnabledValue
    }

    private class FakeCaptions : Captions(createMockView()) {
        var showCaptionsCalled = false
        var captionsDisabledCalled = false
        var selectedLanguage: String? = null

        var captionLanguageValue: String? = "en"
        var captionStatusValue = "English"

        override fun showCaptionsBottomSheet() { showCaptionsCalled = true }
        override fun onCaptionsDisabled() { captionsDisabledCalled = true }
        override fun onCaptionLanguageSelected(language: String) { selectedLanguage = language }
        override fun getCurrentCaptionLanguage(): String? = captionLanguageValue
        override fun getCurrentCaptionStatus(): String = captionStatusValue
    }

    private class FakeDownloadActions : DownloadActions(createMockView()) {
        var downloadSelectedCalled = false
        var selectedDownloadResolution: String? = null

        var downloadStatusValue = "Downloading"
        var downloadIconValue = 12345

        override fun onDownloadSelected() { downloadSelectedCalled = true }
        override fun onDownloadResolutionSelected(resolution: String) { selectedDownloadResolution = resolution }
        override fun getCurrentDownloadStatus(): String = downloadStatusValue
        override fun getDownloadIcon(): Int = downloadIconValue
    }

    @Test
    fun `getPlayer returns null when provider returns null`() {
        val sheetManager = PlayerSheetManager(
            settingsPanel = FakeSettingsPanel(),
            captions = FakeCaptions(),
            downloadActions = FakeDownloadActions(),
            playerProvider = { null }
        )

        assertNull(sheetManager.getPlayer())
    }

    @Test
    fun `SettingsListener methods delegate accurately to respective controllers`() {
        val fakeSettings = FakeSettingsPanel()
        val fakeCaptions = FakeCaptions()
        val fakeDownloads = FakeDownloadActions()

        val sheetManager = PlayerSheetManager(
            settingsPanel = fakeSettings,
            captions = fakeCaptions,
            downloadActions = fakeDownloads,
            playerProvider = { null }
        )

        sheetManager.onQualitySelected()
        assertTrue(fakeSettings.showQualityCalled)

        sheetManager.onCaptionsSelected()
        assertTrue(fakeCaptions.showCaptionsCalled)

        sheetManager.onPlaybackSpeedSelected()
        assertTrue(fakeSettings.showSpeedCalled)

        sheetManager.onDownloadSelected()
        assertTrue(fakeDownloads.downloadSelectedCalled)

        assertEquals("Auto", sheetManager.getCurrentQuality())
        assertEquals("English", sheetManager.getCurrentCaptionStatus())
        assertEquals(1.25f, sheetManager.getPlaybackSpeed())
        assertEquals("Downloading", sheetManager.getCurrentDownloadStatus())
        assertEquals(12345, sheetManager.getDownloadIcon())
        assertTrue(sheetManager.isDownloadEnabled())
    }

    @Test
    fun `Quality and Resolution listener methods delegate accurately to settingsPanel`() {
        val fakeSettings = FakeSettingsPanel()
        val sheetManager = PlayerSheetManager(
            settingsPanel = fakeSettings,
            captions = FakeCaptions(),
            downloadActions = FakeDownloadActions(),
            playerProvider = { null }
        )

        sheetManager.onAutoQualitySelected()
        assertTrue(fakeSettings.autoQualityCalled)

        sheetManager.onHigherQualitySelected()
        assertTrue(fakeSettings.higherQualityCalled)

        sheetManager.onDataSaverSelected()
        assertTrue(fakeSettings.dataSaverCalled)

        sheetManager.onAdvancedSelected()
        assertTrue(fakeSettings.showAdvancedCalled)

        sheetManager.onResolutionSelected("1080p")
        assertEquals("1080p", fakeSettings.selectedResolution)

        sheetManager.onSpeedSelected(2.0f)
        assertEquals(2.0f, fakeSettings.selectedSpeed)
    }

    @Test
    fun `CaptionsOptionsListener methods delegate accurately to captions`() {
        val fakeCaptions = FakeCaptions()
        val sheetManager = PlayerSheetManager(
            settingsPanel = FakeSettingsPanel(),
            captions = fakeCaptions,
            downloadActions = FakeDownloadActions(),
            playerProvider = { null }
        )

        sheetManager.onCaptionsDisabled()
        assertTrue(fakeCaptions.captionsDisabledCalled)

        sheetManager.onCaptionLanguageSelected("es")
        assertEquals("es", fakeCaptions.selectedLanguage)

        assertEquals("en", sheetManager.getCurrentCaptionLanguage())
    }
}
