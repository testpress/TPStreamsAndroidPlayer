package com.tpstreams.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageWatermarkConfigTest {

    @Test
    fun `test default values`() {
        val config = ImageWatermarkConfig(imageUrl = "https://example.com/avatar.png")

        assertEquals("https://example.com/avatar.png", config.imageUrl)
        assertEquals(48, config.width)
        assertEquals(48, config.height)
        assertEquals(92, config.x)
        assertEquals(88, config.y)
        assertEquals(1.0f, config.opacity, 0.001f)
    }

    @Test
    fun `test custom values within valid ranges`() {
        val config = ImageWatermarkConfig(
            imageUrl = "https://example.com/logo.png",
            width = 64,
            height = 64,
            x = 10,
            y = 20,
            opacity = 0.5f,
        )

        assertEquals("https://example.com/logo.png", config.imageUrl)
        assertEquals(64, config.width)
        assertEquals(64, config.height)
        assertEquals(10, config.x)
        assertEquals(20, config.y)
        assertEquals(0.5f, config.opacity, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test blank imageUrl throws exception`() {
        ImageWatermarkConfig(imageUrl = "   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test invalid negative x throws exception`() {
        ImageWatermarkConfig(imageUrl = "https://example.com/avatar.png", x = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test invalid x greater than 100 throws exception`() {
        ImageWatermarkConfig(imageUrl = "https://example.com/avatar.png", x = 101)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test invalid negative y throws exception`() {
        ImageWatermarkConfig(imageUrl = "https://example.com/avatar.png", y = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test invalid y greater than 100 throws exception`() {
        ImageWatermarkConfig(imageUrl = "https://example.com/avatar.png", y = 101)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test invalid negative opacity throws exception`() {
        ImageWatermarkConfig(imageUrl = "https://example.com/avatar.png", opacity = -0.1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test invalid opacity greater than 1 throws exception`() {
        ImageWatermarkConfig(imageUrl = "https://example.com/avatar.png", opacity = 1.1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test zero width throws exception`() {
        ImageWatermarkConfig(imageUrl = "https://example.com/avatar.png", width = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test zero height throws exception`() {
        ImageWatermarkConfig(imageUrl = "https://example.com/avatar.png", height = 0)
    }
}
