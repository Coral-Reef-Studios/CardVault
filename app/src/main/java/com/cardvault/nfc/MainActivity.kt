package com.cardvault.nfc

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cardvault.nfc.ui.AppRoot
import com.cardvault.nfc.ui.theme.CardVaultTheme
import com.cardvault.nfc.ui.theme.isAppInDarkTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val container get() = (application as CardVaultApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // FLAG_SECURE blocks screenshots, screen recording and recents thumbnails.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                container.settings.state.collect { s ->
                    if (s.secureScreens) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        container.updates.checkIfDue()
        handleIncoming(intent)

        setContent {
            val settings by container.settings.state.collectAsStateWithLifecycle()
            val dark = isAppInDarkTheme(settings)
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            CardVaultTheme(settings) { AppRoot(container) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    /** Wallet pass (.pkpass) opened from a browser, mail app or file manager. */
    private fun handleIncoming(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) intent.data?.let { container.incomingPass.value = it }
    }
}
