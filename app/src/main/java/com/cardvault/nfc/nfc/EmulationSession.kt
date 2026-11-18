package com.cardvault.nfc.nfc

import com.cardvault.nfc.data.Card
import com.cardvault.nfc.data.NfcPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single armed HCE card, held in volatile RAM only (never persisted).
 * [CardVaultApduService] answers 69 82 whenever [current] returns null.
 */
object EmulationSession {

    data class Active(
        val cardId: String,
        val title: String,
        val aidHex: String,
        val payload: NfcPayload,
        val startedAt: Long,
        /** Long.MAX_VALUE = until the app leaves the foreground. */
        val expiresAt: Long,
    ) {
        val remainingMs: Long get() = (expiresAt - System.currentTimeMillis()).coerceAtLeast(0)
    }

    private val _state = MutableStateFlow<Active?>(null)
    val state: StateFlow<Active?> = _state.asStateFlow()

    /** Bumped on every reader tap so the UI can play a haptic. */
    private val _taps = MutableStateFlow(0)
    val taps: StateFlow<Int> = _taps.asStateFlow()

    fun start(card: Card, timeoutSec: Int) {
        val now = System.currentTimeMillis()
        _state.value = Active(
            cardId = card.id,
            title = card.title,
            aidHex = card.aidHex,
            payload = card.nfcPayload,
            startedAt = now,
            expiresAt = if (timeoutSec < 0) Long.MAX_VALUE else now + timeoutSec * 1000L,
        )
    }

    fun current(): Active? {
        val s = _state.value ?: return null
        if (System.currentTimeMillis() >= s.expiresAt) {
            _state.value = null
            return null
        }
        return s
    }

    fun stop() { _state.value = null }

    internal fun onReaderTap() { _taps.value = _taps.value + 1 }
}
