package com.cardvault.nfc.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Field-level AES-256-GCM encryption with a hardware-backed Android Keystore key
 * (StrongBox when the device has one). Blob layout: IV(12) | ciphertext+tag(16).
 *
 * Note: the key is NOT auth-bound (setUserAuthenticationRequired=false) so the HCE service,
 * restore flow and list screen keep working; access is gated by BiometricPrompt in the UI.
 * See README "Hardening" for switching to an auth-bound key.
 */
object KeystoreCrypto {
    private const val TAG = "KeystoreCrypto"
    private const val PROVIDER = "AndroidKeyStore"
    private const val AES_ALIAS = "cardvault_field_key_v1"
    private const val HMAC_ALIAS = "cardvault_hmac_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LEN = 12

    private val keyStore: KeyStore by lazy { KeyStore.getInstance(PROVIDER).apply { load(null) } }

    @Volatile private var cachedAes: SecretKey? = null
    @Volatile private var cachedHmac: SecretKey? = null

    var isStrongBoxBacked: Boolean = false
        private set

    private fun aesKey(): SecretKey {
        cachedAes?.let { return it }
        synchronized(this) {
            cachedAes?.let { return it }
            val existing = keyStore.getKey(AES_ALIAS, null) as? SecretKey
            val key = existing ?: generateAes(strongBox = true) ?: generateAes(strongBox = false)
                ?: error("Unable to create Keystore AES key")
            cachedAes = key
            return key
        }
    }

    private fun generateAes(strongBox: Boolean): SecretKey? = try {
        val spec = KeyGenParameterSpec.Builder(
            AES_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .apply { if (strongBox) setIsStrongBoxBacked(true) }
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
            init(spec)
            generateKey()
        }.also { isStrongBoxBacked = strongBox }
    } catch (e: StrongBoxUnavailableException) {
        Log.i(TAG, "StrongBox unavailable, falling back to TEE")
        null
    } catch (e: Exception) {
        if (strongBox) null else throw e
    }

    private fun hmacKey(): SecretKey {
        cachedHmac?.let { return it }
        synchronized(this) {
            cachedHmac?.let { return it }
            val existing = keyStore.getKey(HMAC_ALIAS, null) as? SecretKey
            val key = existing ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, PROVIDER).run {
                init(KeyGenParameterSpec.Builder(HMAC_ALIAS, KeyProperties.PURPOSE_SIGN).build())
                generateKey()
            }
            cachedHmac = key
            return key
        }
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey())
        val iv = cipher.iv
        check(iv.size == IV_LEN)
        return iv + cipher.doFinal(plain)
    }

    fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > IV_LEN) { "Ciphertext too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, aesKey(), GCMParameterSpec(128, blob, 0, IV_LEN))
        return cipher.doFinal(blob, IV_LEN, blob.size - IV_LEN)
    }

    fun encryptString(value: String?): ByteArray? =
        if (value.isNullOrEmpty()) null else encrypt(value.toByteArray(Charsets.UTF_8))

    fun decryptString(blob: ByteArray?): String =
        if (blob == null || blob.isEmpty()) "" else decrypt(blob).toString(Charsets.UTF_8)

    /** Keyed fingerprint for duplicate detection without storing plaintext. */
    fun fingerprint(value: String): String? {
        val normalized = value.filter { it.isLetterOrDigit() }.uppercase()
        if (normalized.isEmpty()) return null
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey())
        return mac.doFinal(normalized.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
