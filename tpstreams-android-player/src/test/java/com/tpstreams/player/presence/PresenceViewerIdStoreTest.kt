package com.tpstreams.player.presence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val UUID_PATTERN = Regex(
    "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
    RegexOption.IGNORE_CASE
)

private class InMemoryKeyValueStore : PersistentKeyValueStore {
    private val values = mutableMapOf<String, String>()

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }
}

class PresenceViewerIdStoreTest {

    @Test
    fun `generates a UUID-shaped id`() {
        val id = PresenceViewerIdStore(InMemoryKeyValueStore()).getOrCreate()

        assertTrue(UUID_PATTERN.matches(id))
    }

    @Test
    fun `persists the generated id in the underlying store`() {
        val store = InMemoryKeyValueStore()

        val id = PresenceViewerIdStore(store).getOrCreate()

        assertEquals(id, store.getString("viewer_id"))
    }

    @Test
    fun `returns the same id on a later call against the same store, as across an app restart`() {
        val store = InMemoryKeyValueStore()

        val first = PresenceViewerIdStore(store).getOrCreate()
        val second = PresenceViewerIdStore(store).getOrCreate()

        assertEquals(first, second)
    }

    @Test
    fun `returns different ids for independent stores`() {
        val first = PresenceViewerIdStore(InMemoryKeyValueStore()).getOrCreate()
        val second = PresenceViewerIdStore(InMemoryKeyValueStore()).getOrCreate()

        assertNotEquals(first, second)
    }
}
