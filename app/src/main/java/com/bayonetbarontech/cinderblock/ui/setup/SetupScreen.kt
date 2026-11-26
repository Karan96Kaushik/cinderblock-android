package com.bayonetbarontech.cinderblock.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.bayonetbarontech.cinderblock.CinderViewModel
import com.bayonetbarontech.cinderblock.data.finishSetupWithoutPlan
import com.bayonetbarontech.cinderblock.data.generateSetupPlan
import com.bayonetbarontech.cinderblock.data.updateProfile
import com.bayonetbarontech.cinderblock.domain.HapticKind
import com.bayonetbarontech.cinderblock.domain.SETUP_QUESTION_COUNT
import com.bayonetbarontech.cinderblock.domain.SetupPhase
import com.bayonetbarontech.cinderblock.domain.TrainingProfile
import com.bayonetbarontech.cinderblock.ui.components.DisplayText
import com.bayonetbarontech.cinderblock.ui.components.Mono
import com.bayonetbarontech.cinderblock.ui.components.OutlineButton
import com.bayonetbarontech.cinderblock.ui.components.PrimaryButton
import com.bayonetbarontech.cinderblock.ui.components.PulseDot
import com.bayonetbarontech.cinderblock.ui.nav.Routes
import com.bayonetbarontech.cinderblock.ui.theme.LocalColors
import kotlinx.coroutines.launch

private const val STEP_DAYS = 0
private const val STEP_AGE = 1
private const val STEP_SEX = 2
private const val STEP_EXPERIENCE = 3
private const val STEP_EQUIPMENT = 4
private const val STEP_NOTES = 5
private const val STEP_GENERATE = 6

