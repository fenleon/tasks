package com.lightphone.tasks.server

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure-Kotlin date/time helpers for the task display strings and the `dueAt`
 * representation (SPEC §3): `dueAt` is epoch ms with an optional time — a
 * date-only due is stored at local midnight, a due with a time carries the
 * time. "No time" reads back as exactly-midnight on the due date (an explicit
 * 00:00 selection displays as date-only — accepted simplification).
 */
object TaskFormat {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val DAY_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
    private val WEEKDAY = DateTimeFormatter.ofPattern("EEEE", Locale.US)
    private val FULL_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)
    private val DETAILS_DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)

    /** "Aug 25" (current year) / "Aug 25, 2027", + ", 14:30" when a time is set
     *  (feedback 2026-08-26: the row's due reads "[Date], [Time]"). */
    fun formatDue(dueAt: Long?): String {
        if (dueAt == null) return "none"
        val zoned = Instant.ofEpochMilli(dueAt).atZone(zone)
        val date = if (zoned.year == LocalDate.now().year) {
            DAY.format(zoned)
        } else {
            DAY_YEAR.format(zoned)
        }
        val time = timeOfDay(dueAt)?.let { TIME.format(it) }
        return if (time != null) "$date, $time" else date
    }

    /** "August 2026" — the due screen's month title. */
    fun formatMonthTitle(month: LocalDate): String = MONTH_TITLE.format(month)

    /** "14:30" — the due screen's picked-time display. */
    fun formatTime(time: LocalTime): String = TIME.format(time)

    /** The details screen's due lines (feedback 2026-09-02): a due with a time
     *  splits so the weekday sits inline with the time on the first line
     *  ("Thursday, 14:30") and the full date gets its own line beneath
     *  ("September 3, 2026") — the old single long line wrapped awkwardly.
     *  A date-only due keeps the single "Thursday, September 3, 2026" line.
     *  Returns null when there is no time; show [formatDetailsDate] on its
     *  own then. */
    fun formatDetailsDayTime(dueAt: Long?): String? {
        if (dueAt == null) return null
        val time = timeOfDay(dueAt) ?: return null
        val zoned = Instant.ofEpochMilli(dueAt).atZone(zone)
        return "${WEEKDAY.format(zoned)}, ${TIME.format(time)}"
    }

    /** "September 3, 2026" — or "Thursday, September 3, 2026" when the due is
     *  date-only (no time, so no separate weekday line above). */
    fun formatDetailsDate(dueAt: Long?): String {
        if (dueAt == null) return ""
        val zoned = Instant.ofEpochMilli(dueAt).atZone(zone)
        return if (timeOfDay(dueAt) == null) {
            DETAILS_DATE.format(zoned)
        } else {
            FULL_DATE.format(zoned)
        }
    }

    /** Date-only epoch (local midnight of the day). */
    fun startOfDay(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** The local date a stored epoch falls on. */
    fun dateOf(epoch: Long): LocalDate = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()

    /** The stored time-of-day, or null when the epoch is local midnight (date-only). */
    fun timeOfDay(epoch: Long): LocalTime? {
        val zoned = Instant.ofEpochMilli(epoch).atZone(zone)
        return if (epoch == startOfDay(zoned.toLocalDate())) null else zoned.toLocalTime()
    }

    /** Combines a picked date with an optional time into the stored epoch. */
    fun combine(date: LocalDate, time: LocalTime?): Long {
        val day = date.atStartOfDay(zone).toInstant().toEpochMilli()
        if (time == null) return day
        return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }
}