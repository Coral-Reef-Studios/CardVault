package com.cardvault.nfc.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardvault.nfc.data.PassFace
import com.cardvault.nfc.data.PassKind
import com.cardvault.nfc.data.StyleConfig
import androidx.compose.material.icons.filled.Flight
import com.cardvault.nfc.data.GradientStyle
import com.cardvault.nfc.ui.theme.Mono
import java.io.File
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class CardFaceData(
    val title: String,
    val subtitle: String,
    val numberText: String,
    val expiry: String?,
    val holder: String,
    val style: StyleConfig,
    val hasNfc: Boolean,
    val hasBarcode: Boolean,
    val locked: Boolean = false,
    val passFace: PassFace? = null,
)

val CardShape = RoundedCornerShape(20.dp)

fun Modifier.cardBackground(style: StyleConfig): Modifier = drawBehind {
    val c1 = parseHexColor(style.primaryHex)
    val c2 = parseHexColor(style.secondaryHex)
    val brush = when (style.gradient) {
        GradientStyle.SOLID -> Brush.linearGradient(listOf(c1, c1))
        GradientStyle.LINEAR -> {
            val rad = Math.toRadians(style.gradientAngle.toDouble())
            val r = max(size.width, size.height) / 2f
            val cx = size.width / 2f
            val cy = size.height / 2f
            val dx = (cos(rad) * r).toFloat()
            val dy = (sin(rad) * r).toFloat()
            Brush.linearGradient(listOf(c1, c2), start = Offset(cx - dx, cy - dy), end = Offset(cx + dx, cy + dy))
        }
        GradientStyle.RADIAL -> Brush.radialGradient(
            listOf(c1, c2), center = Offset(size.width * 0.2f, size.height * 0.15f), radius = max(size.width, size.height) * 1.1f,
        )
        GradientStyle.METALLIC -> Brush.linearGradient(
            0f to c2, 0.35f to c1, 0.5f to Color.White.copy(alpha = 0.55f).compositeOver(c1), 0.65f to c1, 1f to c2,
            start = Offset.Zero, end = Offset(size.width, size.height),
        )
    }
    drawRect(brush)
}

private fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
}

@Composable
fun CardFace(data: CardFaceData, modifier: Modifier = Modifier, artwork: File? = null, compact: Boolean = false) {
    val textColor = parseHexColor(data.style.textHex, Color.White)
    val art = remember(artwork?.path, artwork?.lastModified()) {
        artwork?.let { f -> runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() }
    }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(if (compact) 3.4f else 1.586f)
            .clip(CardShape)
            .cardBackground(data.style)
            .border(1.dp, Color.White.copy(alpha = 0.12f), CardShape),
    ) {
        if (art != null) {
            Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.55f)
        }
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(iconFor(data.style.iconId), null, tint = textColor) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(data.title.ifBlank { "Untitled card" }, color = textColor, fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (data.style.showSubtitle && data.subtitle.isNotBlank()) {
                        Text(data.subtitle, color = textColor.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
                if (data.locked) Icon(Icons.Filled.Lock, "Biometric protected", tint = textColor.copy(alpha = 0.8f))
            }
            if (compact) {
                data.passFace?.let { f ->
                    val line = if (f.kind == PassKind.BOARDING_PASS && f.origin.isNotBlank()) "${f.origin} → ${f.destination}  ${f.line1}" else f.line1
                    Text(line, color = textColor.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } ?: run {
                    if (data.style.showNumber && data.numberText.isNotBlank()) {
                        Text(data.numberText, color = textColor.copy(alpha = 0.85f), fontFamily = Mono, fontSize = 14.sp, maxLines = 1)
                    }
                }
                return@Column
            }
            data.passFace?.let { f -> PassFaceContent(f, textColor); return@Column }
            Column {
                if (data.style.showNumber && data.numberText.isNotBlank()) {
                    Text(data.numberText, color = textColor, fontFamily = Mono, fontSize = 17.sp, letterSpacing = 1.5.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        if (data.style.showHolder && data.holder.isNotBlank()) {
                            Text(data.holder.uppercase(), color = textColor.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1)
                        }
                        if (data.style.showExpiry && !data.expiry.isNullOrBlank()) {
                            Text("VALID ${data.expiry}", color = textColor.copy(alpha = 0.7f), fontSize = 11.sp)
                        }
                    }
                    if (data.style.showBadge) {
                        if (data.hasNfc) Badge(Icons.Filled.Nfc, "NFC", textColor)
                        if (data.hasBarcode) Badge(Icons.Filled.QrCode2, "CODE", textColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun PassFaceContent(f: PassFace, textColor: Color) {
    Column {
        if (f.kind == PassKind.BOARDING_PASS && (f.origin.isNotBlank() || f.destination.isNotBlank())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.origin.ifBlank { "———" }, color = textColor, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Icon(Icons.Filled.Flight, null, Modifier.padding(horizontal = 12.dp).size(26.dp).graphicsLayerRotate(90f), tint = textColor.copy(alpha = 0.8f))
                Text(f.destination.ifBlank { "———" }, color = textColor, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        } else if (f.line1.isNotBlank() && f.kind != PassKind.BOARDING_PASS) {
            Text(f.line1, color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val second = if (f.kind == PassKind.BOARDING_PASS) listOf(f.line1, f.line2) else listOf(f.line2)
        second.filter { it.isNotBlank() }.forEach {
            Text(it, color = textColor.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun Modifier.graphicsLayerRotate(deg: Float) = this.then(Modifier.rotate(deg))

@Composable
private fun Badge(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color) {
    Row(
        Modifier.padding(start = 6.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.25f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = color)
        Spacer(Modifier.width(3.dp))
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

fun maskNumber(last4: String): String = if (last4.isBlank()) "" else "•••• $last4"
