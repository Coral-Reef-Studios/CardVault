package com.cardvault.nfc.nfc

object Hex {
    fun encode(bytes: ByteArray?, separator: String = ""): String =
        bytes?.joinToString(separator) { "%02X".format(it.toInt() and 0xFF) } ?: ""

    fun clean(hex: String): String = hex.filter { it.isLetterOrDigit() }.uppercase()

    fun isValid(hex: String): Boolean {
        val c = clean(hex)
        return c.length % 2 == 0 && c.all { it in '0'..'9' || it in 'A'..'F' }
    }

    fun decode(hex: String): ByteArray {
        val c = clean(hex)
        require(c.length % 2 == 0) { "Odd hex length" }
        return ByteArray(c.length / 2) { i -> c.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    fun ascii(bytes: ByteArray): String =
        bytes.map { val c = it.toInt() and 0xFF; if (c in 0x20..0x7E) c.toChar() else '.' }.joinToString("")
}

object Apdu {
    val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
    val SW_SECURITY_NOT_SATISFIED = byteArrayOf(0x69, 0x82.toByte())
    val SW_FILE_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
    val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D, 0x00)
    val SW_COMMAND_NOT_ALLOWED = byteArrayOf(0x69, 0x86.toByte())
    val SW_WRONG_P1P2 = byteArrayOf(0x6B, 0x00)

    fun selectByName(aid: ByteArray): ByteArray =
        byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()) + aid + byteArrayOf(0x00)

    fun statusWord(response: ByteArray): String =
        if (response.size >= 2) Hex.encode(response.copyOfRange(response.size - 2, response.size)) else "----"

    fun isOk(response: ByteArray) = statusWord(response) == "9000"

    fun data(response: ByteArray): ByteArray =
        if (response.size > 2) response.copyOfRange(0, response.size - 2) else ByteArray(0)
}

/** Minimal BER-TLV walker (enough for FCI templates). */
object Tlv {
    fun find(data: ByteArray, wantedTag: Int): ByteArray? {
        var i = 0
        while (i < data.size) {
            var tag = data[i].toInt() and 0xFF
            val constructed = tag and 0x20 != 0
            i++
            if (tag and 0x1F == 0x1F) { // multi-byte tag
                while (i < data.size) {
                    val b = data[i].toInt() and 0xFF
                    tag = (tag shl 8) or b
                    i++
                    if (b and 0x80 == 0) break
                }
            }
            if (i >= data.size) return null
            var len = data[i].toInt() and 0xFF
            i++
            if (len and 0x80 != 0) {
                val n = len and 0x7F
                len = 0
                repeat(n) { if (i < data.size) { len = (len shl 8) or (data[i].toInt() and 0xFF); i++ } }
            }
            if (i + len > data.size) return null
            val value = data.copyOfRange(i, i + len)
            if (tag == wantedTag) return value
            if (constructed) find(value, wantedTag)?.let { return it }
            i += len
        }
        return null
    }
}
