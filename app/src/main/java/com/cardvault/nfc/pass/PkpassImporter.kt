package com.cardvault.nfc.pass

import com.cardvault.nfc.data.BarcodeFormat
import com.cardvault.nfc.data.Card
import com.cardvault.nfc.data.CardCategory
import com.cardvault.nfc.data.PassData
import com.cardvault.nfc.data.PassField
import com.cardvault.nfc.data.PassKind
import com.cardvault.nfc.data.StyleConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** Imports Apple Wallet .pkpass files (a zip with pass.json + images). Signature isn't verified. */
object PkpassImporter {

    class Imported(val card: Card, val artwork: ByteArray?)

    private const val MAX_ENTRY = 5 * 1024 * 1024

    fun import(bytes: ByteArray): Imported {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name.substringAfterLast('/')
                if (!e.isDirectory && name in WANTED) {
                    val data = zip.readBytes()
                    require(data.size <= MAX_ENTRY) { "$name too large" }
                    entries[name] = data
                }
            }
        }
        val json = entries["pass.json"] ?: error("Not a wallet pass (no pass.json)")
        val root = Json.parseToJsonElement(json.toString(Charsets.UTF_8).trimStart('﻿')).jsonObject

        val styleKey = STYLES.firstOrNull { root.containsKey(it) } ?: "generic"
        val style = root[styleKey] as? JsonObject ?: JsonObject(emptyMap())

        val sections = listOf("headerFields" to "header", "primaryFields" to "primary", "secondaryFields" to "secondary",
            "auxiliaryFields" to "auxiliary", "backFields" to "back")
        val fields = sections.flatMap { (key, section) ->
            (style[key] as? JsonArray).orEmpty().mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val value = o.str("value") ?: return@mapNotNull null
                FieldRaw(o.str("key") ?: "", o.str("label") ?: "", value, section)
            }
        }
        fun find(vararg hints: String) = fields.firstOrNull { f -> hints.any { h -> f.key.contains(h, true) || f.label.contains(h, true) } }?.value ?: ""

        val kind = when (styleKey) {
            "boardingPass" -> PassKind.BOARDING_PASS
            "eventTicket" -> PassKind.EVENT_TICKET
            "coupon" -> PassKind.COUPON
            "storeCard" -> if (fields.any { it.key.contains("balance", true) || it.label.contains("balance", true) }) PassKind.GIFT_CARD else PassKind.GENERIC
            else -> PassKind.GENERIC
        }
        val primary = fields.filter { it.section == "primary" }
        val relevant = root.str("relevantDate") ?: ""

        val pass = PassData(
            kind = kind,
            transitType = style.str("transitType")?.removePrefix("PKTransitType")?.uppercase() ?: "GENERIC",
            origin = if (kind == PassKind.BOARDING_PASS) primary.getOrNull(0)?.value ?: "" else "",
            originName = if (kind == PassKind.BOARDING_PASS) primary.getOrNull(0)?.label ?: "" else "",
            destination = if (kind == PassKind.BOARDING_PASS) primary.getOrNull(1)?.value ?: "" else "",
            destinationName = if (kind == PassKind.BOARDING_PASS) primary.getOrNull(1)?.label ?: "" else "",
            flightNumber = find("flight"),
            departure = relevant,
            boardingTime = find("boarding"),
            gate = find("gate"),
            terminal = find("terminal"),
            seat = find("seat"),
            group = find("group", "zone"),
            cabin = find("class", "cabin"),
            pnr = find("pnr", "conf", "booking", "record"),
            passenger = find("passenger", "name"),
            eventName = if (kind == PassKind.EVENT_TICKET) primary.firstOrNull()?.value ?: root.str("description") ?: "" else "",
            venue = find("venue", "location"),
            section = find("section"),
            row = find("row"),
            balance = find("balance"),
            fields = fields.map { PassField(it.label.ifBlank { it.key }, it.value, it.section) },
            source = "pkpass",
        )

        val barcode = (root["barcodes"] as? JsonArray)?.firstOrNull() as? JsonObject ?: root["barcode"] as? JsonObject
        val bFormat = when (barcode?.str("format")) {
            "PKBarcodeFormatQR" -> BarcodeFormat.QR_CODE
            "PKBarcodeFormatPDF417" -> BarcodeFormat.PDF_417
            "PKBarcodeFormatAztec" -> BarcodeFormat.AZTEC
            "PKBarcodeFormatCode128" -> BarcodeFormat.CODE_128
            else -> BarcodeFormat.NONE
        }

        val category = when (kind) {
            PassKind.BOARDING_PASS -> CardCategory.BOARDING_PASS
            PassKind.EVENT_TICKET -> CardCategory.TICKET
            PassKind.GIFT_CARD -> CardCategory.GIFT_CARD
            PassKind.COUPON -> CardCategory.LOYALTY
            PassKind.GENERIC -> if (styleKey == "storeCard") CardCategory.LOYALTY else CardCategory.CUSTOM
        }
        val bg = rgb(root.str("backgroundColor")) ?: "#1F2937"
        val fg = rgb(root.str("foregroundColor")) ?: "#FFFFFF"
        val card = Card(
            title = root.str("logoText") ?: root.str("organizationName") ?: "Pass",
            subtitle = root.str("description") ?: "",
            category = category,
            cardNumber = find("member", "card number", "account").ifBlank { root.str("serialNumber") ?: "" },
            barcodeFormat = if (barcode?.str("message").isNullOrEmpty()) BarcodeFormat.NONE else bFormat,
            barcodePayload = barcode?.str("message") ?: "",
            expiryDate = root.str("expirationDate")?.take(10),
            pass = pass,
            style = StyleConfig(
                primaryHex = bg, secondaryHex = darken(bg), textHex = fg,
                gradient = com.cardvault.nfc.data.GradientStyle.LINEAR,
                iconId = when (kind) {
                    PassKind.BOARDING_PASS -> if (pass.transitType == "TRAIN") "train" else "flight"
                    PassKind.EVENT_TICKET -> "ticket"
                    PassKind.GIFT_CARD -> "gift"
                    else -> "card"
                },
                showNumber = kind != PassKind.BOARDING_PASS && kind != PassKind.EVENT_TICKET,
            ),
        )
        return Imported(card, entries["strip@2x.png"] ?: entries["strip.png"] ?: entries["background@2x.png"] ?: entries["background.png"])
    }

    private data class FieldRaw(val key: String, val label: String, val value: String, val section: String)

    private val STYLES = listOf("boardingPass", "eventTicket", "coupon", "storeCard", "generic")
    private val WANTED = setOf("pass.json", "strip.png", "strip@2x.png", "background.png", "background@2x.png")

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

    private fun rgb(v: String?): String? {
        v ?: return null
        if (v.startsWith("#") && v.length == 7) return v.uppercase()
        val nums = Regex("""\d+""").findAll(v).map { it.value.toInt().coerceIn(0, 255) }.toList()
        return if (nums.size >= 3) "#%02X%02X%02X".format(nums[0], nums[1], nums[2]) else null
    }

    private fun darken(hex: String): String {
        val c = hex.removePrefix("#").toIntOrNull(16) ?: return hex
        val r = ((c shr 16 and 0xFF) * 0.55).toInt()
        val g = ((c shr 8 and 0xFF) * 0.55).toInt()
        val b = ((c and 0xFF) * 0.55).toInt()
        return "#%02X%02X%02X".format(r, g, b)
    }

}
