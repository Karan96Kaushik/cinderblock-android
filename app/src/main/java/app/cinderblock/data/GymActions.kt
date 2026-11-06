package app.cinderblock.data

import app.cinderblock.domain.DayLog
import app.cinderblock.domain.ExerciseLog
import app.cinderblock.domain.REST_KEY
import app.cinderblock.domain.REST_LABEL
import app.cinderblock.domain.RestTimer
import app.cinderblock.domain.hasSetData
import app.cinderblock.domain.initExercises
import app.cinderblock.domain.isSetComplete
import app.cinderblock.domain.isTimedHold
import app.cinderblock.domain.resolveProgram

internal suspend fun Controller.selectWorkout(date: String, key: String) {
    edit(sync = true) { state ->
        val existing = state.gymLog[date]
        val now = System.currentTimeMillis()
        val day = when {
            key == REST_KEY -> DayLog(REST_KEY, emptyMap(), state.program.version, REST_LABEL, now)
            existing?.workoutKey == key && existing != null -> existing.copy(updatedAt = now)
            else -> {
                val workout = state.program.workouts[key] ?: return@edit state
                DayLog(key, initExercises(workout), state.program.version, workout.name, now)
            }
        }
        state.copy(gymLog = state.gymLog + (date to day))
    }
}

internal suspend fun Controller.updateSet(
    date: String,
    exercise: String,
    index: Int,
    weight: String?,
    reps: String?,
    seconds: String?,
) {
    edit(sync = true) { state ->
        val day = state.gymLog[date] ?: return@edit state
        val log = day.exercises[exercise] ?: return@edit state
        if (log.completed || log.skipped) return@edit state
        if (index !in log.sets.indices) return@edit state
        val program = resolveProgram(state, day).first
        val definition = program.workouts[day.workoutKey]?.exercises?.firstOrNull { it.name == exercise }
        val timed = definition?.let(::isTimedHold) ?: false
        val current = log.sets[index]
        val nextSet = current.copy(
            weight = weight ?: current.weight,
            reps = reps ?: current.reps,
            seconds = seconds ?: current.seconds,
        )
        val sets = log.sets.toMutableList()
        sets[index] = nextSet
        val wasComplete = isSetComplete(current, timed)
        val nowComplete = isSetComplete(nextSet, timed)
        var rest = state.rest
        if (!wasComplete && nowComplete && state.settings.autoStartRestTimer && index < sets.lastIndex) {
            val started = rest.autoStarted[exercise].orEmpty()
            if (index !in started) {
                val duration = (state.settings.restTimerMinutes * 60).toInt().coerceAtLeast(1)
                rest = rest.start(exercise, date, duration).copy(
                    autoStarted = rest.autoStarted + (exercise to (started + index)),
                )
            }
        }
        val updated = day.copy(
            exercises = day.exercises + (exercise to log.copy(sets = sets)),
            updatedAt = System.currentTimeMillis(),
        )
        state.copy(gymLog = state.gymLog + (date to updated), rest = rest)
    }
}

internal suspend fun Controller.focusSet(date: String, exercise: String, index: Int) {
    if (index <= 0) return
    edit(sync = true) { state ->
        val day = state.gymLog[date] ?: return@edit state
        val log = day.exercises[exercise] ?: return@edit state
        if (log.completed || log.skipped || index !in log.sets.indices) return@edit state
        if (hasSetData(log.sets[index])) return@edit state
        val sets = log.sets.toMutableList()
        sets[index] = sets[index - 1].copy()
        val updated = day.copy(
            exercises = day.exercises + (exercise to log.copy(sets = sets)),
            updatedAt = System.currentTimeMillis(),
        )
        state.copy(gymLog = state.gymLog + (date to updated))
    }
}

