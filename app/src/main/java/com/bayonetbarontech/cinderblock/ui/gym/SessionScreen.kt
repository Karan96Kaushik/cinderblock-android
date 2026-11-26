package com.bayonetbarontech.cinderblock.ui.gym

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.bayonetbarontech.cinderblock.CinderViewModel
import com.bayonetbarontech.cinderblock.data.adjustRest
import com.bayonetbarontech.cinderblock.data.ensureSets
import com.bayonetbarontech.cinderblock.data.focusExercise
import com.bayonetbarontech.cinderblock.data.focusSet
import com.bayonetbarontech.cinderblock.data.markExercise
import com.bayonetbarontech.cinderblock.data.pauseRest
import com.bayonetbarontech.cinderblock.data.resetRest
import com.bayonetbarontech.cinderblock.data.resumeRest
import com.bayonetbarontech.cinderblock.data.startRest
import com.bayonetbarontech.cinderblock.data.updateSet
import com.bayonetbarontech.cinderblock.data.updateSettings
import com.bayonetbarontech.cinderblock.domain.DayLog
import com.bayonetbarontech.cinderblock.domain.HapticKind
import com.bayonetbarontech.cinderblock.domain.ProgramExercise
import com.bayonetbarontech.cinderblock.domain.ProgramWorkout
import com.bayonetbarontech.cinderblock.domain.REST_KEY
import com.bayonetbarontech.cinderblock.domain.exerciseStatus
import com.bayonetbarontech.cinderblock.domain.formatClock
import com.bayonetbarontech.cinderblock.domain.formatLongDate
import com.bayonetbarontech.cinderblock.domain.formatMediumDate
import com.bayonetbarontech.cinderblock.domain.formatOneSet
import com.bayonetbarontech.cinderblock.domain.formatSetSummary
import com.bayonetbarontech.cinderblock.domain.isAddressed
import com.bayonetbarontech.cinderblock.domain.isProgramKey
import com.bayonetbarontech.cinderblock.domain.isTimedHold
import com.bayonetbarontech.cinderblock.domain.lastExerciseRecord
import com.bayonetbarontech.cinderblock.domain.loggedSetCount
import com.bayonetbarontech.cinderblock.domain.logLabel
import com.bayonetbarontech.cinderblock.domain.parseDate
import com.bayonetbarontech.cinderblock.domain.resolveProgram
import com.bayonetbarontech.cinderblock.ui.components.CardBlock
import com.bayonetbarontech.cinderblock.ui.components.DisplayText
import com.bayonetbarontech.cinderblock.ui.components.FeatureBar
import com.bayonetbarontech.cinderblock.ui.components.Mono
import com.bayonetbarontech.cinderblock.ui.components.OutlineButton
import com.bayonetbarontech.cinderblock.ui.components.Pill
import com.bayonetbarontech.cinderblock.ui.components.PrimaryButton
import com.bayonetbarontech.cinderblock.ui.components.PulseDot
import com.bayonetbarontech.cinderblock.ui.components.rememberNow
import com.bayonetbarontech.cinderblock.ui.nav.Routes
import com.bayonetbarontech.cinderblock.ui.nav.goHome
import com.bayonetbarontech.cinderblock.ui.nav.openFromHome
import com.bayonetbarontech.cinderblock.ui.theme.LocalColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionScreen(date: String, exercise: String?, nav: NavHostController, vm: CinderViewModel) {
    val parsed = parseDate(date)
    val state by vm.controller.state.collectAsState()
    val log = state.gymLog[date]
    val invalid = parsed == null || log == null || log.workoutKey == REST_KEY || !isProgramKey(state.program, log.workoutKey)
    LaunchedEffect(date, log?.workoutKey, invalid) {
        if (parsed == null) {
            nav.navigate(Routes.gym(null)) { launchSingleTop = true }
        } else if (invalid) {
            nav.navigate(Routes.select(date)) { launchSingleTop = true }
        } else {
            vm.controller.ensureSets(date)
        }
    }
    if (!invalid && parsed != null && log != null) {
    val (program, _) = resolveProgram(state, log)
    val workout = program.workouts[log.workoutKey]
    if (workout != null && workout.exercises.isNotEmpty()) {
    val colors = LocalColors.current
    val scope = rememberCoroutineScope()
    val opening = remember(date, exercise) {
        val preferred = exercise?.takeIf { it.isNotBlank() } ?: state.sessionExercise[date]
        val found = workout.exercises.indexOfFirst { it.name == preferred }
        if (found >= 0) found else workout.exercises.indexOfFirst { !isAddressed(log.exercises[it.name]) }.coerceAtLeast(0)
    }
    val pager = rememberPagerState(initialPage = opening) { workout.exercises.size.coerceAtLeast(1) }
    val context = LocalContext.current
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(exercise) {
        if (exercise.isNullOrBlank()) return@LaunchedEffect
        val index = workout.exercises.indexOfFirst { it.name == exercise }
        if (index >= 0 && index != pager.currentPage) pager.scrollToPage(index)
    }
    LaunchedEffect(pager.currentPage) {
        workout.exercises.getOrNull(pager.currentPage)?.name?.let { name ->
            vm.controller.focusExercise(date, name)
        }
        if (!armed) {
            armed = true
            return@LaunchedEffect
        }
        vm.controller.playSound("change")
    }
    val completed = workout.exercises.count { isAddressed(log.exercises[it.name]) && log.exercises[it.name]?.completed == true }
    val skipped = workout.exercises.count { log.exercises[it.name]?.skipped == true }
    val allAddressed = workout.exercises.all { isAddressed(log.exercises[it.name]) }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        FeatureBar("TRAINING", { nav.goHome() }, date.substring(5))
        Column(Modifier.padding(horizontal = 16.dp)) {
            TextButton({ nav.navigate(Routes.select(date)) }) { Mono("Change workout", color = colors.neonOrange) }
            DisplayText(logLabel(program, log), size = 22.sp)
            Mono(formatLongDate(parsed))
            Mono("Exercise ${pager.currentPage + 1} of ${workout.exercises.size}")
            Mono("$completed completed · $skipped skipped")
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            workout.exercises.forEachIndexed { index, exercise ->
                val item = log.exercises[exercise.name]
                val current = index == pager.currentPage
                val color = when {
                    current -> colors.neonOrange
                    item?.completed == true -> colors.neonOrange
                    item?.skipped == true -> colors.mutedForeground
                    else -> colors.border
                }
                Box(
                    Modifier
                        .width(if (current) 22.dp else 12.dp)
                        .size(height = 12.dp, width = if (current) 22.dp else 12.dp)
                        .border(1.dp, color, CircleShape)
                        .background(if (item?.completed == true || current) color else colors.background, CircleShape)
                        .clickable { scope.launch { pager.animateScrollToPage(index) } },
                )
            }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            val exercise = workout.exercises[page]
            val item = log.exercises[exercise.name]
            val timed = isTimedHold(exercise)
            val addressed = isAddressed(item)
            val status = exerciseStatus(item)
            val planned = exercise.sets
            val last = lastExerciseRecord(state.gymLog, exercise.name, date)
            val priorSets = last?.second?.sets.orEmpty()
            var notesOpen by remember(exercise.name) { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DisplayText(
                        exercise.name,
                        size = 20.sp,
                        color = if (status == com.bayonetbarontech.cinderblock.domain.ExerciseStatus.Skipped) colors.mutedForeground else if (status == com.bayonetbarontech.cinderblock.domain.ExerciseStatus.Done) colors.neonOrange else colors.foreground,
                    )
                    if (item?.completed == true) Pill("DONE", colors.neonOrange)
                    if (item?.skipped == true) Pill("SKIPPED", colors.mutedForeground)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("${exercise.sets} sets", colors.neonOrange)
                    Pill(exercise.reps ?: exercise.duration ?: "—", colors.neonAmber)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.videos[exercise.name]?.let { link ->
                        Box(Modifier.weight(1f)) { OutlineButton("Reference", { openLink(context, link) }) }
                    }
                    exercise.refVideo?.let { link ->
                        Box(Modifier.weight(1f)) { OutlineButton("Video", { openLink(context, link) }) }
                    }
                }
                item?.let {
                    Mono("This session: Set ${loggedSetCount(it, timed)} of $planned logged", color = colors.foreground)
                }
                last?.let { (lastDate, lastLog) ->
                    val whenLabel = parseDate(lastDate)?.let { formatMediumDate(it) } ?: lastDate
                    val summary = formatSetSummary(lastLog)
                    Mono(if (summary.isNullOrBlank()) "Last session · $whenLabel" else "Last session · $whenLabel · $summary")
                }
                DisplayText("Log sets", size = 14.sp)
                if (item != null) {
                    item.sets.forEachIndexed { index, set ->
                        val prior = priorSets.getOrNull(index)?.let(::formatOneSet)
                        Mono(if (prior == null) "Set ${index + 1}" else "Set ${index + 1} · last $prior")
                        val onFocus = {
                            scope.launch { vm.controller.focusSet(date, exercise.name, index) }
                            Unit
                        }
                        if (timed) {
                            SetEntryField(
                                set.seconds,
                                "Seconds held",
                                !addressed,
                                { scope.launch { vm.controller.updateSet(date, exercise.name, index, seconds = it) } },
                                onFocus,
                                Modifier.fillMaxWidth(),
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SetEntryField(
                                    set.weight,
                                    "Weight (kg)",
                                    !addressed,
                                    { scope.launch { vm.controller.updateSet(date, exercise.name, index, weight = it) } },
                                    onFocus,
                                    Modifier.weight(1f),
                                )
                                SetEntryField(
                                    set.reps,
                                    "Reps",
                                    !addressed,
                                    { scope.launch { vm.controller.updateSet(date, exercise.name, index, reps = it) } },
                                    onFocus,
                                    Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                if (exercise.muscles.isNotEmpty()) {
                    Mono("Muscles trained")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        exercise.muscles.forEach { Pill(it, colors.mutedForeground) }
                    }
                }
                TextButton({ notesOpen = !notesOpen }) { Mono(if (notesOpen) "Hide notes" else "Exercise notes") }
                if (notesOpen) {
                    CardBlock {
                        Pill("This exercise", colors.neonOrange)
                        exercise.notes.forEach { Mono("· $it") }
                        Pill("Every workout", colors.neonYellow)
                        program.globalNotes.forEach { Mono("· $it") }
                    }
                }
                if (allAddressed) {
                    DisplayText(if (skipped > 0) "WORKOUT COMPLETE" else "ALL DONE", size = 16.sp, color = colors.neonOrange)
                }
                AwakeAndMedia(vm)
            }
        }
        val current = workout.exercises.getOrNull(pager.currentPage)
        val currentLog = current?.let { log.exercises[it.name] }
        RestBar(vm, date, workout, log, current) { index -> scope.launch { pager.animateScrollToPage(index) } }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton({ scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, enabled = pager.currentPage > 0) { Mono("←", size = 22.sp) }
            TextButton({ scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, enabled = pager.currentPage < workout.exercises.lastIndex) { Mono("→", size = 22.sp) }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (current != null && !isAddressed(currentLog)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlineButton("SKIP", {
                            vm.controller.haptic(HapticKind.Warning)
                            scope.launch { vm.controller.markExercise(date, current.name, done = false, skipped = true) }
                        })
                    }
                    Box(Modifier.weight(1f)) {
                        PrimaryButton("MARK DONE →", {
                            vm.controller.haptic(HapticKind.Success)
                            scope.launch { vm.controller.markExercise(date, current.name, done = true, skipped = false) }
                        })
                    }
                }
            } else if (current != null && !allAddressed) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlineButton("UNDO", {
                            scope.launch { vm.controller.markExercise(date, current.name, done = false, skipped = false) }
                        })
                    }
                    Box(Modifier.weight(1f)) {
                        PrimaryButton("NEXT PENDING →", {
                            val index = workout.exercises.indexOfFirst { !isAddressed(log.exercises[it.name]) }
                            if (index >= 0) scope.launch { pager.animateScrollToPage(index) }
                        })
                    }
                }
            } else if (current != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlineButton("UNDO", {
                            scope.launch { vm.controller.markExercise(date, current.name, done = false, skipped = false) }
                        })
                    }
                    Box(Modifier.weight(1f)) {
                        PrimaryButton("FINISH WORKOUT", {
                            vm.controller.haptic(HapticKind.Success)
                            nav.openFromHome(Routes.gym(date))
                        })
                    }
                }
            }
        }
    }
    }
    }
}

