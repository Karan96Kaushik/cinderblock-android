package app.cinderblock.ui.settings

import android.Manifest
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import app.cinderblock.CinderViewModel
import app.cinderblock.domain.ActionResult
import app.cinderblock.domain.REST_KEY
import app.cinderblock.domain.clampRestMinutes
import app.cinderblock.domain.dayStatus
import app.cinderblock.domain.daySummary
import app.cinderblock.domain.formatMediumDate
import app.cinderblock.domain.formatPlan
import app.cinderblock.domain.formatRunSummary
import app.cinderblock.domain.formatVersionLabel
import app.cinderblock.domain.parseDate
import app.cinderblock.domain.planTotalMinutes
import app.cinderblock.domain.settingsBadge
import app.cinderblock.domain.workoutKeys
import app.cinderblock.domain.workoutLabel
import app.cinderblock.ui.components.CardBlock
import app.cinderblock.ui.components.ConfirmBlock
import app.cinderblock.ui.components.DisplayText
import app.cinderblock.ui.components.FeatureBar
import app.cinderblock.ui.components.Mono
import app.cinderblock.ui.components.OutlineButton
import app.cinderblock.ui.components.Pill
import app.cinderblock.ui.components.PrimaryButton
import app.cinderblock.ui.components.SectionHeader
import app.cinderblock.ui.nav.goHome
import app.cinderblock.ui.theme.LocalColors
import app.cinderblock.ui.theme.colorsFor
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(nav: NavHostController, vm: CinderViewModel) {
    val state by vm.controller.state.collectAsState()
    val colors = LocalColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var notice by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var openWorkout by remember { mutableStateOf<String?>(null) }
    var openRun by remember { mutableStateOf<String?>(null) }
    var askedNotification by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        askedNotification = true
        notice = if (granted) "Notifications enabled." else "Notifications are blocked. Allow them in your device settings, then refresh."
    }
    LaunchedEffect(state.auth?.userId, state.cloud.configured) {
        if (state.auth != null) vm.controller.refreshCloudLists()
    }
    val settings = state.settings
    Column(Modifier.fillMaxSize().background(colors.background)) {
        FeatureBar("SETTINGS", { nav.goHome() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader("Program")
            CardBlock {
                DisplayText(state.program.name.ifBlank { "Program" }, size = 18.sp)
                Mono(formatVersionLabel(state.program.version))
                Mono("${state.program.workouts.size} workouts")
                Mono("To edit workouts, go to Training log → Explore → Edit.")
            }

            SectionHeader("Training")
            CardBlock {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Mono("Auto-start rest timer between sets", color = colors.foreground, modifier = Modifier.weight(1f))
                    Switch(settings.autoStartRestTimer, {
                        scope.launch { vm.controller.updateSettings { current -> current.copy(autoStartRestTimer = it) } }
                    })
                }
                Mono("Rest duration (minutes)")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({
                        scope.launch { vm.controller.updateSettings { it.copy(restTimerMinutes = clampRestMinutes(it.restTimerMinutes - 0.5)) } }
                    }) { Mono("−", color = colors.neonOrange, size = 20.sp) }
                    DisplayText(settings.restTimerMinutes.toString(), size = 18.sp)
                    TextButton({
                        scope.launch { vm.controller.updateSettings { it.copy(restTimerMinutes = clampRestMinutes(it.restTimerMinutes + 0.5)) } }
                    }) { Mono("+", color = colors.neonOrange, size = 20.sp) }
                }
                Mono("This value is a rest-timer preset, and the auto-start duration when that switch is on. The timer does not start after the final set.")
            }

            SectionHeader("Appearance")
            CardBlock {
                Mono("Theme")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("orange", "green", "blue").forEach { theme ->
                        ThemeSwatch(theme, settings.theme == theme) {
                            scope.launch { vm.controller.updateSettings { it.copy(theme = theme) } }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("white", "cream").forEach { theme ->
                        ThemeSwatch(theme, settings.theme == theme) {
                            scope.launch { vm.controller.updateSettings { it.copy(theme = theme) } }
                        }
                    }
                }
                Mono("Font size")
                ChoiceRow(listOf("sm", "md", "lg", "xl"), settings.fontSize) { value ->
                    scope.launch { vm.controller.updateSettings { it.copy(fontSize = value) } }
                }
                Mono("Font style")
                ChoiceRow(listOf("cinderblock", "readable", "mono"), settings.fontPreset) { value ->
                    scope.launch { vm.controller.updateSettings { it.copy(fontPreset = value) } }
                }
                OutlineButton("Reset appearance to defaults", { scope.launch { vm.controller.resetAppearance() } })
                DisplayText("Preview heading", size = 18.sp)
                Mono("Secondary caption")
                Mono("Accent text — MARK DONE · 3 sets × 8 reps", color = colors.neonOrange)
            }

            SectionHeader("Sounds")
            CardBlock {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Mono("Sounds", color = colors.foreground)
                    Switch(settings.soundEnabled, {
                        scope.launch { vm.controller.updateSettings { current -> current.copy(soundEnabled = it) } }
                    })
                }
                val percent = (settings.soundVolume * 100).roundToInt()
                Mono("Volume $percent%")
                Slider(
                    value = percent.toFloat(),
                    onValueChange = { raw ->
                        val snapped = (raw / 5f).roundToInt() * 5
                        scope.launch { vm.controller.updateSettings { it.copy(soundVolume = snapped / 100.0) } }
                    },
                    valueRange = 0f..100f,
                    steps = 19,
                    enabled = settings.soundEnabled,
                )
                OutlineButton("Preview timer sound", { vm.controller.playSound("timer") })
            }

            SectionHeader("Notifications")
            val status = notificationStatus(context, askedNotification)
            CardBlock {
                Mono(status, color = colors.foreground, size = 14.sp)
                Mono("Alerts for workout reminders. Permission is granted by the device.")
                notice?.let { Mono(it, color = if (it.startsWith("Notifications enabled")) colors.neonOrange else colors.neonYellow) }
                if (status == "Ready to enable") {
                    PrimaryButton("Enable notifications", {
                        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else notice = "Notifications enabled."
                    })
                }
                if (status == "Blocked") {
                    Mono("Notifications are blocked. Allow them in your device settings, then refresh.")
                }
                if (status == "Enabled") {
                    OutlineButton("Send test notification", {
                        notice = vm.controller.postTestNotification(context) ?: "Test notification sent."
                    })
                }
            }

            SectionHeader("Amplify")
            CardBlock {
                val signedIn = state.auth != null
                val deployed = state.cloud.testFunctionUrl.isNotBlank()
                PrimaryButton(
                    "Server test",
                    {
                        scope.launch {
                            when (val result = vm.controller.serverTest()) {
                                is ActionResult.Info -> notice = result.message
                                is ActionResult.Error -> notice = result.message
                                ActionResult.Ok -> Unit
                            }
                        }
                    },
                    enabled = signedIn && deployed,
                )
                Mono(
                    when {
                        !signedIn -> "Sign in to run the server test."
                        !deployed -> "Deploy the Amplify sandbox so the test function URL is set."
                        else -> "Calls the Amplify test function with your Supabase session."
                    },
                )
            }

            SectionHeader("Backup")
            CardBlock {
                PrimaryButton("Download training logs", {
                    val (name, json, summary) = vm.controller.exportBackup()
                    notice = "$summary ${saveJson(context, name, json)}"
                })
                if (state.auth == null || !state.cloud.configured) {
                    Mono("Sign in with Supabase to upload and restore cloud backups, and import training logs from your other devices.")
                } else {
                    Mono("Cloud backups · this device + account")
                    Mono(state.deviceId.take(8), color = colors.foreground)
                    PrimaryButton("Upload current data", {
                        scope.launch {
                            when (val result = vm.controller.uploadBackup()) {
                                is ActionResult.Error -> notice = result.message
                                else -> notice = "Backup uploaded."
                            }
                        }
                    })
                    if (state.cloudListsLoading && state.backups.isEmpty()) Mono("Loading cloud backups…")
                    if (!state.cloudListsLoading && state.backups.isEmpty()) Mono("No cloud backups yet.")
                    state.backups.forEach { backup ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Mono(backup.label, color = colors.foreground)
                            Mono("${backup.createdAt} · ${backup.gymDays} gym days · ${backup.runs} runs")
                            if (confirm == "restore:${backup.id}") {
                                ConfirmBlock("Restore this backup over local data?", "Restore", {
                                    scope.launch {
                                        val result = vm.controller.restoreBackup(backup.id)
                                        confirm = null
                                        if (result is ActionResult.Error) notice = result.message
                                    }
                                }, { confirm = null })
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.weight(1f)) { OutlineButton("Restore", { confirm = "restore:${backup.id}" }) }
                                    Box(Modifier.weight(1f)) {
                                        OutlineButton("Delete", { scope.launch { vm.controller.deleteBackup(backup.id) } }, color = colors.destructive)
                                    }
                                }
                            }
                        }
                    }
                    DisplayText("Import from another device", size = 16.sp)
                    val others = state.devices.filter { it.deviceId != state.deviceId }
                    if (others.isEmpty()) Mono("No other devices with training logs on this account yet.")
                    others.forEach { device ->
                        Mono("${device.deviceId.take(8)} · ${device.updatedAt}")
                        Mono("${device.gymDays} gym days · ${device.runs} runs · ${device.metrics} metrics")
                        if (confirm == "import:${device.deviceId}") {
                            ConfirmBlock("Merge this device’s logs into this device?", "Import & merge", {
                                scope.launch {
                                    val result = vm.controller.importDevice(device.deviceId)
                                    confirm = null
                                    if (result is ActionResult.Error) notice = result.message
                                }
                            }, { confirm = null })
                        } else {
                            OutlineButton("Import & merge", { confirm = "import:${device.deviceId}" })
                        }
                    }
                }
            }

            SectionHeader("Workout history")
            if (state.gymLog.isEmpty()) {
                Mono("No workouts logged yet.")
            }
            state.gymLog.entries.sortedByDescending { it.key }.forEach { (date, log) ->
                val status = dayStatus(log)
                CardBlock {
                    Row(Modifier.fillMaxWidth().clickable { openWorkout = if (openWorkout == date) null else date }, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Mono(parseDate(date)?.let { formatMediumDate(it) } ?: date, color = colors.foreground)
                            Mono(daySummary(state.program, log))
                        }
                        Pill(settingsBadge(status), colors.neonOrange)
                    }
                    if (openWorkout == date) {
                        Mono("Change workout")
                        (workoutKeys(state.program) + REST_KEY).forEach { key ->
                            TextButton({ scope.launch { vm.controller.selectWorkout(date, key) } }) {
                                Mono(workoutLabel(state.program, key), color = if (log.workoutKey == key) colors.neonOrange else colors.foreground)
                            }
                        }
                        TextButton({ scope.launch { vm.controller.deleteDay(date) } }) { Mono("Remove this entry", color = colors.destructive) }
                    }
                }
            }
            if (confirm == "old-workouts") {
                ConfirmBlock("Remove workouts older than 30 days?", "Remove", {
                    scope.launch { vm.controller.deleteOldWorkouts() }
                    confirm = null
                }, { confirm = null })
            } else {
                OutlineButton("Remove workouts older than 30 days", { confirm = "old-workouts" })
            }
            if (confirm == "all-workouts") {
                ConfirmBlock("Clear all workout history?", "Clear all", {
                    scope.launch { vm.controller.clearWorkouts() }
                    confirm = null
                }, { confirm = null })
            } else {
                OutlineButton("Clear all workout history", { confirm = "all-workouts" }, color = colors.destructive)
            }

            SectionHeader("Run history")
            if (state.runLog.isEmpty()) Mono("No runs logged yet.")
            Mono("Deleting a run removes it from Home and the training log.")
            state.runLog.sortedByDescending { it.completedAt }.forEach { run ->
                CardBlock {
                    Row(Modifier.fillMaxWidth().clickable { openRun = if (openRun == run.id) null else run.id }, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Mono(parseDate(run.date)?.let { formatMediumDate(it) } ?: run.date, color = colors.foreground)
                            Mono(formatRunSummary(run))
                            Mono("${planTotalMinutes(run.plan)} min · ${clock(run.completedAt)}")
                        }
                        Pill("Run", colors.neonAmber)
                    }
                    if (openRun == run.id) {
                        Mono("Warmup ${run.plan.warmupMinutes} min")
                        Mono("Run ${run.plan.runMinutes} min · ${formatPlan(run.plan)}")
                        Mono("Cooldown ${run.plan.cooldownMinutes} min")
                        TextButton({ scope.launch { vm.controller.deleteRun(run.id) } }) { Mono("Remove this run", color = colors.destructive) }
                    }
                }
            }
            if (confirm == "old-runs") {
                ConfirmBlock("Remove runs older than 30 days?", "Remove", {
                    scope.launch { vm.controller.deleteOldRuns() }
                    confirm = null
                }, { confirm = null })
            } else {
                OutlineButton("Remove runs older than 30 days", { confirm = "old-runs" })
            }
            if (confirm == "all-runs") {
                ConfirmBlock("Clear all run history?", "Clear all", {
                    scope.launch { vm.controller.clearRuns() }
                    confirm = null
                }, { confirm = null })
            } else {
                OutlineButton("Clear all run history", { confirm = "all-runs" }, color = colors.destructive)
            }
        }
    }
}

