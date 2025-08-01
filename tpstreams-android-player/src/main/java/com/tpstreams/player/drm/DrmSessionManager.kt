package com.tpstreams.player.drm

import android.media.MediaDrm
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.DrmConfiguration
import androidx.media3.common.C
import androidx.media3.exoplayer.drm.DrmSession
import android.media.MediaDrm.MediaDrmStateException
import android.os.Build
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class DrmSessionManager(
    private val assetId: String,
    private val isTokenValid: (String, (Boolean) -> Unit) -> Unit,
    private val onAccessTokenExpired: (String, (String) -> Unit) -> Unit,
    private val accessToken: String,
    private val organizationId: String?,
    private val exoPlayer: Player
) : Player.Listener {

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @OptIn(UnstableApi::class)
    override fun onPlayerError(error: PlaybackException) {
        val cause = error.cause
        if (cause is DrmSession.DrmSessionException &&
            cause.cause is MediaDrmStateException) {
            val drmStateException = cause.cause as MediaDrmStateException
            Log.w("DrmSessionManager", "DRM session error for asset: $assetId")
            handleDrmLicenseExpiry()
        }
    }

    private fun handleDrmLicenseExpiry() {
        Log.d("DrmSessionManager", "Handling DRM license expiry for asset: $assetId")
        isTokenValid(assetId) { isValid ->
            if (isValid) {
                Log.d("DrmSessionManager", "Token is still valid, retrying with existing token")
                retryPlayback(accessToken)
            } else {
                Log.d("DrmSessionManager", "Token is expired, requesting new token")
                onAccessTokenExpired(assetId) { newToken ->
                    if (newToken.isNotEmpty()) {
                        Log.d("DrmSessionManager", "Received new token, retrying playback")
                        retryPlayback(newToken)
                    } else {
                        Log.e("DrmSessionManager", "Failed to get new token for DRM license renewal")
                    }
                }
            }
        }
    }

    private fun retryPlayback(token: String) {
        val currentMediaItem = exoPlayer.currentMediaItem ?: return
        val orgId = organizationId ?: return
        
        Log.d("DrmSessionManager", "Retrying playback with token for asset: $assetId")
        
        val updatedMediaItem = currentMediaItem.buildUpon()
            .apply {
                val currentDrmConfig = currentMediaItem.localConfiguration?.drmConfiguration
                if (currentDrmConfig != null) {
                    val licenseUrl = "https://app.tpstreams.com/api/v1/$orgId/assets/$assetId/drm_license/?access_token=$token"
                    val drmHeaders = mapOf("Authorization" to "Bearer $token")
                    
                    val newDrmConfig = DrmConfiguration.Builder(C.WIDEVINE_UUID)
                        .setLicenseUri(licenseUrl)
                        .setLicenseRequestHeaders(drmHeaders)
                        .setMultiSession(true)
                        .build()
                    
                    setDrmConfiguration(newDrmConfig)
                }
            }
            .build()

        coroutineScope.launch {
            val currentPosition = exoPlayer.currentPosition
            val wasPlaying = exoPlayer.isPlaying
            
            exoPlayer.setMediaItem(updatedMediaItem)
            exoPlayer.prepare()
            
            if (currentPosition > 0) {
                exoPlayer.seekTo(currentPosition)
            }
            if (wasPlaying) {
                exoPlayer.play()
            }
            
            Log.d("DrmSessionManager", "Successfully retried playback with updated DRM license")
        }
    }

    fun release() {
        coroutineScope.cancel()
    }
} 