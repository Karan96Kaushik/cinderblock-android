package app.cinderblock.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.cinderblock.CinderViewModel
import app.cinderblock.data.setRecovery
import app.cinderblock.domain.UiEffect
import app.cinderblock.ui.auth.LoginScreen
import app.cinderblock.ui.editor.EditorScreen
import app.cinderblock.ui.ai.AiChatScreen
import app.cinderblock.ui.gym.CalendarScreen
import app.cinderblock.ui.gym.ExploreScreen
import app.cinderblock.ui.gym.PickerScreen
import app.cinderblock.ui.gym.SessionScreen
import app.cinderblock.ui.home.HomeScreen
import app.cinderblock.ui.metrics.MetricsScreen
import app.cinderblock.ui.nav.Routes
import app.cinderblock.ui.nav.recoveryAccessToken
import app.cinderblock.ui.nav.routeFromUri
import app.cinderblock.ui.running.RunPlanScreen
import app.cinderblock.ui.running.RunSessionScreen
import app.cinderblock.ui.settings.SettingsScreen
import app.cinderblock.ui.theme.CinderTheme
import app.cinderblock.ui.theme.LocalColors

@Composable
fun CinderApp(vm: CinderViewModel = viewModel()) {
    val state by vm.controller.state.collectAsState()
    CinderTheme(state.settings.theme, state.settings.fontPreset, state.settings.fontSize) {
        val colors = LocalColors.current
        val view = LocalView.current
        val holdAwake = state.settings.alwaysAwake && (state.rest.running || state.activeRun?.running == true)
        DisposableEffect(holdAwake) {
            val window = (view.context as Activity).window
            if (holdAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        Box(Modifier.fillMaxSize().background(colors.background)) {
            if (!state.ready) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    app.cinderblock.ui.components.DisplayText("CINDERBLOCK", size = 22.sp)
                }
            } else {
                val nav = rememberNavController()
                val snackbar = remember { SnackbarHostState() }
                val context = LocalContext.current
                val start = remember {
                    val needsLogin = state.cloud.configured && state.auth == null && !state.skipLogin
                    if (needsLogin) Routes.Login else Routes.Home
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
                CinderNav(nav, vm, start)
                SnackbarHost(snackbar)
            }
        }
    }
}

@Composable
private fun CinderNav(nav: NavHostController, vm: CinderViewModel, start: String) {
    NavHost(nav, startDestination = start) {
        composable(Routes.Home) { HomeScreen(nav, vm) }
        composable(Routes.Login) { LoginScreen(nav, vm) }
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
            arguments = listOf(navArgument("date") { type = NavType.StringType }),
        ) { entry -> SessionScreen(entry.arguments?.getString("date").orEmpty(), nav, vm) }
        composable(Routes.Explore) { ExploreScreen(null, nav, vm) }
        composable(
            Routes.ExploreOne,
            arguments = listOf(navArgument("key") { type = NavType.StringType }),
        ) { entry -> ExploreScreen(entry.arguments?.getString("key"), nav, vm) }
        composable(Routes.Running) { RunPlanScreen(nav, vm) }
        composable(Routes.RunSession) { RunSessionScreen(nav, vm) }
        composable(Routes.Metrics) { MetricsScreen(nav, vm) }
        composable(Routes.Settings) { SettingsScreen(nav, vm) }
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
