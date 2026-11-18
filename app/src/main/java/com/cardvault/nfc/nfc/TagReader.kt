package com.cardvault.nfc.nfc

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.NfcA
import android.nfc.tech.Ndef
import com.cardvault.nfc.data.AidProbe
import com.cardvault.nfc.data.Card
import com.cardvault.nfc.data.CardCategory
import com.cardvault.nfc.data.EmulationMode
import com.cardvault.nfc.data.NfcPayload
import com.cardvault.nfc.data.NfcTechType
import com.cardvault.nfc.data.StyleConfig
import com.cardvault.nfc.data.TechInfo
import com.cardvault.nfc.data.TransitData

data class ScanOutcome(val techInfo: TechInfo, val transit: TransitData?, val draft: Card)

/** Runs on the reader-mode binder thread; all I/O is blocking. */
object TagReader {

    private data class KnownAid(val hex: String, val label: String, val kind: Kind)
    private enum class Kind { CALYPSO, NDEF, EMV }

    private val PROBES = listOf(
        KnownAid("A0000004040125090101", "MOBIB (Calypso)", Kind.CALYPSO),
        KnownAid("315449432E494341", "Calypso 1TIC.ICA", Kind.CALYPSO),
        KnownAid("D2760000850101", "NDEF Type 4 application", Kind.NDEF),
        KnownAid("325041592E5359532E4444463031", "Payment (PPSE)", Kind.EMV),
    )

    fun read(tag: Tag, progress: (String) -> Unit): ScanOutcome {
        progress("Detecting technology…")
        val techs = tag.techList.map { it.substringAfterLast('.') }
        val notes = mutableListOf<String>()

        var atqa: String? = null
        var sak: Int? = null
        var maxTx: Int? = null
        NfcA.get(tag)?.let { a ->
            atqa = Hex.encode(a.atqa)
            sak = a.sak.toInt()
            maxTx = a.maxTransceiveLength
        }

        val sakVal = sak
        val classic = "MifareClassic" in techs || (sakVal != null && sakVal in setOf(0x08, 0x09, 0x18, 0x88, 0x38))
        if (classic) {
            notes += "MIFARE Classic detected. Proprietary Crypto1 can't be emulated by Android HCE. Stored as a reference card."
            if (MifareClassic.get(tag) == null) notes += "This phone's NFC controller can't read MIFARE Classic sectors."
        }

        // NDEF
        var ndefMessage: NdefMessage? = null
        var ndefType: String? = null
        var ndefMax: Int? = null
        var ndefWritable: Boolean? = null
        Ndef.get(tag)?.let { ndef ->
            progress("Reading NDEF…")
            ndefType = ndef.type.substringAfterLast('.')
            ndefMax = ndef.maxSize
            ndefWritable = ndef.isWritable
            ndefMessage = ndef.cachedNdefMessage ?: runCatching {
                ndef.connect(); ndef.ndefMessage.also { ndef.close() }
            }.getOrNull()
        }

        // ISO-DEP
        var hist: String? = null
        var hiLayer: String? = null
        val probes = mutableListOf<AidProbe>()
        var transit: TransitData? = null
        var isEmv = false
        var calypsoLabel: String? = null
        var calypsoAid: String? = null

        IsoDep.get(tag)?.let { iso ->
            hist = iso.historicalBytes?.let(Hex::encode)
            hiLayer = iso.hiLayerResponse?.let(Hex::encode)
            maxTx = iso.maxTransceiveLength
            iso.timeout = 3000
            iso.connect()
            try {
                for (p in PROBES) {
                    progress("Probing ${p.label}…")
                    val resp = iso.transceive(Apdu.selectByName(Hex.decode(p.hex)))
                    val sw = Apdu.statusWord(resp)
                    probes += AidProbe(p.hex, p.label, sw, Hex.encode(Apdu.data(resp)))
                    if (!Apdu.isOk(resp)) continue
                    when (p.kind) {
                        Kind.CALYPSO -> if (transit == null) {
                            progress("Reading transit contracts…")
                            calypsoLabel = p.label
                            calypsoAid = p.hex
                            transit = CalypsoReader.read(iso, p.hex, resp, p.label, progress)
                        }
                        Kind.EMV -> {
                            isEmv = true
                            notes += "EMV payment card. CardVault doesn't store or emulate payment cards."
                        }
                        Kind.NDEF -> Unit
                    }
                }
            } finally {
                runCatching { iso.close() }
            }
        }

        val techInfo = TechInfo(
            techList = techs,
            uidHex = Hex.encode(tag.id),
            atqaHex = atqa,
            sak = sak,
            historicalBytesHex = hist,
            hiLayerResponseHex = hiLayer,
            maxTransceiveLength = maxTx,
            ndefType = ndefType,
            ndefMaxSize = ndefMax,
            ndefWritable = ndefWritable,
            ndefRecords = ndefMessage?.let { NdefTools.describe(it) } ?: emptyList(),
            rawNdefHex = ndefMessage?.toByteArray()?.let(Hex::encode),
            aidProbes = probes,
            isMifareClassic = classic,
            isEmvPayment = isEmv,
            notes = notes + (transit?.notes ?: emptyList()),
        )
        progress("Done!")
        return ScanOutcome(techInfo, transit, buildDraft(techInfo, transit, calypsoLabel, calypsoAid, ndefMessage))
    }

