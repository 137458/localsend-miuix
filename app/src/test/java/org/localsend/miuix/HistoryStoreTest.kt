package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.history.HistoryStore
import org.localsend.miuix.model.TransferHistoryItem
import org.localsend.miuix.model.TransferStatus
import java.io.File
import kotlin.io.path.createTempDirectory

class HistoryStoreTest {

    private fun item(id: String, alias: String = "Peer") = TransferHistoryItem(
        id = id,
        deviceAlias = alias,
        deviceIp = "192.168.1.8",
        isIncoming = true,
        fileCount = 1,
        totalSize = 10,
        status = TransferStatus.Completed,
        fileNames = listOf("a.txt")
    )

    private fun storeInTemp(): Pair<HistoryStore, File> {
        val dir = createTempDirectory("localsend-history").toFile()
        val file = File(dir, HistoryStore.FILENAME)
        return HistoryStore(file, maxItems = 3) to file
    }

    @Test
    fun missingAndCorruptFilesLoadAsEmpty() {
        val (store, file) = storeInTemp()
        assertTrue(store.load().isEmpty())
        file.writeText("{not-valid-json")
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun addCapsAtMaxAndRoundTripsThroughDisk() {
        val (store, _) = storeInTemp()
        var items = emptyList<TransferHistoryItem>()
        items = store.add(items, item("1"))
        items = store.add(items, item("2"))
        items = store.add(items, item("3"))
        items = store.add(items, item("4"))
        assertEquals(listOf("4", "3", "2"), items.map { it.id })
        assertEquals(listOf("4", "3", "2"), store.load().map { it.id })
    }

    @Test
    fun deleteAndClearPersist() {
        val (store, _) = storeInTemp()
        var items = store.add(emptyList(), item("1"))
        items = store.add(items, item("2"))
        items = store.delete(items, "2")
        assertEquals(listOf("1"), items.map { it.id })
        assertEquals(listOf("1"), store.load().map { it.id })
        assertTrue(store.clear().isEmpty())
        assertTrue(store.load().isEmpty())
    }
}
