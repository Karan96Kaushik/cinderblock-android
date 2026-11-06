package app.cinderblock.data

import app.cinderblock.domain.ActiveRun
import app.cinderblock.domain.PHASES
import app.cinderblock.domain.RunCompletion
import app.cinderblock.domain.RunPhase
import app.cinderblock.domain.buildLoggedRunPlan
import app.cinderblock.domain.displayRemaining
import app.cinderblock.domain.phaseSeconds
import app.cinderblock.domain.projectRun
import java.time.LocalDate

internal suspend fun Controller.saveRunPlan(plan: app.cinderblock.domain.RunningPlan) {
    edit(sync = true) { state ->
        val active = state.activeRun
        val nextActive = if (active != null && !active.started) {
            active.copy(plan = plan, remainingSeconds = plan.warmupMinutes * 60, phaseIndex = 0)
        } else {
            active
        }
        state.copy(runningPlan = plan, activeRun = nextActive, planUpdatedAt = System.currentTimeMillis())
    }
}

internal suspend fun Controller.openRun(fresh: Boolean) {
    edit { state ->
        var next = if (fresh) state.copy(activeRun = null, runCompletion = null) else state
        if (next.activeRun == null) {
            val plan = next.runningPlan
            next = next.copy(
                activeRun = ActiveRun(
                    plan = plan,
                    remainingSeconds = plan.warmupMinutes * 60,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
        next
    }
}

internal suspend fun Controller.startRunClock() {
    val now = System.currentTimeMillis()
    edit { state ->
        val run = state.activeRun ?: return@edit state
        val remaining = if (run.started) displayRemaining(run, now) else phaseSeconds(run.plan, run.phaseIndex)
        state.copy(
            activeRun = run.copy(
                started = true,
                running = true,
                remainingSeconds = remaining,
                phaseEndsAt = now + remaining * 1000L,
                updatedAt = now,
            ),
        )
    }
}

internal suspend fun Controller.pauseRun() {
    val now = System.currentTimeMillis()
    edit { state ->
        val run = state.activeRun ?: return@edit state
        state.copy(
            activeRun = run.copy(
                running = false,
                remainingSeconds = displayRemaining(run, now),
                phaseEndsAt = null,
                updatedAt = now,
            ),
        )
    }
}

internal suspend fun Controller.resumeRun() {
    val now = System.currentTimeMillis()
    edit { state ->
        val run = state.activeRun ?: return@edit state
        if (!run.started) return@edit state
        state.copy(
            activeRun = run.copy(
                running = true,
                phaseEndsAt = now + run.remainingSeconds.coerceAtLeast(0) * 1000L,
                updatedAt = now,
            ),
        )
    }
}

internal suspend fun Controller.skipPhase() {
    val now = System.currentTimeMillis()
    var sound = false
    edit(sync = true) { state ->
        val run = state.activeRun ?: return@edit state
        if (!run.started) return@edit state
        val nextIndex = run.phaseIndex + 1
        if (nextIndex >= PHASES.size) {
            return@edit finishRun(state, run, now)
        }
        sound = true
        val seconds = phaseSeconds(run.plan, nextIndex)
        state.copy(
            activeRun = run.copy(
                phaseIndex = nextIndex,
                remainingSeconds = seconds,
                phaseEndsAt = if (run.running) now + seconds * 1000L else null,
                updatedAt = now,
            ),
        )
    }
    if (sound) playSound("phase")
}

internal suspend fun Controller.endRunEarly() {
    val now = System.currentTimeMillis()
    edit { state ->
        val run = state.activeRun ?: return@edit state
        if (PHASES.getOrNull(run.phaseIndex) != RunPhase.Run || run.runEndedEarly) return@edit state
        val remaining = displayRemaining(run, now)
        val logged = buildLoggedRunPlan(run.plan, run.phaseIndex, remaining)
        val cooldown = PHASES.indexOf(RunPhase.Cooldown)
        val seconds = phaseSeconds(run.plan, cooldown)
        state.copy(
            activeRun = run.copy(
                phaseIndex = cooldown,
                remainingSeconds = seconds,
                phaseEndsAt = if (run.running) now + seconds * 1000L else null,
                runEndedEarly = true,
                earlyLoggedPlan = logged,
                updatedAt = now,
            ),
        )
    }
    playSound("phase")
}

internal suspend fun Controller.discardRun() {
    edit(sync = true) { it.copy(activeRun = null, runCompletion = null) }
}

internal suspend fun Controller.acknowledgeRun() {
    edit(persist = false) { it.copy(runCompletion = null) }
}

internal suspend fun Controller.deleteRun(id: String) {
    edit(sync = true) { it.copy(runLog = it.runLog.filterNot { run -> run.id == id }) }
}

internal suspend fun Controller.deleteOldRuns() {
    val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
    edit(sync = true) { it.copy(runLog = it.runLog.filter { run -> run.completedAt >= cutoff }) }
}

internal suspend fun Controller.clearRuns() {
    edit(sync = true) { it.copy(runLog = emptyList()) }
}

internal suspend fun Controller.catchUpRun(now: Long) {
    val before = snapshot().activeRun ?: return
    var phaseChanged = false
    var finished = false
    edit(sync = true) { state ->
        val run = state.activeRun ?: return@edit state
        if (!run.running || run.phaseEndsAt == null || now < run.phaseEndsAt) return@edit state
        val projected = projectRun(run, now)
        if (projected.finished) {
            finished = true
            finishRun(state, run, now)
        } else {
            val next = projected.run ?: return@edit state
            phaseChanged = next.phaseIndex != run.phaseIndex
            state.copy(activeRun = next)
        }
    }
    if (phaseChanged) playSound("phase")
    if (finished) playSound("timer")
    if (before.phaseIndex != snapshot().activeRun?.phaseIndex && !finished && !phaseChanged) {
        playSound("phase")
    }
}

private fun finishRun(
    state: app.cinderblock.domain.AppState,
    run: ActiveRun,
    now: Long,
): app.cinderblock.domain.AppState {
    val log = completedLog(run, now).copy(date = LocalDate.now().toString())
    return state.copy(
        activeRun = null,
        runLog = listOf(log) + state.runLog,
        runCompletion = RunCompletion(log, run.runEndedEarly),
    )
}
