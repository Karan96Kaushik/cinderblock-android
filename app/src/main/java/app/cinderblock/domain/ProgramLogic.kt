package app.cinderblock.domain

fun isTimedHold(exercise: ProgramExercise): Boolean = !exercise.duration.isNullOrBlank()

fun workoutKeys(program: ProgramDocument): List<String> = program.workouts.keys.toList()

fun isProgramKey(program: ProgramDocument, key: String): Boolean = program.workouts.containsKey(key)

fun workoutLabel(program: ProgramDocument, key: String): String {
    if (key == REST_KEY) return REST_LABEL
    return program.workouts[key]?.name ?: key
}

fun logLabel(program: ProgramDocument, log: DayLog): String {
    if (log.workoutKey == REST_KEY) return REST_LABEL
    if (!log.workoutName.isNullOrBlank()) return log.workoutName
    return workoutLabel(program, log.workoutKey)
}

fun colorGroup(program: ProgramDocument, key: String): String {
    val normalized = key.lowercase()
    if (normalized.contains("upper")) return "upper"
    if (normalized.contains("lower")) return "lower"
    val index = workoutKeys(program).indexOf(key)
    return if (index >= 0 && index % 2 == 1) "lower" else "upper"
}

fun scheduleHint(program: ProgramDocument, key: String): String {
    val name = program.workouts[key]?.name ?: return ""
    for ((dayKey, label) in program.schedule.entries()) {
        if (label.lowercase().contains(name.lowercase())) {
            return "${formatDayKey(dayKey)} — $label"
        }
    }
    val count = program.workouts[key]?.exercises?.size ?: 0
    return "$count exercise${if (count == 1) "" else "s"}"
}

fun scheduleSectionLabel(program: ProgramDocument): String {
    val count = program.workouts.size
    val hasRun = program.schedule.entries().any { it.second.lowercase().contains("run") }
    return if (hasRun) "$count-day split + running" else "$count-day split"
}

fun resolveProgram(state: AppState, log: DayLog?): Pair<ProgramDocument, Boolean> {
    if (log?.programVersion.isNullOrBlank() || log.programVersion == state.program.version) {
        return state.program to false
    }
    val historical = state.versions.firstOrNull { it.version == log.programVersion }?.program
    return if (historical != null) historical to true else state.program to false
}

fun emptySetFor(exercise: ProgramExercise): SetLog =
    if (isTimedHold(exercise)) SetLog() else SetLog()

fun initExercises(workout: ProgramWorkout): Map<String, ExerciseLog> =
    workout.exercises.associate { exercise ->
        exercise.name to ExerciseLog(
            sets = List(exercise.sets.coerceAtLeast(1)) { SetLog() },
        )
    }

fun validateProgram(program: ProgramDocument): ProgramIssue? {
    if (program.name.isBlank()) return ProgramIssue("name", "Program name is required")
    if (program.version.isBlank()) return ProgramIssue("version", "Version is required")
    program.workouts.forEach { (key, workout) ->
        if (key.isBlank()) return ProgramIssue("workouts", "Workout key is required")
        if (workout.name.isBlank()) return ProgramIssue("workouts.$key.name", "Workout name is required")
        workout.exercises.forEachIndexed { index, exercise ->
            if (exercise.sets < 1) {
                return ProgramIssue(
                    "workouts.$key.exercises.$index.sets",
                    "Sets must be a positive integer",
                )
            }
        }
    }
    return null
}

fun programToPlaintext(program: ProgramDocument): String {
    val lines = mutableListOf<String>()
    lines += "Program: ${program.name}"
    lines += "Version: ${program.version}"
    lines += ""
    if (program.goal.isNotEmpty()) {
        lines += "Goals:"
        program.goal.forEach { lines += "  - $it" }
        lines += ""
    }
    lines += "Weekly schedule:"
    program.schedule.entries().forEach { (day, label) -> lines += "  $day: $label" }
    lines += ""
    if (program.globalNotes.isNotEmpty()) {
        lines += "Global notes:"
        program.globalNotes.forEach { lines += "  - $it" }
        lines += ""
    }
    lines += "Progression: ${program.progression.method}"
    lines += "  Example (${program.progression.example.exercise}): ${program.progression.example.nextStep}"
    lines += ""
    lines += "Workouts:"
    program.workouts.forEach { (key, workout) ->
        lines += ""
        lines += "[$key] ${workout.name}"
        workout.exercises.forEach { exercise ->
            val load = exercise.reps ?: exercise.duration ?: "?"
            val muscles = if (exercise.muscles.isEmpty()) "" else " [${exercise.muscles.joinToString(", ")}]"
            lines += "  - ${exercise.name}: ${exercise.sets}×$load$muscles"
        }
    }
    return lines.joinToString("\n")
}
