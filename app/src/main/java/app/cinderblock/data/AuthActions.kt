package app.cinderblock.data

import app.cinderblock.domain.ActionResult
import app.cinderblock.domain.AppSettings
import app.cinderblock.domain.AuthSession
import app.cinderblock.domain.SyncPhase
import app.cinderblock.domain.TrainingLogPayload
import app.cinderblock.domain.UiEffect
import app.cinderblock.domain.clampRestMinutes
import app.cinderblock.domain.mergeGym
import app.cinderblock.domain.mergeMetrics
import app.cinderblock.domain.mergeRuns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

internal suspend fun Controller.signIn(email: String, password: String): ActionResult =
    authCall(email, password) { config, mail, pass -> cloud.signIn(config, mail, pass) }

internal suspend fun Controller.register(email: String, password: String): ActionResult {
    val invalid = passwordProblem(password) 
    if (invalid != null) return invalid
    if (email.isBlank()) return ActionResult.Error("Enter an email.")
    val state = snapshot()
    if (!state.cloud.configured) return ActionResult.Error("Cloud sync is not configured.")
    edit(persist = false) { it.copy(authBusy = true) }
    return try {
        val session = withContext(Dispatchers.IO) { cloud.signUp(state.cloud, email.trim(), password) }
        if (session == null) {
            edit(persist = false) { it.copy(authBusy = false) }
            ActionResult.Info("Check your email to confirm the account.")
        } else {
            finishSignIn(session)
            ActionResult.Ok
        }
    } catch (error: Exception) {
        edit(persist = false) { it.copy(authBusy = false) }
        ActionResult.Error(cloudMessage(error, "Could not create the account"))
    }
}

internal suspend fun Controller.sendReset(email: String): ActionResult {
    if (email.isBlank()) return ActionResult.Error("Enter an email.")
    val state = snapshot()
    if (!state.cloud.configured) return ActionResult.Error("Cloud sync is not configured.")
    edit(persist = false) { it.copy(authBusy = true) }
    return try {
        withContext(Dispatchers.IO) { cloud.recover(state.cloud, email.trim()) }
        edit(persist = false) { it.copy(authBusy = false) }
        ActionResult.Info("Check your email.")
    } catch (error: Exception) {
        edit(persist = false) { it.copy(authBusy = false) }
        ActionResult.Error(cloudMessage(error, "Could not send the reset link"))
    }
}

internal suspend fun Controller.updatePassword(password: String, confirm: String): ActionResult {
    if (password != confirm) return ActionResult.Error("Passwords do not match")
    val invalid = passwordProblem(password)
    if (invalid != null) return invalid
    val token = snapshot().recoveryAccessToken ?: return ActionResult.Error("Open the recovery link again.")
    edit(persist = false) { it.copy(authBusy = true) }
    return try {
        withContext(Dispatchers.IO) { cloud.updatePassword(snapshot().cloud, token, password) }
        edit { it.copy(authBusy = false, recoveryAccessToken = null) }
        effect(UiEffect.Navigate("home"))
        ActionResult.Ok
    } catch (error: Exception) {
        edit(persist = false) { it.copy(authBusy = false) }
        ActionResult.Error(cloudMessage(error, "Could not update the password"))
    }
}

internal suspend fun Controller.signOut() {
    val state = snapshot()
    val session = state.auth
    if (session != null && state.cloud.configured) {
        runCatching {
            withContext(Dispatchers.IO) {
                httpRequest(
                    state.cloud.supabaseUrl.trimEnd('/') + "/auth/v1/logout",
                    "POST",
                    "{}",
                    mapOf(
                        "apikey" to state.cloud.supabaseAnonKey,
                        "Authorization" to "Bearer ${session.accessToken}",
                        "Content-Type" to "application/json",
                    ),
                )
            }
        }
    }
    edit { it.copy(auth = null, syncPhase = SyncPhase.Idle, syncError = null) }
}

internal suspend fun Controller.skipLogin() {
    edit(persist = false) { it.copy(skipLogin = true) }
}

internal suspend fun Controller.clearSkip() {
    edit(persist = false) { it.copy(skipLogin = false) }
}

internal suspend fun Controller.setRecovery(token: String) {
    edit(persist = false) { it.copy(recoveryAccessToken = token, skipLogin = false) }
}

internal suspend fun Controller.syncNow(): ActionResult {
    val state = snapshot()
    val session = state.auth ?: return ActionResult.Error("Sign in to sync.")
    if (!accountOk(state, session.userId)) {
        return ActionResult.Error("Local data belongs to a different account. Sign out, clear local data, or continue only after switching back.")
    }
    edit(persist = false) { it.copy(syncPhase = SyncPhase.Syncing, syncError = null) }
    return try {
        val auth = ensureAuth()
        pullAndPush(auth)
        edit {
            it.copy(
                syncPhase = SyncPhase.Idle,
                syncError = null,
                boundAccountId = it.boundAccountId ?: auth.userId,
            )
        }
        ActionResult.Ok
    } catch (error: Exception) {
        val message = cloudMessage(error, "Sync failed")
        edit(persist = false) { it.copy(syncPhase = SyncPhase.Error, syncError = message) }
        ActionResult.Error(message)
    }
}

