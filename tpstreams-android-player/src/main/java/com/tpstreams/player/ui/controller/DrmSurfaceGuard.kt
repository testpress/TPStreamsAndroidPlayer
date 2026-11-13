package com.tpstreams.player.ui.controller

import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.tpstreams.player.TPStreamsPlayer
import com.tpstreams.player.util.WidevinePlaybackLevelResolver

/** Blocks the unsupported L1 DRM + TextureView combination before it renders black video. */
@UnstableApi
internal class DrmSurfaceGuard(
    private val isTextureView: () -> Boolean,
    private val getPlayer: () -> TPStreamsPlayer?,
    private val showError: (String) -> Unit
) {
    fun check(): Boolean {
        if (!isTextureView()) return false
        val player = getPlayer() ?: return false
        val isL1Device = WidevinePlaybackLevelResolver.getNativeWidevineLevel() == "L1"
        if (!isL1Device || !player.isDrmContent) return false

        Log.w(
            TAG,
            "TextureView on Widevine L1 with DRM content. Playback halted to prevent black video."
        )
        player.stop()
        showError("DRM protected content cannot be played in Performance mode on this device.")
        return true
    }

    private companion object {
        private const val TAG = "DrmSurfaceGuard"
    }
}
