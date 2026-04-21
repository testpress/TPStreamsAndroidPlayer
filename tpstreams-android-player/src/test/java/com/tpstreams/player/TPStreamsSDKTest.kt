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
}
