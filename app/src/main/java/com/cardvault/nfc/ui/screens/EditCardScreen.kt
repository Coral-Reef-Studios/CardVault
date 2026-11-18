@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.cardvault.nfc.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cardvault.nfc.AppContainer
import com.cardvault.nfc.data.ApduRule
import com.cardvault.nfc.data.BarcodeFormat
import com.cardvault.nfc.data.Card
import com.cardvault.nfc.data.CardCategory
import com.cardvault.nfc.data.CustomField
import com.cardvault.nfc.data.EmulationMode
import com.cardvault.nfc.data.GradientStyle
import com.cardvault.nfc.data.NfcPayload
import com.cardvault.nfc.data.NfcTechType
import com.cardvault.nfc.data.PassData
import com.cardvault.nfc.data.PassFormat
import com.cardvault.nfc.data.PassKind
import com.cardvault.nfc.data.StyleConfig
import com.cardvault.nfc.nfc.Hex
import com.cardvault.nfc.nfc.NdefTools
import com.cardvault.nfc.ui.components.CARD_ICONS
import com.cardvault.nfc.ui.components.CardFace
import com.cardvault.nfc.ui.components.CardFaceData
import com.cardvault.nfc.ui.components.PRESETS
import com.cardvault.nfc.ui.components.SectionCard
import com.cardvault.nfc.ui.components.SimpleDropdown
import com.cardvault.nfc.ui.components.hsvColor
import com.cardvault.nfc.ui.components.parseHexColor
import com.cardvault.nfc.ui.components.renderBarcode
import com.cardvault.nfc.ui.components.toHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class NdefKind(val label: String) { KEEP("Keep scanned NDEF"), URL("URL"), TEXT("Text"), WIFI("Wi-Fi"), VCARD("vCard") }

