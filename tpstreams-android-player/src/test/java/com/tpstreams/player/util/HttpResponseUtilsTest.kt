package com.tpstreams.player.util

import androidx.media3.datasource.HttpDataSource
import com.tpstreams.player.constants.PlaybackError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class HttpResponseUtilsTest {

    private fun createHttpException(code: Int): HttpDataSource.InvalidResponseCodeException {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = theUnsafeField.get(null)
        val allocateMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        val exception = allocateMethod.invoke(unsafe, HttpDataSource.InvalidResponseCodeException::class.java) as HttpDataSource.InvalidResponseCodeException
        val codeField = HttpDataSource.InvalidResponseCodeException::class.java.getDeclaredField("responseCode").apply { isAccessible = true }
        codeField.setInt(exception, code)
        return exception
    }

    @Test
    fun `findHttpResponseCode extracts code from InvalidResponseCodeException`() {
        val exception = createHttpException(404)
        assertEquals(404, exception.findHttpResponseCode())
    }

    @Test
    fun `findHttpResponseCode returns null for non-HTTP exceptions`() {
        val exception = IOException("Generic IO exception")
        assertNull(exception.findHttpResponseCode())
    }

    @Test
    fun `isAuthOrContentHttpFailure returns true for 401, 403, and 404`() {
        assertTrue(createHttpException(401).isAuthOrContentHttpFailure())
        assertTrue(createHttpException(403).isAuthOrContentHttpFailure())
        assertTrue(createHttpException(404).isAuthOrContentHttpFailure())
        assertFalse(createHttpException(500).isAuthOrContentHttpFailure())
        assertFalse(createHttpException(200).isAuthOrContentHttpFailure())
    }

    @Test
    fun `isLiveStreamEndHttpError returns true for non-auth non-server error status codes`() {
        // Codes indicative of stream ended/removed without being an auth or server failure
        assertTrue(createHttpException(410).isLiveStreamEndHttpError())
        assertTrue(createHttpException(400).isLiveStreamEndHttpError())

        // Excluded codes should return false
        assertFalse(createHttpException(401).isLiveStreamEndHttpError())
        assertFalse(createHttpException(403).isLiveStreamEndHttpError())
        assertFalse(createHttpException(404).isLiveStreamEndHttpError())
        assertFalse(createHttpException(500).isLiveStreamEndHttpError())
        assertFalse(createHttpException(502).isLiveStreamEndHttpError())
        assertFalse(createHttpException(599).isLiveStreamEndHttpError())
        assertFalse(IOException("No code").isLiveStreamEndHttpError())
    }


    @Test
    fun `toPlaybackErrorFromHttpStatus maps HTTP codes to PlaybackError accurately`() {
        assertEquals(PlaybackError.INVALID_ASSETS_ID, 404.toPlaybackErrorFromHttpStatus())
        assertEquals(PlaybackError.INVALID_ACCESS_TOKEN_FOR_ASSETS, 401.toPlaybackErrorFromHttpStatus())
        assertEquals(PlaybackError.INVALID_ACCESS_TOKEN_FOR_ASSETS, 403.toPlaybackErrorFromHttpStatus())
        assertEquals(PlaybackError.SERVER_ERROR, 500.toPlaybackErrorFromHttpStatus())
        assertEquals(PlaybackError.SERVER_ERROR, 503.toPlaybackErrorFromHttpStatus())
        assertEquals(PlaybackError.UNSPECIFIED, 418.toPlaybackErrorFromHttpStatus())
    }
}
