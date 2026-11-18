package com.cardvault.nfc.ui.components

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cardvault.nfc.data.BarcodeFormat
import com.cardvault.nfc.security.findFragmentActivity
import com.cardvault.nfc.ui.theme.Mono
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.BarcodeFormat as ZxFormat

private fun BarcodeFormat.toZxing(): ZxFormat? = when (this) {
    BarcodeFormat.NONE -> null
    BarcodeFormat.QR_CODE -> ZxFormat.QR_CODE
    BarcodeFormat.CODE_128 -> ZxFormat.CODE_128
    BarcodeFormat.CODE_39 -> ZxFormat.CODE_39
    BarcodeFormat.EAN_13 -> ZxFormat.EAN_13
    BarcodeFormat.EAN_8 -> ZxFormat.EAN_8
    BarcodeFormat.UPC_A -> ZxFormat.UPC_A
    BarcodeFormat.AZTEC -> ZxFormat.AZTEC
    BarcodeFormat.PDF_417 -> ZxFormat.PDF_417
    BarcodeFormat.DATA_MATRIX -> ZxFormat.DATA_MATRIX
}

private val BarcodeFormat.is2D get() = this == BarcodeFormat.QR_CODE || this == BarcodeFormat.AZTEC || this == BarcodeFormat.DATA_MATRIX

fun renderBarcode(format: BarcodeFormat, payload: String): Result<Bitmap> = runCatching {
    val zx = format.toZxing() ?: error("No barcode format")
    require(payload.isNotEmpty()) { "Empty payload" }
    val (w, h) = when {
        format.is2D -> 600 to 600
        format == BarcodeFormat.PDF_417 -> 900 to 300
        else -> 1000 to 320
    }
    val matrix = MultiFormatWriter().encode(payload, zx, w, h, mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8"))
    val pixels = IntArray(matrix.width * matrix.height) { i ->
        if (matrix.get(i % matrix.width, i / matrix.width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}

/** High-contrast barcode on a white plate. Forces screen brightness to 100% while visible. */
@Composable
fun BarcodeView(format: BarcodeFormat, payload: String, modifier: Modifier = Modifier, boostBrightness: Boolean = true) {
    val context = LocalContext.current
    if (boostBrightness) {
        DisposableEffect(Unit) {
            val window = runCatching { context.findFragmentActivity().window }.getOrNull()
            window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = 1f } }
            onDispose {
                window?.let { w ->
                    w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
                }
            }
        }
    }
    val result = remember(format, payload) { renderBarcode(format, payload) }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        result.fold(
            onSuccess = { bmp ->
                Image(
                    bmp.asImageBitmap(), "Barcode",
                    Modifier.fillMaxWidth().heightIn(max = if (format.is2D) 280.dp else 140.dp),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                )
            },
            onFailure = { e ->
                Box(Modifier.padding(16.dp)) { Text("Can't render ${format.label}: ${e.message}", color = Color.Black) }
            },
        )
        Text(payload, color = Color.Black, fontFamily = Mono, modifier = Modifier.padding(top = 8.dp))
    }
}
