package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.localsend.miuix.discovery.FavoriteDevice
import org.localsend.miuix.discovery.FavoriteStore
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.DeviceType
import java.io.File

class FavoriteStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storeFile: File
    private lateinit var favoriteStore: FavoriteStore

    private fun testDevice(alias: String, ip: String, fingerprint: String = "fp-$ip") = Device(
        alias = alias,
        fingerprint = fingerprint,
        port = 53317,
        protocol = "http",
        download = false,
        ip = ip,
        deviceType = DeviceType.mobile
    )

    @Before
    fun setUp() {
        storeFile = tempFolder.newFile("favorite_devices.json")
        favoriteStore = FavoriteStore(storeFile, maxItems = 5)
    }

    @Test
    fun testEmptyOnStart() {
        val list = favoriteStore.load()
        assertTrue(list.isEmpty())
    }

    @Test
    fun testToggleFavoriteAddsAndRemoves() {
        val dev = testDevice("MacBook", "192.168.1.50")
        val afterAdd = favoriteStore.toggle(dev)
        assertEquals(1, afterAdd.size)
        assertEquals("MacBook", afterAdd[0].alias)
        assertTrue(favoriteStore.isFavorite(dev))

        val afterRemove = favoriteStore.toggle(dev)
        assertTrue(afterRemove.isEmpty())
        assertFalse(favoriteStore.isFavorite(dev))
    }

    @Test
    fun testFavoritePersistence() {
        val dev1 = testDevice("Phone", "192.168.1.10")
        val dev2 = testDevice("PC", "192.168.1.20")
        favoriteStore.toggle(dev1)
        favoriteStore.toggle(dev2)

        // 创建新 store 实例重新从磁盘加载
        val newStore = FavoriteStore(storeFile, maxItems = 5)
        val loaded = newStore.load()
        assertEquals(2, loaded.size)
        assertTrue(loaded.any { it.alias == "Phone" })
        assertTrue(loaded.any { it.alias == "PC" })
    }

    @Test
    fun testMaxItemsCapped() {
        val store = FavoriteStore(storeFile, maxItems = 3)
        for (i in 1..5) {
            store.toggle(testDevice("Dev$i", "192.168.1.$i"))
        }
        val loaded = store.load()
        assertEquals(3, loaded.size)
    }
}
