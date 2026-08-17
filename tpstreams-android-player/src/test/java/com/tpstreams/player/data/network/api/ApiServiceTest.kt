package com.tpstreams.player.data.network.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `tpstreams api appends viewer_id when one is supplied`() {
        assertEquals(
            "https://app.tpstreams.com/api/v1/org/assets/asset/?access_token=token&viewer_id=device-abc",
            tpStreamsApi.assetInfoUrl("org", "asset", "token", viewerId = "device-abc")
        )
    }

    @Test
    fun `tpstreams api omits viewer_id when none is supplied`() {
        assertEquals(
            "https://app.tpstreams.com/api/v1/org/assets/asset/?access_token=token",
            tpStreamsApi.assetInfoUrl("org", "asset", "token", viewerId = null)
        )
    }

    // The legacy TestPress provider has no presence/live-viewer-count support,
    // so a viewer id has nothing to bind there and must not leak into the URL.
    @Test
    fun `testpress api ignores viewer_id even when one is supplied`() {
        assertEquals(
            "https://demo.testpress.in/api/v2.5/video_info/asset/?v=2&access_token=token",
            testPressApi.assetInfoUrl("demo", "asset", "token", viewerId = "device-abc")
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

    private fun liveStreamJson(presenceJson: String = ""): JSONObject {
        return JSONObject(
            """
            {
                "title": "Live Class",
                "type": "livestream",
                "live_stream": {
                    "status": "Streaming",
                    "hls_url": "https://example.com/live.m3u8",
                    "dash_url": "https://example.com/live.mpd",
                    "enable_drm": false
                    $presenceJson
                }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `tpstreams api parses a present presence config on a live stream`() {
        val assetInfo = tpStreamsApi.parseAsset(
            liveStreamJson(""", "presence": {"token": "presence-token-abc", "vid": "irrelevant", "base_url": "https://presence.tpstreams.test"}""")
        )

        assertEquals("presence-token-abc", assetInfo.presence?.token)
        assertEquals("https://presence.tpstreams.test", assetInfo.presence?.baseUrl)
    }

    @Test
    fun `tpstreams api parses an absent presence key as no presence`() {
        // What every organization not yet opted into the rollout actually
        // gets back — must parse cleanly rather than error.
        val assetInfo = tpStreamsApi.parseAsset(liveStreamJson())

        assertNull(assetInfo.presence)
    }

    // A malformed presence (missing token or base_url) is treated the same as
    // no presence at all, rather than letting a half-populated config reach
    // the heartbeat manager.
    @Test
    fun `tpstreams api parses a presence config missing its token as no presence`() {
        val assetInfo = tpStreamsApi.parseAsset(
            liveStreamJson(""", "presence": {"base_url": "https://presence.tpstreams.test"}""")
        )

        assertNull(assetInfo.presence)
    }
}
