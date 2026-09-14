package org.localsend.miuix.util

object SavePathHelper {
    data class PathComponents(
        val subDirectory: String,
        val fileName: String
    )

    /**
     * Resolves a raw relative path from LocalSend protocol (which may contain slashes or redundant separators)
     * into a safe subDirectory and leaf fileName.
     * Sanitizes path traversal attempts ("..") and strips leading/trailing slashes.
     */
    fun resolve(rawPath: String): PathComponents {
        val normalized = rawPath.replace('\\', '/').trim()
        val parts = normalized.split('/').filter { it.isNotEmpty() && it != "." && it != ".." }
        if (parts.isEmpty()) {
            return PathComponents("", "unnamed")
        }
        val fileName = parts.last()
        val subDirectory = parts.dropLast(1).joinToString("/")
        return PathComponents(subDirectory = subDirectory, fileName = fileName)
    }

    /**
     * Formats MediaStore RELATIVE_PATH. MediaStore expects a trailing slash (e.g. "Download/LocalSend/sub/").
     */
    fun buildMediaStoreRelativePath(baseDir: String, subDirectory: String): String {
        val cleanBase = baseDir.trim().trimEnd('/')
        return if (subDirectory.isBlank()) {
            "$cleanBase/"
        } else {
            val cleanSub = subDirectory.trim().trim('/')
            "$cleanBase/$cleanSub/"
        }
    }

    /**
     * Splits subDirectory path into individual directory names for recursive SAF creation.
     */
    fun splitSegments(subDirectory: String): List<String> {
        return subDirectory.split('/').filter { it.isNotEmpty() && it != "." && it != ".." }
    }
}
