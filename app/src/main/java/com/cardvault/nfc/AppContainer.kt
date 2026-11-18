package com.cardvault.nfc

import android.content.Context
import android.net.Uri
import com.cardvault.nfc.backup.BackupManager
import com.cardvault.nfc.backup.DriveClient
import com.cardvault.nfc.data.CardRepository
import com.cardvault.nfc.data.Card
import com.cardvault.nfc.data.SettingsStore
import com.cardvault.nfc.data.VaultDatabase
import com.cardvault.nfc.security.VaultLock
import com.cardvault.nfc.update.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-rolled DI container (kept deliberately small instead of Hilt). */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsStore(appContext)
    val vaultLock = VaultLock()

    private val database by lazy { VaultDatabase.open(appContext) }
    val repository by lazy { CardRepository(appContext, database.cardDao(), database.apduLogDao()) }
    val backupManager by lazy { BackupManager(appContext, repository) }
    val driveClient by lazy { DriveClient() }

    val updates by lazy { UpdateManager(appContext, settings, appScope) }

    /** Hand-off slot for a card pre-filled by a scanner/importer before opening the editor. */
    @Volatile var pendingDraft: Card? = null
    /** Artwork bytes that came with [pendingDraft] (e.g. a .pkpass strip image). */
    @Volatile var pendingArtwork: ByteArray? = null

    /** A .pkpass the app was asked to open; consumed by the nav host once unlocked. */
    val incomingPass = MutableStateFlow<Uri?>(null)
}
