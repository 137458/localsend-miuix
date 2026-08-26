package org.localsend.miuix.ui

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.notification.TransferNotifier

class MainActivity : ComponentActivity() {

    private lateinit var manager: LocalSendManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 接收文件需要系统通知，Android 13+ 需运行时授予 POST_NOTIFICATIONS 权限
        TransferNotifier.ensure(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION
                )
            }
        }

        manager = LocalSendManager(applicationContext)
        manager.start()

        setContent {
            App(manager = manager)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        manager.stop()
    }

    companion object {
        private const val REQUEST_NOTIFICATION = 100
    }
}