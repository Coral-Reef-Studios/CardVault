@file:OptIn(ExperimentalMaterial3Api::class)

package com.cardvault.nfc.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.TagLostException
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn as AndroidOptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.cardvault.nfc.AppContainer
import com.cardvault.nfc.data.BarcodeFormat
import com.cardvault.nfc.data.Card
import com.cardvault.nfc.data.CardCategory
import com.cardvault.nfc.data.CardSummary
import com.cardvault.nfc.data.StyleConfig
import com.cardvault.nfc.nfc.ScanOutcome
import com.cardvault.nfc.pass.BcbpParser
import com.cardvault.nfc.nfc.TagReader
import com.cardvault.nfc.security.findFragmentActivity
import com.cardvault.nfc.ui.components.InfoRow
import com.cardvault.nfc.ui.components.SectionCard
import com.cardvault.nfc.ui.components.confirmHaptic
import com.cardvault.nfc.ui.components.rejectHaptic
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun ScannerScreen(
    container: AppContainer,
    initialMode: String,
    onBack: () -> Unit,
    onSaveDraft: (Card) -> Unit,
    onOpenExisting: (String) -> Unit,
) {
    var tab by remember { mutableStateOf(if (initialMode == "camera") 1 else 0) }
    var duplicate by remember { mutableStateOf<Pair<Card, CardSummary>?>(null) }
    val scope = rememberCoroutineScope()

    val trySave: (Card) -> Unit = { draft ->
        scope.launch {
            val dup = container.repository.findDuplicate(draft)
            if (dup != null) duplicate = draft to dup else onSaveDraft(draft)
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Add card") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("NFC") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Camera") })
            }
            when (tab) {
                0 -> NfcScanPane(onSave = trySave)
                else -> CameraScanPane(onSave = trySave)
            }
        }
    }

    duplicate?.let { (draft, existing) ->
        AlertDialog(
            onDismissRequest = { duplicate = null },
            title = { Text("Already in your vault") },
            text = { Text("\"${existing.title}\" has the same number. Update it with this scan, or save a separate copy?") },
            confirmButton = {
                TextButton(onClick = {
                    duplicate = null
                    scope.launch {
                        val old = container.repository.get(existing.id) ?: return@launch
                        container.repository.save(
                            old.copy(
                                techInfo = draft.techInfo ?: old.techInfo,
                                transit = draft.transit ?: old.transit,
                                nfcPayload = if (draft.nfcPayload.hasContent) draft.nfcPayload else old.nfcPayload,
                                barcodeFormat = if (draft.barcodeFormat != BarcodeFormat.NONE) draft.barcodeFormat else old.barcodeFormat,
                                barcodePayload = draft.barcodePayload.ifBlank { old.barcodePayload },
                            )
                        )
                        onOpenExisting(existing.id)
                    }
                }) { Text("Update existing") }
            },
            dismissButton = {
                TextButton(onClick = { duplicate = null; onSaveDraft(draft) }) { Text("Save as copy") }
            },
        )
    }
}

// ---------------------------------------------------------------------------------------- NFC

@Composable
private fun NfcScanPane(onSave: (Card) -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val adapter = remember { NfcAdapter.getDefaultAdapter(context) }

    var status by remember { mutableStateOf("Hold your card or tag against the back of the phone") }
    var result by remember { mutableStateOf<ScanOutcome?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    if (adapter == null) {
        Message("This device has no NFC hardware. You can still add cards with the camera or manually.")
        return
    }
    if (!adapter.isEnabled) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("NFC is turned off.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) { Text("Open NFC settings") }
        }
        return
    }

    val activity = remember { context.findFragmentActivity() }
    DisposableEffect(result == null) {
        if (result == null) {
            val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
            adapter.enableReaderMode(activity, { tag ->
                // Binder thread: blocking I/O is fine here, UI updates go through the scope.
                try {
                    val outcome = TagReader.read(tag) { p -> scope.launch { status = p } }
                    scope.launch { view.confirmHaptic(); error = null; result = outcome }
                } catch (e: TagLostException) {
                    scope.launch { view.rejectHaptic(); error = "Card moved away too fast. Hold it steady for 1 second." }
                } catch (e: Exception) {
                    scope.launch { view.rejectHaptic(); error = "Read failed: ${e.message ?: e.javaClass.simpleName}" }
                }
            }, flags, null)
        }
        onDispose { runCatching { adapter.disableReaderMode(activity) } }
    }

    val outcome = result
    if (outcome == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            NfcRipple()
            Spacer(Modifier.height(24.dp))
            Text(status, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        }
    } else {
        ScanResultView(outcome, onSave = { onSave(outcome.draft) }, onRescan = { result = null; status = "Ready for the next card" })
    }
}