@Composable
private fun ThemeSwatch(theme: String, selected: Boolean, onClick: () -> Unit) {
    val palette = colorsFor(theme)
    val colors = LocalColors.current
    Box(
        Modifier
            .size(36.dp)
            .background(palette.background, RoundedCornerShape(8.dp))
            .border(2.dp, if (selected) colors.neonOrange else palette.neonOrange, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    )
}

@Composable
private fun ChoiceRow(options: List<String>, selected: String, onPick: (String) -> Unit) {
    val colors = LocalColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            TextButton({ onPick(option) }) {
                Mono(option, color = if (option == selected) colors.neonOrange else colors.mutedForeground)
            }
        }
    }
}

private fun notificationStatus(context: Context, asked: Boolean): String {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return "Not supported"
    if (!manager.areNotificationsEnabled()) return "Blocked"
    if (Build.VERSION.SDK_INT >= 33) {
        val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) return if (asked) "Blocked" else "Ready to enable"
    }
    return "Enabled"
}

private fun clock(epochMs: Long): String =
    DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMs))

private fun saveJson(context: Context, name: String, json: String): String = try {
    if (Build.VERSION.SDK_INT >= 29) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return shareJson(context, name, json)
        resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        "Saved to Downloads."
    } else {
        shareJson(context, name, json)
    }
} catch (_: Exception) {
    "Could not save the file."
}

private fun shareJson(context: Context, name: String, json: String): String {
    val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
    val file = File(dir, name)
    file.writeText(json)
    val uri = runCatching { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }.getOrNull()
    if (uri != null) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    return "Saved ${file.absolutePath}"
}