@Composable
fun EditCardScreen(container: AppContainer, cardId: String?, onDone: (String) -> Unit, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var card by remember { mutableStateOf<Card?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    var pendingArtwork by remember { mutableStateOf<Bitmap?>(null) }
    var removeArtwork by remember { mutableStateOf(false) }

    LaunchedEffect(cardId) {
        card = if (cardId != null) container.repository.get(cardId) else (container.pendingDraft ?: Card()).also {
            container.pendingDraft = null
        }
        container.pendingArtwork?.let { bytes ->
            container.pendingArtwork = null
            pendingArtwork = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
        }
    }
    val c = card ?: return
    // Always apply edits to the latest state, not the snapshot captured by this composition.
    fun update(f: (Card) -> Card) { card = card?.let(f) }
    fun style(f: (StyleConfig) -> StyleConfig) { card = card?.let { it.copy(style = f(it.style)) } }

    // NDEF builder state
    val scannedNdef = c.nfcPayload.mode == EmulationMode.NDEF_TYPE4 && c.nfcPayload.ndefMessageHex.isNotBlank()
    var ndefKind by remember { mutableStateOf(if (scannedNdef) NdefKind.KEEP else NdefKind.URL) }
    var f1 by remember { mutableStateOf("") }
    var f2 by remember { mutableStateOf("") }
    var f3 by remember { mutableStateOf("") }
    var f4 by remember { mutableStateOf("") }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            pendingArtwork = withContext(Dispatchers.IO) { loadScaled(context, uri) }
            removeArtwork = false
        }
    }

    fun buildPayload(): NfcPayload? = when (c.nfcPayload.mode) {
        EmulationMode.NONE -> c.nfcPayload
        EmulationMode.APDU_TABLE -> c.nfcPayload
        EmulationMode.NDEF_TYPE4 -> {
            val msg = when (ndefKind) {
                NdefKind.KEEP -> null
                NdefKind.URL -> f1.takeIf { it.isNotBlank() }?.let { NdefTools.uri(it.trim()) }
                NdefKind.TEXT -> f1.takeIf { it.isNotBlank() }?.let { NdefTools.text(it) }
                NdefKind.WIFI -> f1.takeIf { it.isNotBlank() }?.let { NdefTools.wifi(it, f2) }
                NdefKind.VCARD -> f1.takeIf { it.isNotBlank() }?.let { NdefTools.vCard(it, f2, f3, f4) }
            }
            when {
                msg != null -> c.nfcPayload.copy(ndefMessageHex = Hex.encode(msg.toByteArray()))
                ndefKind == NdefKind.KEEP && scannedNdef -> c.nfcPayload
                else -> null
            }
        }
    }

    fun save() {
        error = null
        if (c.title.isBlank()) { error = "Give the card a title"; return }
        c.expiryDate?.takeIf { it.isNotBlank() }?.let {
            if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(it)) { error = "Expiry must be YYYY-MM-DD"; return }
        }
        if (c.barcodeFormat != BarcodeFormat.NONE && renderBarcode(c.barcodeFormat, c.barcodePayload).isFailure) {
            error = "Barcode value isn't valid for ${c.barcodeFormat.label}"; return
        }
        val payload = buildPayload() ?: run { error = "Fill in the NDEF record to emulate"; return }
        if (payload.mode == EmulationMode.APDU_TABLE) {
            if (!Hex.isValid(c.aidHex) || Hex.clean(c.aidHex).length !in 10..32) { error = "AID must be 5–16 bytes of hex"; return }
            if (payload.apduRules.any { !Hex.isValid(it.commandPrefixHex) || !Hex.isValid(it.responseHex) || Hex.clean(it.responseHex).length < 4 }) {
                error = "Each APDU rule needs hex command prefix and a hex response ending in a status word (e.g. 9000)"; return
            }
        }
        scope.launch {
            var style = c.style
            withContext(Dispatchers.IO) {
                val file = container.repository.artworkFile(c.id)
                when {
                    pendingArtwork != null -> {
                        file.outputStream().use { pendingArtwork!!.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                        style = style.copy(artworkFile = file.name)
                    }
                    removeArtwork -> { file.delete(); style = style.copy(artworkFile = null) }
                }
            }
            val techType = if (payload.mode != EmulationMode.NONE && c.nfcTechType == NfcTechType.NONE) {
                if (payload.mode == EmulationMode.NDEF_TYPE4) NfcTechType.NDEF else NfcTechType.ISO_DEP
            } else c.nfcTechType
            val saved = container.repository.save(
                c.copy(
                    nfcPayload = payload,
                    style = style,
                    nfcTechType = techType,
                    aidHex = if (payload.mode == EmulationMode.NDEF_TYPE4) "D2760000850101" else Hex.clean(c.aidHex),
                    customFields = c.customFields.filter { it.key.isNotBlank() },
                )
            )
            onDone(saved.id)
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (cardId == null) "New card" else "Edit card") },
            navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "Cancel") } },
            actions = { TextButton(onClick = { save() }) { Text("Save") } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Live preview pinned at the top
            val previewArt: File? = if (removeArtwork) null else container.repository.resolveArtwork(c.style)
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                CardFace(
                    CardFaceData(c.title, c.subtitle, c.cardNumber.chunked(4).joinToString(" "), c.expiryDate, c.holderName,
                        c.style, c.nfcTechType != NfcTechType.NONE || c.nfcPayload.mode != EmulationMode.NONE,
                        c.barcodeFormat != BarcodeFormat.NONE, c.requireBiometric, c.pass?.let(PassFormat::face)),
                    artwork = previewArt,
                )
                pendingArtwork?.let {
                    Text("New artwork selected", Modifier.align(Alignment.BottomCenter).padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(4.dp))
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionCard("Basics") {
                    Field("Title", c.title) { v -> update { it.copy(title = v) } }
                    Field("Subtitle / issuer", c.subtitle) { v -> update { it.copy(subtitle = v) } }
                    SimpleDropdown("Category", CardCategory.entries, c.category, { it.label }, { v ->
                        update {
                            val kind = v.passKind
                            it.copy(
                                category = v,
                                pass = when {
                                    kind != null -> (it.pass ?: PassData()).copy(kind = kind)
                                    it.pass?.source == "pkpass" -> it.pass // keep imported generic passes
                                    else -> null
                                },
                                style = if (kind != null && it.style.iconId == "card") it.style.copy(
                                    iconId = when (kind) { PassKind.BOARDING_PASS -> "flight"; PassKind.EVENT_TICKET -> "ticket"; else -> "gift" },
                                    showNumber = kind == PassKind.GIFT_CARD,
                                ) else it.style,
                            )
                        }
                    },
                        Modifier.padding(vertical = 4.dp))
                    Field("Card number", c.cardNumber, mono = true) { v -> update { it.copy(cardNumber = v) } }
                    Field("Holder name", c.holderName) { v -> update { it.copy(holderName = v) } }
                    Field("Expiry (YYYY-MM-DD)", c.expiryDate ?: "") { v -> update { it.copy(expiryDate = v.ifBlank { null }) } }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Always require biometrics", Modifier.weight(1f))
                        Switch(c.requireBiometric, { v -> update { it.copy(requireBiometric = v) } })
                    }
                }

                c.pass?.let { p ->
                    PassEditor(p) { f -> update { it.copy(pass = it.pass?.let(f)) } }
                }

                SectionCard("Barcode") {
                    SimpleDropdown("Format", BarcodeFormat.entries, c.barcodeFormat, { it.label }, { v -> update { it.copy(barcodeFormat = v) } })
                    if (c.barcodeFormat != BarcodeFormat.NONE) {
                        Field("Barcode value", c.barcodePayload, mono = true) { v -> update { it.copy(barcodePayload = v) } }
                    }
                }

                SectionCard("NFC emulation (HCE)") {
                    if (c.transit != null) {
                        Text("Transit cards use signed sessions with the validator, so they can't be emulated. " +
                            "You can still add an NDEF or custom profile for something else.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SimpleDropdown("Mode", EmulationMode.entries, c.nfcPayload.mode, { it.label },
                        { v -> update { it.copy(nfcPayload = it.nfcPayload.copy(mode = v)) } }, Modifier.padding(vertical = 4.dp))
                    when (c.nfcPayload.mode) {
                        EmulationMode.NDEF_TYPE4 -> {
                            val kinds = if (scannedNdef) NdefKind.entries else NdefKind.entries - NdefKind.KEEP
                            SimpleDropdown("Record", kinds, ndefKind, { it.label }, { ndefKind = it })
                            when (ndefKind) {
                                NdefKind.KEEP -> Text("${c.nfcPayload.ndefMessageHex.length / 2} bytes from the scanned tag",
                                    style = MaterialTheme.typography.bodySmall)
                                NdefKind.URL -> Field("https://…", f1) { f1 = it }
                                NdefKind.TEXT -> Field("Text", f1) { f1 = it }
                                NdefKind.WIFI -> { Field("SSID", f1) { f1 = it }; Field("Password (empty = open)", f2) { f2 = it } }
                                NdefKind.VCARD -> {
                                    Field("Full name", f1) { f1 = it }; Field("Phone", f2) { f2 = it }
                                    Field("Email", f3) { f3 = it }; Field("Organisation", f4) { f4 = it }
                                }
                            }
                        }
                        EmulationMode.APDU_TABLE -> {
                            Field("AID (hex)", c.aidHex, mono = true) { v -> update { it.copy(aidHex = v) } }
                            Text("Rules are matched top to bottom by command prefix.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            c.nfcPayload.apduRules.forEachIndexed { i, rule ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Field("C-APDU prefix", rule.commandPrefixHex, mono = true) { v -> update { it.withRule(i, rule.copy(commandPrefixHex = v)) } }
                                        Field("R-APDU (incl. SW)", rule.responseHex, mono = true) { v -> update { it.withRule(i, rule.copy(responseHex = v)) } }
                                    }
                                    IconButton(onClick = { update { it.copy(nfcPayload = it.nfcPayload.copy(apduRules = it.nfcPayload.apduRules.filterIndexed { j, _ -> j != i })) } }) {
                                        Icon(Icons.Filled.Close, "Remove rule")
                                    }
                                }
                            }
                            TextButton(onClick = {
                                update { it.copy(nfcPayload = it.nfcPayload.copy(apduRules = it.nfcPayload.apduRules + ApduRule("00CA", "9000"))) }
                            }) { Icon(Icons.Filled.Add, null); Text("Add rule") }
                        }
                        EmulationMode.NONE -> Unit
                    }
                }

                CustomizationStudio(
                    style = c.style,
                    onStyle = { f -> style(f) },
                    onPickImage = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    hasArtwork = pendingArtwork != null || (!removeArtwork && previewArt != null),
                    onRemoveArtwork = { pendingArtwork = null; removeArtwork = true },
                )

                SectionCard("Custom fields") {
                    c.customFields.forEachIndexed { i, f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(f.key, { v -> update { it.copy(customFields = it.customFields.replace(i, f.copy(key = v))) } },
                                label = { Text("Key") }, modifier = Modifier.weight(1f), singleLine = true)
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(f.value, { v -> update { it.copy(customFields = it.customFields.replace(i, f.copy(value = v))) } },
                                label = { Text("Value") }, modifier = Modifier.weight(1f), singleLine = true)
                            IconButton(onClick = { update { it.copy(customFields = it.customFields.filterIndexed { j, _ -> j != i }) } }) {
                                Icon(Icons.Filled.Close, "Remove field")
                            }
                        }
                    }
                    TextButton(onClick = { update { it.copy(customFields = it.customFields + CustomField("", "")) } }) {
                        Icon(Icons.Filled.Add, null); Text("Add field")
                    }
                }
                Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("Save card") }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun PassEditor(p: PassData, onChange: ((PassData) -> PassData) -> Unit) {
    val title = when (p.kind) {
        PassKind.BOARDING_PASS -> "Boarding pass"
        PassKind.EVENT_TICKET -> "Ticket"
        PassKind.GIFT_CARD -> "Gift card"
        PassKind.COUPON -> "Coupon"
        PassKind.GENERIC -> "Pass"
    }
    SectionCard(title) {
        when (p.kind) {
            PassKind.BOARDING_PASS -> {
                SimpleDropdown("Transport", listOf("AIR", "TRAIN", "BUS", "BOAT"), p.transitType.ifBlank { "AIR" },
                    { it.lowercase().replaceFirstChar(Char::uppercase) }, { v -> onChange { it.copy(transitType = v) } })
                PassRow {
                    SmallField("From", p.origin, Modifier.weight(1f)) { v -> onChange { it.copy(origin = v.uppercase()) } }
                    SmallField("To", p.destination, Modifier.weight(1f)) { v -> onChange { it.copy(destination = v.uppercase()) } }
                }
                PassRow {
                    SmallField("Carrier", p.carrier, Modifier.weight(1f)) { v -> onChange { it.copy(carrier = v.uppercase()) } }
                    SmallField("Flight / train no.", p.flightNumber, Modifier.weight(1f)) { v -> onChange { it.copy(flightNumber = v) } }
                }
                Field("Departure (YYYY-MM-DD HH:mm)", p.departure) { v -> onChange { it.copy(departure = v) } }
                PassRow {
                    SmallField("Boarding", p.boardingTime, Modifier.weight(1f)) { v -> onChange { it.copy(boardingTime = v) } }
                    SmallField("Gate", p.gate, Modifier.weight(1f)) { v -> onChange { it.copy(gate = v) } }
                    SmallField("Terminal", p.terminal, Modifier.weight(1f)) { v -> onChange { it.copy(terminal = v) } }
                }
                PassRow {
                    SmallField("Seat", p.seat, Modifier.weight(1f)) { v -> onChange { it.copy(seat = v) } }
                    SmallField("Group / zone", p.group, Modifier.weight(1f)) { v -> onChange { it.copy(group = v) } }
                    SmallField("Class", p.cabin, Modifier.weight(1f)) { v -> onChange { it.copy(cabin = v) } }
                }
                PassRow {
                    SmallField("Booking ref", p.pnr, Modifier.weight(1f)) { v -> onChange { it.copy(pnr = v.uppercase()) } }
                    SmallField("Passenger", p.passenger, Modifier.weight(1f)) { v -> onChange { it.copy(passenger = v) } }
                }
            }
            PassKind.EVENT_TICKET -> {
                Field("Event", p.eventName) { v -> onChange { it.copy(eventName = v) } }
                Field("Venue", p.venue) { v -> onChange { it.copy(venue = v) } }
                Field("Date & time (YYYY-MM-DD HH:mm)", p.departure) { v -> onChange { it.copy(departure = v) } }
                PassRow {
                    SmallField("Entrance / gate", p.gate, Modifier.weight(1f)) { v -> onChange { it.copy(gate = v) } }
                    SmallField("Section", p.section, Modifier.weight(1f)) { v -> onChange { it.copy(section = v) } }
                }
                PassRow {
                    SmallField("Row", p.row, Modifier.weight(1f)) { v -> onChange { it.copy(row = v) } }
                    SmallField("Seat", p.seat, Modifier.weight(1f)) { v -> onChange { it.copy(seat = v) } }
                }
                Field("Ticket holder", p.passenger) { v -> onChange { it.copy(passenger = v) } }
            }
            PassKind.GIFT_CARD -> {
                PassRow {
                    SmallField("Balance", p.balance, Modifier.weight(1f)) { v -> onChange { it.copy(balance = v) } }
                    SmallField("PIN", p.pin, Modifier.weight(1f)) { v -> onChange { it.copy(pin = v) } }
                }
            }
            else -> Field("Date (optional)", p.departure) { v -> onChange { it.copy(departure = v) } }
        }
        if (p.departure.isNotBlank() && PassFormat.parseTime(p.departure) == null) {
            Text("Couldn't read that date. Use YYYY-MM-DD HH:mm.", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        if (p.fields.isNotEmpty()) {
            Text("${p.fields.size} extra fields from the imported pass are kept.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PassRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) =
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)

@Composable
private fun SmallField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = modifier.padding(vertical = 3.dp))
}

private fun Card.withRule(i: Int, rule: ApduRule) =
    copy(nfcPayload = nfcPayload.copy(apduRules = nfcPayload.apduRules.replace(i, rule)))

private fun <T> List<T>.replace(i: Int, v: T): List<T> = toMutableList().also { it[i] = v }

@Composable
private fun Field(label: String, value: String, mono: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        textStyle = if (mono) com.cardvault.nfc.ui.theme.MonoStyle else MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    )
}

@Composable
private fun CustomizationStudio(
    style: StyleConfig,
    onStyle: ((StyleConfig) -> StyleConfig) -> Unit,
    onPickImage: () -> Unit,
    hasArtwork: Boolean,
    onRemoveArtwork: () -> Unit,
) {
    SectionCard("Customize") {
        Text("Presets", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 6.dp)) {
            PRESETS.forEach { (p, s) ->
                val selected = style.primaryHex.equals(p, true) && style.secondaryHex.equals(s, true)
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(parseHexColor(p), parseHexColor(s))))
                        .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { onStyle { it.copy(primaryHex = p, secondaryHex = s) } },
                )
            }
        }
        Text("Primary hue", style = MaterialTheme.typography.labelMedium)
        var hue by remember { mutableStateOf(0f) }
        Slider(hue, { h -> hue = h; onStyle { it.copy(primaryHex = hsvColor(h).toHex()) } }, valueRange = 0f..360f)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HexField("Primary", style.primaryHex, Modifier.weight(1f)) { v -> onStyle { it.copy(primaryHex = v) } }
            HexField("Secondary", style.secondaryHex, Modifier.weight(1f)) { v -> onStyle { it.copy(secondaryHex = v) } }
            HexField("Text", style.textHex, Modifier.weight(1f)) { v -> onStyle { it.copy(textHex = v) } }
        }

        Spacer(Modifier.height(8.dp))
        Text("Gradient", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GradientStyle.entries.forEach { g ->
                FilterChip(style.gradient == g, { onStyle { it.copy(gradient = g) } }, label = { Text(g.label) })
            }
        }
        if (style.gradient == GradientStyle.LINEAR) {
            Text("Angle ${style.gradientAngle.toInt()}°", style = MaterialTheme.typography.labelMedium)
            Slider(style.gradientAngle, { a -> onStyle { it.copy(gradientAngle = a) } }, valueRange = 0f..360f)
        }

        Text("Icon", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(vertical = 6.dp)) {
            CARD_ICONS.forEach { (id, icon) ->
                val selected = style.iconId == id
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { onStyle { it.copy(iconId = id) } },
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, id, tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPickImage) { Icon(Icons.Filled.Image, null); Spacer(Modifier.width(6.dp)); Text("Artwork") }
            if (hasArtwork) TextButton(onClick = onRemoveArtwork) { Text("Remove artwork") }
        }

        Spacer(Modifier.height(8.dp))
        Text("Visible on card face", style = MaterialTheme.typography.labelMedium)
        Toggle("Subtitle", style.showSubtitle) { v -> onStyle { it.copy(showSubtitle = v) } }
        Toggle("Card number (masked in wallet)", style.showNumber) { v -> onStyle { it.copy(showNumber = v) } }
        Toggle("Expiry date", style.showExpiry) { v -> onStyle { it.copy(showExpiry = v) } }
        Toggle("Holder name", style.showHolder) { v -> onStyle { it.copy(showHolder = v) } }
        Toggle("NFC / barcode badge", style.showBadge) { v -> onStyle { it.copy(showBadge = v) } }
    }
}

@Composable
private fun HexField(label: String, value: String, modifier: Modifier, onValid: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v
            val norm = if (v.startsWith("#")) v else "#$v"
            if (Regex("#[0-9a-fA-F]{6}").matches(norm)) onValid(norm.uppercase())
        },
        label = { Text(label) },
        singleLine = true,
        textStyle = com.cardvault.nfc.ui.theme.MonoStyle,
        modifier = modifier,
    )
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

/** Decodes and downsizes a picked image to max 1200 px wide. */
private fun loadScaled(context: android.content.Context, uri: Uri): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 1200) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}.getOrNull()
