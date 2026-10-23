package com.tpstreams.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tpstreams.player.data.network.api.TPStreamsApiService
import com.tpstreams.player.data.network.api.TestPressApiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TPStreamsSdkConfigurationInstrumentedTest {

    @Test
    fun tpStreamsProvider_configuresApiAndWidevinePolicy() {
        TPStreamsSDK.init("instrumented-org", TPStreamsSDK.Provider.TPStreams, allowFallbackToL3 = true)

        assertEquals("SDK provider should be TPStreams", TPStreamsSDK.Provider.TPStreams, TPStreamsSDK.provider)
        assertEquals("SDK should retain the configured organization", "instrumented-org", TPStreamsSDK.orgId)
        assertTrue("TPStreams provider should create TPStreamsApiService", TPStreamsSDK.apiService is TPStreamsApiService)
        assertTrue("TPStreams provider should not create JWT headers", TPStreamsSDK.getAuthHeaders().isEmpty())
        assertTrue("L3 fallback policy should be enabled when requested", TPStreamsSDK.allowFallbackToL3)
    }

    @Test
    fun testPressProvider_configuresJwtHeaders() {
        TPStreamsSDK.init(
            orgId = "instrumented-org",
            provider = TPStreamsSDK.Provider.TestPress,
            authToken = "instrumented-token",
        )

        assertEquals("SDK provider should be TestPress", TPStreamsSDK.Provider.TestPress, TPStreamsSDK.provider)
        assertTrue("TestPress provider should create TestPressApiService", TPStreamsSDK.apiService is TestPressApiService)
        assertEquals("TestPress should expose the configured JWT header", mapOf("Authorization" to "JWT instrumented-token"), TPStreamsSDK.getAuthHeaders())
        assertFalse("L3 fallback should remain disabled by default", TPStreamsSDK.allowFallbackToL3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankOrganization_isRejected() {
        TPStreamsSDK.init("   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun tpStreamsProvider_doesNotAcceptAuthToken() {
        TPStreamsSDK.init("instrumented-org", authToken = "not-allowed")
    }
}
