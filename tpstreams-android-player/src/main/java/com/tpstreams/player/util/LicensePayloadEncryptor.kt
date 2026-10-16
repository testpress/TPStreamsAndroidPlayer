package com.tpstreams.player.util

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Helper to encrypt and decrypt license API payloads using AES-256-GCM.
 * Wire format: [12 bytes IV] + [Ciphertext + 16 bytes GCM Auth Tag]
 */
class LicensePayloadEncryptor(keyBytes: ByteArray) {

    constructor(keyString: String) : this(parseKey(keyString))

    val keyBytes: ByteArray = keyBytes.clone()
    private val secretKey: SecretKey = SecretKeySpec(this.keyBytes, "AES")
    private val secureRandom = SecureRandom()

    init {
        require(this.keyBytes.size == 32) {
            "AES-256 key must be exactly 32 bytes (256 bits), got ${this.keyBytes.size} bytes."
        }
    }

    fun encrypt(plainBytes: ByteArray): ByteArray {
        val iv = ByteArray(IV_LENGTH).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plainBytes)
        return iv + ciphertext
    }

    fun decrypt(encryptedBytes: ByteArray): ByteArray {
        require(encryptedBytes.size >= IV_LENGTH + (GCM_TAG_LENGTH / 8)) {
            "Invalid encrypted payload: size must be at least ${IV_LENGTH + (GCM_TAG_LENGTH / 8)} bytes"
        }
        val iv = encryptedBytes.copyOfRange(0, IV_LENGTH)
        val ciphertext = encryptedBytes.copyOfRange(IV_LENGTH, encryptedBytes.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128 // bits (16 bytes)
        private const val IV_LENGTH = 12 // bytes

        fun parseKey(key: String): ByteArray {
            // First try Base64 decoding
            val decoded = try {
                java.util.Base64.getDecoder().decode(key)
            } catch (_: Throwable) {
                try {
                    android.util.Base64.decode(key, android.util.Base64.DEFAULT)
                } catch (_: Throwable) {
                    null
                }
            }
            if (decoded != null && decoded.size == 32) {
                return decoded
            }
            val rawBytes = key.toByteArray(Charsets.UTF_8)
            if (rawBytes.size == 32) {
                return rawBytes
            }
            throw IllegalArgumentException(
                "License payload encryption key must be 32 bytes (256 bits), either raw string or Base64 encoded."
            )
        }
    }
}
