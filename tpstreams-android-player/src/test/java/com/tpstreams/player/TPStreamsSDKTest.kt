package com.tpstreams.player

import com.tpstreams.player.data.network.api.TPStreamsApiService
import com.tpstreams.player.data.network.api.TestPressApiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TPStreamsSDKTest {

    @Test
    fun `test legacy init defaults to TPStreamsApiService`() {
        TPStreamsSDK.init("test_org")
        
        assertEquals("test_org", TPStreamsSDK.orgId)
        assertTrue(TPStreamsSDK.apiService is TPStreamsApiService)
    }

    @Test
    fun `test init with Provider enum`() {
        TPStreamsSDK.init("test_org", TPStreamsSDK.Provider.TestPress)
        
        assertEquals("test_org", TPStreamsSDK.orgId)
        assertTrue(TPStreamsSDK.apiService is TestPressApiService)
    }

    @Test
    fun `test init with authToken for TestPress`() {
        TPStreamsSDK.init("test_org", TPStreamsSDK.Provider.TestPress, "test_auth_token")

        assertEquals("test_auth_token", TPStreamsSDK.authToken)
        val headers = TPStreamsSDK.getAuthHeaders()
        assertEquals("JWT test_auth_token", headers["Authorization"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test init with authToken for TPStreams throws exception`() {
        TPStreamsSDK.init("test_org", TPStreamsSDK.Provider.TPStreams, "test_auth_token")
    }

    @Test
    fun `test getAuthHeaders returns empty when no authToken`() {
        TPStreamsSDK.init("test_org", TPStreamsSDK.Provider.TestPress, null)
        assertTrue(TPStreamsSDK.getAuthHeaders().isEmpty())
    }

    @Test
    fun `test license payload encryption is disabled by default when no key is provided`() {
        TPStreamsSDK.init("test_org", TPStreamsSDK.Provider.TPStreams)
        org.junit.Assert.assertNull(TPStreamsSDK.licensePayloadEncryptionKey)
        org.junit.Assert.assertFalse(TPStreamsSDK.isLicensePayloadEncryptionEnabled())
    }

    @Test
    fun `test license payload encryption is enabled when key is passed for TPStreams`() {
        TPStreamsSDK.init(
            orgId = "test_org",
            provider = TPStreamsSDK.Provider.TPStreams,
            licensePayloadEncryptionKey = "7N9UuCkpKS9d84VAvMRFLos4AGCcS1aFsOjFKusmDsc="
        )
        assertEquals("7N9UuCkpKS9d84VAvMRFLos4AGCcS1aFsOjFKusmDsc=", TPStreamsSDK.licensePayloadEncryptionKey)
        assertTrue(TPStreamsSDK.isLicensePayloadEncryptionEnabled())
    }

    @Test
    fun `test license payload encryption is disabled for non-TPStreams provider even if key is passed`() {
        TPStreamsSDK.init(
            orgId = "test_org",
            provider = TPStreamsSDK.Provider.TestPress,
            licensePayloadEncryptionKey = "7N9UuCkpKS9d84VAvMRFLos4AGCcS1aFsOjFKusmDsc="
        )
        org.junit.Assert.assertFalse(TPStreamsSDK.isLicensePayloadEncryptionEnabled())
    }
}
