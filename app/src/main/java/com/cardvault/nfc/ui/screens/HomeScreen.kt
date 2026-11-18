@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.cardvault.nfc.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import com.cardvault.nfc.update.UpdateState
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardvault.nfc.AppContainer
import com.cardvault.nfc.data.CardCategory
import com.cardvault.nfc.data.CardSummary
import com.cardvault.nfc.ui.components.CardFace
import com.cardvault.nfc.ui.components.CardFaceData
import com.cardvault.nfc.ui.components.maskNumber
import com.cardvault.nfc.ui.theme.Amber
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenCard: (String) -> Unit,
    onScanNfc: () -> Unit,
    onScanCamera: () -> Unit,
    onCreate: () -> Unit,
    onImportPass: () -> Unit,
    onSettings: () -> Unit,
) {
    val cards by container.repository.summaries.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var filter by remember { mutableStateOf<CardCategory?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var fabOpen by remember { mutableStateOf(false) }

    // Wallet-style: passes departing soon float to the top in time order.
    val now = System.currentTimeMillis()
    val visible = cards.filter { c ->
        (filter == null || c.category == filter) &&
            (query.isBlank() || c.title.contains(query, true) || c.subtitle.contains(query, true))
    }.let { list ->
        val upcoming = list.filter { it.eventTime != null && it.eventTime > now - 6 * 3600_000L }.sortedBy { it.eventTime }
        upcoming + (list - upcoming.toSet())
    }
    val update by container.updates.state.collectAsStateWithLifecycle()
    val backedUpRecently = settings.lastBackupAt > 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        OutlinedTextField(query, { query = it }, placeholder = { Text("Search cards") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                    } else Text("CardVault")
                },
                actions = {
                    IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                        Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, "Search")
                    }
                    Icon(
                        if (backedUpRecently) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                        contentDescription = if (backedUpRecently) "Backed up" else "Not backed up",
                        tint = if (backedUpRecently) MaterialTheme.colorScheme.tertiary else Amber,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnimatedVisibility(fabOpen) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FabAction("Scan NFC card / tag", Icons.Filled.Nfc) { fabOpen = false; onScanNfc() }
                        FabAction("Scan barcode / camera", Icons.Filled.QrCodeScanner) { fabOpen = false; onScanCamera() }
                        FabAction("Import wallet pass (.pkpass)", Icons.Filled.FileOpen) { fabOpen = false; onImportPass() }
                        FabAction("Create card or pass", Icons.Filled.Edit) { fabOpen = false; onCreate() }
                    }
                }
                FloatingActionButton(onClick = { fabOpen = !fabOpen }) {
                    Icon(if (fabOpen) Icons.Filled.Close else Icons.Filled.Add, "Add card")
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            (update as? UpdateState.Available)?.let { u ->
                Surface(
                    onClick = onSettings,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.SystemUpdate, null)
                        Spacer(Modifier.width(12.dp))
                        Text("Update ${u.release.versionName} available. Tap to install.", Modifier.weight(1f))
                    }
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(filter == null, { filter = null }, label = { Text("All") }) }
                items(CardCategory.entries) { cat ->
                    FilterChip(filter == cat, { filter = if (filter == cat) null else cat }, label = { Text(cat.label) })
                }
            }
            if (visible.isEmpty()) {
                EmptyWallet(Modifier.weight(1f), hasCards = cards.isNotEmpty())
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 120.dp),
                    verticalArrangement = Arrangement.spacedBy(if (settings.compactWallet) 8.dp else 14.dp),
                ) {
                    items(visible, key = { it.id }) { card ->
                        WalletCard(
                            card = card,
                            compact = settings.compactWallet,
                            onOpen = { onOpenCard(card.id) },
                            onMove = { delta -> scope.launch { container.repository.move(card.id, delta, cards) } },
                            artwork = container.repository.resolveArtwork(card.style),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletCard(card: CardSummary, compact: Boolean, onOpen: () -> Unit, onMove: (Int) -> Unit, artwork: java.io.File?) {
    var menu by remember { mutableStateOf(false) }
    Box {
        CardFace(
            CardFaceData(
                title = card.title,
                subtitle = card.subtitle,
                numberText = maskNumber(card.last4),
                expiry = card.expiryDate,
                holder = "",
                style = card.style,
                hasNfc = card.hasNfc || card.isEmulatable,
                hasBarcode = card.hasBarcode,
                locked = card.requireBiometric,
                passFace = card.passFace,
            ),
            modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
            artwork = artwork,
            compact = compact,
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; onMove(-1) })
            DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; onMove(1) })
        }
    }
}

@Composable
private fun FabAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 12.dp))
        SmallFloatingActionButton(onClick = onClick, containerColor = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Icon(icon, label)
        }
    }
}

@Composable
private fun EmptyWallet(modifier: Modifier, hasCards: Boolean) {
    Column(modifier.fillMaxWidth().padding(32.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Wallet, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(if (hasCards) "No cards match" else "Your vault is empty", style = MaterialTheme.typography.titleMedium)
        if (!hasCards) {
            Text("Tap + to scan an NFC card, a barcode, or build a custom tag.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
