package com.cardvault.nfc.backup

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class WrongPassphraseException : Exception("Wrong passphrase or corrupted backup")

/**
 * Zero-knowledge envelope: Argon2id(passphrase) → AES-256-GCM over gzip(json).
 *
 * Layout: "CVLT" | version(1) | t(1) | mKiB(4) | p(1) | salt(16) | iv(12) | ciphertext+tag
 * The KDF parameters live in the header, so they can be raised later without breaking old files.
 */
object BackupCodec {
    private val MAGIC = "CVLT".toByteArray()
    private const val VERSION: Byte = 1
    private const val T = 3
    private const val M_KIB = 64 * 1024
    private const val P = 1

    fun encrypt(plain: ByteArray, passphrase: CharArray): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val key = derive(passphrase, salt, T, M_KIB, P)
        val header = MAGIC + byteArrayOf(VERSION, T.toByte()) + int32(M_KIB) + byteArrayOf(P.toByte()) + salt + iv
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(header) // header tampering breaks the tag
        key.fill(0)
        return header + cipher.doFinal(gzip(plain))
    }

    fun decrypt(blob: ByteArray, passphrase: CharArray): ByteArray {
        require(blob.size > 39 && blob.copyOfRange(0, 4).contentEquals(MAGIC)) { "Not a CardVault backup" }
        require(blob[4] == VERSION) { "Unsupported backup version ${blob[4]}" }
        val t = blob[5].toInt() and 0xFF
        val m = readInt32(blob, 6)
        val p = blob[10].toInt() and 0xFF
        val salt = blob.copyOfRange(11, 27)
        val iv = blob.copyOfRange(27, 39)
        val header = blob.copyOfRange(0, 39)
        val key = derive(passphrase, salt, t, m, p)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(header)
        key.fill(0)
        val compressed = try {
            cipher.doFinal(blob, 39, blob.size - 39)
        } catch (e: AEADBadTagException) {
            throw WrongPassphraseException()
        }
        return gunzip(compressed)
    }

    private fun derive(pass: CharArray, salt: ByteArray, t: Int, mKib: Int, p: Int): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(t)
            .withMemoryAsKB(mKib)
            .withParallelism(p)
            .withSalt(salt)
            .build()
        val out = ByteArray(32)
        Argon2BytesGenerator().apply { init(params) }.generateBytes(pass, out)
        return out
    }

    private fun int32(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
    private fun readInt32(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun gzip(b: ByteArray): ByteArray = ByteArrayOutputStream().also { bos -> GZIPOutputStream(bos).use { it.write(b) } }.toByteArray()
    private fun gunzip(b: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(b)).use { it.readBytes() }
}