@Composable
private fun NfcRipple() {
    val t = rememberInfiniteTransition(label = "ripple")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "p")
    val color = MaterialTheme.colorScheme.primary
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until 3) {
                val f = (p + i / 3f) % 1f
                drawCircle(color.copy(alpha = (1f - f) * 0.6f), radius = size.minDimension / 2 * (0.3f + 0.7f * f), style = Stroke(4f))
            }
        }
        Icon(Icons.Filled.Nfc, null, Modifier.size(56.dp), tint = color)
    }
}

@Composable
private fun ScanResultView(outcome: ScanOutcome, onSave: () -> Unit, onRescan: () -> Unit) {
    val info = outcome.techInfo
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard("Detected") {
            InfoRow("Type", outcome.draft.title)
            InfoRow("Technologies", info.techList.joinToString(", "))
            InfoRow("UID", info.uidHex, mono = true)
            outcome.transit?.let {
                InfoRow("Network", it.network)
                InfoRow("Serial", it.serialDecimal.ifBlank { it.serialHex }, mono = true)
                InfoRow("Files read", it.files.joinToString { f -> "${f.name} (${f.records.size})" })
                if (it.counters.isNotEmpty()) InfoRow("Counters", it.counters.joinToString())
            }
            info.ndefRecords.forEach { InfoRow("NDEF", it.decoded) }
        }
        val mode = when {
            outcome.draft.isEmulatable -> "HCE emulation ready"
            outcome.transit != null -> "Read / inspect only (transit cards are cryptographically signed)"
            else -> "Reference only (can't be emulated)"
        }
        SectionCard("Emulation") { Text(mode) }
        if (info.notes.isNotEmpty()) {
            SectionCard("Notes") { info.notes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRescan, modifier = Modifier.weight(1f)) { Text("Scan again") }
            Button(onClick = onSave, modifier = Modifier.weight(1f), enabled = !info.isEmvPayment) { Text("Save to vault") }
        }
    }
}

// ------------------------------------------------------------------------------------- Camera

private data class CameraHit(val format: BarcodeFormat, val value: String)

