package com.cardvault.nfc

import com.cardvault.nfc.backup.BackupCodec
import com.cardvault.nfc.backup.WrongPassphraseException
import com.cardvault.nfc.data.ApduRule
import com.cardvault.nfc.data.EmulationMode
import com.cardvault.nfc.data.NfcPayload
import com.cardvault.nfc.nfc.ApduResponder
import com.cardvault.nfc.nfc.Hex
import com.cardvault.nfc.nfc.Tlv
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApduResponderTest {

    private val uriMessage = Hex.decode("D1010C55046578616D706C652E636F6D") // https://example.com

    private fun ApduResponder.tx(hex: String) = Hex.encode(process(Hex.decode(hex)))

    @Test fun `type 4 read flow`() {
        val r = ApduResponder(NfcPayload(EmulationMode.NDEF_TYPE4, Hex.encode(uriMessage)), "")
        assertEquals("9000", r.tx("00A4040007D276000085010100"))
        assertEquals("9000", r.tx("00A4000C02E103"))
        val cc = r.tx("00B000000F")
        assertTrue(cc.startsWith("000F20") && cc.endsWith("9000"))
        assertEquals("9000", r.tx("00A4000C02E104"))
        assertEquals("00109000", r.tx("00B0000002"))
        assertEquals(Hex.encode(uriMessage) + "9000", r.tx("00B0000210"))
    }

    @Test fun `type 4 is read only and resets per tap`() {
        val r = ApduResponder(NfcPayload(EmulationMode.NDEF_TYPE4, Hex.encode(uriMessage)), "")
        r.tx("00A4040007D276000085010100"); r.tx("00A4000C02E104")
        assertEquals("6982", r.tx("00D6000001FF"))
        r.reset()
        assertEquals("6986", r.tx("00B0000002"))
    }

    @Test fun `apdu table`() {
        val r = ApduResponder(NfcPayload(EmulationMode.APDU_TABLE, apduRules = listOf(ApduRule("00CA", "01029000"))), "F0010203040506")
        assertEquals("9000", r.tx("00A4040007F001020304050600"))
        assertEquals("01029000", r.tx("00CA9F7F00"))
        assertEquals("6D00", r.tx("80CA000000"))
    }

    @Test fun `fci serial`() {
        val fci = Hex.decode("6F1A8408315449432E494341A50EBF0C0BC708000000001234567853070A3C2011141001")
        assertEquals("0000000012345678", Hex.encode(Tlv.find(fci, 0xC7)))
    }

    @Test fun `backup round trip`() {
        val data = "hello vault".toByteArray()
        val blob = BackupCodec.encrypt(data, "correct horse battery".toCharArray())
        assertArrayEquals(data, BackupCodec.decrypt(blob, "correct horse battery".toCharArray()))
    }

    @Test(expected = WrongPassphraseException::class) fun `backup wrong passphrase`() {
        val blob = BackupCodec.encrypt("x".toByteArray(), "correct horse battery".toCharArray())
        BackupCodec.decrypt(blob, "wrong passphrase!!".toCharArray())
    }
}
