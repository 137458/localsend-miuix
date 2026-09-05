package org.localsend.miuix.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.localsend.miuix.manager.LocalSendManager

/**
 * 传输动作广播接收器。
 * 用于响应系统通知栏、流体云胶囊等外部界面的快捷操作（如一键取消传输）。
 */
class TransferActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)

        if (action == ACTION_CANCEL_TRANSFER && !sessionId.isNullOrEmpty()) {
            if (sessionId == TransferNotifier.TEST_SESSION_ID) {
                TransferNotifier.stopTestSimulation(context)
            } else {
                LocalSendManager.getInstance()?.cancelTransfer(sessionId)
            }
        }
    }

    companion object {
        const val ACTION_CANCEL_TRANSFER = "org.localsend.miuix.action.CANCEL_TRANSFER"
        const val EXTRA_SESSION_ID = "extra_session_id"
    }
}
