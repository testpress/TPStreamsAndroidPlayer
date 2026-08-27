package com.tpstreams.player.ui

import com.tpstreams.player.Captions
import com.tpstreams.player.DownloadActions
import com.tpstreams.player.SettingsPanel
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerSheetManagerTest {

    @Test
    fun `getPlayer returns null when provider returns null`() {
        val sheetManager = PlayerSheetManager(
            settingsPanel = createDummySettingsPanel(),
            captions = createDummyCaptions(),
            downloadActions = createDummyDownloadActions(),
            playerProvider = { null }
        )

        assertNull(sheetManager.getPlayer())
    }

    private fun createDummySettingsPanel(): SettingsPanel {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = theUnsafeField.get(null)
        val allocateMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return allocateMethod.invoke(unsafe, SettingsPanel::class.java) as SettingsPanel
    }

    private fun createDummyCaptions(): Captions {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = theUnsafeField.get(null)
        val allocateMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return allocateMethod.invoke(unsafe, Captions::class.java) as Captions
    }

    private fun createDummyDownloadActions(): DownloadActions {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = theUnsafeField.get(null)
        val allocateMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return allocateMethod.invoke(unsafe, DownloadActions::class.java) as DownloadActions
    }
}
