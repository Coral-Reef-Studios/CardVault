package com.cardvault.nfc.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import com.cardvault.nfc.data.NdefRecordInfo
import java.nio.ByteBuffer
import java.nio.charset.Charset

object NdefTools {

    fun describe(message: NdefMessage): List<NdefRecordInfo> = message.records.map { describe(it) }

    fun describe(record: NdefRecord): NdefRecordInfo {
        val type = String(record.type, Charsets.US_ASCII)
        val decoded = runCatching { decode(record, type) }.getOrElse { "(unparsed) ${Hex.ascii(record.payload)}" }
        return NdefRecordInfo(
            tnf = record.tnf.toInt(),
            type = tnfName(record.tnf) + if (type.isNotEmpty()) ":$type" else "",
            idHex = Hex.encode(record.id),
            payloadHex = Hex.encode(record.payload),
            decoded = decoded,
        )
    }

    private fun decode(record: NdefRecord, type: String): String = when (record.tnf) {
        NdefRecord.TNF_WELL_KNOWN -> when {
            record.type.contentEquals(NdefRecord.RTD_URI) -> "URI: ${record.toUri()}"
            record.type.contentEquals(NdefRecord.RTD_TEXT) -> "Text: ${decodeText(record.payload)}"
            record.type.contentEquals(NdefRecord.RTD_SMART_POSTER) -> "Smart Poster (${record.payload.size} bytes)"
            else -> "Well-known $type"
        }
        NdefRecord.TNF_ABSOLUTE_URI -> "Absolute URI: ${String(record.type, Charsets.UTF_8)}"
        NdefRecord.TNF_MIME_MEDIA -> when (type.lowercase()) {
            "text/vcard", "text/x-vcard" -> "vCard:\n" + String(record.payload, Charsets.UTF_8)
            "application/vnd.wfa.wsc" -> "Wi-Fi: " + (decodeWifi(record.payload) ?: "config")
            else -> if (type.startsWith("text/")) "$type: ${String(record.payload, Charsets.UTF_8)}"
            else "MIME $type (${record.payload.size} bytes)"
        }
        NdefRecord.TNF_EXTERNAL_TYPE ->
            if (type == "android.com:pkg") "Android app: ${String(record.payload, Charsets.US_ASCII)}"
            else "External $type: ${Hex.ascii(record.payload)}"
        NdefRecord.TNF_EMPTY -> "Empty record"
        else -> "TNF ${record.tnf}: ${Hex.ascii(record.payload)}"
    }

    private fun decodeText(payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        val status = payload[0].toInt()
        val utf16 = status and 0x80 != 0
        val langLen = status and 0x3F
        val charset = if (utf16) Charset.forName("UTF-16") else Charsets.UTF_8
        return String(payload, 1 + langLen, payload.size - 1 - langLen, charset)
    }

    private fun decodeWifi(payload: ByteArray): String? {
        // Walk WSC TLVs (2-byte type, 2-byte length), descending into Credential (0x100E).
        fun walk(buf: ByteArray, out: MutableMap<Int, ByteArray>) {
            var i = 0
            while (i + 4 <= buf.size) {
                val t = ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
                val l = ((buf[i + 2].toInt() and 0xFF) shl 8) or (buf[i + 3].toInt() and 0xFF)
                if (i + 4 + l > buf.size) return
                val v = buf.copyOfRange(i + 4, i + 4 + l)
                if (t == 0x100E) walk(v, out) else out[t] = v
                i += 4 + l
            }
        }
        val map = mutableMapOf<Int, ByteArray>()
        walk(payload, map)
        val ssid = map[0x1045]?.let { String(it, Charsets.UTF_8) } ?: return null
        return "SSID \"$ssid\"" + if (map.containsKey(0x1027)) " (password stored)" else " (open)"
    }

    private fun tnfName(tnf: Short) = when (tnf) {
        NdefRecord.TNF_EMPTY -> "EMPTY"
        NdefRecord.TNF_WELL_KNOWN -> "WELL_KNOWN"
        NdefRecord.TNF_MIME_MEDIA -> "MIME"
        NdefRecord.TNF_ABSOLUTE_URI -> "ABS_URI"
        NdefRecord.TNF_EXTERNAL_TYPE -> "EXTERNAL"
        NdefRecord.TNF_UNKNOWN -> "UNKNOWN"
        NdefRecord.TNF_UNCHANGED -> "UNCHANGED"
        else -> "TNF$tnf"
    }

    // ---- builders for the Custom Tag Builder ----------------------------------------------

    fun uri(uri: String): NdefMessage = NdefMessage(NdefRecord.createUri(uri))

    fun text(text: String, lang: String = "en"): NdefMessage = NdefMessage(NdefRecord.createTextRecord(lang, text))

    fun vCard(name: String, phone: String, email: String, org: String): NdefMessage {
        val vcard = buildString {
            append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
            append("FN:").append(name).append("\r\n")
            if (org.isNotBlank()) append("ORG:").append(org).append("\r\n")
            if (phone.isNotBlank()) append("TEL:").append(phone).append("\r\n")
            if (email.isNotBlank()) append("EMAIL:").append(email).append("\r\n")
            append("END:VCARD\r\n")
        }
        return NdefMessage(NdefRecord.createMime("text/vcard", vcard.toByteArray(Charsets.UTF_8)))
    }

    /** Wi-Fi Simple Config credential (WPA2-PSK or open). */
    fun wifi(ssid: String, password: String): NdefMessage {
        fun tlv(type: Int, value: ByteArray): ByteArray =
            ByteBuffer.allocate(4 + value.size).putShort(type.toShort()).putShort(value.size.toShort()).put(value).array()
        fun short(v: Int) = byteArrayOf((v shr 8).toByte(), v.toByte())
        val open = password.isEmpty()
        val credential = tlv(0x1026, byteArrayOf(1)) +
            tlv(0x1045, ssid.toByteArray(Charsets.UTF_8)) +
            tlv(0x1003, short(if (open) 0x0001 else 0x0020)) +
            tlv(0x100F, short(if (open) 0x0001 else 0x0008)) +
            tlv(0x1027, password.toByteArray(Charsets.UTF_8)) +
            tlv(0x1020, ByteArray(6) { 0xFF.toByte() })
        val payload = tlv(0x100E, credential)
        return NdefMessage(NdefRecord.createMime("application/vnd.wfa.wsc", payload))
    }

    fun parseHex(hex: String): NdefMessage? = runCatching { NdefMessage(Hex.decode(hex)) }.getOrNull()
}
