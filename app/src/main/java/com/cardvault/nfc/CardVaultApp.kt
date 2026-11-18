package com.cardvault.nfc

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.cardvault.nfc.nfc.EmulationSession

class CardVaultApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("sqlcipher")
        container = AppContainer(this)

        // Lock the vault and kill any HCE session as soon as the app leaves the foreground.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                EmulationSession.stop()
                if (container.settings.state.value.autoLockOnBackground) {
                    container.vaultLock.lock()
                }
            }
        })
    }
}
