package com.bayonetbarontech.cinderblock.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.bayonetbarontech.cinderblock.BuildConfig
import com.bayonetbarontech.cinderblock.CinderViewModel
import com.bayonetbarontech.cinderblock.data.clearSkip
import com.bayonetbarontech.cinderblock.data.signOut
import com.bayonetbarontech.cinderblock.data.syncNow
import com.bayonetbarontech.cinderblock.domain.HapticKind
import com.bayonetbarontech.cinderblock.domain.PHASES
import com.bayonetbarontech.cinderblock.domain.SyncPhase
import com.bayonetbarontech.cinderblock.domain.bodyTrend
import com.bayonetbarontech.cinderblock.domain.displayRemaining
import com.bayonetbarontech.cinderblock.domain.formatClock
import com.bayonetbarontech.cinderblock.domain.formatMediumDate
import com.bayonetbarontech.cinderblock.domain.formatNumber
import com.bayonetbarontech.cinderblock.domain.formatPlan
import com.bayonetbarontech.cinderblock.domain.formatVersionLabel
import com.bayonetbarontech.cinderblock.domain.logLabel
import com.bayonetbarontech.cinderblock.domain.parseDate
import com.bayonetbarontech.cinderblock.domain.phaseLabel
import com.bayonetbarontech.cinderblock.domain.unfinishedSession
import com.bayonetbarontech.cinderblock.ui.components.BrandMark
import com.bayonetbarontech.cinderblock.ui.components.ConfirmBlock
import com.bayonetbarontech.cinderblock.ui.components.DisplayText
import com.bayonetbarontech.cinderblock.ui.components.Mono
import com.bayonetbarontech.cinderblock.ui.components.OutlineButton
import com.bayonetbarontech.cinderblock.ui.components.PrimaryButton
import com.bayonetbarontech.cinderblock.ui.components.PulseDot
import com.bayonetbarontech.cinderblock.ui.components.rememberNow
import com.bayonetbarontech.cinderblock.ui.theme.LocalColors
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PageLink(val label: String, val onClick: () -> Unit)

@Stable
class MenuState {
    var open by mutableStateOf(false)
    var pageLinks by mutableStateOf<List<PageLink>>(emptyList())
}

val LocalMenu = staticCompositionLocalOf<MenuState?> { null }

@Composable
fun MenuButton() {
    val menu = LocalMenu.current ?: return
    val colors = LocalColors.current
    IconButton({ menu.open = !menu.open }) {
        Icon(Icons.Default.Menu, "Open menu", tint = colors.foreground)
    }
}

@Composable
fun RegisterPageLinks(links: List<PageLink>) {
    val menu = LocalMenu.current ?: return
    DisposableEffect(menu, links) {
        menu.pageLinks = links
        onDispose { if (menu.pageLinks === links) menu.pageLinks = emptyList() }
    }
}

private enum class Section { Home, Training, Running, Metrics, Explore, Editor, Ai, Settings, Other }

private fun sectionOf(route: String?): Section = when {
    route == null -> Section.Other
    route == Routes.Home -> Section.Home
    route.startsWith("gym/explore") -> Section.Explore
    route.startsWith("gym") -> Section.Training
    route.startsWith("running") -> Section.Running
    route == Routes.Metrics -> Section.Metrics
    route == Routes.Settings -> Section.Settings
    route == Routes.Editor -> Section.Editor
    route.startsWith("ai/") -> Section.Ai
    else -> Section.Other
}

@Composable
fun NavMenuOverlay(menu: MenuState, nav: NavHostController, vm: CinderViewModel) {
    BackHandler(menu.open) { menu.open = false }
    AnimatedVisibility(menu.open, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(remember { MutableInteractionSource() }, indication = null) { menu.open = false },
        )
    }
    AnimatedVisibility(
        menu.open,
        enter = slideInVertically { -it / 4 } + fadeIn(),
        exit = slideOutVertically { -it / 4 } + fadeOut(),
    ) {
        MenuPanel(menu, nav, vm)
    }
}

