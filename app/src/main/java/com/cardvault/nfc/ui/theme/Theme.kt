package com.cardvault.nfc.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cardvault.nfc.data.AppSettings
import com.cardvault.nfc.data.ThemeMode

val Navy = Color(0xFF141A2B)
val Cyan = Color(0xFF22D3EE)
val Coral = Color(0xFFFF7A6B)
val Amber = Color(0xFFFBBF24)
val Mint = Color(0xFF34D399)

val ACCENT_PRESETS = listOf(
    "#22D3EE" to "Cyan", "#FF7A6B" to "Coral", "#A78BFA" to "Violet", "#34D399" to "Emerald",
    "#FBBF24" to "Amber", "#F472B6" to "Pink", "#60A5FA" to "Blue", "#A3E635" to "Lime",
)

val Mono = FontFamily.Monospace
val MonoStyle = TextStyle(fontFamily = Mono, fontSize = 13.sp)

private fun parse(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Cyan)

private fun onColor(c: Color) = if (c.luminance() > 0.45f) Color(0xFF0B1020) else Color.White

private fun darkScheme(accent: Color, amoled: Boolean): ColorScheme {
    val bg = if (amoled) Color.Black else Navy
    val surface = if (amoled) Color(0xFF0D0F14) else Color(0xFF1A2238)
    val high = if (amoled) Color(0xFF181B22) else Color(0xFF232C47)
    return darkColorScheme(
        primary = accent, onPrimary = onColor(accent),
        secondary = Coral, onSecondary = Navy,
        tertiary = Mint,
        background = bg, onBackground = Color(0xFFE6EAF5),
        surface = surface, onSurface = Color(0xFFE6EAF5),
        surfaceVariant = high, onSurfaceVariant = Color(0xFFA7B0C8),
        surfaceContainer = surface, surfaceContainerHigh = high,
        surfaceContainerHighest = if (amoled) Color(0xFF21252E) else Color(0xFF2B3554),
        surfaceContainerLow = if (amoled) Color(0xFF07080B) else Color(0xFF171E31),
        outline = if (amoled) Color(0xFF2E3340) else Color(0xFF3A4566),
        error = Color(0xFFF87171),
    )
}

private fun lightScheme(accent: Color): ColorScheme {
    // Bright accents (cyan, lime…) need darkening to stay readable on white.
    val primary = if (accent.luminance() > 0.35f) lerp(accent, Color.Black, 0.35f) else accent
    return lightColorScheme(
        primary = primary, onPrimary = onColor(primary),
        secondary = Color(0xFFE5533F), onSecondary = Color.White,
        tertiary = Color(0xFF059669),
        background = Color(0xFFF4F6FB), onBackground = Color(0xFF141A2B),
        surface = Color.White, onSurface = Color(0xFF141A2B),
        surfaceVariant = Color(0xFFE7EBF3), onSurfaceVariant = Color(0xFF55607A),
        surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFEFF2F8),
        surfaceContainerHighest = Color(0xFFE3E8F1), surfaceContainerLow = Color(0xFFF0F3F9),
        outline = Color(0xFFC3CADA),
        error = Color(0xFFDC2626),
    )
}

@Composable
fun isAppInDarkTheme(settings: AppSettings): Boolean = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun CardVaultTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = isAppInDarkTheme(settings)
    val context = LocalContext.current
    val scheme = remember(dark, settings.dynamicColor, settings.accentHex, settings.amoledBlack) {
        val base = when {
            settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dark -> darkScheme(parse(settings.accentHex), settings.amoledBlack)
            else -> lightScheme(parse(settings.accentHex))
        }
        if (dark && settings.amoledBlack && settings.dynamicColor) {
            base.copy(background = Color.Black, surfaceContainerLow = Color(0xFF07080B))
        } else base
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography().let {
            it.copy(
                headlineSmall = it.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                titleLarge = it.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            )
        },
        content = content,
    )
}
