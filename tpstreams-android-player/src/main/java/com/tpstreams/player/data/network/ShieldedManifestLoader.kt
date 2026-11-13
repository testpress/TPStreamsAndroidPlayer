package com.tpstreams.player.data.network

import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import com.tpstreams.player.TPStreamsSDK
import com.tpstreams.player.data.network.model.AssetInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal data class DecryptedShieldedManifest(
    val mpd: String,
    val segmentSeed: ByteArray,
)

/** Fetches and decrypts a shielded DASH manifest without changing player playback yet. */
internal object ShieldedManifestLoader {
    private const val TAG = "TPSTREAMS_SHIELDED_METRICS"
    private const val NONCE_SIZE = 12
    private const val MANIFEST_KEY_SIZE = 32
    private const val SEED_SIZE = 32
    private const val RAW_PUBLIC_KEY_SIZE = 65
    private const val SEALED_KEY_SIZE = RAW_PUBLIC_KEY_SIZE + NONCE_SIZE + MANIFEST_KEY_SIZE + 16
    private const val HKDF_INFO = "tpstreams-manifest-shield-v1"
    private val httpClient = OkHttpClient()

    fun load(
        assetInfo: AssetInfo,
        assetId: String,
        accessToken: String,
    ): DecryptedShieldedManifest? {
        val totalStartNs = SystemClock.elapsedRealtimeNanos()
        val manifestUrl = assetInfo.shieldedManifestUrl ?: run {
            Log.d(TAG, "load skipped — shielded_manifest_url is absent")
            return null
        }
        Log.d(
            TAG,
            "load started — assetId=$assetId, manifestHost=${Uri.parse(manifestUrl).host}, " +
                "accessTokenPresent=${accessToken.isNotBlank()}"
        )
        val keyPair = generateKeyPair()
        val clientPublicKey = encodeBase64Url(rawPublicKey(keyPair))
        val keyUrl = TPStreamsSDK.apiService.shieldedManifestKeyUrl(
            orgId = TPStreamsSDK.requireOrgId(),
            assetId = assetId,
            accessToken = accessToken,
            clientPublicKey = clientPublicKey,
        )

        Log.d(
            TAG,
            "requesting manifest key — endpoint=${safeEndpoint(keyUrl)}, " +
                "clientPublicKeyLength=${clientPublicKey.length}"
        )
        val keyFetchStartNs = SystemClock.elapsedRealtimeNanos()
        val sealedKey = getBytes("manifest key", keyUrl)
        val keyFetchMs = elapsedMs(keyFetchStartNs)
        Log.d(TAG, "manifest key received — sealedKeyBytes=${sealedKey.size}")
        val manifestFetchStartNs = SystemClock.elapsedRealtimeNanos()
        val encryptedManifest = getBytes("encrypted manifest", manifestUrl)
        val manifestFetchMs = elapsedMs(manifestFetchStartNs)
        Log.d(TAG, "encrypted manifest received — bytes=${encryptedManifest.size}")
        val unwrapStartNs = SystemClock.elapsedRealtimeNanos()
        val manifestKey = unsealManifestKey(sealedKey, keyPair)
        val unwrapMs = elapsedMs(unwrapStartNs)
        Log.d(TAG, "manifest key unwrapped — keyBytes=${manifestKey.size}")
        val decryptStartNs = SystemClock.elapsedRealtimeNanos()
        val decryptedManifest = decryptManifest(manifestKey, encryptedManifest)
        val decryptMs = elapsedMs(decryptStartNs)
        Log.d(
            TAG,
            "SHIELDED_TIMING manifest_key_fetch_ms=$keyFetchMs, " +
                "manifest_fetch_ms=$manifestFetchMs, key_unwrap_ms=$unwrapMs, " +
                "manifest_decrypt_ms=$decryptMs, crypto_overhead_ms=${unwrapMs + decryptMs}, " +
                "total_manifest_pipeline_ms=${elapsedMs(totalStartNs)}"
        )
        return decryptedManifest
    }

    private fun generateKeyPair(): KeyPair {
        return KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
    }

    private fun rawPublicKey(keyPair: KeyPair): ByteArray {
        val publicKey = keyPair.public as ECPublicKey
        return byteArrayOf(4) +
            publicKey.w.affineX.toFixedLengthBytes(32) +
            publicKey.w.affineY.toFixedLengthBytes(32)
    }

