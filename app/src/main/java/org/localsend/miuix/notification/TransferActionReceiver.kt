package org.localsend.miuix.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.localsend.miuix.manager.LocalSendManager

/**
 * 传输动作广播接收器。
 * 用于响应系统通知栏、焦点胶囊等外部界面的快捷操作（如一键取消传输）。
 */
class TransferActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)

        if (action == ACTION_CANCEL_TRANSFER && !sessionId.isNullOrEmpty()) {
            LocalSendManager.getInstance()?.cancelTransfer(sessionId)
            TransferNotifier.cancelSessionNotification(context, sessionId)
        } else if (action == ACTION_ACCEPT_TRANSFER && !sessionId.isNullOrEmpty()) {
            // 通知栏快捷操作：直接接受全部文件（等价于弹窗中的"接收"）
            LocalSendManager.getInstance()?.acceptIncomingTransfer(sessionId, null)
            TransferNotifier.cancelSessionNotification(context, sessionId)
        } else if (action == ACTION_DECLINE_TRANSFER && !sessionId.isNullOrEmpty()) {
            LocalSendManager.getInstance()?.declineIncomingTransfer(sessionId)
            TransferNotifier.cancelSessionNotification(context, sessionId)
        }
    }

    companion object {
        const val ACTION_CANCEL_TRANSFER = org.localsend.miuix.core.AppActions.ACTION_CANCEL_TRANSFER
        const val ACTION_ACCEPT_TRANSFER = org.localsend.miuix.core.AppActions.ACTION_ACCEPT_TRANSFER
        const val ACTION_DECLINE_TRANSFER = org.localsend.miuix.core.AppActions.ACTION_DECLINE_TRANSFER
        const val EXTRA_SESSION_ID = org.localsend.miuix.core.AppActions.EXTRA_SESSION_ID
    }
}
