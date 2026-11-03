package org.localsend.miuix.history

import kotlinx.serialization.json.Json
import org.localsend.miuix.core.AppJson
import org.localsend.miuix.core.JsonFileStore
import org.localsend.miuix.model.TransferHistoryItem
import java.io.File

/**
 * Transfer history persistence. Atomic replace via sibling temp file.
 * Corrupt or missing files yield an empty list rather than crashing startup.
 */
class HistoryStore(
    file: File,
    json: Json = AppJson.default,
    private val maxItems: Int = DEFAULT_MAX_ITEMS,
) {
    private val store =
        JsonFileStore(
            file = file,
            serializer = TransferHistoryItem.serializer(),
            maxItems = maxItems,
            json = json,
        )

    fun load(): List<TransferHistoryItem> = store.load()

    fun persist(items: List<TransferHistoryItem>) = store.persist(items)

    fun add(
        current: List<TransferHistoryItem>,
        item: TransferHistoryItem,
    ): List<TransferHistoryItem> {
        val updated = (listOf(item) + current).take(maxItems)
        persist(updated)
        return updated
    }

    fun clear(): List<TransferHistoryItem> {
        persist(emptyList())
        return emptyList()
    }

    fun delete(
        current: List<TransferHistoryItem>,
        id: String,
    ): List<TransferHistoryItem> {
        val updated = current.filterNot { it.id == id }
        persist(updated)
        return updated
    }

    companion object {
        const val DEFAULT_MAX_ITEMS = 200
        const val FILENAME = "transfer_history.json"
    }
}