@Composable
private fun MenuPanel(menu: MenuState, nav: NavHostController, vm: CinderViewModel) {
    val state by vm.controller.state.collectAsState()
    val colors = LocalColors.current
    val now = rememberNow()
    val here = sectionOf(nav.currentBackStackEntryAsState().value?.destination?.route)
    val program = state.program
    val today = LocalDate.now().toString()

    fun go(route: String) {
        vm.controller.haptic(HapticKind.Selection)
        menu.open = false
        if (route == Routes.Home) nav.goHome() else nav.openFromHome(route)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.background, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(36.dp, Modifier.padding(end = 10.dp))
            DisplayText("Cinderblock", size = 18.sp, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton({ menu.open = false }) { Icon(Icons.Default.Close, "Close menu", tint = colors.foreground) }
        }

        Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val rest = state.rest.takeIf { it.ownerExercise != null && it.ownerDate != null }
            val unfinished = unfinishedSession(state.gymLog)?.takeIf { it.first != rest?.ownerDate }
            val run = state.activeRun?.takeIf { it.started && state.runCompletion == null }
            if (rest != null || unfinished != null || run != null) {
                GroupLabel("Pick up where you left off")
                rest?.let {
                    val finished = it.announced || (!it.running && it.remainingSeconds <= 0)
                    LiveRow(
                        Icons.Default.Timer,
                        if (finished) "Rest complete" else "Resting · ${it.ownerExercise}",
                        when {
                            finished -> "Back to ${it.ownerExercise}"
                            it.running -> "${formatClock(it.displayRemaining(now))} left"
                            else -> "Paused · ${formatClock(it.remainingSeconds)} left"
                        },
                        colors.neonOrange,
                        pulsing = it.running || finished,
                    ) { go(Routes.workout(it.ownerDate!!, it.ownerExercise)) }
                }
                unfinished?.let { (date, log) ->
                    LiveRow(
                        Icons.Default.FitnessCenter,
                        "Continue ${logLabel(program, log)}",
                        "Unfinished · ${parseDate(date)?.let(::formatMediumDate) ?: date}",
                        colors.neonOrange,
                        pulsing = false,
                    ) { go(Routes.workout(date, state.sessionExercise[date])) }
                }
                run?.let {
                    val phase = phaseLabel(PHASES[it.phaseIndex.coerceIn(0, PHASES.lastIndex)])
                    val left = formatClock(displayRemaining(it, now))
                    LiveRow(
                        Icons.AutoMirrored.Filled.DirectionsRun,
                        "Run · $phase",
                        if (it.running) "$left left in this phase" else "Paused · $left left",
                        colors.neonYellow,
                        pulsing = it.running,
                    ) { go(Routes.RunSession) }
                }
            }

            GroupLabel("Train")
            val todayLog = state.gymLog[today]
            val lastRun = state.runLog.firstOrNull()
            val weight = bodyTrend(state.bodyMetrics, "weight")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(Icons.Default.Home, "Home", "Today & stats", here == Section.Home, Modifier.weight(1f)) { go(Routes.Home) }
                Tile(
                    Icons.Default.FitnessCenter,
                    "Training",
                    todayLog?.let { "Today · ${logLabel(program, it)}" } ?: "Log today's workout",
                    here == Section.Training,
                    Modifier.weight(1f),
                ) { go(Routes.gym(null)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(
                    Icons.AutoMirrored.Filled.DirectionsRun,
                    "Running",
                    lastRun?.let { "Last · ${parseDate(it.date)?.let(::formatMediumDate) ?: it.date}" } ?: "Plan ${formatPlan(state.runningPlan)}",
                    here == Section.Running,
                    Modifier.weight(1f),
                ) { go(Routes.Running) }
                Tile(
                    Icons.Default.MonitorWeight,
                    "Metrics",
                    weight?.let { "${formatNumber(it.value)} kg latest" } ?: "Weight & waist",
                    here == Section.Metrics,
                    Modifier.weight(1f),
                ) { go(Routes.Metrics) }
            }

            GroupLabel("Program · ${program.name} ${formatVersionLabel(program.version)}")
            Column {
                MenuRow(Icons.Default.GridView, "Browse workouts", "${program.workouts.size} workouts, exercises & notes", here == Section.Explore) {
                    go(Routes.Explore)
                }
                MenuRow(Icons.Default.Edit, "Edit program", "Workouts, schedule & goals", here == Section.Editor) {
                    go(Routes.Editor)
                }
                MenuRow(
                    Icons.Default.AutoAwesome,
                    "AI coach",
                    if (state.cloud.aiConfigured) "Ask about or change your plan" else "Not set up on this build",
                    here == Section.Ai,
                ) { go(Routes.ai("discuss")) }
            }

            if (menu.pageLinks.isNotEmpty()) {
                GroupLabel("Jump to")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    menu.pageLinks.forEach { link ->
                        Mono(
                            link.label,
                            color = colors.neonOrange,
                            size = 13.sp,
                            modifier = Modifier
                                .border(1.dp, colors.neonOrange.copy(alpha = 0.6f), RoundedCornerShape(99.dp))
                                .clickable {
                                    menu.open = false
                                    link.onClick()
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            GroupLabel("Account & app")
            AccountCard(menu, nav, vm)
            MenuRow(Icons.Default.Settings, "Settings", "Appearance, sounds, alerts, backups", here == Section.Settings) {
                go(Routes.Settings)
            }
            Mono("v${BuildConfig.VERSION_NAME}", size = 10.sp)
        }
    }
}

@Composable
private fun AccountCard(menu: MenuState, nav: NavHostController, vm: CinderViewModel) {
    val state by vm.controller.state.collectAsState()
    val colors = LocalColors.current
    val scope = rememberCoroutineScope()
    var confirmSignOut by remember { mutableStateOf(false) }
    val auth = state.auth
    Column(
        Modifier.fillMaxWidth().border(1.dp, colors.border, RoundedCornerShape(10.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            !state.cloud.configured -> AccountLine(Icons.Default.CloudOff, "Local only", "Your data stays on this device", colors.mutedForeground)
            auth == null -> {
                AccountLine(Icons.Default.CloudOff, "Not signed in", "Sign in to back up and sync across devices", colors.mutedForeground)
                PrimaryButton("Sign in", {
                    menu.open = false
                    scope.launch { vm.controller.clearSkip() }
                    nav.navigate(Routes.Login)
                })
            }
            else -> {
                val (icon, status, tint) = when (state.syncPhase) {
                    SyncPhase.Syncing -> Triple(Icons.Default.CloudSync, "Syncing…", colors.neonYellow)
                    SyncPhase.Error -> Triple(Icons.Default.ErrorOutline, state.syncError ?: "Sync failed", colors.destructive)
                    SyncPhase.Idle -> Triple(Icons.Default.Cloud, "Synced", colors.success)
                }
                AccountLine(icon, auth.email, status, tint)
                if (confirmSignOut) {
                    ConfirmBlock(
                        "Sign out? Your training data stays on this device.",
                        "Sign out",
                        {
                            confirmSignOut = false
                            scope.launch { vm.controller.signOut() }
                        },
                        { confirmSignOut = false },
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            OutlineButton(if (state.syncPhase == SyncPhase.Syncing) "Syncing…" else "Sync now", {
                                if (state.syncPhase != SyncPhase.Syncing) scope.launch { vm.controller.syncNow() }
                            })
                        }
                        Box(Modifier.weight(1f)) {
                            OutlineButton("Sign out", { confirmSignOut = true }, color = colors.mutedForeground)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountLine(icon: ImageVector, title: String, subtitle: String, tint: Color) {
    val colors = LocalColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Column(Modifier.padding(start = 12.dp)) {
            Mono(title, color = colors.foreground, size = 14.sp, maxLines = 1)
            Mono(subtitle, color = tint, maxLines = 2)
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Mono(text.uppercase(), color = LocalColors.current.mutedForeground, size = 10.sp, weight = FontWeight.Bold, maxLines = 1)
}

@Composable
private fun LiveRow(icon: ImageVector, title: String, subtitle: String, accent: Color, pulsing: Boolean, onClick: () -> Unit) {
    val colors = LocalColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mono(title, color = colors.foreground, size = 14.sp, weight = FontWeight.Bold, maxLines = 1)
                if (pulsing) {
                    Box(Modifier.width(8.dp))
                    PulseDot(accent)
                }
            }
            Mono(subtitle, color = accent, maxLines = 1)
        }
        Icon(Icons.Default.ChevronRight, null, tint = accent)
    }
}

@Composable
private fun Tile(icon: ImageVector, title: String, subtitle: String, current: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalColors.current
    Column(
        modifier
            .heightIn(min = 92.dp)
            .background(if (current) colors.primary.copy(alpha = 0.16f) else colors.card, RoundedCornerShape(18.dp))
            .border(1.dp, if (current) colors.primary else colors.border.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = if (current) colors.neonOrange else colors.foreground, modifier = Modifier.size(22.dp))
        DisplayText(title, size = 14.sp, color = if (current) colors.neonOrange else colors.foreground, maxLines = 1)
        Mono(subtitle, maxLines = 1)
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String, current: Boolean, onClick: () -> Unit) {
    val colors = LocalColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(if (current) colors.neonOrange.copy(alpha = 0.14f) else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (current) colors.neonOrange else colors.foreground, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Mono(title, color = if (current) colors.neonOrange else colors.foreground, size = 14.sp, weight = FontWeight.Bold, maxLines = 1)
            Mono(subtitle, maxLines = 1)
        }
        if (!current) Icon(Icons.Default.ChevronRight, null, tint = colors.mutedForeground)
    }
}
