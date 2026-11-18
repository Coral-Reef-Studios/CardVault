package com.cardvault.nfc.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cards WHERE isDeleted = 0 ORDER BY sortOrder ASC, createdAt ASC")
    fun observeActive(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun get(id: String): CardEntity?

    @Query("SELECT * FROM cards")
    suspend fun all(): List<CardEntity>

    @Upsert
    suspend fun upsert(entity: CardEntity)

    @Query("SELECT * FROM cards WHERE dedupeHash = :hash AND isDeleted = 0 AND id != :excludeId LIMIT 1")
    suspend fun findByHash(hash: String, excludeId: String): CardEntity?

    @Query("UPDATE cards SET sortOrder = :order, updatedAt = :now WHERE id = :id")
    suspend fun setOrder(id: String, order: Int, now: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM cards")
    suspend fun maxOrder(): Int

    @Query("UPDATE cards SET isDeleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}

@Dao
interface ApduLogDao {
    @Insert
    suspend fun insert(entity: ApduLogEntity)

    @Query("SELECT * FROM apdu_logs WHERE cardId = :cardId ORDER BY logId DESC LIMIT 300")
    fun observe(cardId: String): Flow<List<ApduLogEntity>>

    @Query("DELETE FROM apdu_logs WHERE cardId = :cardId")
    suspend fun clear(cardId: String)
}
