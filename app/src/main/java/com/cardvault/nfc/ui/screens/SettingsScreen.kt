@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.cardvault.nfc.ui.screens

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardvault.nfc.AppContainer
import com.cardvault.nfc.backup.DriveClient
import com.cardvault.nfc.backup.WrongPassphraseException
import com.cardvault.nfc.data.AppSettings
import com.cardvault.nfc.data.ThemeMode
import com.cardvault.nfc.ui.components.parseHexColor
import com.cardvault.nfc.ui.theme.ACCENT_PRESETS
import com.cardvault.nfc.update.UpdateState
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.cardvault.nfc.security.KeystoreCrypto
import com.cardvault.nfc.security.findFragmentActivity
import com.cardvault.nfc.ui.components.InfoRow
import com.cardvault.nfc.ui.components.SectionCard
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class BackupOp(val title: String, val confirm: Boolean) {
    EXPORT_FILE("Export encrypted .cvault", true),
    IMPORT_FILE("Import .cvault", false),
    DRIVE_BACKUP("Back up to Google Drive", true),
    DRIVE_RESTORE("Restore from Google Drive", false),
}

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.state.collectAsStateWithLifecycle()

    var op by remember { mutableStateOf<BackupOp?>(null) }
    var passphrase by remember { mutableStateOf<CharArray?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun recordBackup(bytes: Int, target: String) = container.settings.update {
        it.copy(lastBackupAt = System.currentTimeMillis(), lastBackupBytes = bytes.toLong(), lastBackupTarget = target)
    }
    fun finish() { passphrase?.fill(' '); passphrase = null; busy = null }

    // ---- local file export / import ------------------------------------------------------
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pass = passphrase
        if (uri == null || pass == null) { finish(); return@rememberLauncherForActivityResult }
        scope.launch {
            busy = "Encrypting…"
            message = runCatching {
                val bytes = container.backupManager.export(pass)
                container.backupManager.writeTo(uri, bytes)
                recordBackup(bytes.size, "Local file")
                "Exported ${Formatter.formatShortFileSize(context, bytes.size.toLong())}"
            }.getOrElse { "Export failed: ${it.message}" }
            finish()
        }
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val pass = passphrase
        if (uri == null || pass == null) { finish(); return@rememberLauncherForActivityResult }
        scope.launch {
            busy = "Decrypting…"
            message = runCatching {
                "Imported: " + container.backupManager.import(container.backupManager.readFrom(uri), pass)
            }.getOrElse { if (it is WrongPassphraseException) "Wrong passphrase" else "Import failed: ${it.message}" }
            finish()
        }
    }

    // ---- Google Drive authorization ------------------------------------------------------
    var pendingAuth by remember { mutableStateOf<CompletableDeferred<String?>?>(null) }
    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val token = runCatching {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(res.data).accessToken
        }.getOrNull()
        pendingAuth?.complete(token)
    }

    suspend fun driveToken(): String? {
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DriveClient.SCOPE))).build()
        val result = Identity.getAuthorizationClient(context.findFragmentActivity()).authorize(request).await()
        if (!result.hasResolution()) return result.accessToken
        val deferred = CompletableDeferred<String?>()
        pendingAuth = deferred
        authLauncher.launch(IntentSenderRequest.Builder(result.pendingIntent!!.intentSender).build())
        return deferred.await()
    }

    fun runDrive(backup: Boolean, pass: CharArray) {
        scope.launch {
            busy = "Connecting to Google Drive…"
            message = runCatching {
                val token = driveToken() ?: error("Google authorization was cancelled")
                val drive = container.driveClient
                val remote = drive.find(token)
                var merged = ""
                if (remote != null) {
                    busy = "Downloading & merging…"
                    val report = container.backupManager.import(drive.download(token, remote.id), pass)
                    merged = report.toString()
                } else if (!backup) error("No backup found in this Google account")
                if (backup) {
                    busy = "Encrypting & uploading…"
                    val bytes = container.backupManager.export(pass)
                    drive.upload(token, bytes, remote?.id)
                    recordBackup(bytes.size, "Google Drive")
                    "Backed up to Drive" + if (merged.isNotEmpty()) " (merged first: $merged)" else ""
                } else "Restored: $merged"
            }.getOrElse {
                when (it) {
                    is WrongPassphraseException -> "The Drive backup was made with a different passphrase."
                    is DriveClient.DriveException -> if (it.code == 401) "Google session expired. Try again." else it.message
                    else -> it.message ?: it.javaClass.simpleName
                }
            }
            finish()
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppearanceSection(container, settings)
            UpdatesSection(container, settings)
            SectionCard("Security") {
                ToggleRow("App lock (biometric / PIN)", settings.appLockEnabled) { v -> container.settings.update { it.copy(appLockEnabled = v) } }
                ToggleRow("Lock when app is backgrounded", settings.autoLockOnBackground) { v -> container.settings.update { it.copy(autoLockOnBackground = v) } }
                ToggleRow("Block screenshots & screen recording", settings.secureScreens) { v -> container.settings.update { it.copy(secureScreens = v) } }
                InfoRow("Key storage", if (KeystoreCrypto.isStrongBoxBacked) "StrongBox secure element" else "Android Keystore (TEE)")
            }
            SectionCard("NFC emulation window") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15 to "15 s", 45 to "45 s", 120 to "2 min", AppSettings.WHILE_SCREEN_ON to "While app open").forEach { (v, l) ->
                        FilterChip(settings.emulationTimeoutSec == v, { container.settings.update { it.copy(emulationTimeoutSec = v) } }, label = { Text(l) })
                    }
                }
                if (settings.emulationTimeoutSec == AppSettings.WHILE_SCREEN_ON) {
                    Text("Less secure: the card stays tappable until you leave the app.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            SectionCard("Backup & sync") {
                Text("Backups are encrypted on this phone with your passphrase (Argon2id + AES-256-GCM) before they leave it. " +
                    "Nobody can read them without it, including Google and us. If you lose the passphrase, the backup can't be recovered.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
                InfoRow("Last backup", if (settings.lastBackupAt == 0L) "Never" else
                    "${fmt.format(Date(settings.lastBackupAt))} · ${settings.lastBackupTarget} · " +
                        Formatter.formatShortFileSize(context, settings.lastBackupBytes))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { op = BackupOp.DRIVE_BACKUP }, Modifier.weight(1f), enabled = busy == null) { Text("Backup now") }
                    OutlinedButton(onClick = { op = BackupOp.DRIVE_RESTORE }, Modifier.weight(1f), enabled = busy == null) { Text("Restore") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { op = BackupOp.EXPORT_FILE }, Modifier.weight(1f), enabled = busy == null) { Text("Export file") }
                    OutlinedButton(onClick = { op = BackupOp.IMPORT_FILE }, Modifier.weight(1f), enabled = busy == null) { Text("Import file") }
                }
                busy?.let { Spacer(Modifier.height(8.dp)); Text(it); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                message?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.primary) }
            }
            Text("CardVault NFC ${container.updates.currentVersion}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    op?.let { current ->
        PassphraseDialog(current, onDismiss = { op = null }) { pass ->
            op = null
            passphrase = pass
            when (current) {
                BackupOp.EXPORT_FILE -> createDoc.launch("cardvault-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.cvault")
                BackupOp.IMPORT_FILE -> openDoc.launch(arrayOf("*/*"))
                BackupOp.DRIVE_BACKUP -> runDrive(backup = true, pass)
                BackupOp.DRIVE_RESTORE -> runDrive(backup = false, pass)
            }
        }
    }
}

