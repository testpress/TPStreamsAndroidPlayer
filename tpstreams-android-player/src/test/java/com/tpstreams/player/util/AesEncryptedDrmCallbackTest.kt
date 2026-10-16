package com.tpstreams.player.util

import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.UUID

class AesEncryptedDrmCallbackTest {

    private val testKey = "7N9UuCkpKS9d84VAvMRFLos4AGCcS1aFsOjFKusmDsc="
    private lateinit var mockDelegate: HttpMediaDrmCallback
    private lateinit var encryptor: LicensePayloadEncryptor
    private lateinit var callback: AesEncryptedDrmCallback

    @Before
    fun setUp() {
        mockDelegate = mock(HttpMediaDrmCallback::class.java)
        encryptor = LicensePayloadEncryptor(testKey)
        callback = AesEncryptedDrmCallback(mockDelegate, encryptor)
    }

    @Test
    fun `test header is set on delegate upon initialization`() {
        verify(mockDelegate).setKeyRequestProperty(
            AesEncryptedDrmCallback.HEADER_PAYLOAD_ENCRYPTION,
            AesEncryptedDrmCallback.ENCRYPTION_ALGORITHM
        )
    }

    @Test
    fun `test executeKeyRequest encrypts challenge and decrypts response`() {
        val uuid = UUID.randomUUID()
        val plainChallenge = "raw-widevine-challenge".toByteArray(Charsets.UTF_8)
        val plainLicense = "raw-widevine-license".toByteArray(Charsets.UTF_8)
        val encryptedLicenseResponse = encryptor.encrypt(plainLicense)

        val request = ExoMediaDrm.KeyRequest(plainChallenge, "https://app.tpstreams.com/drm_license/")

        val captor = ArgumentCaptor.forClass(ExoMediaDrm.KeyRequest::class.java)
        `when`(mockDelegate.executeKeyRequest(org.mockito.ArgumentMatchers.eq(uuid), captor.capture()))
            .thenReturn(encryptedLicenseResponse)

        val result = callback.executeKeyRequest(uuid, request)

        // The delegate should have received encrypted challenge
        val forwardedRequest = captor.value
        val decryptedForwardedData = encryptor.decrypt(forwardedRequest.data)
        assertArrayEquals(plainChallenge, decryptedForwardedData)

        // The callback should return decrypted license bytes
        assertArrayEquals(plainLicense, result)
    }

    @Test
    fun `test executeProvisionRequest delegates unencrypted`() {
        val uuid = UUID.randomUUID()
        val provisionRequest = ExoMediaDrm.ProvisionRequest("data".toByteArray(), "https://google.com/cert")
        val expectedResponse = "cert-response".toByteArray()

        `when`(mockDelegate.executeProvisionRequest(uuid, provisionRequest)).thenReturn(expectedResponse)

        val response = callback.executeProvisionRequest(uuid, provisionRequest)
        assertArrayEquals(expectedResponse, response)
        verify(mockDelegate).executeProvisionRequest(uuid, provisionRequest)
    }
}
