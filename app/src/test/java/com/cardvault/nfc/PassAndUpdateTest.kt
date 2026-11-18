package com.cardvault.nfc

import com.cardvault.nfc.pass.BcbpParser
import com.cardvault.nfc.update.UpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PassAndUpdateTest {

    @Test fun `parses IATA BCBP sample`() {
        val s = "M1DESMARAIS/LUC       EABC123 YULFRAAC 0834 226F001A0025 100"
        val r = BcbpParser.parse(s, LocalDate.of(2026, 8, 1))!!
        assertEquals("YUL", r.pass.origin)
        assertEquals("FRA", r.pass.destination)
        assertEquals("AC", r.pass.carrier)
        assertEquals("834", r.pass.flightNumber)
        assertEquals("1A", r.pass.seat)
        assertEquals("ABC123", r.pass.pnr)
        assertEquals("LUC DESMARAIS", r.pass.passenger)
        assertEquals("2026-08-14", r.pass.departure)
    }

    @Test fun `bcbp julian date wraps to next year`() {
        val s = "M1DOE/JANE            EXYZ789 BRUJFKSN 0501 003Y012C0001 100"
        assertEquals("2027-01-03", BcbpParser.parse(s, LocalDate.of(2026, 12, 28))!!.pass.departure)
    }

    @Test fun `non bcbp barcodes are ignored`() {
        assertNull(BcbpParser.parse("https://example.com/ticket/123"))
        assertNull(BcbpParser.parse("4006381333931"))
    }

    @Test fun `version compare`() {
        assertTrue(UpdateManager.isNewer("0.2.1", "0.2.0"))
        assertTrue(UpdateManager.isNewer("0.10.0", "0.9.9"))
        assertTrue(UpdateManager.isNewer("1.0.0", "0.2.0-dev.7"))
        assertFalse(UpdateManager.isNewer("0.2.0", "0.2.0-dev.7"))
        assertFalse(UpdateManager.isNewer("0.1.9", "0.2.0"))
    }
}
