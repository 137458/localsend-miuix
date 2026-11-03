package org.localsend.miuix

import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.localsend.miuix.core.JsonFileStore
import java.io.File

class JsonFileStoreTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Serializable
    data class TestItem(
        val id: String,
        val value: Int,
    )

    @Test
    fun missingFileReturnsEmpty() {
        val file = File(tempFolder.root, "missing.json")
        val store = JsonFileStore(file, TestItem.serializer())
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun blankFileReturnsEmpty() {
        val file = tempFolder.newFile("blank.json")
        file.writeText("   \n  ")
        val store = JsonFileStore(file, TestItem.serializer())
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun corruptFileReturnsEmpty() {
        val file = tempFolder.newFile("corrupt.json")
        file.writeText("{ this is invalid json content")
        val store = JsonFileStore(file, TestItem.serializer())
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun persistAndLoadRoundTrip() {
        val file = File(tempFolder.root, "items.json")
        val store = JsonFileStore(file, TestItem.serializer())
        val items = listOf(TestItem("1", 100), TestItem("2", 200))
        store.persist(items)

        val reloaded = store.load()
        assertEquals(2, reloaded.size)
        assertEquals("1", reloaded[0].id)
        assertEquals(100, reloaded[0].value)
        assertEquals("2", reloaded[1].id)
        assertEquals(200, reloaded[1].value)
    }

    @Test
    fun persistCapsAtMaxItems() {
        val file = File(tempFolder.root, "capped.json")
        val store = JsonFileStore(file, TestItem.serializer(), maxItems = 2)
        val items = listOf(TestItem("1", 10), TestItem("2", 20), TestItem("3", 30))
        store.persist(items)

        val reloaded = store.load()
        assertEquals(2, reloaded.size)
        assertEquals(listOf("1", "2"), reloaded.map { it.id })
    }

    @Test
    fun parentDirectoryAutoCreated() {
        val nestedDir = File(tempFolder.root, "nested/sub/dir")
        val file = File(nestedDir, "nested.json")
        val store = JsonFileStore(file, TestItem.serializer())
        store.persist(listOf(TestItem("nested", 999)))

        assertTrue(file.exists())
        assertEquals(1, store.load().size)
        assertEquals("nested", store.load()[0].id)
    }

    @Test
    fun tempFileCleanedUpAfterPersist() {
        val file = File(tempFolder.root, "atomic.json")
        val store = JsonFileStore(file, TestItem.serializer())
        store.persist(listOf(TestItem("a", 1)))

        val tmpFile = File(tempFolder.root, "atomic.json.tmp")
        assertFalse(tmpFile.exists())
    }
}
