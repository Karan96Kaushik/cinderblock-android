package com.bayonetbarontech.cinderblock.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.util.Consumer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bayonetbarontech.cinderblock.CinderViewModel
import com.bayonetbarontech.cinderblock.data.setRecovery
import com.bayonetbarontech.cinderblock.domain.UiEffect
import com.bayonetbarontech.cinderblock.ui.auth.LoginScreen
import com.bayonetbarontech.cinderblock.ui.editor.EditorScreen
import com.bayonetbarontech.cinderblock.ui.ai.AiChatScreen
import com.bayonetbarontech.cinderblock.ui.gym.CalendarScreen
import com.bayonetbarontech.cinderblock.ui.gym.ExploreScreen
import com.bayonetbarontech.cinderblock.ui.gym.PickerScreen
import com.bayonetbarontech.cinderblock.ui.gym.SessionScreen
import com.bayonetbarontech.cinderblock.ui.home.HomeScreen
import com.bayonetbarontech.cinderblock.ui.legal.LegalScreen
import com.bayonetbarontech.cinderblock.ui.metrics.MetricsScreen
import com.bayonetbarontech.cinderblock.ui.nav.BottomDock
import com.bayonetbarontech.cinderblock.ui.nav.LocalMenu
import com.bayonetbarontech.cinderblock.ui.nav.MenuState
import com.bayonetbarontech.cinderblock.ui.nav.NavMenuOverlay
import com.bayonetbarontech.cinderblock.ui.nav.Routes
import com.bayonetbarontech.cinderblock.ui.nav.showsDock
import com.bayonetbarontech.cinderblock.ui.nav.goHome
import com.bayonetbarontech.cinderblock.ui.nav.openFromHome
import com.bayonetbarontech.cinderblock.ui.nav.recoveryAccessToken
import com.bayonetbarontech.cinderblock.ui.nav.routeFromUri
import com.bayonetbarontech.cinderblock.ui.running.RunPlanScreen
import com.bayonetbarontech.cinderblock.ui.running.RunSessionScreen
import com.bayonetbarontech.cinderblock.ui.settings.SettingsScreen
import com.bayonetbarontech.cinderblock.ui.setup.SetupScreen
import com.bayonetbarontech.cinderblock.ui.components.BrandMark
import com.bayonetbarontech.cinderblock.ui.components.DisplayText
import com.bayonetbarontech.cinderblock.ui.theme.CinderTheme
import com.bayonetbarontech.cinderblock.ui.theme.LocalColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CinderApp(vm: CinderViewModel = viewModel()) {
    val state by vm.controller.state.collectAsState()
    CinderTheme(state.settings.theme, state.settings.fontPreset, state.settings.fontSize) {
        val colors = LocalColors.current
        val view = LocalView.current
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = colors.light
            controller.isAppearanceLightNavigationBars = colors.light
        }
        val holdAwake = state.settings.alwaysAwake && (state.rest.running || state.activeRun?.running == true)
        DisposableEffect(holdAwake) {
            val window = (view.context as Activity).window
            if (holdAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            if (!state.ready) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    BrandMark(96.dp)
                    DisplayText("Cinderblock", size = 28.sp, color = colors.primary, modifier = Modifier.padding(top = 16.dp))
                }
            } else {
                val nav = rememberNavController()
                val snackbar = remember { SnackbarHostState() }
                val context = LocalContext.current
                val start = remember {
                    val needsLogin = state.cloud.configured && state.auth == null && !state.skipLogin
                    when {
                        needsLogin -> Routes.Login
                        else -> Routes.Home
                    }
                }
                LaunchedEffect(Unit) {
                    val uri = (context as? Activity)?.intent?.data
                    if (uri != null) {
                        recoveryAccessToken(uri)?.let { vm.controller.setRecovery(it) }
                        val route = routeFromUri(uri)
                        if (route != start) nav.navigate(route)
                    }
                    vm.controller.effects.collect { effect ->
                        when (effect) {
                            is UiEffect.Toast -> snackbar.showSnackbar(effect.message)
                            is UiEffect.Navigate -> nav.navigate(effect.route) {
                                popUpTo(nav.graph.id) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                }
                val scope = rememberCoroutineScope()
                DisposableEffect(context) {
                    val activity = context as? ComponentActivity
                    val listener = Consumer<Intent> { intent ->
                        val uri = intent.data ?: return@Consumer
                        recoveryAccessToken(uri)?.let { token -> scope.launch { vm.controller.setRecovery(token) } }
                        val route = routeFromUri(uri)
                        if (route == Routes.Home) nav.goHome() else nav.openFromHome(route)
                    }
                    activity?.addOnNewIntentListener(listener)
                    onDispose { activity?.removeOnNewIntentListener(listener) }
                }
                val menu = remember { MenuState() }
                val entry = nav.currentBackStackEntryAsState().value
                val route = entry?.destination?.route
                val mode = entry?.arguments?.getString("mode")
                val dock = showsDock(route, mode)
                val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val imeInset = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                val restingInset = if (dock) 76.dp + navInset else navInset
                val bottomInset = if (imeInset > restingInset) imeInset else restingInset
                LaunchedEffect(route) { menu.open = false }
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = bottomInset)
                        .pointerInput(focusManager, keyboard) {
                            awaitEachGesture {
                                val down = awaitFirstDown(pass = PointerEventPass.Final, requireUnconsumed = false)
                                val up = waitForUpOrCancellation(pass = PointerEventPass.Final)
                                if (up != null && !down.isConsumed && !up.isConsumed) {
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                }
                            }
                        },
                ) {
                    CompositionLocalProvider(LocalMenu provides menu) {
                        CinderNav(nav, vm, start)
                    }
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
                }
                if (dock) {
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        BottomDock(nav, route, vm.controller::haptic)
                    }
                }
                NavMenuOverlay(menu, nav, vm)
                NotificationPrompt(vm, nav, snackbar)
            }
        }
    }
}

