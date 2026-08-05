package com.tpstreams.player

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal class ResumePlaybackManager(
    private val player: Player,
    private val assetId: String
) {

    companion object {
        private const val RESUME_PLAYBACK_BASE_URL = "https://data.tpstreams.com/api/player"
        private const val LAST_WATCHED_POSITION_URL = "$RESUME_PLAYBACK_BASE_URL/last-watched-position/"
        private const val UPDATE_WATCHED_POSITION_URL = "$RESUME_PLAYBACK_BASE_URL/update-watched-position/"
        private const val SAVE_INTERVAL_MS = 2 * 60 * 1000L
        private val client = OkHttpClient()
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun watchPositionBody(userId: String, orgId: String, assetId: String): JSONObject =
            JSONObject().apply {
                put("user_id", userId)
                put("organization_id", orgId)
                put("asset_id", assetId)
            }

        private fun updateBody(userId: String, orgId: String, assetId: String, watchedSeconds: Int): JSONObject =
            watchPositionBody(userId, orgId, assetId).put("watched_seconds", watchedSeconds)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fetched = false

    private val periodicSaveJob = scope.launch {
        while (isActive) {
            delay(SAVE_INTERVAL_MS)
            if (player.isPlaying) saveWatchedPosition()
        }
    }

    fun onPlayerReady() {
        if (fetched) return
        fetched = true
        val userId = TPStreamsSDK.userId ?: return
        val orgId = TPStreamsSDK.orgId ?: return
        val body = watchPositionBody(userId, orgId, assetId)
        scope.launch {
            val response = post(LAST_WATCHED_POSITION_URL, body) ?: return@launch
            val seconds = response.optInt("watched_seconds", 0)
            if (seconds > 0) {
                withContext(Dispatchers.Main) { player.seekTo(seconds * 1000L) }
            }
        }
    }

    fun onPaused() = saveWatchedPosition()

    fun onSeeked() = saveWatchedPosition()

    fun onVideoEnded() {
        periodicSaveJob.cancel()
        val userId = TPStreamsSDK.userId ?: return
        val orgId = TPStreamsSDK.orgId ?: return
        scope.launch { delete(LAST_WATCHED_POSITION_URL, watchPositionBody(userId, orgId, assetId)) }
    }

    fun onRelease() {
        saveWatchedPosition(allowCancellation = false)
        scope.cancel()
    }

    private fun saveWatchedPosition(allowCancellation: Boolean = true) {
        val userId = TPStreamsSDK.userId ?: return
        val orgId = TPStreamsSDK.orgId ?: return
        if (player.playbackState == Player.STATE_ENDED) return
        val body = updateBody(userId, orgId, assetId, (player.currentPosition / 1000).toInt())
        val context = if (allowCancellation) Dispatchers.IO else NonCancellable + Dispatchers.IO
        scope.launch(context) { post(UPDATE_WATCHED_POSITION_URL, body) }
    }

    private fun post(url: String, body: JSONObject): JSONObject? = runCatching {
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: return@use null
            JSONObject(responseBody)
        }
    }.getOrNull()

    private fun delete(url: String, body: JSONObject) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .delete(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(request).execute().close()
        }
    }
}
