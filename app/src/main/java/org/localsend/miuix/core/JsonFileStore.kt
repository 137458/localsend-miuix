package org.localsend.miuix.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 通用 JSON 列表本地文件持久化存储工具。
 *
 * 核心特性：
 * 1. 采用同级临时文件原子替换（.tmp -> renameTo / copyTo 回退），杜绝写入中断导致的数据损坏；
 * 2. 线程安全（持有独立写锁）；
 * 3. 容错读取（文件不存在、空白或 JSON 损坏时优雅返回空列表）；
 * 4. 支持容量限制自动截断。
 */
class JsonFileStore<T>(
    private val file: File,
    serializer: KSerializer<T>,
    private val maxItems: Int = Int.MAX_VALUE,
    private val json: Json = AppJson.default,
) {
    private val writeLock = Any()
    private val listSerializer = ListSerializer(serializer)

    /**
     * 从磁盘加载列表。
     * 若文件不存在、内容为空或解析异常，均返回空列表。
     */
    fun load(): List<T> {
        return try {
            if (!file.exists()) return emptyList()
            val text = file.readText()
            if (text.isBlank()) emptyList() else json.decodeFromString(listSerializer, text)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 原子落盘。通过同级临时文件写入并重命名替换，超出 [maxItems] 的条目会被截断。
     */
    fun persist(items: List<T>) {
        synchronized(writeLock) {
            val capped = if (maxItems < Int.MAX_VALUE) items.take(maxItems) else items
            val parent = file.parentFile ?: return@synchronized
            if (!parent.exists() && !parent.mkdirs()) return@synchronized
            val tempFile = File(parent, "${file.name}.tmp")
            tempFile.writeText(json.encodeToString(listSerializer, capped))
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
        }
    }
}