@Composable
private fun NotificationPrompt(vm: CinderViewModel, nav: NavHostController, snackbar: SnackbarHostState) {
    val state by vm.controller.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) return@rememberLauncherForActivityResult
        scope.launch {
            val result = snackbar.showSnackbar(
                "Notifications are off, so rest timer alerts won't show.",
                actionLabel = "Settings",
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) openNotificationSettings(context)
        }
    }
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(route == null || route == Routes.Login || route == Routes.Setup, state.notificationPrompted) {
        if (route == null || route == Routes.Login || route == Routes.Setup || route == Routes.Terms || route == Routes.Privacy || state.notificationPrompted) return@LaunchedEffect
        if (!needsNotificationPermission(context)) return@LaunchedEffect
        delay(800)
        vm.controller.markNotificationPrompted()
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun CinderNav(nav: NavHostController, vm: CinderViewModel, start: String) {
    NavHost(nav, startDestination = start) {
        composable(Routes.Home) { HomeScreen(nav, vm) }
        composable(Routes.Login) { LoginScreen(nav, vm) }
        composable(Routes.Setup) { SetupScreen(nav, vm) }
        composable(
            Routes.Gym,
            arguments = listOf(navArgument("date") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry -> CalendarScreen(entry.arguments?.getString("date"), nav, vm) }
        composable(
            Routes.Select,
            arguments = listOf(navArgument("date") { type = NavType.StringType }),
        ) { entry -> PickerScreen(entry.arguments?.getString("date").orEmpty(), nav, vm) }
        composable(
            Routes.Workout,
            arguments = listOf(
                navArgument("date") { type = NavType.StringType },
                navArgument("exercise") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            val exercise by entry.savedStateHandle
                .getStateFlow("exercise", entry.arguments?.getString("exercise"))
                .collectAsState()
            SessionScreen(entry.arguments?.getString("date").orEmpty(), exercise, nav, vm)
        }
        composable(Routes.Explore) { ExploreScreen(null, nav, vm) }
        composable(
            Routes.ExploreOne,
            arguments = listOf(navArgument("key") { type = NavType.StringType }),
        ) { entry -> ExploreScreen(entry.arguments?.getString("key"), nav, vm) }
        composable(Routes.Running) { RunPlanScreen(nav, vm) }
        composable(Routes.RunSession) { RunSessionScreen(nav, vm) }
        composable(Routes.Metrics) { MetricsScreen(nav, vm) }
        composable(Routes.Settings) { SettingsScreen(nav, vm) }
        composable(Routes.Terms) { LegalScreen(nav, "legal/terms.html", "TERMS") }
        composable(Routes.Privacy) { LegalScreen(nav, "legal/privacy.html", "PRIVACY") }
        composable(Routes.Editor) { EditorScreen(nav, vm) }
        composable(
            Routes.Ai,
            arguments = listOf(navArgument("mode") { type = NavType.StringType }),
        ) { entry ->
            val mode = entry.arguments?.getString("mode") ?: "discuss"
            if (mode != "create" && mode != "edit" && mode != "discuss") {
                LaunchedEffect(mode) { nav.navigate(Routes.Home) { popUpTo(Routes.Home) { inclusive = true } } }
            } else {
                AiChatScreen(mode, nav, vm)
            }
        }
    }
}
