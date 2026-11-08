package org.localsend.miuix

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.transfer.isReceivedSizeConsistent

class UploadIntegrityTest {
    @Test
    fun declaredSizeMustMatchExactly() {
        assertTrue(isReceivedSizeConsistent(declaredSize = 1024, receivedBytes = 1024))
        assertFalse(isReceivedSizeConsistent(declaredSize = 1024, receivedBytes = 1023))
        assertFalse(isReceivedSizeConsistent(declaredSize = 1024, receivedBytes = 2048))
    }

    @Test
    fun unknownDeclaredSizeIsNotChecked() {
        assertTrue(isReceivedSizeConsistent(declaredSize = 0, receivedBytes = 0))
        assertTrue(isReceivedSizeConsistent(declaredSize = 0, receivedBytes = 4096))
        assertTrue(isReceivedSizeConsistent(declaredSize = -1, receivedBytes = 4096))
    }
}
