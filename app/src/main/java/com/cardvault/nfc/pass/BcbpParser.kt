package com.cardvault.nfc.pass

import com.cardvault.nfc.data.PassData
import com.cardvault.nfc.data.PassKind
import java.time.LocalDate

/**
 * IATA Resolution 792 Bar Coded Boarding Pass (BCBP), mandatory items of the first leg.
 * Works on the text inside the PDF417 / Aztec / QR code printed on almost every boarding pass.
 */
object BcbpParser {

    data class Result(val pass: PassData, val title: String)

    fun looksLikeBcbp(text: String): Boolean =
        text.length >= 60 && text[0] == 'M' && text[1].isDigit() && text.substring(30, 36).all { it.isLetter() }

    /** [today] is injectable for tests. */
    fun parse(text: String, today: LocalDate = LocalDate.now()): Result? {
        if (!looksLikeBcbp(text)) return null
        fun f(start: Int, len: Int) = text.substring(start, start + len)

        val rawName = f(2, 20).trim()
        val pnr = f(23, 7).trim()
        val from = f(30, 3)
        val to = f(33, 3)
        val carrier = f(36, 3).trim()
        val flight = f(39, 5).trim().trimStart('0').ifEmpty { "0" }
        val julian = f(44, 3).trim().toIntOrNull()
        val compartment = f(47, 1)
        val seat = f(48, 4).trim().trimStart('0')
        val seq = f(52, 5).trim().trimStart('0')

        val date = julian?.takeIf { it in 1..366 }?.let { resolveDate(it, today) }
        val passenger = rawName.split('/').let { parts ->
            if (parts.size == 2) "${parts[1].trim()} ${parts[0].trim()}".trim() else rawName
        }

        val pass = PassData(
            kind = PassKind.BOARDING_PASS,
            transitType = "AIR",
            origin = from,
            destination = to,
            carrier = carrier,
            flightNumber = flight,
            departure = date?.toString() ?: "",
            seat = seat,
            cabin = compartment,
            pnr = pnr,
            passenger = passenger,
            fields = listOfNotNull(seq.takeIf { it.isNotEmpty() }?.let { com.cardvault.nfc.data.PassField("Sequence", it) }),
            source = "bcbp",
        )
        return Result(pass, "$carrier $flight · $from → $to")
    }

    /** BCBP stores only day-of-year: choose the year that puts the date closest to today. */
    private fun resolveDate(dayOfYear: Int, today: LocalDate): LocalDate? {
        val candidates = listOf(today.year - 1, today.year, today.year + 1).mapNotNull { y ->
            runCatching { LocalDate.ofYearDay(y, dayOfYear) }.getOrNull()
        }
        return candidates.minByOrNull { kotlin.math.abs(it.toEpochDay() - today.toEpochDay()) }
    }
}
