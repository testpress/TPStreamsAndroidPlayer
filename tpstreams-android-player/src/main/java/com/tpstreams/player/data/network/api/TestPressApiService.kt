package com.tpstreams.player.data.network.api

import com.tpstreams.player.constants.LiveStreamEndedException
import com.tpstreams.player.constants.LiveStreamNotStartedException
import com.tpstreams.player.data.network.model.AssetInfo
import org.json.JSONObject
import java.util.Locale

class TestPressApiService : BaseApiService() {
    override fun assetInfoUrl(orgId: String, assetId: String, accessToken: String): String {
        return "https://$orgId.testpress.in/api/v2.5/video_info/$assetId/?v=2&access_token=$accessToken"
    }

    override fun drmLicenseUrl(
        orgId: String,
        assetId: String,
        accessToken: String,
        download: Boolean,
        licenseDurationSeconds: Long?
    ): String {
        return "https://$orgId.testpress.in/api/v2.5/drm_license_key/$assetId/?access_token=$accessToken"
    }

    override fun parseAsset(json: JSONObject): AssetInfo {
        val title = json.optString("title").ifEmpty { json.optString("name", "Undefined") }
        val contentType = json.optString("content_type", "video")
        val isLiveStream = contentType == "livestream"

        return if (isLiveStream && json.has("live_stream") && !json.isNull("live_stream")) {
            parseLiveStreamAssetInfo(json, title)
        } else {
            parseVideoAssetInfo(json, title)
        }
    }

    private fun parseLiveStreamAssetInfo(json: JSONObject, title: String): AssetInfo {
        val liveStreamObj = json.getJSONObject("live_stream")
        val liveStreamStatus = liveStreamObj.optString("status", "")
        val shouldShowRecordedVideo = liveStreamObj.optBoolean("show_recorded_video", false)

        return when (liveStreamStatus.uppercase(Locale.ROOT)) {
            "NOT STARTED" -> throw LiveStreamNotStartedException("Live stream will begin soon")
            "COMPLETED" -> {
                if (shouldShowRecordedVideo && json.has("video") && !json.isNull("video")) {
                    val videoObj = json.getJSONObject("video")
                    val videoStatus = videoObj.optString("transcoding_status", "")
                    if (videoStatus.equals("Completed", ignoreCase = true)) {
                        val enableDrm = videoObj.optBoolean("drm_enabled", false)
                        AssetInfo(
                            mediaUrl = getVideoPlaybackUrl(videoObj, enableDrm),
                            enableDrm = enableDrm,
                            thumbnailUrl = getThumbnail(videoObj),
                            videoObj = videoObj,
                            isLiveStream = false,
                            durationSeconds = videoObj.optDouble("duration", 0.0),
                            title = title
                        )
                    } else {
                        throw LiveStreamEndedException("Live stream has ended")
                    }
                } else {
                    throw LiveStreamEndedException("Live stream has ended")
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
        val enableDrm = videoObj.optBoolean("drm_enabled", false)
        return AssetInfo(
            mediaUrl = getVideoPlaybackUrl(videoObj, enableDrm),
            enableDrm = enableDrm,
            thumbnailUrl = getThumbnail(videoObj),
            videoObj = videoObj,
            isLiveStream = false,
            durationSeconds = videoObj.optDouble("duration", 0.0),
            title = title
        )
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
