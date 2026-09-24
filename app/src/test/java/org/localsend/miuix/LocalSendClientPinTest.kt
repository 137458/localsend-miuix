package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.network.LocalSendClient
import org.localsend.miuix.network.TargetPinRequiredException

class LocalSendClientPinTest {
    @Test
    fun urlWithoutPin() {
        val url = LocalSendClient.buildPrepareUploadUrl("https://192.168.1.100:53317", null)
        assertEquals("https://192.168.1.100:53317/api/localsend/v2/prepare-upload", url)
    }

    @Test
    fun urlWithBlankPin() {
        val url = LocalSendClient.buildPrepareUploadUrl("https://192.168.1.100:53317", "   ")
        assertEquals("https://192.168.1.100:53317/api/localsend/v2/prepare-upload", url)
    }

    @Test
    fun urlWithTargetPin() {
        val url = LocalSendClient.buildPrepareUploadUrl("https://192.168.1.100:53317", "1234")
        assertEquals("https://192.168.1.100:53317/api/localsend/v2/prepare-upload?pin=1234", url)
    }

    @Test
    fun urlWithSpecialCharPin() {
        val url = LocalSendClient.buildPrepareUploadUrl("https://192.168.1.100:53317", "my pin#1")
        assertEquals("https://192.168.1.100:53317/api/localsend/v2/prepare-upload?pin=my+pin%231", url)
    }

    @Test
    fun targetPinRequiredExceptionType() {
        val ex = TargetPinRequiredException("PIN required")
        assertTrue(ex is IllegalStateException)
        assertEquals("PIN required", ex.message)
    }
}
