package com.tpstreams.player.data.network.api

import com.tpstreams.player.constants.LiveStreamEndedException
import com.tpstreams.player.constants.LiveStreamNotStartedException
import com.tpstreams.player.data.network.model.AssetInfo
import org.json.JSONObject
import java.util.Locale

class TestPressApiService : BaseApiService() {
    override fun assetInfoUrl(orgId: String, assetId: String, accessToken: String): String {
        val baseUrl = "https://$orgId.testpress.in/api/v2.5/video_info/$assetId/?v=2"
        return if (accessToken.isNotBlank()) "$baseUrl&access_token=$accessToken" else baseUrl
    }

    override fun drmLicenseUrl(
        orgId: String,
        assetId: String,
        accessToken: String,
        download: Boolean,
        licenseDurationSeconds: Long?
    ): String {
        val baseUrl = "https://$orgId.testpress.in/api/v2.5/drm_license_key/$assetId/"
        return if (accessToken.isNotBlank()) "$baseUrl?access_token=$accessToken" else baseUrl
    }

    override fun parseAsset(json: JSONObject): AssetInfo {
        val title = json.optString("title").ifEmpty { json.optString("name", "Undefined") }
        val contentType = json.optString("content_type", "video")
        val isLiveStream = contentType.equals("Live Stream", ignoreCase = true) || contentType.equals("livestream", ignoreCase = true)

        return if (isLiveStream && json.has("live_stream") && !json.isNull("live_stream")) {
            parseLiveStreamAssetInfo(json, title)
        } else {
            parseVideoAssetInfo(json, title)
        }
    }

    private fun parseLiveStreamAssetInfo(json: JSONObject, title: String): AssetInfo {
        val liveStreamObj = json.getJSONObject("live_stream")
        val liveStreamStatus = liveStreamObj.optString("status", "")
        val status = liveStreamStatus.uppercase(Locale.ROOT)
        val shouldShowRecordedVideo = liveStreamObj.optBoolean("show_recorded_video", false)
        val defaultMessage = if (status == "NOT STARTED") "Live stream will begin soon" else "Live stream has ended"
        val noticeMessage = liveStreamObj.optString("notice_message").ifBlank { defaultMessage }

        return when (status) {
            "NOT STARTED" -> throw LiveStreamNotStartedException(noticeMessage)
            "COMPLETED" -> {
                if (shouldShowRecordedVideo && json.has("video") && !json.isNull("video")) {
                    val videoObj = json.getJSONObject("video")
                    val videoStatus = videoObj.optString("transcoding_status", "")
                    if (videoStatus.equals("Completed", ignoreCase = true)) {
                        createVideoAssetInfo(videoObj, title)
                    } else {
                        throw LiveStreamEndedException(noticeMessage)
                    }
                } else {
                    throw LiveStreamEndedException(noticeMessage)
                }
            }

            else -> {
                AssetInfo(
                    mediaUrl = liveStreamObj.optString("stream_url", ""),
                    enableDrm = false,
                    thumbnailUrl = "",
                    videoObj = null,
                    isLiveStream = true,
                    durationSeconds = liveStreamObj.optDouble("duration", 0.0),
                    title = title
                )
            }
        }
    }

    private fun parseVideoAssetInfo(json: JSONObject, title: String): AssetInfo {
        val videoObj = json.getJSONObject("video")
        return createVideoAssetInfo(videoObj, title)
    }

    private fun createVideoAssetInfo(videoObj: JSONObject, title: String): AssetInfo {
        val enableDrm = videoObj.optBoolean("drm_enabled", false)
        return AssetInfo(
            mediaUrl = getVideoPlaybackUrl(videoObj, enableDrm),
            enableDrm = enableDrm,
            thumbnailUrl = getThumbnail(videoObj),
            videoObj = videoObj,
            isLiveStream = false,
            durationSeconds = videoObj.optDouble("duration", 0.0),
            title = title,
            isAes = isAesProtected(videoObj)
        )
    }

    private fun isAesProtected(videoObj: JSONObject): Boolean {
        return videoObj.optString("content_protection_type").equals("aes", ignoreCase = true)
    }

    private fun getVideoPlaybackUrl(videoObj: JSONObject, enableDrm: Boolean): String {
        return if (enableDrm) {
            videoObj.optString("dash_url")
        } else {
            videoObj.optString("url").ifEmpty { videoObj.optString("hls_url") }
        }
    }

    private fun getThumbnail(videoObj: JSONObject): String {
        return videoObj.optString("thumbnail")
            .ifEmpty { videoObj.optString("thumbnail_medium") }
            .ifEmpty { videoObj.optString("thumbnail_small") }
    }
}
