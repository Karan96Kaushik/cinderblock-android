package com.bayonetbarontech.cinderblock.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.bayonetbarontech.cinderblock.domain.HapticKind
import com.bayonetbarontech.cinderblock.ui.components.Mono
import com.bayonetbarontech.cinderblock.ui.theme.LocalColors

private data class DockTab(val label: String, val icon: ImageVector, val route: String)

private val tabs = listOf(
    DockTab("Home", Icons.Default.Home, Routes.Home),
    DockTab("Train", Icons.Default.FitnessCenter, "gym"),
    DockTab("Progress", Icons.Default.BarChart, Routes.Metrics),
    DockTab("Coach", Icons.Default.AutoAwesome, Routes.ai("discuss")),
)

fun showsDock(route: String?, mode: String?): Boolean {
    if (route == null || route == Routes.Login || route == Routes.Setup) return false
    if (route == Routes.Terms || route == Routes.Privacy) return false
    if (route == Routes.Workout || route == Routes.Select || route == Routes.RunSession || route == Routes.Editor) return false
    if (route == Routes.Ai && mode != null && mode != "discuss") return false
    return true
}

private fun selectedIndex(route: String?): Int = when {
    route == null -> 0
    route.startsWith("gym") || route.startsWith("running") -> 1
    route == Routes.Metrics -> 2
    route == Routes.Ai || route.startsWith("ai/") -> 3
    else -> 0
}

@Composable
fun BottomDock(nav: NavHostController, route: String?, haptic: (HapticKind) -> Unit) {
    val colors = LocalColors.current
    val selected = selectedIndex(route)
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(colors.card)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            val on = index == selected && route != Routes.Settings
            val tint = if (on) colors.primaryForeground else colors.mutedForeground
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (on) colors.primary else colors.card)
                    .clickable {
                        if (on) return@clickable
                        haptic(HapticKind.Selection)
                        if (tab.route == Routes.Home) nav.goHome() else nav.openFromHome(tab.route)
                    }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(tab.icon, tab.label, tint = tint, modifier = Modifier.size(20.dp))
                Mono(tab.label, color = tint, size = 11.sp, weight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}
