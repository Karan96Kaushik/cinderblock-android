package app.cinderblock.data

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import app.cinderblock.R
import app.cinderblock.domain.ActionResult
import app.cinderblock.domain.TrainingBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal fun Controller.exportBackup(): Triple<String, String, String> {
    val state = snapshot()
    val backup = TrainingBackup(
        version = 2,
        exportedAt = isoNow(),
        settings = state.settings,
        defaultRunningPlan = state.runningPlan,
        gymLog = state.gymLog,
        runLog = state.runLog,
        bodyMetrics = state.bodyMetrics,
    )
    val name = "cinderblock-training-logs-${java.time.LocalDate.now()}.json"
    val body = AppJson.encodeToString(TrainingBackup.serializer(), backup)
    val summary = "Downloaded ${state.gymLog.size} gym days, ${state.runLog.size} runs, ${state.bodyMetrics.size} metrics."
    return Triple(name, body, summary)
}

internal suspend fun Controller.refreshCloudLists() {
    val state = snapshot()
    val session = state.auth ?: return
    if (!state.cloud.configured) return
    edit(persist = false) { it.copy(cloudListsLoading = true) }
    try {
        val auth = ensureAuth()
        val backups = withContext(Dispatchers.IO) { cloud.listBackups(state.cloud, auth, state.deviceId) }
        val devices = withContext(Dispatchers.IO) { cloud.listDevices(state.cloud, auth) }
        edit(persist = false) { it.copy(backups = backups, devices = devices, cloudListsLoading = false) }
    } catch (error: Exception) {
        edit(persist = false) {
            it.copy(cloudListsLoading = false, syncError = cloudMessage(error, "Could not load cloud backups"))
        }
    }
}

internal suspend fun Controller.uploadBackup(): ActionResult {
    val state = snapshot()
    if (!accountOk(state, state.auth?.userId.orEmpty())) {
        return ActionResult.Error("Local data belongs to a different account. Sign out, clear local data, or continue only after switching back.")
    }
    return try {
        val session = ensureAuth()
        val backup = TrainingBackup(
            exportedAt = isoNow(),
            settings = state.settings,
            defaultRunningPlan = state.runningPlan,
            gymLog = state.gymLog,
            runLog = state.runLog,
            bodyMetrics = state.bodyMetrics,
        )
        val label = "Backup " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        val summary = withContext(Dispatchers.IO) {
            cloud.uploadBackup(state.cloud, session, state.deviceId, backup, label)
        }
        edit(persist = false) { it.copy(backups = listOf(summary) + it.backups) }
        ActionResult.Ok
    } catch (error: Exception) {
        ActionResult.Error(cloudMessage(error, "Upload failed"))
    }
}

internal suspend fun Controller.restoreBackup(id: String): ActionResult {
    return try {
        val session = ensureAuth()
        val backup = withContext(Dispatchers.IO) { cloud.fetchBackup(snapshot().cloud, session, id) }
        edit(sync = true) { state ->
            state.copy(
                settings = backup.settings,
                runningPlan = backup.defaultRunningPlan,
                gymLog = backup.gymLog,
                runLog = backup.runLog,
                bodyMetrics = backup.bodyMetrics,
                settingsUpdatedAt = System.currentTimeMillis(),
                planUpdatedAt = System.currentTimeMillis(),
            )
        }
        ActionResult.Ok
    } catch (error: Exception) {
        ActionResult.Error(cloudMessage(error, "Restore failed"))
    }
}

internal suspend fun Controller.deleteBackup(id: String): ActionResult {
    return try {
        val session = ensureAuth()
        withContext(Dispatchers.IO) { cloud.deleteBackup(snapshot().cloud, session, id) }
        edit(persist = false) { it.copy(backups = it.backups.filterNot { backup -> backup.id == id }) }
        ActionResult.Ok
    } catch (error: Exception) {
        ActionResult.Error(cloudMessage(error, "Could not delete the backup"))
    }
}

internal suspend fun Controller.importDevice(deviceId: String): ActionResult {
    val state = snapshot()
    if (deviceId == state.deviceId) return ActionResult.Error("That is already this device.")
    return try {
        val session = ensureAuth()
        if (!accountOk(state, session.userId)) {
            return ActionResult.Error("Local data belongs to a different account. Sign out, clear local data, or continue only after switching back.")
        }
        val remote = withContext(Dispatchers.IO) { cloud.fetchLogs(state.cloud, session, deviceId) }
            ?: return ActionResult.Error("No training logs found for that device.")
        edit(sync = true) { current ->
            current.copy(
                gymLog = app.cinderblock.domain.mergeGym(current.gymLog, remote.gymLog),
                runLog = app.cinderblock.domain.mergeRuns(current.runLog, remote.runLog),
                bodyMetrics = app.cinderblock.domain.mergeMetrics(current.bodyMetrics, remote.bodyMetrics),
            )
        }
        pushAll(quiet = true)
        ActionResult.Ok
    } catch (error: Exception) {
        ActionResult.Error(cloudMessage(error, "Import failed"))
    }
}

internal suspend fun Controller.serverTest(): ActionResult {
    val state = snapshot()
    val session = state.auth ?: return ActionResult.Error("Sign in to run the server test.")
    if (state.cloud.testFunctionUrl.isBlank()) return ActionResult.Error("Deploy the sandbox so the test function URL is set.")
    return try {
        val auth = ensureAuth()
        val body = withContext(Dispatchers.IO) { cloud.testFunction(state.cloud, auth) }
        val settings = runCatching { withContext(Dispatchers.IO) { cloud.fetchSettings(state.cloud, auth) } }.getOrNull()
        val row = if (settings != null) "A settings row exists." else "No settings row yet."
        ActionResult.Info("Signed in as ${session.email}. $row ${body.take(180)}")
    } catch (error: Exception) {
        ActionResult.Error(cloudMessage(error, "Server test failed"))
    }
}

internal fun Controller.postTestNotification(appContext: Context): String? {
    val manager = appContext.getSystemService(NotificationManager::class.java)
        ?: return "Notifications are not supported on this device."
    if (Build.VERSION.SDK_INT >= 33) {
        val granted = appContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) return "Notification permission is missing. Allow it in device settings."
    }
    val notification = Notification.Builder(appContext, "cinderblock")
        .setSmallIcon(R.drawable.ic_stat)
        .setContentTitle("CINDERBLOCK")
        .setContentText("Test notification — your alerts are working.")
        .build()
    manager.notify(42, notification)
    return null
}
