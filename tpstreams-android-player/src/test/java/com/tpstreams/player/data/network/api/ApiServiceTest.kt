package com.tpstreams.player.data.network.api

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiServiceTest {
    private val tpStreamsApi = TPStreamsApiService()
    private val testPressApi = TestPressApiService()

    @Test
    fun `tpstreams api builds urls`() {
        assertEquals(
            "https://app.tpstreams.com/api/v1/org/assets/asset/?access_token=token",
            tpStreamsApi.assetInfoUrl("org", "asset", "token")
        )
        assertEquals(
            "https://app.tpstreams.com/api/v1/org/assets/asset/drm_license/?access_token=token&download=true&license_duration_seconds=1000",
            tpStreamsApi.drmLicenseUrl("org", "asset", "token", download = true, licenseDurationSeconds = 1000)
        )
    }

    @Test
    fun `testpress api builds urls`() {
        assertEquals(
            "https://demo.testpress.in/api/v2.5/video_info/asset/?v=2&access_token=token",
            testPressApi.assetInfoUrl("demo", "asset", "token")
        )
        assertEquals(
            "https://demo.testpress.in/api/v2.5/drm_license_key/asset/?access_token=token",
            testPressApi.drmLicenseUrl("demo", "asset", "token")
        )
    }
}
