package org.localsend.miuix.network

/**
 * 「先落临时位置、校验通过才转正」的临时文件命名与残留清理规则。
 *
 * 临时名带本应用专有标记，清理只认这一规则，只扫描本次保存目标目录且有深度/数量上限，
 * 不会把用户文件或其他应用的文件当成残留删除；旧的 `.part-<id>` 命名从未发布，无需兼容。
 */
internal object DeferredTempNames {
    const val MARKER = ".localsend-miuix-tmp-"

    private const val SUFFIX_LENGTH = 8

    fun of(
        finalName: String,
        fileId: String,
    ): String = "$finalName$MARKER${fileId.take(SUFFIX_LENGTH).padEnd(SUFFIX_LENGTH, '0')}"

    /** 仅当“非空前缀 + 标记 + 8 位字母数字”时判定为本应用临时文件。 */
    fun isTempFile(name: String?): Boolean {
        if (name == null) return false
        val markerIndex = name.lastIndexOf(MARKER)
        if (markerIndex <= 0) return false
        val suffix = name.substring(markerIndex + MARKER.length)
        return suffix.length == SUFFIX_LENGTH && suffix.all { it.isLetterOrDigit() }
    }
}

/** 目录遍历的最小抽象，便于在纯 JVM 单测中验证清理边界。 */
internal interface SweepNode {
    val name: String?
    val isDirectory: Boolean

    fun children(): List<SweepNode>

    fun delete(): Boolean
}

/**
 * 有界残留清理：只删除命名符合 [DeferredTempNames] 的文件，绝不删除目录或根节点。
 * [maxDepth] 与 [maxEntries] 限制扫描范围，避免启动时遍历整片存储。
 */
internal class TempFileSweeper(
    private val maxDepth: Int = DEFAULT_MAX_DEPTH,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    fun sweep(root: SweepNode): Int {
        var visited = 0
        var removed = 0
        val queue = java.util.ArrayDeque<Pair<SweepNode, Int>>()
        queue.add(root to 0)
        while (queue.isNotEmpty() && visited < maxEntries) {
            val (dir, depth) = queue.poll()
            val children = runCatching { dir.children() }.getOrDefault(emptyList())
            for (child in children) {
                if (visited >= maxEntries) break
                visited++
                if (child.isDirectory) {
                    if (depth + 1 < maxDepth) queue.add(child to depth + 1)
                } else if (DeferredTempNames.isTempFile(child.name)) {
                    if (runCatching { child.delete() }.getOrDefault(false)) removed++
                }
            }
        }
        return removed
    }

    companion object {
        const val DEFAULT_MAX_DEPTH = 4
        const val DEFAULT_MAX_ENTRIES = 500
    }
}

/**
 * SAF provider 不支持重命名时的复制回退：复制成功后再删临时文件；
 * 复制失败时由 [copyToFinal] 自行删除半成品，本函数返回 false 且保留临时文件交给调用方回滚，
 * 保证任何路径都不会留下内容不完整的最终文件。
 */
internal fun commitDeferredWithFallback(
    rename: () -> Boolean,
    copyToFinal: () -> Boolean,
    deleteTemp: () -> Unit,
): Boolean {
    if (runCatching(rename).getOrDefault(false)) return true
    if (!runCatching(copyToFinal).getOrDefault(false)) return false
    runCatching(deleteTemp)
    return true
}