@Composable
private fun SetEntryField(
    value: String,
    label: String,
    enabled: Boolean,
    onValue: (String) -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Mono(label) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocus() },
    )
}

@Composable
private fun RestBar(
    vm: CinderViewModel,
    date: String,
    workout: ProgramWorkout,
    log: DayLog,
    current: ProgramExercise?,
    onJump: (Int) -> Unit,
) {
    val state by vm.controller.state.collectAsState()
    val rest = state.rest
    val owner = rest.ownerExercise?.takeIf { rest.ownerDate == date }
    if (owner == null && (current == null || isAddressed(log.exercises[current.name]))) return
    val colors = LocalColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var askedNotify by rememberSaveable { mutableStateOf(false) }
    fun askNotifyOnce() {
        if (Build.VERSION.SDK_INT < 33 || askedNotify) return
        askedNotify = true
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    CardBlock(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (owner == null) {
            val defaultSeconds = (state.settings.restTimerMinutes * 60).toInt().coerceAtLeast(1)
            val presets = (listOf(30, 45, 60, 90, 120) + defaultSeconds).distinct().sorted()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mono("TIMER", color = colors.foreground, weight = FontWeight.Bold)
                Row(
                    Modifier.weight(1f).padding(start = 12.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presets.forEach { seconds ->
                        RestChip(formatRestPreset(seconds), filled = seconds == defaultSeconds) {
                            vm.controller.haptic(HapticKind.Selection)
                            askNotifyOnce()
                            scope.launch { vm.controller.startRest(date, current!!.name, seconds) }
                        }
                    }
                }
            }
        } else {
            RestActive(vm, date, workout, log, current, owner, onJump)
        }
    }
}

@Composable
private fun RestActive(
    vm: CinderViewModel,
    date: String,
    workout: ProgramWorkout,
    log: DayLog,
    current: ProgramExercise?,
    owner: String,
    onJump: (Int) -> Unit,
) {
    val state by vm.controller.state.collectAsState()
    val rest = state.rest
    val colors = LocalColors.current
    val scope = rememberCoroutineScope()
    val ownerIndex = workout.exercises.indexOfFirst { it.name == owner }
    val elsewhere = owner != current?.name && ownerIndex >= 0
    val finished = rest.announced || (!rest.running && rest.remainingSeconds <= 0)
    val header = if (elsewhere) Modifier.clickable { onJump(ownerIndex) } else Modifier
    if (finished) {
        val definition = workout.exercises.getOrNull(ownerIndex)
        val ownerLog = log.exercises[owner]
        val cue = when {
            definition == null || ownerLog == null -> "Back to it"
            isAddressed(ownerLog) -> "On to the next exercise"
            else -> {
                val next = loggedSetCount(ownerLog, isTimedHold(definition)) + 1
                if (next <= definition.sets) "Up next · $owner, set $next of ${definition.sets}" else "Back to $owner"
            }
        }
        Column(header.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulseDot(colors.neonOrange)
                DisplayText("  TIMER COMPLETE", size = 16.sp, color = colors.neonOrange)
            }
            Mono(if (elsewhere) "$cue · tap to return" else cue, color = colors.foreground, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                OutlineButton("Again ${formatRestPreset(rest.durationSeconds)}", {
                    vm.controller.haptic(HapticKind.Selection)
                    scope.launch { vm.controller.startRest(date, owner, rest.durationSeconds.coerceAtLeast(1)) }
                })
            }
            Box(Modifier.weight(1f)) { PrimaryButton("Got it", { scope.launch { vm.controller.resetRest() } }) }
        }
    } else {
        val now = rememberNow()
        val remaining = rest.displayRemaining(now)
        Row(header.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (rest.running) PulseDot(colors.neonOrange)
                    Mono(
                        if (rest.running) "  RUNNING" else "PAUSED",
                        color = if (rest.running) colors.neonOrange else colors.neonYellow,
                        weight = FontWeight.Bold,
                    )
                }
                Mono(if (elsewhere) "$owner · tap to return" else owner, maxLines = 1)
            }
            DisplayText(formatClock(remaining), size = 36.sp, color = if (rest.running) colors.foreground else colors.mutedForeground)
        }
        LinearProgressIndicator(
            progress = { if (rest.durationSeconds > 0) remaining.toFloat() / rest.durationSeconds else 0f },
            color = if (rest.running) colors.neonOrange else colors.neonYellow,
            trackColor = colors.muted,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                OutlineButton("−15s", {
                    vm.controller.haptic(HapticKind.Light)
                    scope.launch { vm.controller.adjustRest(-15) }
                })
            }
            Box(Modifier.weight(1.3f)) {
                PrimaryButton(if (rest.running) "Pause" else "Resume", {
                    vm.controller.haptic(HapticKind.Selection)
                    scope.launch { if (rest.running) vm.controller.pauseRest() else vm.controller.resumeRest() }
                })
            }
            Box(Modifier.weight(1f)) {
                OutlineButton("+15s", {
                    vm.controller.haptic(HapticKind.Light)
                    scope.launch { vm.controller.adjustRest(15) }
                })
            }
            Box(Modifier.weight(1f)) {
                OutlineButton("Skip", { scope.launch { vm.controller.resetRest() } }, color = colors.mutedForeground)
            }
        }
    }
}

