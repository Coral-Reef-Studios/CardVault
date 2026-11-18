package com.cardvault.nfc.nfc

import com.cardvault.nfc.data.EmulationMode
import com.cardvault.nfc.data.NfcPayload

/**
 * Pure APDU state machine (no Android deps, so it can be unit-tested on the JVM).
 *
 * NDEF_TYPE4: NFC Forum Type 4 Tag v2.0, read-only.
 *   SELECT D2760000850101 → SELECT E103 (CC) → READ BINARY → SELECT E104 → READ BINARY
 * APDU_TABLE: first rule whose command prefix matches the incoming C-APDU wins.
 */
class ApduResponder(private val payload: NfcPayload, private val cardAidHex: String) {

    private val ndefFile: ByteArray by lazy {
        val msg = runCatching { Hex.decode(payload.ndefMessageHex) }.getOrDefault(ByteArray(0))
        byteArrayOf((msg.size shr 8).toByte(), msg.size.toByte()) + msg
    }

    private val ccFile: ByteArray by lazy {
        val max = ndefFile.size.coerceAtLeast(0x80)
        byteArrayOf(
            0x00, 0x0F,             // CCLEN
            0x20,                   // mapping version 2.0
            0x00, 0x3B,             // MLe
            0x00, 0x34,             // MLc
            0x04, 0x06,             // NDEF File Control TLV
            0xE1.toByte(), 0x04,    // file id
            (max shr 8).toByte(), max.toByte(),
            0x00,                   // read access: granted
            0xFF.toByte(),          // write access: denied
        )
    }

    private var appSelected = false
    private var selectedFile: ByteArray? = null

    fun reset() {
        appSelected = false
        selectedFile = null
    }

    fun process(cmd: ByteArray): ByteArray {
        if (cmd.size < 4) return Apdu.SW_WRONG_P1P2
        return when (payload.mode) {
            EmulationMode.NDEF_TYPE4 -> type4(cmd)
            EmulationMode.APDU_TABLE -> table(cmd)
            EmulationMode.NONE -> Apdu.SW_SECURITY_NOT_SATISFIED
        }
    }

    private fun type4(cmd: ByteArray): ByteArray {
        val ins = cmd[1].toInt() and 0xFF
        val p1 = cmd[2].toInt() and 0xFF
        val p2 = cmd[3].toInt() and 0xFF
        return when (ins) {
            0xA4 -> when (p1) {
                0x04 -> {
                    val aid = commandData(cmd)
                    if (Hex.encode(aid) == NDEF_AID) {
                        appSelected = true; selectedFile = null; Apdu.SW_OK
                    } else Apdu.SW_FILE_NOT_FOUND
                }
                0x00 -> {
                    if (!appSelected) return Apdu.SW_FILE_NOT_FOUND
                    when (Hex.encode(commandData(cmd))) {
                        "E103" -> { selectedFile = ccFile; Apdu.SW_OK }
                        "E104" -> { selectedFile = ndefFile; Apdu.SW_OK }
                        else -> Apdu.SW_FILE_NOT_FOUND
                    }
                }
                else -> Apdu.SW_WRONG_P1P2
            }
            0xB0 -> {
                val file = selectedFile ?: return Apdu.SW_COMMAND_NOT_ALLOWED
                val offset = ((p1 and 0x7F) shl 8) or p2
                if (offset > file.size) return Apdu.SW_WRONG_P1P2
                val le = if (cmd.size >= 5) (cmd[4].toInt() and 0xFF).let { if (it == 0) 256 else it } else 256
                val end = (offset + le).coerceAtMost(file.size)
                file.copyOfRange(offset, end) + Apdu.SW_OK
            }
            0xD6 -> Apdu.SW_SECURITY_NOT_SATISFIED // read-only tag
            else -> Apdu.SW_INS_NOT_SUPPORTED
        }
    }

    private fun table(cmd: ByteArray): ByteArray {
        val hex = Hex.encode(cmd)
        payload.apduRules.firstOrNull { rule ->
            val prefix = Hex.clean(rule.commandPrefixHex)
            prefix.isNotEmpty() && hex.startsWith(prefix)
        }?.let { rule ->
            return runCatching { Hex.decode(rule.responseHex) }.getOrDefault(Apdu.SW_INS_NOT_SUPPORTED)
        }
        // Implicit success for SELECT of the card's own AID when no rule overrides it.
        val isSelect = (cmd[1].toInt() and 0xFF) == 0xA4 && (cmd[2].toInt() and 0xFF) == 0x04
        if (isSelect && cardAidHex.isNotBlank() && Hex.encode(commandData(cmd)) == Hex.clean(cardAidHex)) {
            return Apdu.SW_OK
        }
        return if (isSelect) Apdu.SW_FILE_NOT_FOUND else Apdu.SW_INS_NOT_SUPPORTED
    }

    /** Extracts Lc data from a short APDU (CLA INS P1 P2 Lc data [Le]). */
    private fun commandData(cmd: ByteArray): ByteArray {
        if (cmd.size < 6) return ByteArray(0)
        val lc = cmd[4].toInt() and 0xFF
        if (5 + lc > cmd.size) return ByteArray(0)
        return cmd.copyOfRange(5, 5 + lc)
    }

    companion object {
        const val NDEF_AID = "D2760000850101"
    }
}
