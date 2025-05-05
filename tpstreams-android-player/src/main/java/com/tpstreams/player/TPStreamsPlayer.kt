package com.tpstreams.player

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.DrmConfiguration
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class TPStreamsPlayer private constructor(
    private val exoPlayer: ExoPlayer,
    private val trackSelector: DefaultTrackSelector,
    assetId: String,
    accessToken: String,
    private val shouldAutoPlay: Boolean = true
) : Player by exoPlayer {

    private var isPrepared = false
    private var requestedPlay = false

    init {
        val org = organizationId
            ?: throw IllegalStateException("TPStreamsPlayer.init(organizationId) must be called before using the player.")
        fetchAndPrepare(org, assetId, accessToken)
    }

    private fun fetchAndPrepare(orgId: String, assetId: String, accessToken: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val assetApiUrl =
                    "https://app.tpstreams.com/api/v1/$orgId/assets/$assetId/?access_token=$accessToken"

                val request = Request.Builder().url(assetApiUrl).build()
                val response = OkHttpClient().newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.e("TPStreamsPlayer", "Failed to fetch asset metadata: ${response.code}")
                    return@launch
                }

                val body = response.body?.string() ?: return@launch
                Log.d("TPStreamsPlayer", "Fetched asset metadata: $body")

                val json = JSONObject(body)
                val dashUrl = json.getJSONObject("video").getString("dash_url")
                val licenseUrl =
                    "https://app.tpstreams.com/api/v1/$orgId/assets/$assetId/drm_license/?access_token=$accessToken"

                val drmHeaders = mapOf("Authorization" to "Bearer $accessToken")

                val drmConfig = DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri(licenseUrl)
                    .setLicenseRequestHeaders(drmHeaders)
                    .setMultiSession(true)
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setUri(dashUrl)
                    .setDrmConfiguration(drmConfig)
                    .build()

                launch(Dispatchers.Main) {
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    isPrepared = true
                    if (shouldAutoPlay || requestedPlay) {
                        exoPlayer.play()
                    }
                }
            } catch (e: Exception) {
                Log.e("TPStreamsPlayer", "Error preparing video: ${e.message}", e)
            }
        }
    }

    override fun play() {
        if (isPrepared) {
            exoPlayer.play()
        } else {
            requestedPlay = true
        }
    }

    override fun pause() = exoPlayer.pause()
    override fun release() = exoPlayer.release()

    fun getTrackSelector(): DefaultTrackSelector = trackSelector

    companion object {
        private var organizationId: String? = null

        fun init(orgId: String) {
            organizationId = orgId
        }

        @OptIn(UnstableApi::class)
        private fun createExoPlayer(context: Context): Pair<ExoPlayer, DefaultTrackSelector> {
            val trackSelector = DefaultTrackSelector(context).apply {
                parameters = DefaultTrackSelector.Parameters.Builder()
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .build()
            }

            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("TPStreamsPlayer")
                .setAllowCrossProtocolRedirects(true)

            val mediaSourceFactory = DefaultMediaSourceFactory(context)
                .setDataSourceFactory(dataSourceFactory)

            return ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .setTrackSelector(trackSelector)
                .build() to trackSelector
        }

        fun create(
            context: Context,
            assetId: String,
            accessToken: String,
            shouldAutoPlay: Boolean = true
        ): TPStreamsPlayer {
            val (exo, trackSelector) = createExoPlayer(context)
            return TPStreamsPlayer(exo, trackSelector, assetId, accessToken, shouldAutoPlay)
        }
    }
}
