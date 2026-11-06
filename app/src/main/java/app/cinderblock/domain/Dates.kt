package app.cinderblock.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val longDate: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
private val mediumDate: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
private val monthYear: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val shortMonth: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val monthDayRange: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

fun parseDate(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    return try {
        LocalDate.parse(value)
    } catch (_: Exception) {
        null
    }
}

fun LocalDate.iso(): String = toString()

fun formatLongDate(date: LocalDate): String = date.format(longDate)

fun formatMediumDate(date: LocalDate): String = date.format(mediumDate)

fun formatMonthYear(month: YearMonth): String = month.format(monthYear)

fun formatShortMonth(date: LocalDate): String = date.format(shortMonth)

fun formatWeekRange(start: LocalDate, end: LocalDate): String =
    "${start.format(monthDayRange)} – ${end.format(monthDayRange)}"

fun weekRange(anchor: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate> {
    val start = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return start to start.plusDays(6)
}

fun dateInRange(iso: String, start: LocalDate, end: LocalDate): Boolean {
    val day = parseDate(iso) ?: return false
    return !day.isBefore(start) && !day.isAfter(end)
}

fun daysBetween(earlier: String, later: String): Long {
    val a = parseDate(earlier) ?: return 0
    val b = parseDate(later) ?: return 0
    return ChronoUnit.DAYS.between(a, b)
}

fun monthCells(month: YearMonth): List<LocalDate?> {
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value - DayOfWeek.MONDAY.value
    val cells = MutableList<LocalDate?>(lead) { null }
    for (day in 1..month.lengthOfMonth()) cells += month.atDay(day)
    while (cells.size % 7 != 0) cells += null
    return cells
}

fun formatDayKey(key: String): String {
    val number = key.removePrefix("day")
    return if (number.isNotEmpty()) "Day $number" else key
}

fun formatClock(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val minutes = safe / 60
    val seconds = safe % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

fun formatVersionLabel(version: String): String {
    val trimmed = version.trim()
    if (trimmed.isEmpty()) return "v?"
    return if (trimmed.startsWith("v", ignoreCase = true)) trimmed else "v$trimmed"
}

fun bumpVersion(version: String): String {
    val trimmed = version.trim().ifEmpty { "1.0" }
    val match = Regex("""^(\d+)(?:\.(\d+))?""").find(trimmed) ?: return "$trimmed.1"
    val major = match.groupValues[1].toInt()
    val minor = match.groupValues[2].toIntOrNull() ?: 0
    return "$major.${minor + 1}"
}

fun clampRestMinutes(value: Double): Double {
    if (!value.isFinite()) return 2.0
    val clamped = value.coerceIn(0.5, 10.0)
    return (kotlin.math.round(clamped * 2.0) / 2.0)
}

fun normalizeTheme(value: String?): String {
    val key = value?.trim()?.lowercase().orEmpty()
    val legacy = mapOf(
        "inferno" to "orange",
        "ember" to "green",
        "forge" to "blue",
        "ash" to "cream",
        "daylight" to "white",
    )
    if (key in setOf("orange", "green", "blue", "white", "cream")) return key
    return legacy[key] ?: "orange"
}

fun normalizeFontSize(value: String?): String =
    if (value in setOf("sm", "md", "lg", "xl")) value!! else "md"

fun normalizeFontPreset(value: String?): String =
    if (value in setOf("cinderblock", "readable", "mono")) value!! else "cinderblock"

fun isHttpUrl(value: String): Boolean {
    val trimmed = value.trim()
    return try {
        val uri = java.net.URI(trimmed)
        (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
    } catch (_: Exception) {
        false
    }
}
