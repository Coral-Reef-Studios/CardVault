package com.cardvault.nfc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import com.cardvault.nfc.pass.PkpassImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cardvault.nfc.AppContainer
import com.cardvault.nfc.security.AuthResult
import com.cardvault.nfc.security.Authenticator
import com.cardvault.nfc.security.findFragmentActivity
import com.cardvault.nfc.ui.screens.CardDetailScreen
import com.cardvault.nfc.ui.screens.EditCardScreen
import com.cardvault.nfc.ui.screens.HomeScreen
import com.cardvault.nfc.ui.screens.ScannerScreen
import com.cardvault.nfc.ui.screens.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    fun scan(mode: String) = "scan/$mode"
    fun card(id: String) = "card/$id"
    fun edit(id: String) = "edit/$id"
    const val NEW = "new"
}

@Composable
fun AppRoot(container: AppContainer) {
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val unlocked by container.vaultLock.unlocked.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (settings.appLockEnabled && !unlocked) {
            LockScreen(onUnlocked = { container.vaultLock.unlock() })
        } else {
            VaultNavHost(container)
        }
    }
}

@Composable
private fun VaultNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val incoming by container.incomingPass.collectAsStateWithLifecycle()
    var importError by remember { mutableStateOf<String?>(null) }

    // A .pkpass opened from outside (or via the import button): parse, then open the editor pre-filled.
    LaunchedEffect(incoming) {
        val uri = incoming ?: return@LaunchedEffect
        container.incomingPass.value = null
        runCatching {
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(64 * 1024)
                    var total = 0
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        require(total <= 20 * 1024 * 1024) { "Pass file is too large" }
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                } ?: error("Can't open the file")
            }
            withContext(Dispatchers.Default) { PkpassImporter.import(bytes) }
        }.onSuccess { imported ->
            container.pendingDraft = imported.card
            container.pendingArtwork = imported.artwork
            nav.navigate(Routes.edit(Routes.NEW)) { popUpTo(Routes.HOME) }
        }.onFailure { importError = it.message ?: "Couldn't import this pass" }
    }
    val pickPass = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) container.incomingPass.value = uri
    }
    importError?.let { msg ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text("Import failed") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { importError = null }) { Text("OK") } },
        )
    }

    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                container = container,
                onOpenCard = { nav.navigate(Routes.card(it)) },
                onScanNfc = { nav.navigate(Routes.scan("nfc")) },
                onScanCamera = { nav.navigate(Routes.scan("camera")) },
                onCreate = { container.pendingDraft = null; container.pendingArtwork = null; nav.navigate(Routes.edit(Routes.NEW)) },
                onImportPass = { pickPass.launch(arrayOf("application/vnd.apple.pkpass", "application/vnd-com.apple.pkpass", "application/zip", "application/octet-stream")) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable("scan/{mode}") { entry ->
            ScannerScreen(
                container = container,
                initialMode = entry.arguments?.getString("mode") ?: "nfc",
                onBack = { nav.popBackStack() },
                onSaveDraft = { draft ->
                    container.pendingDraft = draft
                    nav.navigate(Routes.edit(Routes.NEW)) { popUpTo(Routes.HOME) }
                },
                onOpenExisting = { id -> nav.navigate(Routes.card(id)) { popUpTo(Routes.HOME) } },
            )
        }
        composable("card/{id}") { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            CardDetailScreen(
                container = container,
                cardId = id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.edit(id)) },
            )
        }
        composable("edit/{id}") { entry ->
            val id = entry.arguments?.getString("id") ?: Routes.NEW
            EditCardScreen(
                container = container,
                cardId = id.takeIf { it != Routes.NEW },
                onDone = { savedId ->
                    nav.navigate(Routes.card(savedId)) { popUpTo(Routes.HOME) }
                },
                onCancel = { nav.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(container = container, onBack = { nav.popBackStack() })
        }
    }
}

@Composable
private fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }

    fun prompt() {
        Authenticator.authenticate(context.findFragmentActivity(), "Unlock CardVault") { r ->
            when (r) {
                AuthResult.Success -> onUnlocked()
                AuthResult.Unprotected -> {
                    message = "No screen lock is set on this device, so the vault can't be protected. Set a PIN in Android settings."
                    onUnlocked()
                }
                is AuthResult.Failed -> message = r.message
            }
        }
    }
    LaunchedEffect(Unit) { prompt() }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Fingerprint, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("CardVault is locked", style = MaterialTheme.typography.headlineSmall)
        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = { prompt() }) { Text("Unlock") }
    }
}
