package org.localsend.miuix.updates

object UpdatePrompt {
    fun normalized(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        val withoutPrefix = if (trimmed.startsWith("v", ignoreCase = true)) trimmed.substring(1).trim() else trimmed
        return withoutPrefix.substringBefore('+').trim()
    }

    /** 语义化比较：数字段逐位比较，正式的 release 高于同版本 prerelease。 */
    fun compare(
        a: String,
        b: String,
    ): Int {
        val left = normalized(a)
        val right = normalized(b)
        val leftCore = left.substringBefore('-')
        val rightCore = right.substringBefore('-')
        val coreResult = compareCore(leftCore, rightCore)
        if (coreResult != 0) return coreResult

        val leftPre = left.substringAfter('-', "")
        val rightPre = right.substringAfter('-', "")
        return when {
            leftPre.isEmpty() && rightPre.isEmpty() -> 0
            leftPre.isEmpty() -> 1
            rightPre.isEmpty() -> -1
            else -> comparePrerelease(leftPre, rightPre)
        }
    }

    fun sameVersion(
        a: String?,
        b: String?,
    ): Boolean {
        val left = normalized(a)
        val right = normalized(b)
        if (left.isEmpty() || right.isEmpty()) return false
        return compare(left, right) == 0
    }

    fun shouldNotify(
        latestVersion: String?,
        ignoredVersion: String?,
        promptedVersion: String?,
    ): Boolean {
        if (normalized(latestVersion).isEmpty()) return false
        if (isIgnored(ignoredVersion, latestVersion)) return false
        if (sameVersion(promptedVersion, latestVersion)) return false
        return true
    }

    fun isIgnored(
        ignoredVersion: String?,
        latestVersion: String?,
    ): Boolean {
        if (normalized(ignoredVersion).isEmpty()) return false
        return sameVersion(ignoredVersion, latestVersion)
    }

    fun toggleIgnored(
        currentIgnored: String?,
        latestVersion: String?,
    ): String? {
        val latest = normalized(latestVersion)
        if (latest.isEmpty()) return null
        return if (isIgnored(currentIgnored, latest)) null else latest
    }

    private fun compareCore(
        a: String,
        b: String,
    ): Int {
        val left = a.split('.').map { it.takeWhile(Char::isDigit).toLongOrNull() ?: 0L }
        val right = b.split('.').map { it.takeWhile(Char::isDigit).toLongOrNull() ?: 0L }
        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val result = left.getOrElse(index) { 0L }.compareTo(right.getOrElse(index) { 0L })
            if (result != 0) return result
        }
        return 0
    }

    private fun comparePrerelease(
        a: String,
        b: String,
    ): Int {
        val left = a.split('.')
        val right = b.split('.')
        val size = minOf(left.size, right.size)
        for (index in 0 until size) {
            val l = left[index]
            val r = right[index]
            val lNum = l.toLongOrNull()
            val rNum = r.toLongOrNull()
            val result =
                when {
                    lNum != null && rNum != null -> lNum.compareTo(rNum)
                    lNum != null -> -1
                    rNum != null -> 1
                    else -> l.compareTo(r)
                }
            if (result != 0) return result
        }
        return left.size.compareTo(right.size)
    }
}
