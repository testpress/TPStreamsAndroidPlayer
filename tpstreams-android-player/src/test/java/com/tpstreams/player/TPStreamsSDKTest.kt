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
}