    private fun unsealManifestKey(sealed: ByteArray, keyPair: KeyPair): ByteArray {
        require(sealed.size == SEALED_KEY_SIZE) { "Invalid sealed manifest key" }

        val serverPublicKey = decodeRawPublicKey(
            sealed.copyOfRange(0, RAW_PUBLIC_KEY_SIZE),
            keyPair,
        )
        val sharedSecret = KeyAgreement.getInstance("ECDH").apply {
            init(keyPair.private)
            doPhase(serverPublicKey, true)
        }.generateSecret()
        val wrappingKey = hkdfSha256(
            input = sharedSecret,
            info = HKDF_INFO.toByteArray(Charsets.UTF_8),
            outputLength = MANIFEST_KEY_SIZE,
        )
        val nonceStart = RAW_PUBLIC_KEY_SIZE
        val nonce = sealed.copyOfRange(nonceStart, nonceStart + NONCE_SIZE)
        val ciphertext = sealed.copyOfRange(nonceStart + NONCE_SIZE, sealed.size)
        return aesGcmDecrypt(wrappingKey, nonce, ciphertext)
    }

    private fun decodeRawPublicKey(
        raw: ByteArray,
        clientKeyPair: KeyPair,
    ): java.security.PublicKey {
        require(raw.size == RAW_PUBLIC_KEY_SIZE && raw[0] == 4.toByte()) {
            "Invalid server public key"
        }
        val x = BigInteger(1, raw.copyOfRange(1, 33))
        val y = BigInteger(1, raw.copyOfRange(33, 65))
        val clientPublicKey = clientKeyPair.public as ECPublicKey
        return KeyFactory.getInstance("EC").generatePublic(
            ECPublicKeySpec(ECPoint(x, y), clientPublicKey.params)
        )
    }

    private fun decryptManifest(
        manifestKey: ByteArray,
        encryptedManifest: ByteArray,
    ): DecryptedShieldedManifest {
        require(manifestKey.size == MANIFEST_KEY_SIZE) { "Invalid manifest key" }
        require(encryptedManifest.size > NONCE_SIZE) { "Invalid encrypted manifest" }

        val nonce = encryptedManifest.copyOfRange(0, NONCE_SIZE)
        val ciphertext = encryptedManifest.copyOfRange(NONCE_SIZE, encryptedManifest.size)
        val plaintext = aesGcmDecrypt(manifestKey, nonce, ciphertext)
        require(plaintext.size > SEED_SIZE) { "Invalid decrypted manifest" }

        val seed = plaintext.copyOfRange(0, SEED_SIZE)
        val payload = JSONObject(
            String(plaintext, SEED_SIZE, plaintext.size - SEED_SIZE, Charsets.UTF_8)
        )
        return DecryptedShieldedManifest(
            mpd = payload.getString("mpd"),
            segmentSeed = seed,
        )
    }

    private fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            doFinal(ciphertext)
        }
    }

    private fun hkdfSha256(input: ByteArray, info: ByteArray, outputLength: Int): ByteArray {
        val extract = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(ByteArray(32), "HmacSHA256"))
        }.doFinal(input)
        val expand = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(extract, "HmacSHA256"))
        }
        val output = ByteArray(outputLength)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < outputLength) {
            expand.reset()
            expand.update(previous)
            expand.update(info)
            expand.update(counter.toByte())
            previous = expand.doFinal()
            val copyLength = minOf(previous.size, outputLength - offset)
            previous.copyInto(output, offset, 0, copyLength)
            offset += copyLength
            counter++
        }
        return output
    }

    private fun getBytes(label: String, url: String): ByteArray {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            Log.d(TAG, "$label response — httpStatus=${response.code}")
            check(response.isSuccessful) {
                "Shielded $label request failed: HTTP ${response.code}"
            }
            return response.body?.bytes() ?: error("Shielded manifest response was empty")
        }
    }

    private fun safeEndpoint(url: String): String {
        val parsed = Uri.parse(url)
        return Uri.Builder()
            .scheme(parsed.scheme)
            .encodedAuthority(parsed.encodedAuthority)
            .encodedPath(parsed.encodedPath)
            .build()
            .toString()
    }

    private fun elapsedMs(startNs: Long): Long {
        return (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000L
    }

    private fun encodeBase64Url(bytes: ByteArray): String {
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
    }

    private fun BigInteger.toFixedLengthBytes(length: Int): ByteArray {
        val bytes = toByteArray()
        return when {
            bytes.size == length -> bytes
            bytes.size > length -> bytes.copyOfRange(bytes.size - length, bytes.size)
            else -> ByteArray(length - bytes.size) + bytes
        }
    }
}
