package com.wmt.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Parsing + humanising helpers for the ISO-8601 timestamps the API returns. */
object DateUtils {

    private val displayDate: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    private val displayDateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")

    /** Parses an API date (date-only or full ISO timestamp) into a [LocalDate]. */
    fun parseDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(raw.substring(0, 10)) }.getOrNull()
    }

    fun parseInstant(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(raw).toInstant() }
            .recoverCatching { Instant.parse(raw) }
            .recoverCatching {
                parseDate(raw)?.atStartOfDay(ZoneId.systemDefault())?.toInstant()
                    ?: error("unparseable")
            }
            .getOrNull()
    }

    fun formatDate(raw: String?): String? = parseDate(raw)?.format(displayDate)

    fun formatDateTime(raw: String?): String? =
        parseInstant(raw)?.atZone(ZoneId.systemDefault())?.format(displayDateTime)

    /** Whole-day delta from today; negative = overdue, 0 = today. Null if unparseable. */
    fun daysUntil(raw: String?): Long? {
        val date = parseDate(raw) ?: return null
        return ChronoUnit.DAYS.between(LocalDate.now(), date)
    }

    fun isOverdue(raw: String?): Boolean = (daysUntil(raw) ?: 1L) < 0L

    /** True when a completion timestamp lands after the end of the due date's day. */
    fun isCompletedLate(dueDate: String?, completedAt: String?): Boolean {
        val due = parseDate(dueDate) ?: return false
        val completed = parseInstant(completedAt) ?: return false
        val endOfDueDay = due.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        return completed >= endOfDueDay
    }

    /** Compact "x ago" / "in x" style relative label for a timestamp. */
    fun timeAgo(raw: String?, now: Instant = Instant.now()): String {
        val instant = parseInstant(raw) ?: return ""
        val seconds = ChronoUnit.SECONDS.between(instant, now)
        val past = seconds >= 0
        val s = abs(seconds)
        val label = when {
            s < 60 -> "just now"
            s < 3600 -> "${s / 60}m"
            s < 86_400 -> "${s / 3600}h"
            s < 604_800 -> "${s / 86_400}d"
            s < 2_592_000 -> "${s / 604_800}w"
            s < 31_536_000 -> "${s / 2_592_000}mo"
            else -> "${s / 31_536_000}y"
        }
        return when {
            label == "just now" -> label
            past -> "$label ago"
            else -> "in $label"
        }
    }
}
