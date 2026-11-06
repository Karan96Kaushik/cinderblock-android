package app.cinderblock.data

import app.cinderblock.domain.ActionResult
import app.cinderblock.domain.MetricEntry
import app.cinderblock.domain.ProgramDocument
import app.cinderblock.domain.ProgramVersionRecord
import app.cinderblock.domain.UiEffect
import app.cinderblock.domain.bumpVersion
import app.cinderblock.domain.validateProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun Controller.saveMetric(entry: MetricEntry) {
    edit(sync = true) { state ->
        val stamped = entry.copy(updatedAt = System.currentTimeMillis())
        val next = state.bodyMetrics.filterNot { it.date == stamped.date } + stamped
        state.copy(bodyMetrics = next.sortedByDescending { it.date })
    }
}

internal suspend fun Controller.deleteMetric(date: String) {
    edit(sync = true) { it.copy(bodyMetrics = it.bodyMetrics.filterNot { entry -> entry.date == date }) }
}

internal suspend fun Controller.saveProgram(
    draft: ProgramDocument,
    note: String,
    source: String,
    goHome: Boolean = true,
): ActionResult {
    val state = snapshot()
    if (state.auth == null) return ActionResult.Error("You must be logged in to save changes")
    val issue = validateProgram(draft)
    if (issue != null) return ActionResult.Error(issue.message)
    val previousProgram = state.program
    val previousVersions = state.versions
    val previousStamp = state.planUpdatedAt
    val next = draft.copy(version = bumpVersion(state.program.version))
    val record = ProgramVersionRecord(
        id = newId(),
        programId = state.programId,
        version = next.version,
        program = next,
        source = source,
        note = note,
        createdAt = isoNow(),
    )
    edit(sync = false) {
        it.copy(
            program = next,
            versions = listOf(record) + it.versions,
            planUpdatedAt = System.currentTimeMillis(),
        )
    }
    return try {
        if (state.cloud.configured) {
            val session = ensureAuth()
            withContext(Dispatchers.IO) {
                cloud.upsertPlan(state.cloud, session, activePlanJson(state.programId, next, state.runningPlan))
                cloud.insertVersion(state.cloud, session, record)
            }
        }
        if (goHome) effect(UiEffect.Navigate("home"))
        ActionResult.Ok
    } catch (_: Exception) {
        edit(sync = false) {
            it.copy(program = previousProgram, versions = previousVersions, planUpdatedAt = previousStamp)
        }
        ActionResult.Error("Failed to save program. Please try again.")
    }
}

internal suspend fun Controller.toggleVersionHistory() {
    val opening = !snapshot().versionHistory.expanded
    if (!opening) {
        edit(persist = false) { it.copy(versionHistory = it.versionHistory.copy(expanded = false)) }
        return
    }
    val state = snapshot()
    if (state.auth == null || !state.cloud.configured) {
        edit(persist = false) {
            it.copy(versionHistory = it.versionHistory.copy(expanded = true, loading = false, rows = emptyList(), error = null))
        }
        return
    }
    edit(persist = false) {
        it.copy(versionHistory = it.versionHistory.copy(expanded = true, loading = true, error = null))
    }
    try {
        val session = ensureAuth()
        val remote = withContext(Dispatchers.IO) { cloud.listVersions(snapshot().cloud, session) }
        val merged = (remote + snapshot().versions).distinctBy { it.version }.sortedByDescending { it.createdAt }.take(20)
        edit(persist = false) {
            it.copy(versionHistory = it.versionHistory.copy(loading = false, rows = merged, error = null))
        }
    } catch (_: Exception) {
        edit(persist = false) {
            it.copy(
                versionHistory = it.versionHistory.copy(
                    loading = false,
                    error = "Failed to load version history",
                ),
            )
        }
    }
}

internal suspend fun Controller.revertVersion(record: ProgramVersionRecord): ActionResult {
    val result = saveProgram(record.program, "Reverted from v${record.version}", "revert", goHome = false)
    return if (result is ActionResult.Error && result.message == "Failed to save program. Please try again.") {
        ActionResult.Error("Failed to revert version")
    } else {
        result
    }
}
