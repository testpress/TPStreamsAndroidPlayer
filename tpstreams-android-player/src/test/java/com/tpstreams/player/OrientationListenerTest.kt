package com.tpstreams.player

import android.content.Context
import android.view.OrientationEventListener.ORIENTATION_UNKNOWN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class OrientationListenerTest {

    private val context = mock(Context::class.java)
    private lateinit var listener: OrientationListener
    private val capturedChanges = mutableListOf<Boolean>()

    @Before
    fun setUp() {
        listener = OrientationListener(context).apply {
            autoRotationChecker = { true }
            setOnChangeListener { isLandscape ->
                capturedChanges.add(isLandscape)
            }
        }
    }

    @Test
    fun `when auto-rotation is disabled, onOrientationChanged does nothing`() {
        listener.autoRotationChecker = { false }

        // Send orientation events
        listener.onOrientationChanged(0)
        listener.onOrientationChanged(90)
        listener.onOrientationChanged(180)

        assertTrue(capturedChanges.isEmpty())
    }

    @Test
    fun `when orientation is unknown, onOrientationChanged does nothing`() {
        listener.onOrientationChanged(ORIENTATION_UNKNOWN)

        assertTrue(capturedChanges.isEmpty())
    }

    @Test
    fun `initial reading in landscape records baseline and does not fire onChange`() {
        // First reading: phone is held horizontally
        listener.onOrientationChanged(90)

        // Baseline should be established without false trigger
        assertTrue(capturedChanges.isEmpty())
    }

    @Test
    fun `initial reading in portrait records baseline and does not fire onChange`() {
        // First reading: phone is held vertically
        listener.onOrientationChanged(0)

        // Baseline should be established without false trigger
        assertTrue(capturedChanges.isEmpty())
    }

    @Test
    fun `rotating from baseline landscape to portrait fires onChange with false`() {
        // 1. Initial reading while in landscape (e.g. exiting fullscreen)
        listener.onOrientationChanged(90)
        assertTrue(capturedChanges.isEmpty())

        // 2. Physical rotation to portrait
        listener.onOrientationChanged(0)

        assertEquals(1, capturedChanges.size)
        assertFalse(capturedChanges.first())
    }

    @Test
    fun `rotating from baseline portrait to landscape fires onChange with true`() {
        // 1. Initial reading in portrait
        listener.onOrientationChanged(0)
        assertTrue(capturedChanges.isEmpty())

        // 2. Physical rotation to landscape
        listener.onOrientationChanged(90)

        assertEquals(1, capturedChanges.size)
        assertTrue(capturedChanges.first())
    }

    @Test
    fun `remaining within landscape does not fire duplicate onChange events`() {
        // Baseline portrait
        listener.onOrientationChanged(0)

        // Rotate to landscape
        listener.onOrientationChanged(90)
        assertEquals(1, capturedChanges.size)
        assertTrue(capturedChanges.last())

        // Minor tilt within landscape (e.g. 85, 100 degrees)
        listener.onOrientationChanged(85)
        listener.onOrientationChanged(100)
        listener.onOrientationChanged(270) // reverse landscape is also landscape

        // Should not trigger duplicate events since orientation is still landscape
        assertEquals(1, capturedChanges.size)
    }

    @Test
    fun `landscape angle ranges are recognized correctly`() {
        // Landscape range 1: 60..120
        // Landscape range 2: 240..300
        val landscapeAngles = listOf(60, 90, 120, 240, 270, 300)
        val portraitAngles = listOf(0, 59, 121, 180, 239, 301, 359)

        // Establish portrait baseline
        listener.onOrientationChanged(0)

        for (angle in landscapeAngles) {
            listener.onOrientationChanged(angle)
            assertTrue("Expected landscape for angle $angle", capturedChanges.last())

            // Rotate back to portrait
            listener.onOrientationChanged(0)
            assertFalse("Expected portrait after returning to 0", capturedChanges.last())
        }

        // Test non-landscape angles from portrait baseline do not trigger landscape
        capturedChanges.clear()
        val newListener = OrientationListener(context).apply {
            autoRotationChecker = { true }
            setOnChangeListener { isLandscape -> capturedChanges.add(isLandscape) }
        }
        newListener.onOrientationChanged(0) // baseline portrait

        for (angle in portraitAngles) {
            newListener.onOrientationChanged(angle)
            // Stays in portrait, so no change should fire
            assertTrue("Expected no change for portrait angle $angle", capturedChanges.isEmpty())
        }
    }

    @Test
    fun `stop resets baseline so subsequent restart records baseline without false trigger`() {
        // Start in landscape -> established baseline
        listener.onOrientationChanged(90)
        assertTrue(capturedChanges.isEmpty())

        // Stop the listener (e.g. view detached during exit fullscreen)
        listener.stop()

        // Restart listener in landscape again
        listener.onOrientationChanged(90)

        // Baseline should be re-established without a false orientation change
        assertTrue(capturedChanges.isEmpty())
    }
}