internal suspend fun Controller.markExercise(date: String, exercise: String, done: Boolean, skipped: Boolean) {
    edit(sync = true) { state ->
        val day = state.gymLog[date] ?: return@edit state
        val log = day.exercises[exercise] ?: return@edit state
        val updatedLog = log.copy(completed = done, skipped = skipped)
        val updated = day.copy(
            exercises = day.exercises + (exercise to updatedLog),
            updatedAt = System.currentTimeMillis(),
        )
        state.copy(gymLog = state.gymLog + (date to updated))
    }
}

internal suspend fun Controller.ensureSets(date: String) {
    edit(sync = false) { state ->
        val day = state.gymLog[date] ?: return@edit state
        if (day.workoutKey == REST_KEY) return@edit state
        val program = resolveProgram(state, day).first
        val workout = program.workouts[day.workoutKey] ?: return@edit state
        var changed = false
        val exercises = day.exercises.toMutableMap()
        for (exercise in workout.exercises) {
            val log = exercises[exercise.name]
            if (log == null) {
                exercises[exercise.name] = ExerciseLog(sets = List(exercise.sets.coerceAtLeast(1)) { app.cinderblock.domain.SetLog() })
                changed = true
            } else if (log.sets.size < exercise.sets) {
                exercises[exercise.name] = log.copy(
                    sets = log.sets + List(exercise.sets - log.sets.size) { app.cinderblock.domain.SetLog() },
                )
                changed = true
            }
        }
        if (!changed) state else state.copy(gymLog = state.gymLog + (date to day.copy(exercises = exercises)))
    }
}

internal suspend fun Controller.deleteDay(date: String) {
    edit(sync = true) { it.copy(gymLog = it.gymLog - date) }
}

internal suspend fun Controller.deleteOldWorkouts() {
    val cutoff = java.time.LocalDate.now().minusDays(30).toString()
    edit(sync = true) { state ->
        state.copy(gymLog = state.gymLog.filterKeys { it >= cutoff })
    }
}

internal suspend fun Controller.clearWorkouts() {
    edit(sync = true) { it.copy(gymLog = emptyMap()) }
}

internal suspend fun Controller.setVideo(exercise: String, url: String?) {
    edit(sync = false) { state ->
        val next = state.videos.toMutableMap()
        if (url.isNullOrBlank()) next.remove(exercise) else next[exercise] = url.trim()
        state.copy(videos = next)
    }
}

internal suspend fun Controller.startRest(date: String, exercise: String, seconds: Int) {
    edit(persist = false) { state ->
        state.copy(rest = state.rest.start(exercise, date, seconds))
    }
}

internal suspend fun Controller.pauseRest() {
    val now = System.currentTimeMillis()
    edit(persist = false) { state ->
        val rest = state.rest
        if (!rest.running) return@edit state
        state.copy(rest = rest.copy(running = false, endsAt = null, remainingSeconds = rest.displayRemaining(now)))
    }
}

internal suspend fun Controller.resumeRest() {
    val now = System.currentTimeMillis()
    edit(persist = false) { state ->
        val rest = state.rest
        if (rest.remainingSeconds <= 0) return@edit state
        state.copy(
            rest = rest.copy(
                running = true,
                announced = false,
                endsAt = now + rest.remainingSeconds * 1000L,
            ),
        )
    }
}

internal suspend fun Controller.resetRest() {
    edit(persist = false) { state ->
        state.copy(
            rest = RestTimer(autoStarted = state.rest.autoStarted),
        )
    }
}

internal suspend fun Controller.completeRest() {
    val state = snapshot()
    if (!state.rest.running || state.rest.announced) return
    edit(persist = false) { current ->
        current.copy(
            rest = current.rest.copy(running = false, endsAt = null, remainingSeconds = 0, announced = true),
        )
    }
    playSound("timer")
}

private fun RestTimer.start(exercise: String, date: String, seconds: Int): RestTimer {
    val now = System.currentTimeMillis()
    return copy(
        ownerExercise = exercise,
        ownerDate = date,
        endsAt = now + seconds * 1000L,
        remainingSeconds = seconds,
        running = true,
        durationSeconds = seconds,
        announced = false,
    )
}
