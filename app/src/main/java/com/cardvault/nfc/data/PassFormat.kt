package com.cardvault.nfc.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object PassFormat {

    private val LOCAL_PATTERNS = listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd'T'HH:mm:ss")
        .map { DateTimeFormatter.ofPattern(it, Locale.ROOT) }

    /** Parses "yyyy-MM-dd HH:mm", ISO local, ISO with offset, or a bare date. */
    fun parseTime(value: String): Long? {
        val v = value.trim()
        if (v.isEmpty()) return null
        runCatching { return OffsetDateTime.parse(v).toInstant().toEpochMilli() }
        runCatching { return Instant.parse(v).toEpochMilli() }
        for (f in LOCAL_PATTERNS) {
            runCatching { return LocalDateTime.parse(v, f).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        }
        runCatching { return LocalDate.parse(v).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        return null
    }

    fun formatTime(ms: Long, withTime: Boolean = true): String {
        val pattern = if (withTime) "EEE d MMM · HH:mm" else "EEE d MMM yyyy"
        return DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
            .format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
    }

    /** Human date for a stored departure string; falls back to the raw text. */
    fun display(value: String): String {
        val ms = parseTime(value) ?: return value
        val hasTime = value.contains(':')
        return formatTime(ms, hasTime)
    }

    fun face(p: PassData): PassFace = when (p.kind) {
        PassKind.BOARDING_PASS -> PassFace(
            kind = p.kind,
            origin = p.origin.uppercase(),
            destination = p.destination.uppercase(),
            line1 = listOf("${p.carrier} ${p.flightNumber}".trim(), p.departure.takeIf { it.isNotBlank() }?.let(::display))
                .filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
            line2 = listOfNotNull(
                p.boardingTime.takeIf { it.isNotBlank() }?.let { "Boards $it" },
                p.gate.takeIf { it.isNotBlank() }?.let { "Gate $it" },
                p.seat.takeIf { it.isNotBlank() }?.let { "Seat $it" },
            ).joinToString(" · "),
        )
        PassKind.EVENT_TICKET -> PassFace(
            kind = p.kind,
            line1 = p.eventName,
            line2 = listOfNotNull(
                p.departure.takeIf { it.isNotBlank() }?.let(::display),
                p.venue.takeIf { it.isNotBlank() },
                listOfNotNull(
                    p.section.takeIf { it.isNotBlank() }?.let { "Sec $it" },
                    p.row.takeIf { it.isNotBlank() }?.let { "Row $it" },
                    p.seat.takeIf { it.isNotBlank() }?.let { "Seat $it" },
                ).joinToString(" ").takeIf { it.isNotBlank() },
            ).joinToString(" · "),
        )
        PassKind.GIFT_CARD -> PassFace(kind = p.kind, line1 = p.balance.takeIf { it.isNotBlank() }?.let { "Balance $it" } ?: "")
        else -> PassFace(
            kind = p.kind,
            line1 = p.fields.firstOrNull()?.let { "${it.label} ${it.value}".trim() } ?: "",
            line2 = p.departure.takeIf { it.isNotBlank() }?.let(::display) ?: "",
        )
    }
}
