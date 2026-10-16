package com.tpstreams.player.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.GeneralSecurityException
import kotlin.experimental.xor

class LicensePayloadEncryptorTest {

    private val validBase64Key = "7N9UuCkpKS9d84VAvMRFLos4AGCcS1aFsOjFKusmDsc="
    private val validRawKey = "KEY+p85TS9yuIVS719srZJ0CBzCi4MCN" // exactly 32 chars

    @Test
    fun `test encryptor parses Base64 key correctly`() {
        val encryptor = LicensePayloadEncryptor(validBase64Key)
        assertEquals("Key size must be exactly 32 bytes (256 bits)", 32, encryptor.keyBytes.size)
        assertTrue("Key bytes must not be all zeros", encryptor.keyBytes.any { it != 0.toByte() })
    }

    @Test
    fun `test encryptor parses raw 32-byte key correctly`() {
        val encryptor = LicensePayloadEncryptor(validRawKey)
        assertEquals("Key size must be exactly 32 bytes (256 bits)", 32, encryptor.keyBytes.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test invalid key length throws exception`() {
        LicensePayloadEncryptor("short-key")
    }

    @Test
    fun `test encrypt and decrypt round trip`() {
        val encryptor = LicensePayloadEncryptor(validBase64Key)
        val samplePayload = "test-widevine-drm-challenge-bytes-12345".toByteArray(Charsets.UTF_8)
        val encrypted = encryptor.encrypt(samplePayload)

        // 12 bytes IV + ciphertext + 16 bytes GCM tag
        assertEquals(12 + samplePayload.size + 16, encrypted.size)

        val decrypted = encryptor.decrypt(encrypted)
        assertArrayEquals(samplePayload, decrypted)
    }

    @Test
    fun `test consecutive encryptions generate different IVs`() {
        val encryptor = LicensePayloadEncryptor(validBase64Key)
        val samplePayload = "same-plain-text".toByteArray(Charsets.UTF_8)
        val encrypted1 = encryptor.encrypt(samplePayload)
        val encrypted2 = encryptor.encrypt(samplePayload)

        assertFalse("Ciphertexts must differ due to random IV", encrypted1.contentEquals(encrypted2))
        assertArrayEquals(encryptor.decrypt(encrypted1), encryptor.decrypt(encrypted2))
    }

    @Test(expected = GeneralSecurityException::class)
    fun `test tampering ciphertext causes decryption failure`() {
        val encryptor = LicensePayloadEncryptor(validBase64Key)
        val samplePayload = "sensitive-data".toByteArray(Charsets.UTF_8)
        val encrypted = encryptor.encrypt(samplePayload)

        // Corrupt one byte in the ciphertext portion
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1] xor 0x01.toByte())

        encryptor.decrypt(encrypted)
    }
}
