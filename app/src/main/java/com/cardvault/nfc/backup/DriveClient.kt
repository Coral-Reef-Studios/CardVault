package com.cardvault.nfc.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Tiny Drive v3 REST client limited to the hidden appDataFolder.
 * Only ever sees the already-encrypted snapshot.
 */
class DriveClient {

    data class RemoteFile(val id: String, val modifiedTime: String, val size: Long)

    class DriveException(val code: Int, message: String) : Exception(message)

    suspend fun find(token: String): RemoteFile? = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode("name = '$FILE_NAME' and trashed = false", "UTF-8")
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$q&fields=files(id,modifiedTime,size)"
        val json = JSONObject(request("GET", url, token).toString(Charsets.UTF_8))
        val files = json.optJSONArray("files") ?: return@withContext null
        if (files.length() == 0) return@withContext null
        val f = files.getJSONObject(0)
        RemoteFile(f.getString("id"), f.optString("modifiedTime"), f.optString("size", "0").toLongOrNull() ?: 0)
    }

    suspend fun download(token: String, fileId: String): ByteArray = withContext(Dispatchers.IO) {
        request("GET", "https://www.googleapis.com/drive/v3/files/$fileId?alt=media", token)
    }

    suspend fun upload(token: String, bytes: ByteArray, existingId: String?) = withContext(Dispatchers.IO) {
        if (existingId != null) {
            // HttpURLConnection can't send PATCH; Google APIs accept the override header.
            request("POST", "https://www.googleapis.com/upload/drive/v3/files/$existingId?uploadType=media", token,
                body = bytes, contentType = "application/octet-stream", headers = mapOf("X-HTTP-Method-Override" to "PATCH"))
        } else {
            val boundary = "cardvault-" + System.nanoTime()
            val meta = JSONObject().put("name", FILE_NAME).put("parents", org.json.JSONArray().put("appDataFolder")).toString()
            val body = ByteArrayOutputStream().apply {
                write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n".toByteArray())
                write("--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray())
                write(bytes)
                write("\r\n--$boundary--\r\n".toByteArray())
            }.toByteArray()
            request("POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart", token,
                body = body, contentType = "multipart/related; boundary=$boundary")
        }
        Unit
    }

    private fun request(
        method: String, url: String, token: String,
        body: ByteArray? = null, contentType: String? = null, headers: Map<String, String> = emptyMap(),
    ): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                contentType?.let { conn.setRequestProperty("Content-Type", it) }
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                throw DriveException(code, "Drive HTTP $code ${err.take(300)}")
            }
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val FILE_NAME = "vault_snapshot_v1.enc"
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}
