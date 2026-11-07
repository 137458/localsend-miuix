package org.localsend.miuix.updates

import org.localsend.miuix.util.Sha256
import java.io.File
import java.io.IOException

sealed interface DigestCheck {
    object Skipped : DigestCheck

    object Matched : DigestCheck

    data class Mismatched(
        val expected: String,
        val actual: String,
    ) : DigestCheck
}

/** GitHub asset digest 形如 "sha256:<hex>"；缺失或其它算法一律跳过校验。 */
object ApkDigest {
    private const val SHA256_HEX_LENGTH = 64

    fun expectedSha256(digest: String?): String? {
        val trimmed = digest?.trim().orEmpty()
        val prefix = "sha256:"
        if (!trimmed.startsWith(prefix, ignoreCase = true)) return null
        val hex = trimmed.substring(prefix.length).trim().lowercase()
        if (hex.length != SHA256_HEX_LENGTH) return null
        if (!hex.all { it in '0'..'9' || it in 'a'..'f' }) return null
        return hex
    }

    fun verify(
        file: File,
        digest: String?,
    ): DigestCheck {
        val expected = expectedSha256(digest) ?: return DigestCheck.Skipped
        return verify(expected, sha256Hex(file))
    }

    fun verify(
        expected: String?,
        actual: String,
    ): DigestCheck {
        val normalizedExpected = expected?.trim()?.lowercase().orEmpty()
        if (normalizedExpected.isEmpty()) return DigestCheck.Skipped
        val normalizedActual = actual.trim().lowercase()
        return if (normalizedActual == normalizedExpected) {
            DigestCheck.Matched
        } else {
            DigestCheck.Mismatched(expected = normalizedExpected, actual = normalizedActual)
        }
    }

    @Throws(IOException::class)
    fun sha256Hex(file: File): String = Sha256.hexDigest(file)
}
