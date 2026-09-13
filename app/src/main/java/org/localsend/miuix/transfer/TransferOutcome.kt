package org.localsend.miuix.transfer

import org.localsend.miuix.model.FileItem
import org.localsend.miuix.model.TransferStatus

/**
 * Aggregates per-file results into a session-level outcome.
 * Partial failure stays Completed (official LocalSend treats the session as finished)
 * and carries a summary message for the UI.
 */
data class BatchOutcome(
    val status: TransferStatus,
    val errorMessage: String? = null
)

object TransferOutcome {
    fun aggregate(
        files: List<FileItem>,
        currentStatus: TransferStatus,
        allFailedMessage: String,
        partialFailedMessage: (failed: Int, total: Int) -> String
    ): BatchOutcome {
        if (currentStatus == TransferStatus.Canceled) {
            return BatchOutcome(TransferStatus.Canceled, null)
        }
        val failedCount = files.count { it.status == TransferStatus.Failed }
        return when {
            files.isEmpty() || failedCount == files.size -> {
                BatchOutcome(
                    TransferStatus.Failed,
                    files.firstNotNullOfOrNull { it.error } ?: allFailedMessage
                )
            }
            failedCount > 0 -> BatchOutcome(
                TransferStatus.Completed,
                partialFailedMessage(failedCount, files.size)
            )
            else -> BatchOutcome(TransferStatus.Completed, null)
        }
    }
}
