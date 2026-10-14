package com.tpstreams.player.util

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import com.tpstreams.player.TPStreamsSDK
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic

class WidevineDrmSessionManagerProviderTest {

    private val key1 = "7N9UuCkpKS9d84VAvMRFLos4AGCcS1aFsOjFKusmDsc="
    private val key2 = "KEY+p85TS9yuIVS719srZJ0CBzCi4MCN"

    private lateinit var provider: WidevineDrmSessionManagerProvider
    private lateinit var mediaItem: MediaItem
    private lateinit var uriMock: MockedStatic<Uri>

    @Before
    fun setUp() {
        val mockUri = mock(Uri::class.java)
        uriMock = mockStatic(Uri::class.java)
        uriMock.`when`<Uri> { Uri.parse(anyString()) }.thenReturn(mockUri)

        val mockDataSourceFactory = mock(DataSource.Factory::class.java)
        provider = WidevineDrmSessionManagerProvider(mockDataSourceFactory)

        val drmConfig = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
            .setLicenseUri("https://app.tpstreams.com/api/v1/test_org/assets/test_asset/drm_license/")
            .build()

        mediaItem = MediaItem.Builder()
            .setUri("https://example.com/video.mpd")
            .setDrmConfiguration(drmConfig)
            .build()
    }

    @After
    fun tearDown() {
        uriMock.close()
    }

    @Test
    fun `test cache is reused when encryption key and configuration remain same`() {
        TPStreamsSDK.init("test_org", TPStreamsSDK.Provider.TPStreams, licensePayloadEncryptionKey = key1)

        val manager1 = provider.get(mediaItem)
        val manager2 = provider.get(mediaItem)

        assertSame("DrmSessionManager should be cached when configuration is unchanged", manager1, manager2)
    }

    @Test
    fun `test cache is invalidated when encryption key changes`() {
        TPStreamsSDK.init("test_org", TPStreamsSDK.Provider.TPStreams, licensePayloadEncryptionKey = key1)
        val manager1 = provider.get(mediaItem)

        // Re-init with different key
        TPStreamsSDK.init("test_org", TPStreamsSDK.Provider.TPStreams, licensePayloadEncryptionKey = key2)
        val manager2 = provider.get(mediaItem)

        assertNotSame("DrmSessionManager cache should be invalidated when key changes", manager1, manager2)
    }

    @Test
    fun `test cache is invalidated when encryption is disabled`() {
        TPStreamsSDK.init("test_org", TPStreamsSDK.Provider.TPStreams, licensePayloadEncryptionKey = key1)
        val manager1 = provider.get(mediaItem)

        // Re-init with encryption disabled
        TPStreamsSDK.init("test_org", TPStreamsSDK.Provider.TPStreams, licensePayloadEncryptionKey = null)
        val manager2 = provider.get(mediaItem)

        assertNotSame("DrmSessionManager cache should be invalidated when encryption is disabled", manager1, manager2)
    }

    @Test
    fun `test cache is invalidated when provider changes`() {
        TPStreamsSDK.init("test_org", TPStreamsSDK.Provider.TPStreams, licensePayloadEncryptionKey = key1)
        val manager1 = provider.get(mediaItem)

        // Re-init switching to TestPress (which disables license payload encryption)
        TPStreamsSDK.init("test_org", TPStreamsSDK.Provider.TestPress, licensePayloadEncryptionKey = key1)
        val manager2 = provider.get(mediaItem)

        assertNotSame("DrmSessionManager cache should be invalidated when switching provider", manager1, manager2)
    }
}
