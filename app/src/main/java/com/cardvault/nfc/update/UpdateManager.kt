package com.cardvault.nfc.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.cardvault.nfc.BuildConfig
import com.cardvault.nfc.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class ReleaseInfo(
    val tag: String,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    /** "sha256:<hex>" digest GitHub publishes for release assets, when present. */
    val sha256: String?,
    val htmlUrl: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateState
    data class NeedsInstallPermission(val release: ReleaseInfo) : UpdateState
    data class Installing(val release: ReleaseInfo) : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * Self-update from GitHub Releases (the app isn't on the Play Store).
 * Android itself refuses the install unless the new APK is signed with the same key,
 * and we additionally check the package name and the published SHA-256.
 */
class UpdateManager(
    private val context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var job: Job? = null

    val currentVersion: String = BuildConfig.VERSION_NAME

    fun checkIfDue() {
        val s = settings.state.value
        if (s.autoCheckUpdates && System.currentTimeMillis() - s.lastUpdateCheck > 12 * 3600_000L) check(silent = true)
    }

    fun check(silent: Boolean = false) {
        if (job?.isActive == true) return
        job = scope.launch {
            if (!silent) _state.value = UpdateState.Checking
            val result = runCatching { fetchLatest() }
            settings.update { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
            _state.value = result.fold(
                onSuccess = { rel ->
                    when {
                        rel == null -> if (silent) UpdateState.Idle else UpdateState.Error("No published release found yet.")
                        isNewer(rel.versionName, currentVersion) -> UpdateState.Available(rel)
                        else -> if (silent) UpdateState.Idle else UpdateState.UpToDate
                    }
                },
                onFailure = { if (silent) UpdateState.Idle else UpdateState.Error(it.message ?: "Update check failed") },
            )
        }
    }

    fun downloadAndInstall(release: ReleaseInfo) {
        if (job?.isActive == true) return
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsInstallPermission(release)
            return
        }
        job = scope.launch {
            try {
                _state.value = UpdateState.Downloading(release, 0f)
                val file = download(release) { p -> _state.value = UpdateState.Downloading(release, p) }
                verify(file, release)
                _state.value = UpdateState.Installing(release)
                install(file)
            } catch (e: Exception) {
                _state.value = UpdateState.Error(e.message ?: "Update failed")
            }
        }
    }

    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    internal fun onInstallFailed(message: String) { _state.value = UpdateState.Error(message) }

    // -------------------------------------------------------------------------------------

    private suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "CardVaultNFC/$currentVersion")
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            if (conn.responseCode == 404) return@withContext null // no release, or repo still private
            if (conn.responseCode !in 200..299) error("GitHub returned HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
            val assets = json.optJSONArray("assets")
            var apk: JSONObject? = null
            for (i in 0 until (assets?.length() ?: 0)) {
                val a = assets!!.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) { apk = a; break }
            }
            apk ?: return@withContext null
            val tag = json.getString("tag_name")
            ReleaseInfo(
                tag = tag,
                versionName = tag.removePrefix("v"),
                notes = json.optString("body"),
                apkUrl = apk.getString("browser_download_url"),
                apkSize = apk.optLong("size"),
                sha256 = apk.optString("digest").takeIf { it.startsWith("sha256:") },
                htmlUrl = json.optString("html_url"),
            )
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun download(release: ReleaseInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "cardvault-${release.versionName}.apk")
        val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "CardVaultNFC/$currentVersion")
            if (conn.responseCode == 404) throw FileNotFoundException("Release file not found")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        out
    }

    private fun verify(file: File, release: ReleaseInfo) {
        release.sha256?.let { expected ->
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
            }
            val actual = "sha256:" + md.digest().joinToString("") { "%02x".format(it) }
            check(actual.equals(expected, ignoreCase = true)) { "Download corrupted (checksum mismatch)" }
        }
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0)
        check(info?.packageName == context.packageName) { "Downloaded file isn't a CardVault update" }
    }

    private suspend fun install(file: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
            val pi = PendingIntent.getBroadcast(
                context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pi.intentSender)
        }
    }

    companion object {
        const val REPO = "dev-uraXium/CardVault"

        /** Numeric semver compare: "0.10.0" > "0.9.3"; suffixes like "-dev" are ignored. */
        fun isNewer(remote: String, current: String): Boolean {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val r = parts(remote)
            val c = parts(current)
            for (i in 0 until maxOf(r.size, c.size)) {
                val a = r.getOrElse(i) { 0 }
                val b = c.getOrElse(i) { 0 }
                if (a != b) return a > b
            }
            return false
        }
    }
}
