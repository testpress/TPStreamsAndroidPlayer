package com.tpstreams.player.presence

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val VIEWER_ID = "device-abc-123"

// Records every scheduled beat instead of running it, so the test controls
// exactly when each one fires — the real network round-trip against
// MockWebServer still happens on OkHttp's own dispatcher thread, which is
// what makes a blocking queue the right synchronization primitive here rather
// than a plain field.
private class RecordingPresenceScheduler : PresenceScheduler {
    data class ScheduledCall(val delayMillis: Long, val action: () -> Unit)

    private val scheduledCalls = LinkedBlockingQueue<ScheduledCall>()
    val cancelledCount = AtomicInteger(0)

    override fun schedule(delayMillis: Long, action: () -> Unit): PresenceScheduler.Cancellable {
        scheduledCalls.put(ScheduledCall(delayMillis, action))
        return object : PresenceScheduler.Cancellable {
            override fun cancel() {
                cancelledCount.incrementAndGet()
            }
        }
    }

    fun awaitNextSchedule(): ScheduledCall {
        return scheduledCalls.poll(5, TimeUnit.SECONDS)
            ?: throw AssertionError("No beat was scheduled within the timeout")
    }

    fun assertNothingScheduledWithin(millis: Long) {
        val call = scheduledCalls.poll(millis, TimeUnit.MILLISECONDS)
        assertNull("expected no further beat to be scheduled, but one was", call)
    }
}

class PresenceHeartbeatManagerTest {

    private lateinit var server: MockWebServer
    private lateinit var httpClient: OkHttpClient
    private lateinit var scheduler: RecordingPresenceScheduler

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        httpClient = OkHttpClient()
        scheduler = RecordingPresenceScheduler()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun buildManager(
        onTokenExpired: (callback: (String) -> Unit) -> Unit = { callback -> callback("") },
        onError: ((Throwable) -> Unit)? = null,
    ): PresenceHeartbeatManager {
        return PresenceHeartbeatManager(
            httpClient = httpClient,
            viewerId = VIEWER_ID,
            scheduler = scheduler,
            onTokenExpired = onTokenExpired,
            onError = onError,
        )
    }

    private fun heartbeatOkResponse(nextHeartbeatIn: Int = 15) = MockResponse()
        .setResponseCode(200)
        .setBody("""{"ok":true,"next_heartbeat_in":$nextHeartbeatIn}""")

    private fun baseUrl() = server.url("/").toString().trimEnd('/')

    @Test
    fun `sends the first heartbeat with the bearer token and the persisted viewer id`() {
        server.enqueue(heartbeatOkResponse())
        val manager = buildManager()

        manager.start(baseUrl(), "token-1")
        scheduler.awaitNextSchedule().action()

        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("POST", request.method)
        assertEquals("/presence/v1/heartbeat", request.path)
        assertEquals("Bearer token-1", request.getHeader("Authorization"))
        assertEquals(VIEWER_ID, JSONObject(request.body.readUtf8()).getString("viewer_id"))
    }

    @Test
    fun `resends the same viewer id on the next heartbeat, not just the first`() {
        server.enqueue(heartbeatOkResponse(nextHeartbeatIn = 15))
        server.enqueue(heartbeatOkResponse(nextHeartbeatIn = 15))
        val manager = buildManager()

        manager.start(baseUrl(), "token-1")
        scheduler.awaitNextSchedule().action()
        server.takeRequest(5, TimeUnit.SECONDS)
        val second = scheduler.awaitNextSchedule()
        assertEquals(15_000L, second.delayMillis)
        second.action()

        val secondRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals(VIEWER_ID, JSONObject(secondRequest.body.readUtf8()).getString("viewer_id"))
    }

    @Test
    fun `schedules the next heartbeat using next_heartbeat_in from the response`() {
        server.enqueue(heartbeatOkResponse(nextHeartbeatIn = 42))
        val manager = buildManager()

        manager.start(baseUrl(), "token-1")
        scheduler.awaitNextSchedule().action()
        server.takeRequest(5, TimeUnit.SECONDS)

        assertEquals(42_000L, scheduler.awaitNextSchedule().delayMillis)
    }

    @Test
    fun `on 401, refreshes via onTokenExpired and resumes with the new token`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(heartbeatOkResponse())
        var refreshCallCount = 0
        val manager = buildManager(onTokenExpired = { callback ->
            refreshCallCount++
            callback("refreshed-token")
        })

        manager.start(baseUrl(), "token-1")
        scheduler.awaitNextSchedule().action()
        server.takeRequest(5, TimeUnit.SECONDS)
        scheduler.awaitNextSchedule().action()