@Composable
private fun RestChip(label: String, filled: Boolean, onClick: () -> Unit) {
    val colors = LocalColors.current
    Mono(
        label,
        color = if (filled) colors.background else colors.neonOrange,
        size = 13.sp,
        weight = FontWeight.Bold,
        modifier = Modifier
            .background(if (filled) colors.neonOrange else colors.background, CircleShape)
            .border(1.dp, colors.neonOrange, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

private fun formatRestPreset(seconds: Int): String = when {
    seconds < 60 -> "${seconds}s"
    seconds % 60 == 0 -> "${seconds / 60}m"
    else -> formatClock(seconds)
}

@Composable
internal fun AwakeAndMedia(vm: CinderViewModel) {
    val state by vm.controller.state.collectAsState()
    val colors = LocalColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val held = state.settings.alwaysAwake && (state.rest.running || state.activeRun?.running == true)
    val label = when {
        !state.settings.alwaysAwake -> "Screen awake off"
        held -> "Screen awake"
        else -> "Screen awake (idle)"
    }
    TextButton({
        scope.launch { vm.controller.updateSettings { it.copy(alwaysAwake = !it.alwaysAwake) } }
    }) { Mono(label, color = colors.neonYellow) }
    val audio = context.getSystemService(AudioManager::class.java)
    if (audio?.isMusicActive == true) {
        Mono("Media")
        Row {
            listOf(KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_NEXT).forEach { code ->
                TextButton({
                    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
                }) { Mono(if (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) "Play/Pause" else if (code == KeyEvent.KEYCODE_MEDIA_NEXT) "Next" else "Prev") }
            }
        }
    }
}

private fun openLink(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
