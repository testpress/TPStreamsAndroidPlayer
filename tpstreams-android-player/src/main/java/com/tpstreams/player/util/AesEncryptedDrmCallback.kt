package com.tpstreams.player.util

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.MediaDrmCallback
import java.util.UUID

/**
 * A [MediaDrmCallback] wrapper that encrypts the outgoing Widevine challenge payload
 * and decrypts the incoming license response using AES-256-GCM via [LicensePayloadEncryptor].
 *
 * Adds the [HEADER_PAYLOAD_ENCRYPTION] HTTP header to signal to the server that the
 * request payload is encrypted and the response should be encrypted.
 */
@UnstableApi
internal class AesEncryptedDrmCallback(
    private val delegate: HttpMediaDrmCallback,
    private val encryptor: LicensePayloadEncryptor
) : MediaDrmCallback {

    init {
        delegate.setKeyRequestProperty(HEADER_PAYLOAD_ENCRYPTION, ENCRYPTION_ALGORITHM)
    }

    override fun executeProvisionRequest(
        uuid: UUID,
        request: ExoMediaDrm.ProvisionRequest
    ): ByteArray {
        // Provisioning requests go to Google's cert servers and must not be encrypted with TPStreams key
        return delegate.executeProvisionRequest(uuid, request)
    }

    override fun executeKeyRequest(
        uuid: UUID,
        request: ExoMediaDrm.KeyRequest
    ): ByteArray {
        val rawChallenge = request.data
        val encryptedChallenge = encryptor.encrypt(rawChallenge)

        val encryptedKeyRequest = ExoMediaDrm.KeyRequest(
            encryptedChallenge,
            request.licenseServerUrl,
            request.requestType
        )

        val encryptedResponse = delegate.executeKeyRequest(uuid, encryptedKeyRequest)
        return encryptor.decrypt(encryptedResponse)
    }

    companion object {
        const val HEADER_PAYLOAD_ENCRYPTION = "X-TPStreams-Payload-Encryption"
        const val ENCRYPTION_ALGORITHM = "aes-256-gcm"
    }
}
