package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.localsend.miuix.util.Sha256
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

class Sha256Test {
    @get:Rule
    val tmp = TemporaryFolder()

    /** 独立参照实现：单次 update 全量字节，与被测的逐块流式路径互为差分。 */
    private fun expectedSha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** 确定性伪随机字节（LCG），保证差异化数据且测试可重复。 */
    private fun pseudoRandomBytes(
        size: Int,
        seed: Long,
    ): ByteArray {
        var state = seed
        return ByteArray(size) {
            state = state * 6364136223846793005L + 1442695040888963407L
            (state ushr 33).toByte()
        }
    }

    /** 每次 read 最多返回 [maxRead] 字节的流，模拟 ContentResolver 可能的短读。 */
    private class ShortReadInputStream(
        private val data: ByteArray,
        private val maxRead: Int,
    ) : InputStream() {
        private var pos = 0

        override fun read(): Int = if (pos >= data.size) -1 else data[pos++].toInt() and 0xFF

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int {
            if (pos >= data.size) return -1
            val n = minOf(len, maxRead, data.size - pos)
            System.arraycopy(data, pos, b, off, n)
            pos += n
            return n
        }
    }

    @Test
    fun knownDigests_emptyAbcAndFox() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.hexDigest(ByteArrayInputStream(ByteArray(0))),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hexDigest("abc".toByteArray().inputStream()),
        )
        assertEquals(
            "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592",
            Sha256.hexDigest("The quick brown fox jumps over the lazy dog".toByteArray().inputStream()),
        )
    }

    @Test
    fun bufferBoundaries_matchSingleUpdateReference() {
        for (size in listOf(Sha256.BUFFER_SIZE - 1, Sha256.BUFFER_SIZE, Sha256.BUFFER_SIZE + 1, 2 * Sha256.BUFFER_SIZE + 3)) {
            val data = pseudoRandomBytes(size, seed = size.toLong() + 42)
            assertEquals("size=$size", expectedSha256(data), Sha256.hexDigest(data.inputStream()))
        }
    }

    @Test
    fun shortReads_produceSameDigest() {
        for (size in listOf(7, Sha256.BUFFER_SIZE - 1, Sha256.BUFFER_SIZE + 1)) {
            val data = pseudoRandomBytes(size, seed = 7L)
            assertEquals("size=$size", expectedSha256(data), Sha256.hexDigest(ShortReadInputStream(data, maxRead = 7)))
        }
    }

    @Test
    fun fileVariant_matchesStreamVariant() {
        val data = pseudoRandomBytes(Sha256.BUFFER_SIZE + 5, seed = 99L)
        val file: File = tmp.newFile("sample.bin")
        file.writeBytes(data)
        assertEquals(expectedSha256(data), Sha256.hexDigest(file))
    }
}
