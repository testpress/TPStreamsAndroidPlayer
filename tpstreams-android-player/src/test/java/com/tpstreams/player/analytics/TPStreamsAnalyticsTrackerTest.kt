package com.tpstreams.player.analytics

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.tpstreams.player.TPStreamsPlayer
import com.tpstreams.player.TPStreamsSDK
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class TPStreamsAnalyticsTrackerTest {

    private lateinit var context: Context
    private val uploadedDurations = mutableListOf<Long>()
    private var mockCurrentTimeMs = 1000L

    private val testUploader = object : AnalyticsUploader {
        override suspend fun start() {}

        override suspend fun upload(durationSeconds: Long): Boolean {
            synchronized(uploadedDurations) {
                uploadedDurations.add(durationSeconds)
            }
            return true
        }
    }

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        val sharedPrefs = mock(android.content.SharedPreferences::class.java)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(sharedPrefs)
        `when`(context.applicationContext).thenReturn(context)
        uploadedDurations.clear()
        mockCurrentTimeMs = 1000L
        TPStreamsSDK.init("test_org", TPStreamsSDK.Provider.TPStreams)
    }

    private fun createTracker(
        scope: CoroutineScope,
        heartbeatMs: Long = 60_000L
    ): TPStreamsAnalyticsTracker {
        return TPStreamsAnalyticsTracker(
            context = context,
            orgId = "test_org",
            assetId = "asset_123",
            accessToken = "token",
            scope = scope,
            heartbeatIntervalMs = heartbeatMs,
            timeProvider = { mockCurrentTimeMs },
            customUploader = testUploader
        )
    }

    @Test
    fun `playback transitions accurately accumulate active duration and upload`() = runBlocking {
        val tracker = createTracker(this)
        tracker.start()
        delay(50)

        // 1. Enter PLAYING state
        tracker.onIsPlayingChanged(true)
        assertTrue(tracker.isPlaying)

        // Advance 10 seconds of playback
        mockCurrentTimeMs += 10_000L
        assertEquals(10L, tracker.cumulativeDurationSeconds)

        // 2. PAUSE playback
        tracker.onIsPlayingChanged(false)
        assertFalse(tracker.isPlaying)
        assertEquals(10L, tracker.cumulativeDurationSeconds)

        delay(50)
        synchronized(uploadedDurations) {
            assertEquals(listOf(10L), uploadedDurations)
        }

        // User stays paused for 30 seconds (no increase in cumulative duration)
        mockCurrentTimeMs += 30_000L
        assertEquals(10L, tracker.cumulativeDurationSeconds)

        // 3. RESUME playback
        tracker.onIsPlayingChanged(true)
        assertTrue(tracker.isPlaying)

        // Play for 15 more seconds
        mockCurrentTimeMs += 15_000L
        assertEquals(25L, tracker.cumulativeDurationSeconds)

        // 4. BUFFERING occurs
        tracker.onPlaybackStateChanged(Player.STATE_BUFFERING)
        assertFalse(tracker.isPlaying)
        assertEquals(25L, tracker.cumulativeDurationSeconds)

        delay(50)
        synchronized(uploadedDurations) {
            assertEquals(listOf(10L, 25L), uploadedDurations)
        }

        // Stays buffering for 5 seconds (not counted)
        mockCurrentTimeMs += 5_000L
        assertEquals(25L, tracker.cumulativeDurationSeconds)

        // 5. RESUME and then VIDEO ENDS
        tracker.onIsPlayingChanged(true)
        mockCurrentTimeMs += 20_000L
        assertEquals(45L, tracker.cumulativeDurationSeconds)

        tracker.onPlaybackStateChanged(Player.STATE_ENDED)
        assertFalse(tracker.isPlaying)
        assertEquals(45L, tracker.cumulativeDurationSeconds)

        delay(50)
        synchronized(uploadedDurations) {
            assertEquals(listOf(10L, 25L, 45L), uploadedDurations)
        }
    }

    @Test
    fun `periodic heartbeat uploads latest cumulative duration during playback`() = runBlocking {
        val tracker = createTracker(this, heartbeatMs = 50L)
        tracker.start()
        delay(20)

        tracker.onIsPlayingChanged(true)

        mockCurrentTimeMs += 5000L
        delay(60)

        synchronized(uploadedDurations) {
            assertTrue(uploadedDurations.isNotEmpty())
            assertEquals(5L, uploadedDurations.first())
        }

        tracker.onIsPlayingChanged(false)
        delay(20)
    }

    @Test
    fun `seeking while playing flushes active elapsed time without sending seek scrubber position`() = runBlocking {
        val tracker = createTracker(this)
        tracker.start()
        delay(50)

        tracker.onIsPlayingChanged(true)
        mockCurrentTimeMs += 8000L

        // User scrubs to minute 50:00 (which is position 3000s, not watched time)
        tracker.onSeek()

        delay(50)
        assertEquals(8L, tracker.cumulativeDurationSeconds)
        synchronized(uploadedDurations) {
            assertEquals(listOf(8L), uploadedDurations)
        }
    }

    @Test
    fun `onRelease flushes final duration`() = runBlocking {
        val tracker = createTracker(this)
        tracker.start()
        delay(50)

        tracker.onIsPlayingChanged(true)
        mockCurrentTimeMs += 14000L

        tracker.onRelease()
        delay(50)

        assertEquals(14L, tracker.cumulativeDurationSeconds)
        synchronized(uploadedDurations) {
            assertTrue(uploadedDurations.contains(14L))
        }
    }

    @Test
    fun `TPStreamsPlayer only initializes analyticsTracker when provider is TPStreams`() {
        TPStreamsSDK.init("org_tp", TPStreamsSDK.Provider.TPStreams)
        val exoPlayer = mock(ExoPlayer::class.java)
        val trackSelector = mock(DefaultTrackSelector::class.java)

        val tpStreamsPlayer = TPStreamsPlayer(
            context = context,
            exoPlayer = exoPlayer,
            trackSelector = trackSelector,
            assetId = "test_asset",
            accessToken = "test_token"
        )
        assertNotNull(tpStreamsPlayer.analyticsTracker)

        // TestPress provider
        TPStreamsSDK.init("org_testpress", TPStreamsSDK.Provider.TestPress, "authToken")
        val testpressPlayer = TPStreamsPlayer(
            context = context,
            exoPlayer = exoPlayer,
            trackSelector = trackSelector,
            assetId = "test_asset",
            accessToken = "test_token"
        )
        assertNull(testpressPlayer.analyticsTracker)
    }
}
