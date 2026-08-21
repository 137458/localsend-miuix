package org.localsend.miuix.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.localsend.miuix.manager.LocalSendManager

class MainActivity : ComponentActivity() {

    private lateinit var manager: LocalSendManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
}