@Composable
private fun CameraScanPane(onSave: (Card) -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    if (!granted) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Camera access is needed to scan barcodes and card numbers.", textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
        }
        return
    }

    val view = LocalView.current
    var hit by remember { mutableStateOf<CameraHit?>(null) }
    val numbers = remember { mutableStateListOf<String>() }
    var chosenNumber by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(20.dp))) {
            CameraPreview(
                paused = hit != null,
                onBarcode = { fmt, value -> if (hit == null) { hit = CameraHit(fmt, value); view.confirmHaptic() } },
                onNumbers = { found -> found.forEach { if (it !in numbers) numbers.add(0, it) }; while (numbers.size > 6) numbers.removeAt(numbers.lastIndex) },
            )
        }
        hit?.let {
            val bcbp = remember(it.value) { BcbpParser.parse(it.value) }
            SectionCard(if (bcbp != null) "Boarding pass found" else "Barcode found") {
                bcbp?.let { b ->
                    InfoRow("Flight", b.title)
                    InfoRow("Date", b.pass.departure)
                    InfoRow("Seat", b.pass.seat)
                }
                InfoRow("Format", it.format.label)
                InfoRow("Value", it.value, mono = true)
                TextButton(onClick = { hit = null }) { Text("Scan again") }
            }
        }
        if (numbers.isNotEmpty()) {
            SectionCard("Numbers seen (tap to use as card number)") {
                numbers.forEach { n ->
                    AssistChip(onClick = { chosenNumber = n }, label = { Text(if (n == chosenNumber) "✓ $n" else n) },
                        modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
        Button(
            onClick = {
                val h = hit
                val bcbp = h?.let { BcbpParser.parse(it.value) }
                if (h != null && bcbp != null) {
                    onSave(
                        Card(
                            title = "${bcbp.pass.carrier} ${bcbp.pass.flightNumber}",
                            subtitle = "${bcbp.pass.origin} → ${bcbp.pass.destination}",
                            category = CardCategory.BOARDING_PASS,
                            barcodeFormat = h.format,
                            barcodePayload = h.value,
                            pass = bcbp.pass,
                            style = StyleConfig(primaryHex = "#1D4ED8", secondaryHex = "#0B1330", iconId = "flight", showNumber = false),
                        )
                    )
                    return@Button
                }
                onSave(
                    Card(
                        title = "Loyalty card",
                        category = CardCategory.LOYALTY,
                        cardNumber = chosenNumber ?: h?.value?.takeIf { v -> v.length <= 24 } ?: "",
                        barcodeFormat = h?.format ?: BarcodeFormat.NONE,
                        barcodePayload = h?.value ?: "",
                        style = StyleConfig(primaryHex = "#F59E0B", secondaryHex = "#78350F", iconId = "shop"),
                    )
                )
            },
            enabled = hit != null || chosenNumber != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue") }
    }
}

private val NUMBER_REGEX = Regex("""(?:\d[ -]?){8,19}""")

@AndroidOptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(paused: Boolean, onBarcode: (BarcodeFormat, String) -> Unit, onNumbers: (List<String>) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val pausedRef = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    pausedRef.set(paused)
    var frame = 0

    val previewView = remember { PreviewView(context) }
    DisposableEffect(Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null || pausedRef.get()) { proxy.close(); return@setAnalyzer }
                val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                val runOcr = (frame++ % 10) == 0
                barcodeScanner.process(image)
                    .addOnSuccessListener { codes ->
                        codes.firstOrNull { !it.rawValue.isNullOrEmpty() }?.let { b ->
                            mapFormat(b.format)?.let { f -> onBarcode(f, b.rawValue!!) }
                        }
                    }
                    .addOnCompleteListener {
                        if (runOcr) {
                            textRecognizer.process(image)
                                .addOnSuccessListener { text ->
                                    val found = NUMBER_REGEX.findAll(text.text).map { m -> m.value.filter(Char::isDigit) }
                                        .filter { it.length in 8..19 }.distinct().toList()
                                    if (found.isNotEmpty()) onNumbers(found)
                                }
                                .addOnCompleteListener { proxy.close() }
                        } else {
                            proxy.close()
                        }
                    }
            }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            barcodeScanner.close()
            textRecognizer.close()
            executor.shutdown()
        }
    }
    AndroidView({ previewView }, Modifier.fillMaxSize())
}

private fun mapFormat(f: Int): BarcodeFormat? = when (f) {
    Barcode.FORMAT_QR_CODE -> BarcodeFormat.QR_CODE
    Barcode.FORMAT_CODE_128 -> BarcodeFormat.CODE_128
    Barcode.FORMAT_CODE_39 -> BarcodeFormat.CODE_39
    Barcode.FORMAT_EAN_13 -> BarcodeFormat.EAN_13
    Barcode.FORMAT_EAN_8 -> BarcodeFormat.EAN_8
    Barcode.FORMAT_UPC_A -> BarcodeFormat.UPC_A
    Barcode.FORMAT_AZTEC -> BarcodeFormat.AZTEC
    Barcode.FORMAT_PDF417 -> BarcodeFormat.PDF_417
    Barcode.FORMAT_DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
    else -> null
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp)) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
