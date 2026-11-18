package com.cardvault.nfc.security

import android.content.Context
import android.util.Base64
import java.security.SecureRandom

/** Random 256-bit SQLCipher passphrase, stored wrapped by the Keystore key. */
object DatabaseKey {
    private const val PREFS = "vault_keys"
    private const val KEY = "db_key_wrapped"

    fun get(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { wrapped ->
            return KeystoreCrypto.decrypt(Base64.decode(wrapped, Base64.NO_WRAP))
        }
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val wrapped = KeystoreCrypto.encrypt(raw)
        prefs.edit().putString(KEY, Base64.encodeToString(wrapped, Base64.NO_WRAP)).commit()
        return raw
    }
}