    private fun buildDraft(
        info: TechInfo, transit: TransitData?, calypsoLabel: String?, calypsoAid: String?, ndef: NdefMessage?,
    ): Card {
        val techType = when {
            ndef != null -> NfcTechType.NDEF
            "IsoDep" in info.techList -> NfcTechType.ISO_DEP
            "NfcA" in info.techList -> NfcTechType.NFC_A
            "NfcB" in info.techList -> NfcTechType.NFC_B
            "NfcF" in info.techList -> NfcTechType.NFC_F
            "NfcV" in info.techList -> NfcTechType.NFC_V
            else -> NfcTechType.NONE
        }
        return when {
            transit != null -> Card(
                title = if (calypsoLabel?.startsWith("MOBIB") == true) "MOBIB" else "Transit card",
                subtitle = calypsoLabel ?: "Calypso",
                category = CardCategory.TRANSIT,
                cardNumber = transit.serialDecimal.ifBlank { info.uidHex },
                nfcTechType = NfcTechType.ISO_DEP,
                aidHex = calypsoAid ?: "",
                techInfo = info,
                transit = transit,
                style = StyleConfig(primaryHex = "#E11D48", secondaryHex = "#7F1D1D", iconId = "subway"),
            )
            ndef != null && ndef.records.isNotEmpty() -> Card(
                title = "NFC tag",
                subtitle = info.ndefRecords.firstOrNull()?.decoded?.take(40) ?: "NDEF",
                category = CardCategory.NFC_TAG,
                cardNumber = info.uidHex,
                nfcTechType = techType,
                aidHex = "D2760000850101",
                nfcPayload = NfcPayload(EmulationMode.NDEF_TYPE4, ndefMessageHex = Hex.encode(ndef.toByteArray())),
                techInfo = info,
                style = StyleConfig(primaryHex = "#0EA5E9", secondaryHex = "#312E81", iconId = "nfc"),
            )
            else -> Card(
                title = when {
                    info.isEmvPayment -> "Payment card (reference)"
                    info.isMifareClassic -> "Access badge (reference)"
                    else -> "NFC card (reference)"
                },
                subtitle = info.techList.joinToString(" · "),
                category = CardCategory.ACCESS_ID,
                cardNumber = if (info.isEmvPayment) "" else info.uidHex,
                nfcTechType = techType,
                techInfo = info,
                style = StyleConfig(primaryHex = "#475569", secondaryHex = "#0F172A", iconId = "key"),
            )
        }
    }
}