        val secondRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals(1, refreshCallCount)
        assertEquals("Bearer refreshed-token", secondRequest.getHeader("Authorization"))
    }

    @Test
    fun `does not distinguish an expired token from a device-binding mismatch, both 401s refresh the same way`() {
        server.enqueue(MockResponse().setResponseCode(401))
        var refreshCallCount = 0
        val manager = buildManager(onTokenExpired = { callback ->
            refreshCallCount++
            callback("refreshed-token")
        })

        manager.start(baseUrl(), "token-1")
        scheduler.awaitNextSchedule().action()
        server.takeRequest(5, TimeUnit.SECONDS)
        scheduler.awaitNextSchedule()

        assertEquals(1, refreshCallCount)
    }

    @Test
    fun `keeps backing off with a growing delay instead of giving up when refresh keeps failing`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))
        val manager = buildManager(onTokenExpired = { callback -> callback("") })

        manager.start(baseUrl(), "token-1")
        scheduler.awaitNextSchedule().action()
        server.takeRequest(5, TimeUnit.SECONDS)
        val afterFirstFailure = scheduler.awaitNextSchedule()
        assertEquals(15_000L, afterFirstFailure.delayMillis)
        afterFirstFailure.action()
        server.takeRequest(5, TimeUnit.SECONDS)

        val afterSecondFailure = scheduler.awaitNextSchedule()
        assertTrue(
            "expected a longer backoff on the second consecutive failure",
            afterSecondFailure.delayMillis > afterFirstFailure.delayMillis
        )
    }

    @Test
    fun `respects Retry-After on a 429 instead of the default interval`() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "5"))
        val manager = buildManager()

        manager.start(baseUrl(), "token-1")
        scheduler.awaitNextSchedule().action()
        server.takeRequest(5, TimeUnit.SECONDS)

        assertEquals(5_000L, scheduler.awaitNextSchedule().delayMillis)
    }

    @Test
    fun `swallows a network error and keeps the loop alive on the fallback cadence`() {
        val deadServer = MockWebServer()
        deadServer.start()
        val unreachableUrl = deadServer.url("/").toString().trimEnd('/')
        deadServer.shutdown()
        var observedError: Throwable? = null
        val manager = buildManager(onError = { observedError = it })

        manager.start(unreachableUrl, "token-1")
        scheduler.awaitNextSchedule().action()

        assertEquals(15_000L, scheduler.awaitNextSchedule().delayMillis)
        assertTrue(observedError != null)
    }

    @Test
    fun `start() is idempotent, calling it twice does not schedule a second loop`() {
        server.enqueue(heartbeatOkResponse())
        val manager = buildManager()

        manager.start(baseUrl(), "token-1")
        manager.start(baseUrl(), "token-2")
        scheduler.awaitNextSchedule().action()

        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("Bearer token-1", request.getHeader("Authorization"))
        scheduler.assertNothingScheduledWithin(200)
    }

    @Test
    fun `stop() sends a leave beacon with the presence token and the same viewer id`() {
        server.enqueue(MockResponse().setResponseCode(204))
        val manager = buildManager()

        manager.start(baseUrl(), "token-1")
        manager.stop()

        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/presence/v1/leave", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("token-1", body.getString("presence_token"))
        assertEquals(VIEWER_ID, body.getString("viewer_id"))
    }

    @Test
    fun `stop() cancels the scheduled beat`() {
        server.enqueue(MockResponse().setResponseCode(204))
        val manager = buildManager()

        manager.start(baseUrl(), "token-1")
        manager.stop()

        assertEquals(1, scheduler.cancelledCount.get())
    }

    @Test
    fun `stop() is idempotent, a second call does not send another beacon`() {
        server.enqueue(MockResponse().setResponseCode(204))
        val manager = buildManager()

        manager.start(baseUrl(), "token-1")
        manager.stop()
        manager.stop()

        server.takeRequest(5, TimeUnit.SECONDS)
        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `stopped heartbeat loop does not schedule any further beats`() {
        server.enqueue(MockResponse().setResponseCode(204)) // consumed by stop()'s leave beacon
        val manager = buildManager()

        manager.start(baseUrl(), "token-1")
        val first = scheduler.awaitNextSchedule()
        manager.stop()
        val leaveRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/presence/v1/leave", leaveRequest.path)
        first.action()

        // Only the leave beacon above should ever have reached the server —
        // an errant heartbeat from the stopped beat would show up as a second.
        assertNull(server.takeRequest(300, TimeUnit.MILLISECONDS))
        scheduler.assertNothingScheduledWithin(200)
    }
}
