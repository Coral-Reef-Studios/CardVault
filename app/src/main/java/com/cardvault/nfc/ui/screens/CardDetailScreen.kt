@file:OptIn(ExperimentalMaterial3Api::class)

package com.cardvault.nfc.ui.screens

import android.content.ComponentName
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardvault.nfc.AppContainer
import com.cardvault.nfc.data.BarcodeFormat
import com.cardvault.nfc.data.Card
import com.cardvault.nfc.data.EmulationMode
import com.cardvault.nfc.data.PassData
import com.cardvault.nfc.data.PassFormat
import com.cardvault.nfc.data.PassKind
import androidx.compose.material.icons.filled.Flight
import androidx.compose.ui.draw.rotate
import com.cardvault.nfc.data.AppSettings
import com.cardvault.nfc.nfc.CardVaultApduService
import com.cardvault.nfc.nfc.EmulationSession
import com.cardvault.nfc.nfc.Hex
import com.cardvault.nfc.security.AuthResult
import com.cardvault.nfc.security.Authenticator
import com.cardvault.nfc.security.findFragmentActivity
import com.cardvault.nfc.ui.components.BarcodeView
import com.cardvault.nfc.ui.components.CardFace
import com.cardvault.nfc.ui.components.CardFaceData
import com.cardvault.nfc.ui.components.CardShape
import com.cardvault.nfc.ui.components.InfoRow
import com.cardvault.nfc.ui.components.SectionCard
import com.cardvault.nfc.ui.components.confirmHaptic
import com.cardvault.nfc.ui.components.copySensitive
import com.cardvault.nfc.ui.components.maskNumber
import com.cardvault.nfc.ui.theme.Amber
import com.cardvault.nfc.ui.theme.MonoStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CardDetailScreen(container: AppContainer, cardId: String, onBack: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var card by remember { mutableStateOf<Card?>(null) }
    var gateOpen by remember { mutableStateOf(false) }
    var gateError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val settings by container.settings.state.collectAsStateWithLifecycle()

    LaunchedEffect(cardId) { card = container.repository.get(cardId) }

    fun requestGate() {
        Authenticator.authenticate(context.findFragmentActivity(), "Unlock ${card?.title ?: "card"}") { r ->
            when (r) {
                AuthResult.Success, AuthResult.Unprotected -> { gateOpen = true; gateError = null }
                is AuthResult.Failed -> gateError = r.message
            }
        }
    }
    val c = card
    LaunchedEffect(c?.id) {
        if (c != null) { if (c.requireBiometric) requestGate() else gateOpen = true }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(c?.title ?: "") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                if (gateOpen) {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit & customize") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                }
            },
        )
    }) { padding ->
        if (c == null) return@Scaffold
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (!gateOpen) {
                Box(contentAlignment = Alignment.Center) {
                    CardFace(faceData(c, revealed = false), Modifier.blur(18.dp), container.repository.resolveArtwork(c.style))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Lock, null, Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { requestGate() }) { Text("Authenticate to view") }
                        gateError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
                return@Column
            }
            UnlockedDetail(container, c, settings)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete card?") },
            text = { Text("\"${c?.title}\" will be removed from this device and from your next backup.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    EmulationSession.stop()
                    scope.launch { container.repository.delete(cardId); onBack() }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun faceData(c: Card, revealed: Boolean) = CardFaceData(
    title = c.title,
    subtitle = c.subtitle,
    numberText = if (revealed) c.cardNumber.chunked(4).joinToString(" ") else maskNumber(c.last4),
    expiry = c.expiryDate,
    holder = c.holderName,
    style = c.style,
    hasNfc = c.nfcTechType.name != "NONE" || c.isEmulatable,
    hasBarcode = c.barcodeFormat != BarcodeFormat.NONE,
    passFace = c.pass?.let(PassFormat::face),
)

@Composable
private fun UnlockedDetail(container: AppContainer, c: Card, settings: AppSettings) {
    var revealed by remember { mutableStateOf(false) }
    var flipped by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(450), label = "flip")
    val hasBarcode = c.barcodeFormat != BarcodeFormat.NONE && c.barcodePayload.isNotBlank()
    // Passes show their code right under the card (like Google Wallet); other cards flip to it.
    val inlineBarcode = c.pass != null && hasBarcode
    val hasBack = hasBarcode && !inlineBarcode

    // Card (tap to flip to the barcode side)
    Box(
        Modifier.fillMaxWidth().graphicsLayer { rotationY = rotation; cameraDistance = 14f * density }
            .clickable(enabled = hasBack) { flipped = !flipped },
    ) {
        if (rotation <= 90f) {
            CardFace(faceData(c, revealed), artwork = container.repository.resolveArtwork(c.style))
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(1.586f).graphicsLayer { rotationY = 180f }.clip(CardShape)
                .background(Color.White), contentAlignment = Alignment.Center) {
                BarcodeView(c.barcodeFormat, c.barcodePayload, Modifier.padding(8.dp))
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { revealed = !revealed }) {
            Icon(if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
            Spacer(Modifier.width(6.dp))
            Text(if (revealed) "Hide number" else "Show number")
        }
        if (hasBack) TextButton(onClick = { flipped = !flipped }) { Text(if (flipped) "Show front" else "Show barcode") }
    }

    if (inlineBarcode) BarcodeView(c.barcodeFormat, c.barcodePayload)
    c.pass?.let { PassDetails(it) }

    EmulationPanel(c, settings)

    var tab by remember { mutableStateOf(0) }
    val tabs = buildList {
        add("Details")
        if (c.transit != null) add("Transit")
        if (c.techInfo != null || c.isEmulatable) add("Technical")
    }
    PrimaryScrollableTabRow(selectedTabIndex = tab.coerceAtMost(tabs.lastIndex), edgePadding = 0.dp) {
        tabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
    }
    when (tabs.getOrNull(tab)) {
        "Details" -> DetailsTab(c)
        "Transit" -> TransitTab(c)
        "Technical" -> TechnicalTab(container, c)
    }
}

@Composable
private fun EmulationPanel(c: Card, settings: AppSettings) {
    val context = LocalContext.current
    val view = LocalView.current
    val adapter = remember { NfcAdapter.getDefaultAdapter(context) }
    val hceSupported = adapter != null &&
        context.packageManager.hasSystemFeature("android.hardware.nfc.hce")
    val active by EmulationSession.state.collectAsStateWithLifecycle()
    val isActive = active?.cardId == c.id

    SectionCard(null) {
        val pill = when {
            c.isEmulatable -> "HCE ready (${if (c.nfcPayload.mode == EmulationMode.NDEF_TYPE4) "NDEF" else "ISO-DEP"})" to MaterialTheme.colorScheme.tertiary
            c.transit != null -> "Inspect & read only (signed transit card)" to Amber
            else -> "Display only" to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(pill.first, color = pill.second, fontWeight = FontWeight.SemiBold)
        if (!c.isEmulatable) return@SectionCard
        if (!hceSupported) {
            Text("This device can't do NFC card emulation.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SectionCard
        }
        if (adapter?.isEnabled == false) {
            Text("Turn NFC on to emulate this card.", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(10.dp))
        if (isActive) {
            ActiveEmulation(c, active!!)
        } else {
            Button(onClick = {
                // Emulation always needs fresh authentication.
                Authenticator.authenticate(context.findFragmentActivity(), "Arm ${c.title} for tap") { r ->
                    if (r is AuthResult.Success || r is AuthResult.Unprotected) {
                        registerDynamicAid(context, c)
                        EmulationSession.start(c, settings.emulationTimeoutSec)
                        view.confirmHaptic()
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Nfc, null); Spacer(Modifier.width(8.dp)); Text("Emulate NFC")
            }
        }
    }
}

@Composable
private fun ActiveEmulation(c: Card, session: EmulationSession.Active) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var remaining by remember { mutableLongStateOf(session.remainingMs) }
    val total = (session.expiresAt - session.startedAt).toFloat()
    val unlimited = session.expiresAt == Long.MAX_VALUE

    LaunchedEffect(session) {
        while (true) {
            remaining = session.remainingMs
            if (EmulationSession.current() == null) break
            delay(250)
        }
    }
    val taps by EmulationSession.taps.collectAsStateWithLifecycle()
    var firstTaps by remember { mutableStateOf(taps) }
    LaunchedEffect(taps) { if (taps != firstTaps) { view.confirmHaptic(); firstTaps = taps } }

    // Route taps to us instead of the default wallet while this screen is in front.
    DisposableEffect(lifecycleOwner) {
        val activity = context.findFragmentActivity()
        val adapter = NfcAdapter.getDefaultAdapter(context)
        val emu = adapter?.let { CardEmulation.getInstance(it) }
        val component = ComponentName(context, CardVaultApduService::class.java)
        fun prefer() { runCatching { emu?.setPreferredService(activity, component) } }
        fun unprefer() { runCatching { emu?.unsetPreferredService(activity) } }
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> prefer()
                Lifecycle.Event.ON_PAUSE -> unprefer()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) prefer()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unprefer()
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { if (unlimited) 1f else (remaining / total).coerceIn(0f, 1f) },
                modifier = Modifier.size(64.dp), strokeWidth = 6.dp,
            )
            Icon(Icons.Filled.Nfc, null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("Ready to tap", style = MaterialTheme.typography.titleMedium)
            Text(
                if (unlimited) "Active while the app is open" else "${(remaining + 999) / 1000}s remaining",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("HCE active (${if (c.nfcPayload.mode == EmulationMode.NDEF_TYPE4) "NDEF Type 4" else "ISO-DEP"})",
                color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium)
        }
        OutlinedButton(onClick = { EmulationSession.stop() }) { Text("Stop") }
    }
}

private fun registerDynamicAid(context: android.content.Context, c: Card) {
    if (c.nfcPayload.mode != EmulationMode.APDU_TABLE || c.aidHex.isBlank()) return
    val adapter = NfcAdapter.getDefaultAdapter(context) ?: return
    runCatching {
        CardEmulation.getInstance(adapter).registerAidsForService(
            ComponentName(context, CardVaultApduService::class.java),
            CardEmulation.CATEGORY_OTHER,
            listOf(Hex.clean(c.aidHex)),
        )
    }
}

@Composable
private fun PassCell(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PassCells(vararg items: Pair<String, String>) {
    val shown = items.filter { it.second.isNotBlank() }
    if (shown.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) { shown.forEach { (l, v) -> PassCell(l, v, Modifier.weight(1f)) } }
}

@Composable
private fun PassDetails(p: PassData) {
    SectionCard(null) {
        when (p.kind) {
            PassKind.BOARDING_PASS -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.origin, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        if (p.originName.isNotBlank()) Text(p.originName, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Filled.Flight, null, Modifier.rotate(90f), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(p.destination, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        if (p.destinationName.isNotBlank()) Text(p.destinationName, style = MaterialTheme.typography.bodySmall)
                    }
                }
                PassCells("Flight" to "${p.carrier} ${p.flightNumber}".trim(), "Departs" to PassFormat.display(p.departure))
                PassCells("Boarding" to p.boardingTime, "Gate" to p.gate, "Terminal" to p.terminal)
                PassCells("Seat" to p.seat, "Group" to p.group, "Class" to p.cabin)
                PassCells("Passenger" to p.passenger, "Booking ref" to p.pnr)
            }
            PassKind.EVENT_TICKET -> {
                Text(p.eventName, style = MaterialTheme.typography.headlineSmall)
                PassCells("When" to PassFormat.display(p.departure), "Venue" to p.venue)
                PassCells("Entrance" to p.gate, "Section" to p.section, "Row" to p.row, "Seat" to p.seat)
                PassCells("Holder" to p.passenger)
            }
            PassKind.GIFT_CARD -> PassCells("Balance" to p.balance, "PIN" to p.pin)
            else -> PassCells("Date" to PassFormat.display(p.departure))
        }
        p.departure.let { PassFormat.parseTime(it) }?.let { t ->
            val diff = t - System.currentTimeMillis()
            val label = when {
                diff < -24 * 3600_000L -> "This pass is in the past"
                diff < 0 -> "Today"
                diff < 3600_000L -> "In ${diff / 60_000} min"
                diff < 48 * 3600_000L -> "In ${diff / 3600_000} h"
                else -> "In ${diff / (24 * 3600_000L)} days"
            }
            Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
        if (p.fields.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("More info", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            p.fields.forEach { InfoRow(it.label, it.value) }
        }
    }
}

@Composable
private fun DetailsTab(c: Card) {
    val context = LocalContext.current
    SectionCard("Card") {
        InfoRow("Card number", c.cardNumber, mono = true, copyable = true)
        InfoRow("Holder", c.holderName, copyable = true)
        InfoRow("Issuer / type", c.subtitle)
        InfoRow("Expiry", c.expiryDate ?: "")
        InfoRow("Category", c.category.label)
        if (c.barcodeFormat != BarcodeFormat.NONE) InfoRow("Barcode (${c.barcodeFormat.label})", c.barcodePayload, mono = true, copyable = true)
    }
    if (c.customFields.isNotEmpty()) {
        SectionCard("Custom fields") { c.customFields.forEach { InfoRow(it.key, it.value, copyable = true) } }
    }
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    Text("Added ${fmt.format(Date(c.createdAt))} · Updated ${fmt.format(Date(c.updatedAt))}",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (c.barcodeFormat != BarcodeFormat.NONE && c.barcodePayload.isNotBlank()) {
        TextButton(onClick = { copySensitive(context, "Barcode", c.barcodePayload) }) { Text("Copy barcode value") }
    }
}

@Composable
private fun TransitTab(c: Card) {
    val t = c.transit ?: return
    SectionCard("Transit card") {
        InfoRow("Network", t.network)
        InfoRow("Application serial", t.serialDecimal, mono = true, copyable = true)
        InfoRow("Serial (hex)", t.serialHex, mono = true)
        InfoRow("AID", t.applicationAid, mono = true)
        if (t.counters.isNotEmpty()) InfoRow("Counters (remaining trips / value)", t.counters.joinToString(" · "))
        c.techInfo?.scannedAt?.let {
            InfoRow("Last scanned", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it)))
        }
    }
    t.files.forEach { f ->
        SectionCard("${f.name} · SFI %02X".format(f.sfi)) {
            f.records.forEachIndexed { i, r -> HexBlock("Record ${i + 1}", r) }
        }
    }
    if (t.notes.isNotEmpty()) SectionCard("Notes") { t.notes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) } }
}

@Composable
private fun TechnicalTab(container: AppContainer, c: Card) {
    val scope = rememberCoroutineScope()
    c.techInfo?.let { t ->
        SectionCard("NFC properties") {
            InfoRow("Technologies", t.techList.joinToString(", "))
            InfoRow("UID", t.uidHex, mono = true, copyable = true)
            InfoRow("ATQA", t.atqaHex ?: "", mono = true)
            InfoRow("SAK", t.sak?.let { "%02X".format(it) } ?: "", mono = true)
            InfoRow("Historical bytes", t.historicalBytesHex ?: "", mono = true)
            InfoRow("Hi-layer response", t.hiLayerResponseHex ?: "", mono = true)
            InfoRow("Max transceive", t.maxTransceiveLength?.toString() ?: "")
            InfoRow("NDEF", listOfNotNull(t.ndefType, t.ndefMaxSize?.let { "$it bytes max" },
                t.ndefWritable?.let { if (it) "writable" else "read-only" }).joinToString(" · "))
        }
        if (t.aidProbes.isNotEmpty()) {
            SectionCard("AID probes") { t.aidProbes.forEach { InfoRow("${it.label} → ${it.statusWord}", it.aidHex, mono = true) } }
        }
        if (t.ndefRecords.isNotEmpty()) {
            SectionCard("NDEF records") {
                t.ndefRecords.forEach { r ->
                    InfoRow(r.type, r.decoded)
                    HexBlock("Payload", r.payloadHex)
                }
            }
        }
    }
    if (c.isEmulatable) {
        SectionCard("Emulation payload") {
            InfoRow("Mode", c.nfcPayload.mode.label)
            InfoRow("AID", if (c.nfcPayload.mode == EmulationMode.NDEF_TYPE4) "D2760000850101" else c.aidHex, mono = true)
            if (c.nfcPayload.mode == EmulationMode.NDEF_TYPE4) HexBlock("NDEF message", c.nfcPayload.ndefMessageHex)
            c.nfcPayload.apduRules.forEach { InfoRow("C-APDU ${it.commandPrefixHex}…", "R-APDU ${it.responseHex}", mono = true) }
        }
    }
    val logs by container.repository.apduLogs(c.id).collectAsStateWithLifecycle(initialValue = emptyList())
    SectionCard("Live APDU console") {
        if (logs.isEmpty()) Text("No traffic yet. Arm emulation and tap a reader (e.g. ACR122U).",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        logs.take(60).forEach { l ->
            val incoming = l.direction.startsWith("INCOMING")
            Text(
                (if (incoming) "→ " else "← ") + l.hexPayload,
                style = MonoStyle,
                color = if (incoming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
            )
        }
        if (logs.isNotEmpty()) TextButton(onClick = { scope.launch { container.repository.clearLogs(c.id) } }) { Text("Clear log") }
    }
}

@Composable
private fun HexBlock(label: String, hex: String) {
    if (hex.isBlank()) return
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .horizontalScroll(rememberScrollState()).padding(8.dp)) {
            Column {
                hex.chunked(32).forEach { line ->
                    val bytes = runCatching { Hex.decode(line) }.getOrNull()
                    Text(line.chunked(2).joinToString(" ") + "  " + (bytes?.let { Hex.ascii(it) } ?: ""), style = MonoStyle)
                }
            }
        }
    }
}
