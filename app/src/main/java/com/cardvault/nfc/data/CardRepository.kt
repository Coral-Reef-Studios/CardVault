package com.cardvault.nfc.data

import android.content.Context
import com.cardvault.nfc.security.KeystoreCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class CardRepository(
    private val context: Context,
    private val dao: CardDao,
    private val logDao: ApduLogDao,
) {
    val summaries: Flow<List<CardSummary>> =
        dao.observeActive().map { list -> list.map(::toSummary) }.flowOn(Dispatchers.Default)

    fun apduLogs(cardId: String): Flow<List<ApduLogEntity>> = logDao.observe(cardId)

    suspend fun logApdu(entity: ApduLogEntity) = logDao.insert(entity)
    suspend fun clearLogs(cardId: String) = logDao.clear(cardId)

    suspend fun get(id: String): Card? = withContext(Dispatchers.IO) { dao.get(id)?.let(::toCard) }

    /** Saves the card, bumping updatedAt. New cards are appended to the end of the wallet. */
    suspend fun save(card: Card): Card = withContext(Dispatchers.IO) {
        val existing = dao.get(card.id)
        val order = existing?.sortOrder ?: (dao.maxOrder() + 1)
        val toSave = card.copy(sortOrder = order, updatedAt = System.currentTimeMillis())
        dao.upsert(toEntity(toSave))
        toSave
    }

    /** Writes a card exactly as given (used by backup merge; keeps remote timestamps). */
    suspend fun putRaw(card: Card) = withContext(Dispatchers.IO) { dao.upsert(toEntity(card)) }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.softDelete(id, System.currentTimeMillis())
        artworkFile(id).delete()
    }

    suspend fun move(id: String, delta: Int, current: List<CardSummary>) = withContext(Dispatchers.IO) {
        val idx = current.indexOfFirst { it.id == id }
        val target = idx + delta
        if (idx < 0 || target !in current.indices) return@withContext
        val reordered = current.toMutableList().apply { add(target, removeAt(idx)) }
        val now = System.currentTimeMillis()
        reordered.forEachIndexed { i, s -> if (s.sortOrder != i) dao.setOrder(s.id, i, now) }
    }

    suspend fun findDuplicate(card: Card): CardSummary? = withContext(Dispatchers.IO) {
        val hash = dedupeHash(card) ?: return@withContext null
        dao.findByHash(hash, card.id)?.let(::toSummary)
    }

    /** All cards including tombstones, decrypted, for backup. */
    suspend fun allCards(): List<Card> = withContext(Dispatchers.IO) { dao.all().map(::toCard) }

    suspend fun getUpdatedAt(id: String): Long? = withContext(Dispatchers.IO) { dao.get(id)?.updatedAt }

    // ---- artwork -------------------------------------------------------------------------

    fun artworkDir(): File = File(context.filesDir, "art").apply { mkdirs() }
    fun artworkFile(cardId: String): File = File(artworkDir(), "$cardId.jpg")
    fun resolveArtwork(style: StyleConfig): File? =
        style.artworkFile?.let { File(artworkDir(), it) }?.takeIf { it.exists() }

    // ---- mapping -------------------------------------------------------------------------

    private fun dedupeHash(card: Card): String? {
        val source = card.cardNumber.ifBlank { card.barcodePayload }.ifBlank { card.techInfo?.uidHex ?: "" }
        return if (source.isBlank()) null else KeystoreCrypto.fingerprint(source)
    }

    private fun toEntity(c: Card) = CardEntity(
        id = c.id,
        title = c.title,
        subtitle = c.subtitle,
        category = c.category.name,
        cardNumberEnc = KeystoreCrypto.encryptString(c.cardNumber),
        cardNumberLast4 = c.last4,
        holderNameEnc = KeystoreCrypto.encryptString(c.holderName),
        expiryDate = c.expiryDate,
        barcodeFormat = c.barcodeFormat.name,
        barcodePayloadEnc = KeystoreCrypto.encryptString(c.barcodePayload),
        nfcTechType = c.nfcTechType.name,
        aidHex = c.aidHex,
        nfcPayloadEnc = KeystoreCrypto.encryptString(VaultJson.encodeToString(NfcPayload.serializer(), c.nfcPayload)),
        techInfoEnc = c.techInfo?.let { KeystoreCrypto.encryptString(VaultJson.encodeToString(TechInfo.serializer(), it)) },
        transitParsedJsonEnc = c.transit?.let { KeystoreCrypto.encryptString(VaultJson.encodeToString(TransitData.serializer(), it)) },
        customFieldsEnc = KeystoreCrypto.encryptString(
            VaultJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(CustomField.serializer()), c.customFields)
        ),
        styleConfigJson = VaultJson.encodeToString(StyleConfig.serializer(), c.style),
        requireBiometric = c.requireBiometric,
        isEmulatable = c.isEmulatable,
        sortOrder = c.sortOrder,
        createdAt = c.createdAt,
        updatedAt = c.updatedAt,
        isDeleted = c.isDeleted,
        dedupeHash = dedupeHash(c),
        passDataEnc = c.pass?.let { KeystoreCrypto.encryptString(VaultJson.encodeToString(PassData.serializer(), it)) },
        passFaceJson = c.pass?.let { VaultJson.encodeToString(PassFace.serializer(), PassFormat.face(it)) },
        eventTime = c.pass?.departure?.let { PassFormat.parseTime(it) },
    )

    private fun toCard(e: CardEntity): Card {
        fun dec(b: ByteArray?) = KeystoreCrypto.decryptString(b)
        return Card(
            id = e.id,
            title = e.title,
            subtitle = e.subtitle,
            category = enumOr(e.category, CardCategory.CUSTOM),
            cardNumber = dec(e.cardNumberEnc),
            holderName = dec(e.holderNameEnc),
            expiryDate = e.expiryDate,
            barcodeFormat = enumOr(e.barcodeFormat, BarcodeFormat.NONE),
            barcodePayload = dec(e.barcodePayloadEnc),
            nfcTechType = enumOr(e.nfcTechType, NfcTechType.NONE),
            aidHex = e.aidHex,
            nfcPayload = dec(e.nfcPayloadEnc).takeIf { it.isNotEmpty() }
                ?.let { VaultJson.decodeFromString(NfcPayload.serializer(), it) } ?: NfcPayload(),
            techInfo = dec(e.techInfoEnc).takeIf { it.isNotEmpty() }
                ?.let { VaultJson.decodeFromString(TechInfo.serializer(), it) },
            transit = dec(e.transitParsedJsonEnc).takeIf { it.isNotEmpty() }
                ?.let { VaultJson.decodeFromString(TransitData.serializer(), it) },
            pass = dec(e.passDataEnc).takeIf { it.isNotEmpty() }
                ?.let { VaultJson.decodeFromString(PassData.serializer(), it) },
            customFields = dec(e.customFieldsEnc).takeIf { it.isNotEmpty() }
                ?.let { VaultJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(CustomField.serializer()), it) }
                ?: emptyList(),
            style = parseStyle(e.styleConfigJson),
            requireBiometric = e.requireBiometric,
            sortOrder = e.sortOrder,
            createdAt = e.createdAt,
            updatedAt = e.updatedAt,
            isDeleted = e.isDeleted,
        )
    }

    private fun toSummary(e: CardEntity) = CardSummary(
        id = e.id,
        title = e.title,
        subtitle = e.subtitle,
        category = enumOr(e.category, CardCategory.CUSTOM),
        last4 = e.cardNumberLast4,
        expiryDate = e.expiryDate,
        style = parseStyle(e.styleConfigJson),
        requireBiometric = e.requireBiometric,
        isEmulatable = e.isEmulatable,
        hasBarcode = e.barcodeFormat != BarcodeFormat.NONE.name,
        hasNfc = e.nfcTechType != NfcTechType.NONE.name,
        sortOrder = e.sortOrder,
        passFace = e.passFaceJson?.let { runCatching { VaultJson.decodeFromString(PassFace.serializer(), it) }.getOrNull() },
        eventTime = e.eventTime,
    )

    private fun parseStyle(json: String): StyleConfig =
        runCatching { VaultJson.decodeFromString(StyleConfig.serializer(), json) }.getOrDefault(StyleConfig())

    private inline fun <reified T : Enum<T>> enumOr(name: String, fallback: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
}
