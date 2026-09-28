package com.tpstreams.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrientationHelperTest {

    @Test
    fun `isOrientationLandscape returns false for cardinal portrait and non-landscape degrees`() {
        assertFalse(OrientationListener.isOrientationLandscape(0))
        assertFalse(OrientationListener.isOrientationLandscape(180))
        assertFalse(OrientationListener.isOrientationLandscape(359))
        assertFalse(OrientationListener.isOrientationLandscape(45))
        assertFalse(OrientationListener.isOrientationLandscape(135))
        assertFalse(OrientationListener.isOrientationLandscape(225))
        assertFalse(OrientationListener.isOrientationLandscape(315))
    }

    @Test
    fun `isOrientationLandscape returns true for standard landscape degrees`() {
        assertTrue(OrientationListener.isOrientationLandscape(90))
        assertTrue(OrientationListener.isOrientationLandscape(270))
    }

    @Test
    fun `isOrientationLandscape respects threshold boundaries for both landscape orientations`() {
        // Landscape range 1: 60..120 degrees
        assertFalse(OrientationListener.isOrientationLandscape(59))
        assertTrue(OrientationListener.isOrientationLandscape(60))
        assertTrue(OrientationListener.isOrientationLandscape(90))
        assertTrue(OrientationListener.isOrientationLandscape(120))
        assertFalse(OrientationListener.isOrientationLandscape(121))

        // Landscape range 2: 240..300 degrees
        assertFalse(OrientationListener.isOrientationLandscape(239))
        assertTrue(OrientationListener.isOrientationLandscape(240))
        assertTrue(OrientationListener.isOrientationLandscape(270))
        assertTrue(OrientationListener.isOrientationLandscape(300))
        assertFalse(OrientationListener.isOrientationLandscape(301))
    }
}
