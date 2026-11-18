package com.cardvault.nfc.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

val VaultJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
enum class CardCategory(val label: String) {
    TRANSIT("Transit"), BOARDING_PASS("Boarding passes"), TICKET("Tickets"), LOYALTY("Loyalty"),
    GIFT_CARD("Gift cards"), ACCESS_ID("Work & Campus"), NFC_TAG("NFC Tags"), CUSTOM("Custom");

    val passKind: PassKind?
        get() = when (this) {
            BOARDING_PASS -> PassKind.BOARDING_PASS
            TICKET -> PassKind.EVENT_TICKET
            GIFT_CARD -> PassKind.GIFT_CARD
            else -> null
        }
}

@Serializable
enum class PassKind { BOARDING_PASS, EVENT_TICKET, GIFT_CARD, COUPON, GENERIC }

@Serializable
data class PassField(val label: String, val value: String, val section: String = "back")

/** Wallet-style pass content (boarding passes, tickets, gift cards, imported .pkpass). Encrypted at rest. */
@Serializable
data class PassData(
    val kind: PassKind = PassKind.GENERIC,
    /** AIR, TRAIN, BUS, BOAT, GENERIC */
    val transitType: String = "AIR",
    val origin: String = "",
    val originName: String = "",
    val destination: String = "",
    val destinationName: String = "",
    val carrier: String = "",
    val flightNumber: String = "",
    /** Departure / event start, "yyyy-MM-dd HH:mm" or ISO-8601. */
    val departure: String = "",
    val boardingTime: String = "",
    val gate: String = "",
    val terminal: String = "",
    val seat: String = "",
    val group: String = "",
    val cabin: String = "",
    val pnr: String = "",
    val passenger: String = "",
    val eventName: String = "",
    val venue: String = "",
    val section: String = "",
    val row: String = "",
    val balance: String = "",
    val pin: String = "",
    /** Extra label/value pairs (e.g. all front/back fields of an imported .pkpass). */
    val fields: List<PassField> = emptyList(),
    /** manual, bcbp, pkpass */
    val source: String = "manual",
)

/** Small plaintext projection of a pass for the wallet list (no PNR / names). */
@Serializable
data class PassFace(
    val kind: PassKind,
    val origin: String = "",
    val destination: String = "",
    val line1: String = "",
    val line2: String = "",
)

@Serializable
enum class BarcodeFormat(val label: String) {
    NONE("None"), QR_CODE("QR"), CODE_128("Code 128"), CODE_39("Code 39"), EAN_13("EAN-13"),
    EAN_8("EAN-8"), UPC_A("UPC-A"), AZTEC("Aztec"), PDF_417("PDF417"), DATA_MATRIX("Data Matrix")
}

@Serializable
enum class NfcTechType { NONE, ISO_DEP, NDEF, NFC_A, NFC_B, NFC_F, NFC_V }

@Serializable
enum class EmulationMode(val label: String) {
    NONE("Off"), NDEF_TYPE4("NDEF Type 4 tag"), APDU_TABLE("Custom ISO-DEP (APDU table)")
}

@Serializable
enum class GradientStyle(val label: String) { SOLID("Solid"), LINEAR("Linear"), RADIAL("Radial glow"), METALLIC("Metallic") }

@Serializable
data class StyleConfig(
    val primaryHex: String = "#2563EB",
    val secondaryHex: String = "#1E1B4B",
    val textHex: String = "#FFFFFF",
    val gradient: GradientStyle = GradientStyle.LINEAR,
    val gradientAngle: Float = 135f,
    val iconId: String = "card",
    /** File name inside filesDir/art, not an absolute path (portable across devices). */
    val artworkFile: String? = null,
    val showSubtitle: Boolean = true,
    val showNumber: Boolean = true,
    val showExpiry: Boolean = true,
    val showHolder: Boolean = false,
    val showBadge: Boolean = true,
)

@Serializable
data class CustomField(val key: String, val value: String)

@Serializable
data class ApduRule(val commandPrefixHex: String, val responseHex: String)

@Serializable
data class NfcPayload(
    val mode: EmulationMode = EmulationMode.NONE,
    /** Raw NDEF message bytes (hex), served from file E104 in Type 4 mode. */
    val ndefMessageHex: String = "",
    val apduRules: List<ApduRule> = emptyList(),
) {
    val hasContent: Boolean
        get() = when (mode) {
            EmulationMode.NONE -> false
            EmulationMode.NDEF_TYPE4 -> ndefMessageHex.isNotBlank()
            EmulationMode.APDU_TABLE -> apduRules.isNotEmpty()
        }
}

@Serializable
data class NdefRecordInfo(
    val tnf: Int,
    val type: String,
    val idHex: String,
    val payloadHex: String,
    val decoded: String,
)

@Serializable
data class AidProbe(val aidHex: String, val label: String, val statusWord: String, val responseHex: String)

@Serializable
data class TechInfo(
    val techList: List<String> = emptyList(),
    val uidHex: String = "",
    val atqaHex: String? = null,
    val sak: Int? = null,
    val historicalBytesHex: String? = null,
    val hiLayerResponseHex: String? = null,
    val maxTransceiveLength: Int? = null,
    val ndefType: String? = null,
    val ndefMaxSize: Int? = null,
    val ndefWritable: Boolean? = null,
    val ndefRecords: List<NdefRecordInfo> = emptyList(),
    val rawNdefHex: String? = null,
    val aidProbes: List<AidProbe> = emptyList(),
    val isMifareClassic: Boolean = false,
    val isEmvPayment: Boolean = false,
    val notes: List<String> = emptyList(),
    val scannedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class TransitFile(val name: String, val sfi: Int, val records: List<String>)

@Serializable
data class TransitData(
    val network: String,
    val applicationAid: String,
    val serialHex: String,
    val serialDecimal: String,
    val files: List<TransitFile> = emptyList(),
    val counters: List<Int> = emptyList(),
    val notes: List<String> = emptyList(),
)

/** Fully decrypted card. Only ever lives in memory. */
@Serializable
data class Card(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val subtitle: String = "",
    val category: CardCategory = CardCategory.CUSTOM,
    val cardNumber: String = "",
    val holderName: String = "",
    val expiryDate: String? = null,
    val barcodeFormat: BarcodeFormat = BarcodeFormat.NONE,
    val barcodePayload: String = "",
    val nfcTechType: NfcTechType = NfcTechType.NONE,
    val aidHex: String = "",
    val nfcPayload: NfcPayload = NfcPayload(),
    val techInfo: TechInfo? = null,
    val transit: TransitData? = null,
    val pass: PassData? = null,
    val customFields: List<CustomField> = emptyList(),
    val style: StyleConfig = StyleConfig(),
    val requireBiometric: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
) {
    val isEmulatable: Boolean get() = nfcPayload.hasContent
    val last4: String get() = cardNumber.filter { it.isLetterOrDigit() }.takeLast(4)
}

/** Non-sensitive projection used by the wallet list (no decryption needed). */
data class CardSummary(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: CardCategory,
    val last4: String,
    val expiryDate: String?,
    val style: StyleConfig,
    val requireBiometric: Boolean,
    val isEmulatable: Boolean,
    val hasBarcode: Boolean,
    val hasNfc: Boolean,
    val sortOrder: Int,
    val passFace: PassFace? = null,
    /** Pass departure / event time (epoch ms) for "upcoming" ordering. */
    val eventTime: Long? = null,
)