@Composable
private fun AppearanceSection(container: AppContainer, settings: AppSettings) {
    SectionCard("Appearance") {
        Text("Theme", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { m ->
                FilterChip(settings.themeMode == m, { container.settings.update { it.copy(themeMode = m) } }, label = { Text(m.label) })
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ToggleRow("Material You (colors from wallpaper)", settings.dynamicColor) { v -> container.settings.update { it.copy(dynamicColor = v) } }
        }
        ToggleRow("Pure black (AMOLED, dark theme)", settings.amoledBlack) { v -> container.settings.update { it.copy(amoledBlack = v) } }
        ToggleRow("Compact wallet list", settings.compactWallet) { v -> container.settings.update { it.copy(compactWallet = v) } }
        if (!settings.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Spacer(Modifier.height(6.dp))
            Text("Accent color", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(vertical = 8.dp)) {
                ACCENT_PRESETS.forEach { (hex, name) ->
                    val selected = settings.accentHex.equals(hex, true)
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(parseHexColor(hex))
                            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { container.settings.update { it.copy(accentHex = hex) } },
                        contentAlignment = Alignment.Center,
                    ) { if (selected) Icon(Icons.Filled.Check, name, tint = Color.Black.copy(alpha = 0.7f)) }
                }
            }
            var hex by remember(settings.accentHex) { mutableStateOf(settings.accentHex) }
            OutlinedTextField(
                hex,
                { v ->
                    hex = v
                    val norm = if (v.startsWith("#")) v else "#$v"
                    if (Regex("#[0-9a-fA-F]{6}").matches(norm)) container.settings.update { it.copy(accentHex = norm.uppercase()) }
                },
                label = { Text("Custom accent (hex)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun UpdatesSection(container: AppContainer, settings: AppSettings) {
    val context = LocalContext.current
    val updates = container.updates
    val state by updates.state.collectAsStateWithLifecycle()
    SectionCard("Updates") {
        InfoRow("Installed version", updates.currentVersion)
        ToggleRow("Check for updates automatically", settings.autoCheckUpdates) { v ->
            container.settings.update { it.copy(autoCheckUpdates = v) }
        }
        when (val st = state) {
            is UpdateState.Available -> {
                Text("Version ${st.release.versionName} is available", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)
                if (st.release.notes.isNotBlank()) {
                    Text(st.release.notes.take(600), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
                }
                Button(onClick = { updates.downloadAndInstall(st.release) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Download & install (${Formatter.formatShortFileSize(context, st.release.apkSize)})")
                }
            }
            is UpdateState.Downloading -> {
                Text("Downloading ${st.release.versionName}… ${(st.progress * 100).toInt()}%")
                LinearProgressIndicator(progress = { st.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            }
            is UpdateState.Installing -> Text("Installing… confirm on the system prompt.")
            is UpdateState.NeedsInstallPermission -> {
                Text("Allow CardVault to install updates, then tap Retry.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { context.startActivity(updates.installPermissionIntent()) }) { Text("Allow installs") }
                    Button(onClick = { updates.downloadAndInstall(st.release) }) { Text("Retry") }
                }
            }
            UpdateState.Checking -> { Text("Checking…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
            UpdateState.UpToDate -> Text("You're on the latest version.", color = MaterialTheme.colorScheme.tertiary)
            is UpdateState.Error -> Text(st.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            UpdateState.Idle -> Unit
        }
        if (state !is UpdateState.Downloading && state !is UpdateState.Available) {
            TextButton(onClick = { updates.check() }) { Text("Check for updates") }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
private fun PassphraseDialog(op: BackupOp, onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    val tooShort = p1.length < 10
    val mismatch = op.confirm && p1 != p2
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(op.title) },
        text = {
            Column {
                Text("Vault passphrase (at least 10 characters).", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(p1, { p1 = it }, label = { Text("Passphrase") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (op.confirm) {
                    OutlinedTextField(p2, { p2 = it }, label = { Text("Repeat passphrase") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = p2.isNotEmpty() && mismatch)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(p1.toCharArray()) }, enabled = !tooShort && !mismatch) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
