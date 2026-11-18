package com.cardvault.nfc.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

fun parseHexColor(hex: String, fallback: Color = Color.Gray): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex.trim().let { if (it.startsWith("#")) it else "#$it" })) }
        .getOrDefault(fallback)

fun Color.toHex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

fun hsvColor(hue: Float, sat: Float = 0.75f, value: Float = 0.85f): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))

val PRESETS: List<Pair<String, String>> = listOf(
    "#E11D48" to "#7F1D1D", // transit red
    "#2563EB" to "#1E1B4B", // royal
    "#0EA5E9" to "#312E81", // sky
    "#10B981" to "#064E3B", // emerald
    "#F59E0B" to "#78350F", // amber
    "#F97316" to "#7C2D12", // orange
    "#8B5CF6" to "#2E1065", // violet
    "#EC4899" to "#500724", // pink
    "#14B8A6" to "#134E4A", // teal
    "#64748B" to "#0F172A", // slate
    "#FACC15" to "#1C1917", // gold/black
    "#F5F5F4" to "#A8A29E", // pearl
)
