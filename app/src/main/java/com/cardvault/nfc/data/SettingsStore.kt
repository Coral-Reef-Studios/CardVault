package com.cardvault.nfc.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val appLockEnabled: Boolean = true,
    /** Seconds; [WHILE_SCREEN_ON] keeps HCE armed until the app leaves the foreground. */
    val emulationTimeoutSec: Int = 45,
    val secureScreens: Boolean = true,
    val autoLockOnBackground: Boolean = true,
    val lastBackupAt: Long = 0,
    val lastBackupBytes: Long = 0,
    val lastBackupTarget: String = "",
    // Appearance
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val accentHex: String = "#22D3EE",
    val amoledBlack: Boolean = false,
    val compactWallet: Boolean = false,
    // Updates
    val autoCheckUpdates: Boolean = true,
    val lastUpdateCheck: Long = 0,
) {
    companion object { const val WHILE_SCREEN_ON = -1 }
}

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private fun load() = AppSettings(
        appLockEnabled = prefs.getBoolean("appLock", true),
        emulationTimeoutSec = prefs.getInt("emuTimeout", 45),
        secureScreens = prefs.getBoolean("secure", true),
        autoLockOnBackground = prefs.getBoolean("autoLock", true),
        lastBackupAt = prefs.getLong("lastBackupAt", 0),
        lastBackupBytes = prefs.getLong("lastBackupBytes", 0),
        lastBackupTarget = prefs.getString("lastBackupTarget", "") ?: "",
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString("themeMode", "DARK")!!) }.getOrDefault(ThemeMode.DARK),
        dynamicColor = prefs.getBoolean("dynamicColor", false),
        accentHex = prefs.getString("accentHex", "#22D3EE") ?: "#22D3EE",
        amoledBlack = prefs.getBoolean("amoled", false),
        compactWallet = prefs.getBoolean("compactWallet", false),
        autoCheckUpdates = prefs.getBoolean("autoUpdates", true),
        lastUpdateCheck = prefs.getLong("lastUpdateCheck", 0),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_state.value)
        prefs.edit()
            .putBoolean("appLock", s.appLockEnabled)
            .putInt("emuTimeout", s.emulationTimeoutSec)
            .putBoolean("secure", s.secureScreens)
            .putBoolean("autoLock", s.autoLockOnBackground)
            .putLong("lastBackupAt", s.lastBackupAt)
            .putLong("lastBackupBytes", s.lastBackupBytes)
            .putString("lastBackupTarget", s.lastBackupTarget)
            .putString("themeMode", s.themeMode.name)
            .putBoolean("dynamicColor", s.dynamicColor)
            .putString("accentHex", s.accentHex)
            .putBoolean("amoled", s.amoledBlack)
            .putBoolean("compactWallet", s.compactWallet)
            .putBoolean("autoUpdates", s.autoCheckUpdates)
            .putLong("lastUpdateCheck", s.lastUpdateCheck)
            .apply()
        _state.value = s
    }
}
