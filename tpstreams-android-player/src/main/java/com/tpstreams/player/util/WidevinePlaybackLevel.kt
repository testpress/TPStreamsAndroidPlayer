package com.tpstreams.player.util

/**
 * Widevine security level used for DRM playback after init-time resolution.
 *
 * [L1] uses the device default Widevine CDM (hardware when available).
 * [L3] forces software Widevine decryption for the lifetime of the app install
 * (or until the persisted preference is cleared).
 */
internal enum class WidevinePlaybackLevel {
    L1,
    L3,
}
