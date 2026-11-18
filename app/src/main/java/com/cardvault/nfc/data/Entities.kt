package com.cardvault.nfc.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "cards", indices = [Index("dedupeHash")])
data class CardEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val cardNumberEnc: ByteArray?,
    val cardNumberLast4: String,
    val holderNameEnc: ByteArray?,
    val expiryDate: String?,
    val barcodeFormat: String,
    val barcodePayloadEnc: ByteArray?,
    val nfcTechType: String,
    val aidHex: String,
    val nfcPayloadEnc: ByteArray?,
    val techInfoEnc: ByteArray?,
    val transitParsedJsonEnc: ByteArray?,
    val customFieldsEnc: ByteArray?,
    val styleConfigJson: String,
    val requireBiometric: Boolean,
    val isEmulatable: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean,
    /** HMAC of card number / barcode payload for duplicate detection. */
    @ColumnInfo(defaultValue = "NULL") val dedupeHash: String?,
    // v2: wallet passes
    val passDataEnc: ByteArray? = null,
    val passFaceJson: String? = null,
    val eventTime: Long? = null,
) {
    // ByteArray fields: identity equality is fine for Room entities.
    override fun equals(other: Any?) = other is CardEntity && other.id == id && other.updatedAt == updatedAt
    override fun hashCode() = id.hashCode()
}

@Entity(tableName = "apdu_logs", indices = [Index("cardId")])
data class ApduLogEntity(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0,
    val cardId: String,
    val timestamp: Long,
    /** INCOMING_C_APDU or OUTGOING_R_APDU */
    val direction: String,
    val hexPayload: String,
    val statusWord: String,
)
