package org.localsend.miuix.ui

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
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

    // 现代 Activity Result API 申请权限（包含 Android 13+ 通知与 Android 17+ 局域网前瞻性权限）
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        TransferNotifier.ensure(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 初始化通知渠道并检测当前通知开关与权限
        TransferNotifier.ensure(applicationContext)
        requestNecessaryPermissions()

        manager = LocalSendManager(applicationContext)
        manager.start()

        // 仅在非配置变更（如首次冷启动）时处理外部调起的分享与打开文件意图
        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        }

        setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides this) {
                App(manager = manager)
            }
        }
    }

    fun requestNecessaryPermissions() {
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
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: android.content.Intent?) {
        if (intent == null || !org.localsend.miuix.util.ShareIntentHelper.isShareIntent(intent)) return
        if (intent.getBooleanExtra(EXTRA_INTENT_PROCESSED, false)) return

        val items = org.localsend.miuix.util.ShareIntentHelper.extractShareItems(applicationContext, intent)
        if (items.isNotEmpty()) {
            manager.addFiles(items)
            // 自动跳转至主界面的“发送”Tab（索引 1）
            manager.requestNavigateToTab(1)
            val msg = if (items.size == 1 && items[0].textContent != null) {
                "已添加待发送文本"
            } else {
                "已添加 ${items.size} 个待发送文件"
            }
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
        intent.putExtra(EXTRA_INTENT_PROCESSED, true)
    }

    override fun onResume() {
        super.onResume()
        TransferNotifier.ensure(applicationContext)
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
        private const val EXTRA_INTENT_PROCESSED = "org.localsend.miuix.extra.INTENT_PROCESSED"
    }
}