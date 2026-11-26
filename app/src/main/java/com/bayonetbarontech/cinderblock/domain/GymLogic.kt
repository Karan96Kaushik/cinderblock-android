package com.bayonetbarontech.cinderblock.domain

fun hasSetData(set: SetLog): Boolean =
    set.weight.isNotBlank() || set.reps.isNotBlank() || set.seconds.isNotBlank()

fun isSetComplete(set: SetLog, timed: Boolean): Boolean =
    if (timed) set.seconds.isNotBlank() else set.weight.isNotBlank() && set.reps.isNotBlank()

fun isAddressed(log: ExerciseLog?): Boolean = log != null && (log.completed || log.skipped)

fun exerciseStatus(log: ExerciseLog?): ExerciseStatus {
    if (log == null) return ExerciseStatus.Pending
    if (log.completed) return ExerciseStatus.Done
    if (log.skipped) return ExerciseStatus.Skipped
    if (log.sets.any(::hasSetData)) return ExerciseStatus.Logged
    return ExerciseStatus.Pending
}

fun dayStatus(log: DayLog?): DayStatus {
    if (log == null) return DayStatus.Empty
    if (log.workoutKey == REST_KEY) return DayStatus.Rest
    if (log.exercises.isEmpty()) return DayStatus.Empty
    return if (log.exercises.values.all { isAddressed(it) }) DayStatus.Complete else DayStatus.Partial
}

fun isDayDone(log: DayLog?): Boolean {
    val status = dayStatus(log)
    return status == DayStatus.Complete || status == DayStatus.Rest
}

fun formatOneSet(set: SetLog): String? {
    val seconds = set.seconds.trim()
    if (seconds.isNotEmpty()) return "${seconds}s"
    val weight = set.weight.trim()
    val reps = set.reps.trim()
    return when {
        weight.isNotEmpty() && reps.isNotEmpty() -> "${weight}kg × $reps"
        weight.isNotEmpty() -> "${weight}kg"
        reps.isNotEmpty() -> "$reps reps"
        else -> null
    }
}

fun formatSetSummary(log: ExerciseLog): String? =
    log.sets.mapNotNull(::formatOneSet).takeIf { it.isNotEmpty() }?.joinToString(" · ")

fun daySummary(program: ProgramDocument, log: DayLog): String {
    val status = dayStatus(log)
    if (status == DayStatus.Rest) return workoutLabel(program, REST_KEY)
    val label = logLabel(program, log)
    val total = log.exercises.size
    val completed = log.exercises.values.count { it.completed }
    val withSets = log.exercises.values.count { formatSetSummary(it) != null }
    if (status == DayStatus.Complete) return "$label · $completed/$total done"
    if (withSets > 0) return "$label · $completed/$total done · $withSets logged"
    return "$label · $completed/$total in progress"
}

fun loggedSetCount(log: ExerciseLog, timed: Boolean): Int =
    log.sets.count { isSetComplete(it, timed) }

fun visibleSetCount(log: ExerciseLog, planned: Int, timed: Boolean): Int {
    val cap = maxOf(planned, log.sets.size, 1)
    if (isAddressed(log)) return cap
    val lastComplete = log.sets.indexOfLast { isSetComplete(it, timed) }
    return (lastComplete + 2).coerceIn(1, cap)
}

fun lastNonRest(gym: Map<String, DayLog>): Pair<String, DayLog>? =
    gym.entries
        .filter { it.value.workoutKey != REST_KEY }
        .maxByOrNull { it.key }
        ?.toPair()

fun unfinishedSession(gym: Map<String, DayLog>): Pair<String, DayLog>? {
    val latest = lastNonRest(gym) ?: return null
    return if (dayStatus(latest.second) == DayStatus.Partial) latest else null
}

fun lastExerciseRecord(
    gym: Map<String, DayLog>,
    exerciseName: String,
    beforeDate: String,
): Pair<String, ExerciseLog>? {
    val dates = gym.keys.filter { it < beforeDate }.sortedDescending()
    for (date in dates) {
        val day = gym[date] ?: continue
        if (day.workoutKey == REST_KEY) continue
        val log = day.exercises[exerciseName] ?: continue
        if (log.sets.any(::hasSetData)) return date to log
    }
    return null
}

fun homePill(status: DayStatus): String = when (status) {
    DayStatus.Complete -> "Complete"
    DayStatus.Partial -> "In progress"
    DayStatus.Rest -> "Rest"
    DayStatus.Empty -> "Empty"
}

fun settingsBadge(status: DayStatus): String = when (status) {
    DayStatus.Complete -> "Done"
    DayStatus.Partial -> "Partial"
    DayStatus.Rest -> "Rest"
    DayStatus.Empty -> "Empty"
}

fun exerciseStatusLabel(status: ExerciseStatus): String = when (status) {
    ExerciseStatus.Done -> "Done"
    ExerciseStatus.Skipped -> "Skipped"
    ExerciseStatus.Logged -> "Logged"
    ExerciseStatus.Pending -> "Not started"
}

data class CountStat(val total: Int, val thisWeek: Int, val lastWeek: Int)

fun countStat(dates: List<String>, today: java.time.LocalDate = java.time.LocalDate.now()): CountStat {
    val thisWeek = weekRange(today)
    val lastWeek = weekRange(today.minusWeeks(1))
    return CountStat(
        total = dates.size,
        thisWeek = dates.count { dateInRange(it, thisWeek.first, thisWeek.second) },
        lastWeek = dates.count { dateInRange(it, lastWeek.first, lastWeek.second) },
    )
}