@Composable
fun SetupScreen(nav: NavHostController, vm: CinderViewModel) {
    val state by vm.controller.state.collectAsState()
    val colors = LocalColors.current
    val scope = rememberCoroutineScope()
    val profile = state.profile
    val setup = state.setupRuntime
    val generating = setup.phase != SetupPhase.Idle
    var step by remember { mutableIntStateOf(0) }
    var ageText by remember { mutableStateOf(profile.age?.toString().orEmpty()) }
    var notesText by remember { mutableStateOf(profile.notes.orEmpty()) }
    val signedIn = state.auth != null
    val aiReady = state.cloud.aiConfigured

    LaunchedEffect(profile.age) {
        if (profile.age != null) ageText = profile.age.toString()
    }
    LaunchedEffect(profile.notes) {
        if (!profile.notes.isNullOrBlank() && notesText.isBlank()) notesText = profile.notes
    }
    LaunchedEffect(step, signedIn, aiReady) {
        if (step != STEP_GENERATE) return@LaunchedEffect
        if (!signedIn || !aiReady) return@LaunchedEffect
        if (vm.controller.state.value.setupRuntime.phase != SetupPhase.Idle) return@LaunchedEffect
        if (vm.controller.state.value.setupRuntime.error != null) return@LaunchedEffect
        vm.controller.generateSetupPlan()
    }

    fun close() {
        if (generating) return
        scope.launch { vm.controller.finishSetupWithoutPlan() }
    }

    fun back() {
        if (generating) return
        if (step <= 0) close() else step -= 1
    }

    fun advance() {
        if (step < STEP_GENERATE) step += 1
    }

    fun skip() {
        if (generating) return
        vm.controller.haptic(HapticKind.Selection)
        val current = step
        scope.launch {
            when (current) {
                STEP_DAYS -> vm.controller.updateProfile { it.copy(daysPerWeek = null) }
                STEP_AGE -> {
                    ageText = ""
                    vm.controller.updateProfile { it.copy(age = null) }
                }
                STEP_SEX -> vm.controller.updateProfile { it.copy(sex = null) }
                STEP_EXPERIENCE -> vm.controller.updateProfile { it.copy(experience = null) }
                STEP_EQUIPMENT -> vm.controller.updateProfile { it.copy(equipment = null) }
                STEP_NOTES -> {
                    notesText = ""
                    vm.controller.updateProfile { it.copy(notes = null) }
                }
            }
            advance()
        }
    }

    fun choose(update: (TrainingProfile) -> TrainingProfile) {
        vm.controller.haptic(HapticKind.Selection)
        scope.launch {
            vm.controller.updateProfile(update)
            advance()
        }
    }

    BackHandler { back() }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
            StoryHeader(
                step = step,
                generating = generating,
                onClose = { close() },
            )
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Box(
                    Modifier
                        .width(28.dp)
                        .fillMaxHeight()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { back() },
                        ),
                )
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Spacer(Modifier.height(12.dp))
                    when (step) {
                        STEP_DAYS -> {
                            StoryQuestion("How many days do you want to lift?")
                            Mono("Skip if you are not sure. We will assume 4 days.")
                            listOf(2, 3, 4, 5, 6).forEach { days ->
                                StoryOption(
                                    label = "$days days",
                                    selected = profile.daysPerWeek == days,
                                    onClick = { choose { it.copy(daysPerWeek = days) } },
                                )
                            }
                        }
                        STEP_AGE -> {
                            StoryQuestion("How old are you?")
                            Mono("Skip to treat this as a healthy adult plan.")
                            OutlinedTextField(
                                value = ageText,
                                onValueChange = { value -> ageText = value.filter { it.isDigit() }.take(3) },
                                label = { Mono("Age") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                            val parsedAge = ageText.toIntOrNull()
                            PrimaryButton("Next", {
                                val age = parsedAge ?: return@PrimaryButton
                                if (age !in 10..120) return@PrimaryButton
                                choose { it.copy(age = age) }
                            }, enabled = parsedAge != null && parsedAge in 10..120)
                        }
                        STEP_SEX -> {
                            StoryQuestion("What is your sex?")
                            Mono("Skip if you do not want any sex-specific adjustments.")
                            StoryOption("Female", profile.sex == "female") { choose { it.copy(sex = "female") } }
                            StoryOption("Male", profile.sex == "male") { choose { it.copy(sex = "male") } }
                            StoryOption("Prefer not to say", profile.sex == "unspecified") {
                                choose { it.copy(sex = "unspecified") }
                            }
                        }
                        STEP_EXPERIENCE -> {
                            StoryQuestion("Have you lifted before?")
                            Mono("Skip and we will assume some experience.")
                            StoryOption("New to lifting", profile.experience == "beginner") {
                                choose { it.copy(experience = "beginner") }
                            }
                            StoryOption("Some experience", profile.experience == "some") {
                                choose { it.copy(experience = "some") }
                            }
                            StoryOption("Experienced", profile.experience == "experienced") {
                                choose { it.copy(experience = "experienced") }
                            }
                        }
                        STEP_EQUIPMENT -> {
                            StoryQuestion("What do you want to train with?")
                            Mono("Skip and we will use machines and free weights.")
                            StoryOption("Machines", profile.equipment == "machines") {
                                choose { it.copy(equipment = "machines") }
                            }
                            StoryOption("Dumbbells and floor work", profile.equipment == "free-weights") {
                                choose { it.copy(equipment = "free-weights") }
                            }
                            StoryOption("Both", profile.equipment == "both") {
                                choose { it.copy(equipment = "both") }
                            }
                        }
                        STEP_NOTES -> {
                            StoryQuestion("Anything else we should know?")
                            Mono("Injuries, goals, or equipment limits. Skip if there is nothing to add.")
                            OutlinedTextField(
                                value = notesText,
                                onValueChange = { notesText = it.take(500) },
                                label = { Mono("Notes") },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(top = 8.dp),
                            )
                            PrimaryButton("Build my plan", {
                                vm.controller.haptic(HapticKind.Selection)
                                scope.launch {
                                    vm.controller.updateProfile { it.copy(notes = notesText.trim().ifBlank { null }) }
                                    advance()
                                }
                            })
                            OutlineButton("Keep the current program", { close() })
                        }
                        else -> GenerateFrame(
                            signedIn = signedIn,
                            aiReady = aiReady,
                            phase = setup.phase,
                            error = setup.error,
                            onSignIn = { nav.navigate(Routes.Login) },
                            onRetry = { scope.launch { vm.controller.generateSetupPlan() } },
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            if (step <= STEP_NOTES && !generating) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { skip() })
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Mono("Skip", color = colors.mutedForeground, size = 14.sp, weight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StoryHeader(step: Int, generating: Boolean, onClose: () -> Unit) {
    val colors = LocalColors.current
    val filled = if (step >= STEP_GENERATE) SETUP_QUESTION_COUNT else step
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(SETUP_QUESTION_COUNT) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .background(
                            when {
                                index < filled -> colors.foreground
                                index == step -> colors.neonOrange
                                else -> colors.foreground.copy(alpha = 0.22f)
                            },
                            RoundedCornerShape(99.dp),
                        ),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            PulseDot(colors.neonRed)
            Mono(" LIVE", color = colors.foreground, size = 11.sp, weight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose, enabled = !generating) {
                Icon(Icons.Default.Close, "Close setup", tint = if (generating) colors.mutedForeground else colors.foreground)
            }
        }
    }
}

@Composable
private fun StoryQuestion(text: String) {
    DisplayText(text, size = 28.sp)
}

@Composable
private fun StoryOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(
                if (selected) colors.neonOrange.copy(alpha = 0.18f) else colors.card.copy(alpha = 0.72f),
                RoundedCornerShape(12.dp),
            )
            .border(
                1.dp,
                if (selected) colors.neonOrange else colors.border.copy(alpha = 0.7f),
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        DisplayText(label, size = 16.sp, color = if (selected) colors.neonOrange else colors.foreground)
    }
}

@Composable
private fun GenerateFrame(
    signedIn: Boolean,
    aiReady: Boolean,
    phase: SetupPhase,
    error: String?,
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = LocalColors.current
    StoryQuestion("Building your plan")
    when {
        !signedIn -> {
            Mono("Sign in so Cinderblock can generate and save your program. Your answers are already saved.", color = colors.foreground)
            PrimaryButton("Sign in to continue", onSignIn)
        }
        !aiReady -> {
            Mono("AI chat is not configured yet. Deploy the Amplify sandbox so the function URLs are set.", color = colors.neonYellow)
        }
        error != null -> {
            Mono(error, color = colors.destructive, size = 13.sp)
            PrimaryButton("Retry", onRetry)
        }
        else -> {
            val status = when (phase) {
                SetupPhase.Writing -> "Writing your plan…"
                SetupPhase.Checking -> "Checking the plan…"
                SetupPhase.Saving -> "Saving…"
                SetupPhase.Idle -> "Writing your plan…"
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(color = colors.neonOrange, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Mono(status, color = colors.foreground, size = 14.sp)
            }
        }
    }
}
