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

    private fun testDevice(
        alias: String,
        ip: String,
        fingerprint: String = "fp-$ip",
    ) = Device(
        alias = alias,
        fingerprint = fingerprint,
        port = 53317,
        protocol = "http",
        download = false,
        ip = ip,
        deviceType = DeviceType.mobile,
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

    @Test
    fun testDeviceTypeEnumAndMatches() {
        val dev =
            Device(
                alias = "ServerNode",
                fingerprint = "fp-server",
                port = 53317,
                protocol = "https",
                ip = "192.168.1.100",
                deviceType = DeviceType.server,
            )
        val fav = FavoriteDevice.fromDevice(dev)
        assertEquals(DeviceType.server, fav.deviceType)
        assertTrue(fav.matches(dev))

        val converted = fav.toDevice()
        assertEquals(DeviceType.server, converted.deviceType)
        assertEquals("ServerNode", converted.alias)
    }

    @Test
    fun testMissingFileLoadsEmpty() {
        val store = FavoriteStore(File(tempFolder.root, "does_not_exist.json"))
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun testPersistRoundTripsAllFields() {
        favoriteStore.persist(
            listOf(
                FavoriteDevice(alias = "Alpha", fingerprint = "fp-alpha", ip = "10.0.0.1", deviceType = DeviceType.desktop),
                FavoriteDevice(
                    alias = "Beta",
                    fingerprint = "fp-beta",
                    ip = "10.0.0.2",
                    port = 53318,
                    protocol = "https",
                    deviceType = DeviceType.tablet,
                    customAlias = "Renamed Beta",
                ),
            ),
        )
        val reloaded = FavoriteStore(storeFile, maxItems = 5).load()
        assertEquals(2, reloaded.size)
        assertEquals(DeviceType.desktop, reloaded[0].deviceType)
        assertEquals(53318, reloaded[1].port)
        assertEquals("https", reloaded[1].protocol)
        assertEquals("Renamed Beta", reloaded[1].customAlias)
    }

    @Test
    fun testTogglePrependsNewestAndCaps() {
        val store = FavoriteStore(storeFile, maxItems = 2)
        store.toggle(testDevice("One", "10.0.0.1", fingerprint = "fp-1"))
        store.toggle(testDevice("Two", "10.0.0.2", fingerprint = "fp-2"))
        val capped = store.toggle(testDevice("Three", "10.0.0.3", fingerprint = "fp-3"))
        assertEquals(listOf("Three", "Two"), capped.map { it.alias })
        assertEquals(listOf("Three", "Two"), FavoriteStore(storeFile, maxItems = 2).load().map { it.alias })
    }

    @Test
    fun testMatchesFallsBackToIpAndPortWhenFingerprintBlank() {
        favoriteStore.persist(listOf(FavoriteDevice(alias = "NoFp", fingerprint = "", ip = "10.0.0.7", port = 53317)))
        assertTrue(favoriteStore.isFavorite(testDevice("Peer", "10.0.0.7", fingerprint = "real-fp")))
        assertFalse(
            favoriteStore.isFavorite(
                testDevice("Peer", "10.0.0.7", fingerprint = "real-fp").copy(port = 53318),
            ),
        )
    }

    @Test
    fun testFingerprintMatchIgnoresCaseAndFormatting() {
        favoriteStore.persist(listOf(FavoriteDevice(alias = "Fp", fingerprint = "AA:BB:CC", ip = "10.0.0.9", port = 53317)))
        assertTrue(favoriteStore.isFavorite(testDevice("Peer", "10.0.0.9", fingerprint = "aa:bb:cc")))
        assertFalse(favoriteStore.isFavorite(testDevice("Peer", "10.0.0.9", fingerprint = "aa:bb:cd")))
    }

    @Test
    fun testRemoveDeletesOnlyMatchingDevice() {
        val store = FavoriteStore(storeFile, maxItems = 5)
        store.toggle(testDevice("One", "10.0.0.1", fingerprint = "fp-1"))
        store.toggle(testDevice("Two", "10.0.0.2", fingerprint = "fp-2"))
        val remaining = store.remove(testDevice("One", "10.0.0.1", fingerprint = "fp-1"))
        assertEquals(listOf("Two"), remaining.map { it.alias })
        assertEquals(listOf("Two"), FavoriteStore(storeFile, maxItems = 5).load().map { it.alias })
    }

    @Test
    fun testCorruptFileLoadsEmpty() {
        storeFile.writeText("{ this is not valid json")
        assertTrue(favoriteStore.load().isEmpty())
    }

    @Test
    fun testToDevicePrefersNonBlankCustomAlias() {
        val renamed = FavoriteDevice(alias = "Original", fingerprint = "fp", ip = "10.0.0.1", customAlias = "Renamed").toDevice()
        assertEquals("Renamed", renamed.alias)

        val blankCustom = FavoriteDevice(alias = "Original", fingerprint = "fp", ip = "10.0.0.1", customAlias = "   ").toDevice()
        assertEquals("Original", blankCustom.alias)
    }

    @Test
    fun testFromDeviceCopiesCoreFields() {
        val original =
            Device(
                alias = "X",
                fingerprint = "fp-x",
                ip = "10.0.0.4",
                port = 1234,
                protocol = "https",
                deviceModel = "Model",
                deviceType = DeviceType.desktop,
                download = true,
            )
        val favorite = FavoriteDevice.fromDevice(original)
        assertEquals("fp-x", favorite.fingerprint)
        assertEquals("10.0.0.4", favorite.ip)
        assertEquals(1234, favorite.port)
        assertEquals("https", favorite.protocol)
        assertEquals("Model", favorite.deviceModel)
        assertEquals(DeviceType.desktop, favorite.deviceType)
        assertTrue(favorite.download)
    }
}
