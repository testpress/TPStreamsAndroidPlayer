package com.tpstreams.player.presence

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import kotlin.random.Random

// Mirrors the web presence-client's heartbeat loop (see player.js's
// PresenceClient) and the iOS SDK's PresenceHeartbeatManager, so all three
// behave identically from the server's point of view. Kept entirely internal
// to the player: an app never starts or stops this directly, only
// TPStreamsPlayer.Listener.onPresenceTokenExpired reaches outside this class,
// when a fresh token is needed.
internal class PresenceHeartbeatManager(
    private val httpClient: OkHttpClient,
    private val viewerId: String,
    private val scheduler: PresenceScheduler,
    // Called on a 401 with a callback that must be invoked with a fresh token,
    // or with an empty string if none could be obtained. Wraps
    // TPStreamsPlayer.Listener.onPresenceTokenExpired, which is a no-op
    // default — an app that hasn't implemented it simply never calls this
    // back, and the loop quietly stops rather than erroring.
    private val onTokenExpired: (callback: (String) -> Unit) -> Unit,
    private val onError: ((Throwable) -> Unit)? = null,
    private val fallbackIntervalMillis: Long = DEFAULT_FALLBACK_INTERVAL_MILLIS,
) {
    @Volatile private var token: String = ""
    @Volatile private var baseUrl: String = ""
    @Volatile private var active = false
    @Volatile private var unauthorizedStreak = 0
    private var scheduled: PresenceScheduler.Cancellable? = null

    // Call this only while playback is actually active. Safe to call more
    // than once; a call while already active is a no-op.
    @Synchronized
    fun start(baseUrl: String, token: String) {
        if (active || baseUrl.isEmpty() || token.isEmpty()) return
        this.baseUrl = baseUrl
        this.token = token
        this.unauthorizedStreak = 0
        active = true
        // Spreads the first beat across the interval instead of firing it the
        // moment playback starts. A scheduled class means many viewers
        // pressing play within the same few seconds, and without this they
        // would then beat in lockstep for the whole session — a request spike
        // every interval rather than steady load.
        scheduleNextBeat((Random.nextDouble() * fallbackIntervalMillis).toLong())
    }

    // Sends a final leave beacon and stops the loop. Safe to call more than
    // once or without a matching start().
    @Synchronized
    fun stop() {
        if (!active) return
        active = false
        scheduled?.cancel()
        scheduled = null
        sendLeaveBeacon()
    }

    @Synchronized
    private fun scheduleNextBeat(delayMillis: Long) {
        if (!active) return
        scheduled = scheduler.schedule(delayMillis) { beat() }
    }

    private fun beat() {
        val currentToken: String
        val currentBaseUrl: String
        synchronized(this) {
            if (!active) return
            currentToken = token
            currentBaseUrl = baseUrl
        }

        val body = JSONObject().put("viewer_id", viewerId).toString()
        val request = Request.Builder()
            .url("$currentBaseUrl/presence/v1/heartbeat")
            .addHeader("Authorization", "Bearer $currentToken")
            // Resent on every call, not just once when requesting playback: the
            // server hashes it and checks it still matches the vid baked into
            // the token, so a token copied to another device gets rejected.
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Network hiccup: keep the loop alive on the fallback cadence
                // rather than letting this bring playback down with it.
                onError?.invoke(e)
                scheduleNextBeat(fallbackIntervalMillis)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { handleHeartbeatResponse(it) }
            }
        })
    }

    private fun handleHeartbeatResponse(response: Response) {
        when {
            response.code == 401 -> recoverFromUnauthorized()
            response.code == 429 -> {
                unauthorizedStreak = 0
                val retryAfterSeconds = response.header("Retry-After")?.toLongOrNull()
                scheduleNextBeat((retryAfterSeconds ?: (fallbackIntervalMillis / 1000)) * 1000)
            }
            response.isSuccessful -> {
                unauthorizedStreak = 0
                val nextHeartbeatIn = parseNextHeartbeatIn(response)
                scheduleNextBeat((nextHeartbeatIn ?: (fallbackIntervalMillis / 1000)) * 1000)
            }
            else -> {
                onError?.invoke(IOException("Unexpected heartbeat response: ${response.code}"))
                scheduleNextBeat(fallbackIntervalMillis)
            }
        }
    }

    private fun parseNextHeartbeatIn(response: Response): Long? {
        return try {
            val body = response.body?.string() ?: return null
            val nextHeartbeatIn = JSONObject(body).optLong("next_heartbeat_in", -1L)
            if (nextHeartbeatIn > 0) nextHeartbeatIn else null
        } catch (e: Exception) {
            null
        }
    }

    // A 401 here could mean either an expired token or a device-binding
    // mismatch — there's no need to tell them apart, both are resolved by
    // asking the integrator for a fresh playback config and resuming with its
    // token, exactly like the web SDK's recoverFromUnauthorized.
    private fun recoverFromUnauthorized() {
        unauthorizedStreak += 1
        onTokenExpired { newToken ->
            if (newToken.isNotEmpty()) {
                applyRefreshedToken(newToken)
            } else {
                backOffAfterFailedRefresh()
            }
        }
    }

    @Synchronized
    private fun applyRefreshedToken(newToken: String) {
        if (!active) return
        token = newToken
        unauthorizedStreak = 0
        scheduleNextBeat(fallbackIntervalMillis)
    }

    @Synchronized
    private fun backOffAfterFailedRefresh() {
        if (!active) return
        // Backs off further each consecutive failure — capped, not abandoned:
        // an integrator's own backend can be down for a while and recover, and
        // this loop has no playback-affecting reason to ever give up on it.
        val multiplier = minOf(unauthorizedStreak, MAX_BACKOFF_MULTIPLIER)
        scheduleNextBeat(fallbackIntervalMillis * multiplier)
    }

    private fun sendLeaveBeacon() {
        // No Android equivalent to navigator.sendBeacon: this is a best-effort,
        // fire-and-forget POST that never blocks player teardown on a response.
        val body = JSONObject()
            .put("presence_token", token)
            .put("viewer_id", viewerId)
            .toString()
        val request = Request.Builder()
            .url("$baseUrl/presence/v1/leave")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError?.invoke(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    companion object {
        private const val DEFAULT_FALLBACK_INTERVAL_MILLIS = 15_000L
        // Repeated 401s back off by a growing multiple of the fallback
        // interval, capped so a long-broken refresh callback still gets
        // retried every couple of minutes instead of trailing off to nothing.
        private const val MAX_BACKOFF_MULTIPLIER = 8
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
