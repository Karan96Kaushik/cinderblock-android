package app.cinderblock.ui.nav

import android.net.Uri
import androidx.navigation.NavHostController
import app.cinderblock.domain.parseDate

object Routes {
    const val Home = "home"
    const val Login = "login"
    const val Gym = "gym?date={date}"
    const val Select = "gym/{date}/select"
    const val Workout = "gym/{date}/workout"
    const val Explore = "gym/explore"
    const val ExploreOne = "gym/explore/{key}"
    const val Running = "running"
    const val RunSession = "running/session"
    const val Metrics = "metrics"
    const val Settings = "settings"
    const val Editor = "program-editor"
    const val Ai = "ai/{mode}"

    fun gym(date: String?) = if (date.isNullOrBlank()) "gym" else "gym?date=$date"
    fun select(date: String) = "gym/$date/select"
    fun workout(date: String) = "gym/$date/workout"
    fun explore(key: String? = null) = if (key.isNullOrBlank()) Explore else "gym/explore/${Uri.encode(key)}"
    fun ai(mode: String) = "ai/$mode"
}

fun NavHostController.goHome() {
    navigate(Routes.Home) {
        popUpTo(Routes.Home) { inclusive = true }
        launchSingleTop = true
    }
}

fun NavHostController.openFromHome(route: String) {
    navigate(route) {
        popUpTo(Routes.Home) { inclusive = false }
        launchSingleTop = true
    }
}

fun routeFromUri(uri: Uri): String {
    if (uri.scheme == "cinderblock" && uri.host == "login") return Routes.Login
    val raw = uri.path?.trimEnd('/').orEmpty().ifEmpty { "/" }
    return when {
        raw == "/" -> Routes.Home
        raw == "/login" -> Routes.Login
        raw == "/gym" -> "gym"
        raw == "/gym/explore" -> Routes.Explore
        raw.startsWith("/gym/explore/") -> {
            val key = raw.removePrefix("/gym/explore/").substringBefore("/")
            if (key.isBlank()) Routes.Home else Routes.explore(key)
        }
        raw.startsWith("/gym/") -> {
            val parts = raw.removePrefix("/gym/").split("/")
            val date = parts.firstOrNull()
            if (parseDate(date) == null) "gym"
            else when (parts.getOrNull(1)) {
                null -> Routes.gym(date)
                "select" -> Routes.select(date!!)
                "workout" -> Routes.workout(date!!)
                else -> Routes.Home
            }
        }
        raw == "/running" -> Routes.Running
        raw == "/running/session" -> Routes.RunSession
        raw == "/metrics" -> Routes.Metrics
        raw == "/settings" -> Routes.Settings
        raw == "/program-editor" -> Routes.Editor
        raw.startsWith("/ai-chat/") -> {
            val mode = raw.removePrefix("/ai-chat/").substringBefore("/")
            if (mode == "create" || mode == "edit" || mode == "discuss") Routes.ai(mode) else Routes.Home
        }
        else -> Routes.Home
    }
}

fun recoveryAccessToken(uri: Uri): String? {
    val source = uri.fragment ?: return null
    val params = source.split("&").mapNotNull { part ->
        val bits = part.split("=", limit = 2)
        if (bits.size == 2) bits[0] to Uri.decode(bits[1]) else null
    }.toMap()
    return if (params["type"] == "recovery") params["access_token"] else null
}
