package com.cardvault.nfc.nfc

import android.nfc.tech.IsoDep
import com.cardvault.nfc.data.TransitData
import com.cardvault.nfc.data.TransitFile
import java.math.BigInteger

/**
 * Read-only Calypso reader (MOBIB and other Calypso/EN 1545 cards).
 *
 * Only issues SELECT and READ RECORD, never opens a secure session and never writes,
 * so it cannot change the card's state.
 *
 * What it does: application serial number (FCI tag C7), raw records of the public files,
 * and counter values. What it doesn't do yet: decode the MOBIB-specific EN 1545 bit layouts into
 * pass names, dates and stops. That needs a field map validated against real card dumps;
 * the raw records are kept so that decoder can be added later without rescanning (see README).
 */
object CalypsoReader {

    private data class FileDef(val name: String, val sfi: Int, val maxRecords: Int)

    private val FILES = listOf(
        FileDef("ICC", 0x02, 1),
        FileDef("ID", 0x03, 1),
        FileDef("Environment / Holder", 0x07, 1),
        FileDef("Event log", 0x08, 6),
        FileDef("Contracts", 0x09, 8),
        FileDef("Counters", 0x19, 1),
        FileDef("Special events", 0x1D, 4),
        FileDef("Contract list", 0x1E, 1),
    )

    fun read(isoDep: IsoDep, aidHex: String, selectResponse: ByteArray, label: String, progress: (String) -> Unit): TransitData {
        val fci = Apdu.data(selectResponse)
        val serial = Tlv.find(fci, 0xC7)
        val serialHex = Hex.encode(serial)
        val serialDec = serial?.let { BigInteger(1, it).toString() } ?: ""

        var cla: Byte = 0x00
        val files = mutableListOf<TransitFile>()
        val notes = mutableListOf<String>()
        var counters = emptyList<Int>()

        for (f in FILES) {
            progress("Reading ${f.name}…")
            val records = mutableListOf<String>()
            for (rec in 1..f.maxRecords) {
                var resp = isoDep.transceive(readRecord(cla, rec, f.sfi, 0x00))
                var sw = Apdu.statusWord(resp)
                if (sw == "6E00" && cla == 0x00.toByte()) { // older Calypso revisions use CLA 94
                    cla = 0x94.toByte()
                    resp = isoDep.transceive(readRecord(cla, rec, f.sfi, 0x00))
                    sw = Apdu.statusWord(resp)
                }
                if (sw.startsWith("6C")) { // wrong Le, card tells us the right one
                    resp = isoDep.transceive(readRecord(cla, rec, f.sfi, sw.substring(2).toInt(16)))
                    sw = Apdu.statusWord(resp)
                }
                if (sw != "9000") {
                    if (rec == 1 && sw != "6A82" && sw != "6A83") notes += "${f.name}: SW $sw"
                    break
                }
                val data = Apdu.data(resp)
                if (data.all { it == 0.toByte() }) continue // empty slot
                records += Hex.encode(data)
            }
            if (records.isNotEmpty()) {
                files += TransitFile(f.name, f.sfi, records)
                if (f.sfi == 0x19) counters = parseCounters(Hex.decode(records.first()))
            }
        }

        if (files.isEmpty()) notes += "No public files readable. This card may require a secure session."
        notes += "Raw EN 1545 records stored. Pass/journey decoding for this network is not implemented yet."

        return TransitData(
            network = label,
            applicationAid = aidHex,
            serialHex = serialHex,
            serialDecimal = serialDec,
            files = files,
            counters = counters,
            notes = notes,
        )
    }

    /** READ RECORD, P2 = SFI<<3 | 0b100 (read record P1). */
    private fun readRecord(cla: Byte, record: Int, sfi: Int, le: Int) =
        byteArrayOf(cla, 0xB2.toByte(), record.toByte(), ((sfi shl 3) or 0x04).toByte(), le.toByte())

    /** Calypso counter file: consecutive 3-byte big-endian counters. */
    private fun parseCounters(data: ByteArray): List<Int> =
        (0 until data.size / 3).map { i ->
            ((data[i * 3].toInt() and 0xFF) shl 16) or ((data[i * 3 + 1].toInt() and 0xFF) shl 8) or (data[i * 3 + 2].toInt() and 0xFF)
        }.filter { it != 0 }
}
