package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.localsend.miuix.model.FileItem
import org.localsend.miuix.network.LocalSendClient

class LocalSendClientSha256PrefillTest {
    @Test
    fun fillsOnlyMissingHashesAndSkipsExisting() {
        val a = FileItem(id = "a", name = "a.bin")
        val b = FileItem(id = "b", name = "b.bin", expectedSha256 = "preset")
        val computed = mutableListOf<String>()
        LocalSendClient.fillMissingSha256(listOf(a, b)) { item ->
            computed.add(item.id)
            item.id + "-hash"
        }
        assertEquals("a-hash", a.expectedSha256)
        assertEquals("preset", b.expectedSha256)
        assertEquals(listOf("a"), computed)
    }

    @Test
    fun computeFailureStoresNullWithoutCrash() {
        val a = FileItem(id = "a", name = "a.bin")
        LocalSendClient.fillMissingSha256(listOf(a)) { null }
        assertNull(a.expectedSha256)
    }
}
