package com.tpstreams.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.tpstreams.player.constants.PlaybackError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class PlayerListenerTest {

    private lateinit var context: Context
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var player: TPStreamsPlayer

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        `when`(context.applicationContext).thenReturn(context)
        val sharedPrefs = mock(android.content.SharedPreferences::class.java)
        `when`(context.getSharedPreferences(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(sharedPrefs)
        val connectivityManager = mock(android.net.ConnectivityManager::class.java)
        `when`(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
        TPStreamsSDK.init("test_org")
        exoPlayer = mock(ExoPlayer::class.java)
        trackSelector = mock(DefaultTrackSelector::class.java)

        player = TPStreamsPlayer(
            context = context,
            exoPlayer = exoPlayer,
            trackSelector = trackSelector,
            assetId = "",
            accessToken = ""
        )
    }

    @Test
    fun `view registering its listener does not overwrite application listener`() {
        var appErrorCalled = false
        val appListener = object : TPStreamsPlayer.Listener {
            override fun onAccessTokenExpired(videoId: String, callback: (String) -> Unit) {}
            override fun onError(error: PlaybackError, message: String) {
                appErrorCalled = true
            }
        }
        player.listener = appListener

        var viewErrorCalled = false
        val viewListener = object : TPStreamsPlayer.Listener {
            override fun onAccessTokenExpired(videoId: String, callback: (String) -> Unit) {}
            override fun onError(error: PlaybackError, message: String) {
                viewErrorCalled = true
            }
        }
        player.addListener(viewListener)

        // Verify player.listener property still returns the application listener
        assertEquals("player.listener should remain the app's listener", appListener, player.listener)

        // Verify both listeners receive events
        player.notifyError(PlaybackError.UNSPECIFIED, "test error")
        assertTrue("App listener should receive error", appErrorCalled)
        assertTrue("View listener should receive error", viewErrorCalled)
    }

    @Test
    fun `application setting listener does not overwrite previously registered view listener`() {
        var viewErrorCalled = false
        val viewListener = object : TPStreamsPlayer.Listener {
            override fun onAccessTokenExpired(videoId: String, callback: (String) -> Unit) {}
            override fun onError(error: PlaybackError, message: String) {
                viewErrorCalled = true
            }
        }
        // View registers its listener first
        player.addListener(viewListener)

        // App assigns its listener afterwards
        var appErrorCalled = false
        val appListener = object : TPStreamsPlayer.Listener {
            override fun onAccessTokenExpired(videoId: String, callback: (String) -> Unit) {}
            override fun onError(error: PlaybackError, message: String) {
                appErrorCalled = true
            }
        }
        player.listener = appListener

        // Both listeners must receive events
        player.notifyError(PlaybackError.UNSPECIFIED, "test error")
        assertTrue("App listener should receive error", appErrorCalled)
        assertTrue("View listener should receive error", viewErrorCalled)
    }

    @Test
    fun `removing view listener leaves application listener intact`() {
        var viewErrorCalled = false
        val viewListener = object : TPStreamsPlayer.Listener {
            override fun onAccessTokenExpired(videoId: String, callback: (String) -> Unit) {}
            override fun onError(error: PlaybackError, message: String) {
                viewErrorCalled = true
            }
        }
        player.addListener(viewListener)

        var appErrorCount = 0
        val appListener = object : TPStreamsPlayer.Listener {
            override fun onAccessTokenExpired(videoId: String, callback: (String) -> Unit) {}
            override fun onError(error: PlaybackError, message: String) {
                appErrorCount++
            }
        }
        player.listener = appListener

        // Detach view listener
        player.removeListener(viewListener)

        player.notifyError(PlaybackError.UNSPECIFIED, "test error")
        assertFalse("View listener should not be called after removal", viewErrorCalled)
        assertEquals("App listener should still be called", 1, appErrorCount)
    }

    @Test
    fun `multiple views sharing a player receive independent notifications`() {
        var view1Error = false
        var view2Error = false
        val view1 = object : TPStreamsPlayer.Listener {
            override fun onAccessTokenExpired(videoId: String, callback: (String) -> Unit) {}
            override fun onError(error: PlaybackError, message: String) { view1Error = true }
        }
        val view2 = object : TPStreamsPlayer.Listener {
            override fun onAccessTokenExpired(videoId: String, callback: (String) -> Unit) {}
            override fun onError(error: PlaybackError, message: String) { view2Error = true }
        }

        player.addListener(view1)
        player.addListener(view2)

        player.notifyError(PlaybackError.UNSPECIFIED, "test error")
        assertTrue(view1Error)
        assertTrue(view2Error)

        // Detaching view1 should not affect view2
        view1Error = false
        view2Error = false
        player.removeListener(view1)

        player.notifyError(PlaybackError.UNSPECIFIED, "test error 2")
        assertFalse(view1Error)
        assertTrue(view2Error)
    }
}
