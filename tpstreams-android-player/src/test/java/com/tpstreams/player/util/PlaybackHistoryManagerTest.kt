package com.tpstreams.player.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackHistoryManagerTest {
    @Test
    fun `history is isolated per player`() {
        val firstPlayerHistory = PlaybackHistoryManager()
        val secondPlayerHistory = PlaybackHistoryManager()

        firstPlayerHistory.recordLog("first player")
        secondPlayerHistory.recordLog("second player")

        assertEquals(listOf("first player"), firstPlayerHistory.getHistoryList())
        assertEquals(listOf("second player"), secondPlayerHistory.getHistoryList())
    }

    @Test
    fun `history keeps the latest five hundred lines`() {
        val history = PlaybackHistoryManager()

        repeat(501) { history.recordLog("line $it") }

        assertEquals(500, history.getHistoryList().size)
        assertEquals("line 1", history.getHistoryList().first())
        assertEquals("line 500", history.getHistoryList().last())
    }
}
