package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.localsend.miuix.updates.ApkDigest
import org.localsend.miuix.updates.DigestCheck
import org.localsend.miuix.util.Sha256
import java.io.File

/** GitHub asset digest 解析与校验：只认 sha256，缺失跳过，不一致必须给出可排查的两段摘要。 */
class ApkDigestTest {
    private val hex = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"

    @Test
    fun `parses only sha256 digests as lowercase hex`() {
        assertEquals(hex, ApkDigest.expectedSha256("sha256:$hex"))
        assertEquals(hex, ApkDigest.expectedSha256("SHA256:${hex.uppercase()}"))
        assertNull(ApkDigest.expectedSha256("md5:$hex"))
        assertNull(ApkDigest.expectedSha256("sha256:short"))
        assertNull(ApkDigest.expectedSha256("sha256:${hex}0"))
        assertNull(ApkDigest.expectedSha256(null))
        assertNull(ApkDigest.expectedSha256("   "))
    }

    @Test
    fun `verify distinguishes matched mismatched and skipped`() {
        assertEquals(DigestCheck.Matched, ApkDigest.verify(hex, hex))
        assertEquals(DigestCheck.Matched, ApkDigest.verify(hex, hex.uppercase()))
        assertEquals(
            DigestCheck.Mismatched(expected = hex, actual = "deadbeef"),
            ApkDigest.verify(hex, "DEADBEEF"),
        )
        assertEquals(DigestCheck.Skipped, ApkDigest.verify(null, hex))
    }

    @Test
    fun `file digest streams and compares the whole file`() {
        val file = File.createTempFile("apk-digest", ".bin")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
            assertEquals(Sha256.hexDigest(file), ApkDigest.sha256Hex(file))
            assertEquals(DigestCheck.Matched, ApkDigest.verify(file, "sha256:${Sha256.hexDigest(file)}"))
            assertEquals(DigestCheck.Skipped, ApkDigest.verify(file, null))
        } finally {
            file.delete()
        }
    }
}
