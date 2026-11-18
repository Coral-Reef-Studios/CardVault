package com.cardvault.nfc.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.cardvault.nfc.CardVaultApp
import com.cardvault.nfc.data.ApduLogEntity
import kotlinx.coroutines.launch

/**
 * HCE entry point. Stateless with respect to secrets: everything comes from the in-RAM
 * [EmulationSession]. If nothing is armed, or the window expired, every command gets 69 82.
 */
class CardVaultApduService : HostApduService() {

    private var responder: ApduResponder? = null
    private var responderCardId: String? = null

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        val session = EmulationSession.current()
        val response = if (session == null) {
            responder = null
            Apdu.SW_SECURITY_NOT_SATISFIED
        } else {
            if (responderCardId != session.cardId || responder == null) {
                responder = ApduResponder(session.payload, session.aidHex)
                responderCardId = session.cardId
                EmulationSession.onReaderTap()
            }
            responder!!.process(commandApdu)
        }
        log(session?.cardId, commandApdu, response)
        return response
    }

    override fun onDeactivated(reason: Int) {
        // Link lost or another AID selected: start the next tap from a clean state.
        responder?.reset()
        responder = null
        responderCardId = null
    }

    private fun log(cardId: String?, cmd: ByteArray, resp: ByteArray) {
        val app = application as? CardVaultApp ?: return
        val id = cardId ?: "_locked"
        val now = System.currentTimeMillis()
        app.container.appScope.launch {
            runCatching {
                val repo = app.container.repository
                repo.logApdu(ApduLogEntity(cardId = id, timestamp = now, direction = "INCOMING_C_APDU",
                    hexPayload = Hex.encode(cmd), statusWord = ""))
                repo.logApdu(ApduLogEntity(cardId = id, timestamp = now, direction = "OUTGOING_R_APDU",
                    hexPayload = Hex.encode(resp), statusWord = Apdu.statusWord(resp)))
            }
        }
    }
}
