package org.localsend.miuix.ui

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.ActivityCompat
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.notification.TransferNotifier

class MainActivity : ComponentActivity(), NavigationEventDispatcherOwner {

    private val eventDispatcher = NavigationEventDispatcher()
    override val navigationEventDispatcher: NavigationEventDispatcher
        get() = eventDispatcher

    private lateinit var manager: LocalSendManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 接收文件需要系统通知，Android 13+ 需运行时授予 POST_NOTIFICATIONS 权限
        TransferNotifier.ensure(applicationContext)
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        // Android 17+ (API 37+) 局域网访问与多播发现前瞻性运行时权限
        if (Build.VERSION.SDK_INT >= 37) {
            val localNetworkPerm = "android.permission.ACCESS_LOCAL_NETWORK"
            if (checkSelfPermission(localNetworkPerm) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(localNetworkPerm)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_PERMISSIONS
            )
        }

        manager = LocalSendManager(applicationContext)
        manager.start()

        setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides this) {
                App(manager = manager)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::manager.isInitialized) {
            manager.onResume()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        eventDispatcher.dispose()
        manager.stop()
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 100
    }
}