internal suspend fun Controller.pushAll(quiet: Boolean) {
    val state = snapshot()
    val session = state.auth ?: return
    if (!state.cloud.configured || !accountOk(state, session.userId)) return
    try {
        val auth = ensureAuth()
        withContext(Dispatchers.IO) {
            cloud.upsertSettings(state.cloud, auth, state.settings)
            cloud.upsertPlan(state.cloud, auth, activePlanJson(state.programId, state.program, state.runningPlan))
            cloud.upsertLogs(state.cloud, auth, state.deviceId, state.toPayload())
        }
        if (snapshot().syncPhase == SyncPhase.Error) {
            edit(persist = false) { it.copy(syncPhase = SyncPhase.Idle, syncError = null) }
        }
    } catch (error: Exception) {
        if (!quiet || snapshot().syncPhase != SyncPhase.Syncing) {
            edit(persist = false) {
                it.copy(syncPhase = SyncPhase.Error, syncError = cloudMessage(error, "Sync failed"))
            }
        }
    }
}

private suspend fun Controller.pullAndPush(session: AuthSession) {
    val state = snapshot()
    val remoteSettings = withContext(Dispatchers.IO) { cloud.fetchSettings(state.cloud, session) }
    val remotePlan = withContext(Dispatchers.IO) { cloud.fetchPlan(state.cloud, session) }
    val remoteLogs = withContext(Dispatchers.IO) { cloud.fetchLogs(state.cloud, session, state.deviceId) }
    edit(sync = false) { current ->
        var next = current
        val settingsStamp = remoteSettings?.second?.let(::isoToEpoch) ?: 0L
        if (remoteSettings != null && settingsStamp > current.settingsUpdatedAt) {
            next = next.copy(settings = remoteSettings.first, settingsUpdatedAt = settingsStamp)
        }
        val planStamp = remotePlan?.second?.let(::isoToEpoch) ?: 0L
        val decoded = remotePlan?.first?.let(::decodePlan)
        if (decoded != null && planStamp > current.planUpdatedAt) {
            next = next.copy(
                programId = decoded.first,
                program = decoded.second,
                runningPlan = decoded.third,
                planUpdatedAt = planStamp,
            )
        }
        if (remoteLogs != null) {
            next = next.copy(
                gymLog = mergeGym(current.gymLog, remoteLogs.gymLog),
                runLog = mergeRuns(current.runLog, remoteLogs.runLog),
                bodyMetrics = mergeMetrics(current.bodyMetrics, remoteLogs.bodyMetrics),
            )
        }
        next
    }
    val fresh = snapshot()
    withContext(Dispatchers.IO) {
        cloud.upsertSettings(fresh.cloud, session, fresh.settings)
        cloud.upsertPlan(fresh.cloud, session, activePlanJson(fresh.programId, fresh.program, fresh.runningPlan))
        cloud.upsertLogs(fresh.cloud, session, fresh.deviceId, fresh.toPayload())
    }
}

private suspend fun Controller.authCall(
    email: String,
    password: String,
    block: (app.cinderblock.domain.CloudConfig, String, String) -> AuthSession,
): ActionResult {
    val invalid = passwordProblem(password)
    if (invalid != null) return invalid
    if (email.isBlank()) return ActionResult.Error("Enter an email.")
    val state = snapshot()
    if (!state.cloud.configured) return ActionResult.Error("Cloud sync is not configured.")
    edit(persist = false) { it.copy(authBusy = true) }
    return try {
        val session = withContext(Dispatchers.IO) { block(state.cloud, email.trim(), password) }
        finishSignIn(session)
        ActionResult.Ok
    } catch (error: Exception) {
        edit(persist = false) { it.copy(authBusy = false) }
        ActionResult.Error(cloudMessage(error, "Sign in failed"))
    }
}

private suspend fun Controller.finishSignIn(session: AuthSession) {
    val bound = snapshot().boundAccountId
    edit {
        it.copy(
            auth = session,
            authBusy = false,
            skipLogin = false,
            recoveryAccessToken = null,
            boundAccountId = bound ?: session.userId,
        )
    }
    if (accountOk(snapshot(), session.userId)) {
        runCatching { syncNow() }
    } else {
        edit(persist = false) {
            it.copy(
                syncPhase = SyncPhase.Error,
                syncError = "Local data belongs to a different account. Sign out, clear local data, or continue only after switching back.",
            )
        }
    }
    effect(UiEffect.Navigate("home"))
}

internal suspend fun Controller.updateSettings(block: (AppSettings) -> AppSettings) {
    edit(sync = true) { state ->
        val updated = block(state.settings)
        val next = updated.copy(restTimerMinutes = clampRestMinutes(updated.restTimerMinutes))
        state.copy(settings = next, settingsUpdatedAt = System.currentTimeMillis())
    }
}

internal suspend fun Controller.resetAppearance() {
    edit(sync = true) { state ->
        state.copy(
            settings = state.settings.copy(theme = "orange", fontSize = "md", fontPreset = "cinderblock"),
            settingsUpdatedAt = System.currentTimeMillis(),
        )
    }
}

private fun passwordProblem(password: String): ActionResult? =
    if (password.length < 8) ActionResult.Error("Password must be at least 8 characters.") else null

internal fun cloudMessage(error: Exception, fallback: String): String = when (error) {
    is HttpException -> errorText(error.message ?: "", fallback)
    else -> error.message?.ifBlank { fallback } ?: fallback
}

internal fun isoToEpoch(value: String): Long = try {
    Instant.parse(value).toEpochMilli()
} catch (_: Exception) {
    0L
}

internal fun app.cinderblock.domain.AppState.toPayload() = TrainingLogPayload(
    gymLog = gymLog,
    runLog = runLog,
    bodyMetrics = bodyMetrics,
    settings = settings,
    defaultRunningPlan = runningPlan,
    exportedAt = isoNow(),
    version = 2,
)
