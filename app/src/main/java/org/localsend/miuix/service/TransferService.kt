package org.localsend.miuix.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import org.localsend.miuix.notification.TransferNotifier

private const val TAG = "TransferService"

/**
 * 前台传输服务（Android 14+ dataSync 类型）。
 * 当有正在进行的局域网文件传输任务时启动，保持进程高优先级，防止系统在用户切后台、
 * 息屏或多任务切换时冻结网络连接或杀死进程；全部传输完成后自动退出并释放前台通知。
 */
class TransferService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopForegroundService()
            return START_NOT_STICKY
        }

        acquireLocks()

        val sessionCount = intent?.getIntExtra(EXTRA_SESSION_COUNT, 1) ?: 1
        TransferNotifier.ensure(applicationContext)
        val notification = TransferNotifier.buildForegroundNotification(this, sessionCount)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    TransferNotifier.NOTIF_ID_FOREGROUND_SERVICE,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(TransferNotifier.NOTIF_ID_FOREGROUND_SERVICE, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed", e)
        }

        return START_NOT_STICKY
    }

    private fun acquireLocks() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock =
                    powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, org.localsend.miuix.core.NetworkConstants.WAKE_LOCK_TAG)?.apply {
                        setReferenceCounted(false)
                        // 不设超时：长传输可能远超 1 小时，锁随前台服务停止/销毁释放。
                        acquire()
                    }
            }
            if (wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                wifiLock =
                    wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, org.localsend.miuix.core.NetworkConstants.WIFI_LOCK_TAG)?.apply {
                        setReferenceCounted(false)
                        acquire()
                    }
            }
        } catch (e: Exception) {
            Log.w(TAG, "acquireLocks failed", e)
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null
        } catch (ignored: Exception) {
        }
        try {
            wifiLock?.let {
                if (it.isHeld) it.release()
            }
            wifiLock = null
        } catch (ignored: Exception) {
        }
    }

    private fun stopForegroundService() {
        releaseLocks()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (ignored: Exception) {
        }
        stopSelf()
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = org.localsend.miuix.core.AppActions.ACTION_START_SERVICE
        private const val ACTION_STOP = org.localsend.miuix.core.AppActions.ACTION_STOP_SERVICE
        private const val EXTRA_SESSION_COUNT = org.localsend.miuix.core.AppActions.EXTRA_SESSION_COUNT

        fun start(
            context: Context,
            sessionCount: Int = 1,
        ) {
            try {
                val intent =
                    Intent(context, TransferService::class.java).apply {
                        action = ACTION_START
                        putExtra(EXTRA_SESSION_COUNT, sessionCount)
                    }
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "startForegroundService failed", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent =
                    Intent(context, TransferService::class.java).apply {
                        action = ACTION_STOP
                    }
                context.startService(intent)
            } catch (ignored: Exception) {
            }
        }
    }
}
