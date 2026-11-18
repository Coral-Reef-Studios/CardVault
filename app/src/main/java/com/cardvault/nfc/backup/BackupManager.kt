package com.cardvault.nfc.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.cardvault.nfc.data.Card
import com.cardvault.nfc.data.CardRepository
import com.cardvault.nfc.data.VaultJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class VaultSnapshot(
    val format: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val cards: List<Card>,
    /** cardId → base64 JPEG */
    val artwork: Map<String, String> = emptyMap(),
)

data class MergeReport(val added: Int, val updated: Int, val deleted: Int, val keptLocal: Int) {
    override fun toString() = "$added added · $updated updated · $deleted removed · $keptLocal newer locally"
}

class BackupManager(private val context: Context, private val repo: CardRepository) {

    /** Encrypted snapshot of the whole vault (tombstones included so deletes propagate). */
    suspend fun export(passphrase: CharArray): ByteArray = withContext(Dispatchers.Default) {
        val cards = repo.allCards()
        val art = cards.filter { !it.isDeleted }.mapNotNull { c ->
            repo.resolveArtwork(c.style)?.let { f -> c.id to Base64.encodeToString(f.readBytes(), Base64.NO_WRAP) }
        }.toMap()
        val json = VaultJson.encodeToString(VaultSnapshot.serializer(), VaultSnapshot(cards = cards, artwork = art))
        BackupCodec.encrypt(json.toByteArray(Charsets.UTF_8), passphrase)
    }

    /**
     * Per-card last-writer-wins merge on updatedAt. Cards that exist only locally are never
     * touched, so restoring an old backup can't wipe cards added since.
     */
    suspend fun import(blob: ByteArray, passphrase: CharArray): MergeReport = withContext(Dispatchers.Default) {
        val json = BackupCodec.decrypt(blob, passphrase).toString(Charsets.UTF_8)
        val snap = VaultJson.decodeFromString(VaultSnapshot.serializer(), json)
        var added = 0; var updated = 0; var deleted = 0; var kept = 0
        for (remote in snap.cards) {
            val localUpdated = repo.getUpdatedAt(remote.id)
            when {
                localUpdated == null -> { if (!remote.isDeleted) added++ }
                remote.updatedAt > localUpdated -> if (remote.isDeleted) deleted++ else updated++
                else -> { kept++; continue }
            }
            if (localUpdated == null && remote.isDeleted) { repo.putRaw(remote); continue }
            snap.artwork[remote.id]?.let { b64 ->
                val f = repo.artworkFile(remote.id)
                f.writeBytes(Base64.decode(b64, Base64.NO_WRAP))
            }
            val style = if (snap.artwork.containsKey(remote.id)) remote.style.copy(artworkFile = repo.artworkFile(remote.id).name)
            else remote.style.copy(artworkFile = null)
            repo.putRaw(remote.copy(style = style))
            if (remote.isDeleted) repo.artworkFile(remote.id).delete()
        }
        MergeReport(added, updated, deleted, kept)
    }

    suspend fun writeTo(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("Can't open file")
    }

    suspend fun readFrom(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Can't open file")
    }
}
