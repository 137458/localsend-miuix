package org.localsend.miuix.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import org.localsend.miuix.manager.LocalSendManager

class MainActivity : ComponentActivity(), NavigationEventDispatcherOwner {

    private val eventDispatcher = NavigationEventDispatcher()
    override val navigationEventDispatcher: NavigationEventDispatcher
        get() = eventDispatcher

    private lateinit var manager: LocalSendManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        manager = LocalSendManager(applicationContext)
        manager.start()

        setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides this) {
                App(manager = manager)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        eventDispatcher.dispose()
        manager.stop()
    }
}
