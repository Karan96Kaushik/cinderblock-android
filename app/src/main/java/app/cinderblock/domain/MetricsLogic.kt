package app.cinderblock.domain

import java.time.LocalDate
import kotlin.math.abs

data class MetricField(val key: String, val label: String, val unit: String)

val METRIC_FIELDS = listOf(
    MetricField("weight", "Weight", "kg"),
    MetricField("waist", "Waist", "cm"),
    MetricField("chest", "Chest", "cm"),
    MetricField("hips", "Hips", "cm"),
    MetricField("bodyFat", "Body fat", "%"),
)

fun MetricEntry.valueOf(key: String): String = when (key) {
    "weight" -> weight
    "waist" -> waist
    "chest" -> chest
    "hips" -> hips
    "bodyFat" -> bodyFat
    else -> ""
}

fun MetricEntry.withValue(key: String, value: String): MetricEntry = when (key) {
    "weight" -> copy(weight = value)
    "waist" -> copy(waist = value)
    "chest" -> copy(chest = value)
    "hips" -> copy(hips = value)
    "bodyFat" -> copy(bodyFat = value)
    else -> this
}

fun MetricEntry.hasContent(): Boolean =
    weight.isNotBlank() || waist.isNotBlank() || chest.isNotBlank() ||
        hips.isNotBlank() || bodyFat.isNotBlank() || notes.isNotBlank()

fun parseMetric(value: String?): Double? {
    if (value.isNullOrBlank()) return null
    return value.toDoubleOrNull()
}

data class BodyTrend(val value: Double, val delta: Double?, val referenceLabel: String?)

fun bodyTrend(entries: List<MetricEntry>, key: String): BodyTrend? {
    val readings = entries.mapNotNull { entry ->
        val value = parseMetric(entry.valueOf(key)) ?: return@mapNotNull null
        entry.date to value
    }.sortedByDescending { it.first }
    val latest = readings.firstOrNull() ?: return null
    val earlier = readings.drop(1)
    val reference = earlier.firstOrNull { daysBetween(it.first, latest.first) >= 28 } ?: earlier.lastOrNull()
    if (reference == null) return BodyTrend(latest.second, null, null)
    val day = parseDate(reference.first)
    val label = day?.let { "vs ${formatShortMonth(it)}" }
    return BodyTrend(latest.second, latest.second - reference.second, label)
}

fun previousMetric(entries: List<MetricEntry>, date: String): MetricEntry? =
    entries.filter { it.date < date }.maxByOrNull { it.date }

fun formatDelta(current: String, previous: String?, unit: String): String? {
    val c = parseMetric(current) ?: return null
    val p = parseMetric(previous) ?: return null
    val diff = c - p
    if (diff == 0.0) return "No change"
    val sign = if (diff > 0) "+" else ""
    return "$sign${"%.1f".format(diff)} $unit".trim()
}

fun decreaseIsGood(key: String): Boolean = key == "waist" || key == "bodyFat"

fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

fun signedDeltaText(delta: Double, unit: String): String =
    "${"%.1f".format(abs(delta))} $unit"

data class GoalProgress(
    val metricLabel: String,
    val valueLabel: String,
    val ratio: Float? = null,
    val trendDirection: String? = null,
    val trendText: String? = null,
)

fun goalProgress(
    goal: String,
    program: ProgramDocument,
    gym: Map<String, DayLog>,
    runs: List<RunLog>,
    weight: BodyTrend?,
    waist: BodyTrend?,
    today: LocalDate = LocalDate.now(),
): GoalProgress {
    val text = goal.lowercase()
    val plannedGym = program.schedule.entries()
        .count { !Regex("""^\s*rest\b""", RegexOption.IGNORE_CASE).containsMatchIn(it.second) }
        .coerceAtLeast(1)
    if (Regex("""\b(run|running|cardio|endurance|aerobic)\b""").containsMatchIn(text)) {
        val explicit = Regex("""(\d+)\s*x""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val scheduled = program.schedule.entries().count { Regex("""\brun\b""", RegexOption.IGNORE_CASE).containsMatchIn(it.second) }
        val target = explicit.takeIf { it > 0 } ?: scheduled.takeIf { it > 0 } ?: 3
        val done = countStat(runs.map { it.date }, today).thisWeek
        return GoalProgress("Runs this week", "$done/$target", done.toFloat() / target)
    }
    if (Regex("""\b(fat|weight|lean|cut|waist)\b""").containsMatchIn(text)) {
        val body = when {
            waist?.delta != null -> Triple(waist, "cm", "Waist")
            weight?.delta != null -> Triple(weight, "kg", "Weight")
            else -> null
        }
        if (body == null) return GoalProgress("Waist / weight trend", "Log metrics to track")
        val delta = body.first.delta ?: 0.0
        val direction = when {
            delta > 0 -> "up"
            delta < 0 -> "down"
            else -> "flat"
        }
        return GoalProgress(
            metricLabel = "${body.third} trend",
            valueLabel = signedDeltaText(delta, body.second),
            trendDirection = direction,
            trendText = body.first.referenceLabel,
        )
    }
    if (Regex("""\b(strength|strong|stronger|power)\b""").containsMatchIn(text)) {
        val start = today.minusDays(27)
        val done = gym.count { (date, log) ->
            dayStatus(log) == DayStatus.Complete && dateInRange(date, start, today)
        }
        val target = plannedGym * 4
        return GoalProgress(
            "Completed sessions · last 4 wks",
            "$done/$target",
            done.toFloat() / target,
        )
    }
    val (weekStart, weekEnd) = weekRange(today)
    val done = gym.count { (date, log) ->
        dayStatus(log) == DayStatus.Complete && dateInRange(date, weekStart, weekEnd)
    }
    return GoalProgress(
        "Workouts completed this week",
        "$done/$plannedGym",
        done.toFloat() / plannedGym,
    )
}

fun mergeGym(local: Map<String, DayLog>, remote: Map<String, DayLog>): Map<String, DayLog> {
    val merged = remote.toMutableMap()
    for ((date, entry) in local) {
        val other = merged[date]
        if (other == null || entry.updatedAt >= other.updatedAt) merged[date] = entry
    }
    return merged
}

fun mergeMetrics(local: List<MetricEntry>, remote: List<MetricEntry>): List<MetricEntry> {
    val byDate = remote.filter { it.date.isNotBlank() }.associateBy { it.date }.toMutableMap()
    for (entry in local) {
        if (entry.date.isBlank()) continue
        val existing = byDate[entry.date]
        if (existing == null || entry.updatedAt >= existing.updatedAt) byDate[entry.date] = entry
    }
    return byDate.values.sortedByDescending { it.date }
}

fun mergeRuns(local: List<RunLog>, remote: List<RunLog>): List<RunLog> {
    val byId = remote.associateBy { it.id }.toMutableMap()
    for (entry in local) {
        val existing = byId[entry.id]
        if (existing == null || entry.completedAt >= existing.completedAt) byId[entry.id] = entry
    }
    return byId.values.sortedByDescending { it.completedAt }
}
