package com.cardvault.nfc.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cardvault.nfc.security.DatabaseKey
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [CardEntity::class, ApduLogEntity::class], version = 2, exportSchema = true)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun apduLogDao(): ApduLogDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cards ADD COLUMN passDataEnc BLOB")
                db.execSQL("ALTER TABLE cards ADD COLUMN passFaceJson TEXT")
                db.execSQL("ALTER TABLE cards ADD COLUMN eventTime INTEGER")
            }
        }

        fun open(context: Context): VaultDatabase {
            val factory = SupportOpenHelperFactory(DatabaseKey.get(context))
            return Room.databaseBuilder(context, VaultDatabase::class.java, "vault.db")
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
