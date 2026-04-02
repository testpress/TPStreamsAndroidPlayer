package com.tpstreams.player.util

import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Singleton to manage global playback history for production debugging.
 * Stores a chronological list of video events to help trace decoder/memory issues across multiple sessions.
 */
internal object PlaybackHistoryManager {
    private const val MAX_LOG_LINES = 1000
    private val logHistory = ConcurrentLinkedDeque<String>()

    /**
     * Records a log message in the global history.
     */
    fun recordLog(message: String) {
        logHistory.add(message)
        // Keep the history size within limits
        while (logHistory.size > MAX_LOG_LINES) {
            logHistory.pollFirst()
        }
    }

    /**
     * Returns the entire history as a formatted string block.
     * Use this when attaching to Sentry or other error reporting.
     */
    fun getFullHistory(): String {
        return logHistory.joinToString("\n")
    }

    /**
     * Returns the history as a list of strings if needed for tabular context.
     */
    fun getHistoryList(): List<String> {
        return logHistory.toList()
    }

    /**
     * Clears all history. Usually done at app level if required.
     */
    fun clearHistory() {
        logHistory.clear()
    }
}
