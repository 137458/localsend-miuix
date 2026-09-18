package org.localsend.miuix.history

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.localsend.miuix.core.AppJson
import org.localsend.miuix.model.TransferHistoryItem
import java.io.File

/**
 * Transfer history persistence. Atomic replace via sibling temp file.
 * Corrupt or missing files yield an empty list rather than crashing startup.
 */
class HistoryStore(
    private val file: File,
    private val json: Json = AppJson.default,
    private val maxItems: Int = DEFAULT_MAX_ITEMS
) {
    fun load(): List<TransferHistoryItem> {
        return try {
            if (!file.exists()) return emptyList()
            val content = file.readText()
            if (content.isBlank()) emptyList() else json.decodeFromString(content)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private val writeLock = Any()

    fun persist(items: List<TransferHistoryItem>) {
        synchronized(writeLock) {
            val capped = items.take(maxItems)
            val parent = file.parentFile ?: return@synchronized
            if (!parent.exists() && !parent.mkdirs()) return@synchronized
            val tempFile = File(parent, "${file.name}.tmp")
            tempFile.writeText(json.encodeToString(capped))
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
        }
    }

    fun add(current: List<TransferHistoryItem>, item: TransferHistoryItem): List<TransferHistoryItem> {
        val updated = (listOf(item) + current).take(maxItems)
        persist(updated)
        return updated
    }

    fun clear(): List<TransferHistoryItem> {
        persist(emptyList())
        return emptyList()
    }

    fun delete(current: List<TransferHistoryItem>, id: String): List<TransferHistoryItem> {
        val updated = current.filterNot { it.id == id }
        persist(updated)
        return updated
    }

    companion object {
        const val DEFAULT_MAX_ITEMS = 200
        const val FILENAME = "transfer_history.json"
    }
}
