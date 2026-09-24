package org.localsend.miuix

import android.app.Application
import org.localsend.miuix.manager.LocalSendManager
import org.localsend.miuix.network.TlsStore

class LocalSendApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TlsStore.init(this)
        LocalSendManager.getOrCreate(this).start()
    }
}
