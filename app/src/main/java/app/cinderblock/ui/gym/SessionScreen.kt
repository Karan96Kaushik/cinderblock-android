package app.cinderblock.ui.gym

import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.cinderblock.CinderViewModel
import app.cinderblock.data.ensureSets
import app.cinderblock.data.focusSet
import app.cinderblock.data.markExercise
import app.cinderblock.data.pauseRest
import app.cinderblock.data.resetRest
import app.cinderblock.data.resumeRest
import app.cinderblock.data.startRest
import app.cinderblock.data.updateSet
import app.cinderblock.data.updateSettings
import app.cinderblock.domain.HapticKind
import app.cinderblock.domain.REST_KEY
import app.cinderblock.domain.exerciseStatus
import app.cinderblock.domain.formatClock
import app.cinderblock.domain.formatLongDate
import app.cinderblock.domain.formatMediumDate
import app.cinderblock.domain.isAddressed
import app.cinderblock.domain.isProgramKey
import app.cinderblock.domain.isTimedHold
import app.cinderblock.domain.lastExerciseRecord
import app.cinderblock.domain.loggedSetCount
import app.cinderblock.domain.logLabel
import app.cinderblock.domain.parseDate
import app.cinderblock.domain.resolveProgram
import app.cinderblock.domain.visibleSetCount
import app.cinderblock.ui.components.CardBlock
import app.cinderblock.ui.components.DisplayText
import app.cinderblock.ui.components.FeatureBar
import app.cinderblock.ui.components.Mono
import app.cinderblock.ui.components.OutlineButton
import app.cinderblock.ui.components.Pill
import app.cinderblock.ui.components.PrimaryButton
import app.cinderblock.ui.components.PulseDot
import app.cinderblock.ui.components.rememberNow
import app.cinderblock.ui.nav.Routes
import app.cinderblock.ui.nav.goHome
import app.cinderblock.ui.nav.openFromHome
import app.cinderblock.ui.theme.LocalColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionScreen(date: String, nav: NavHostController, vm: CinderViewModel) {
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
    val pager = rememberPagerState { workout.exercises.size.coerceAtLeast(1) }
    val now = rememberNow()
    val context = LocalContext.current
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(pager.currentPage) {
        if (!armed) {
            armed = true
            return@LaunchedEffect
        }
        vm.controller.playSound("change")
    }
    val completed = workout.exercises.count { isAddressed(log.exercises[it.name]) && log.exercises[it.name]?.completed == true }
    val skipped = workout.exercises.count { log.exercises[it.name]?.skipped == true }
    val allAddressed = workout.exercises.all { isAddressed(log.exercises[it.name]) }
    val rest = state.rest
    val restHere = rest.ownerDate == date && rest.ownerExercise != null
    val foreign = restHere && rest.ownerExercise != workout.exercises.getOrNull(pager.currentPage)?.name

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
            val visible = if (item == null) 1 else visibleSetCount(item, planned, timed)
            val last = lastExerciseRecord(state.gymLog, exercise.name, date)
            var notesOpen by remember(exercise.name) { mutableStateOf(false) }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DisplayText(
                        exercise.name,
                        size = 20.sp,
                        color = if (status == app.cinderblock.domain.ExerciseStatus.Skipped) colors.mutedForeground else if (status == app.cinderblock.domain.ExerciseStatus.Done) colors.neonOrange else colors.foreground,
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
                last?.let { (lastDate, _) ->
                    parseDate(lastDate)?.let { Mono("Last session · ${formatMediumDate(it)}") }
                }
                DisplayText("Log sets", size = 14.sp)
                if (item != null) {
                    item.sets.take(visible).forEachIndexed { index, set ->
                        if (timed) {
                            OutlinedTextField(
                                set.seconds,
                                { scope.launch { vm.controller.updateSet(date, exercise.name, index, seconds = it) } },
                                label = { Mono("Seconds held") },
                                enabled = !addressed,
                                modifier = Modifier.fillMaxWidth().onFocusChanged {
                                    if (it.isFocused) scope.launch { vm.controller.focusSet(date, exercise.name, index) }
                                },
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    set.weight,
                                    { scope.launch { vm.controller.updateSet(date, exercise.name, index, weight = it) } },
                                    label = { Mono("Weight (kg)") },
                                    enabled = !addressed,
                                    modifier = Modifier.weight(1f).onFocusChanged {
                                        if (it.isFocused) scope.launch { vm.controller.focusSet(date, exercise.name, index) }
                                    },
                                )
                                OutlinedTextField(
                                    set.reps,
                                    { scope.launch { vm.controller.updateSet(date, exercise.name, index, reps = it) } },
                                    label = { Mono("Reps") },
                                    enabled = !addressed,
                                    modifier = Modifier.weight(1f),
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
                if (rest.ownerExercise == exercise.name && rest.ownerDate == date) {
                    RestBlock(vm, date, exercise.name)
                } else if (page == pager.currentPage && rest.ownerExercise == null) {
                    RestBlock(vm, date, exercise.name)
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
            }
        }
        if (foreign) {
            Row(
                Modifier.fillMaxWidth().clickable {
                    val index = workout.exercises.indexOfFirst { it.name == rest.ownerExercise }
                    if (index >= 0) scope.launch { pager.animateScrollToPage(index) }
                }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulseDot(colors.neonOrange)
                Mono("  ${rest.ownerExercise} · ${formatClock(rest.displayRemaining(now))}  · tap to return", color = colors.foreground)
            }
        }
        val current = workout.exercises.getOrNull(pager.currentPage)
        val currentLog = current?.let { log.exercises[it.name] }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestBlock(vm: CinderViewModel, date: String, exercise: String) {
    val state by vm.controller.state.collectAsState()
    val colors = LocalColors.current
    val scope = rememberCoroutineScope()
    val now = rememberNow()
    val rest = state.rest
    val active = rest.ownerExercise == exercise && rest.ownerDate == date
    val remaining = if (active) rest.displayRemaining(now) else 0
    val settingsSeconds = (state.settings.restTimerMinutes * 60).toInt()
    val presets = listOf(30, 45, 60).let { base -> if (settingsSeconds !in base) base + settingsSeconds else base }
    CardBlock {
        Mono(if (active) rest.phaseLabel else "Pick a preset", color = colors.foreground, size = 14.sp)
        if (active) DisplayText(formatClock(remaining), size = 36.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.forEach { seconds ->
                Pill(if (seconds >= 60 && seconds % 60 == 0) "${seconds / 60}m" else "${seconds}s", colors.neonOrange)
                TextButton({
                    vm.controller.haptic(HapticKind.Selection)
                    scope.launch { vm.controller.startRest(date, exercise, seconds) }
                }) { Mono(if (seconds % 60 == 0) "${seconds / 60}m" else "${seconds}s", color = colors.neonOrange) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                OutlineButton(if (rest.running && active) "Pause" else "Play", {
                    scope.launch { if (rest.running && active) vm.controller.pauseRest() else vm.controller.resumeRest() }
                })
            }
            Box(Modifier.weight(1f)) { OutlineButton("Reset", { scope.launch { vm.controller.resetRest() } }) }
        }
        AwakeAndMedia(vm)
    }
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
