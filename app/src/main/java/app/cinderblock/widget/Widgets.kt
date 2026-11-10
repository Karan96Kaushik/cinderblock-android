package app.cinderblock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import app.cinderblock.CinderblockApp
import app.cinderblock.MainActivity
import app.cinderblock.R
import app.cinderblock.data.ACTION_REST_ADD
import app.cinderblock.data.ACTION_REST_AGAIN
import app.cinderblock.data.ACTION_REST_PAUSE
import app.cinderblock.data.ACTION_REST_RESUME
import app.cinderblock.data.ACTION_REST_SKIP
import app.cinderblock.data.restActionIntent
import app.cinderblock.domain.AppState
import app.cinderblock.domain.DayStatus
import app.cinderblock.domain.PHASES
import app.cinderblock.domain.currentWeekRuns
import app.cinderblock.domain.dateInRange
import app.cinderblock.domain.dayStatus
import app.cinderblock.domain.formatClock
import app.cinderblock.domain.formatMediumDate
import app.cinderblock.domain.isAddressed
import app.cinderblock.domain.logLabel
import app.cinderblock.domain.parseDate
import app.cinderblock.domain.phaseLabel
import app.cinderblock.domain.unfinishedSession
import app.cinderblock.domain.weekRange
import app.cinderblock.ui.nav.Routes
import app.cinderblock.ui.theme.colorsFor
import java.time.LocalDate

internal data class WidgetRest(
    val exercise: String,
    val date: String,
    val running: Boolean,
    val finished: Boolean,
    val endsAt: Long?,
    val remainingSeconds: Int,
    val durationSeconds: Int,
)

internal data class WidgetRun(val phase: String, val running: Boolean, val endsAt: Long?, val remainingSeconds: Int)

internal data class WidgetSnapshot(
    val ready: Boolean,
    val theme: String,
    val title: String,
    val detail: String,
    val todayPath: String,
    val week: String,
    val rest: WidgetRest?,
    val run: WidgetRun?,
)

internal fun widgetSnapshot(state: AppState, today: LocalDate = LocalDate.now()): WidgetSnapshot {
    if (!state.ready) {
        return WidgetSnapshot(false, state.settings.theme, "Open Cinderblock", "Tap to load your training", "/", "", null, null)
    }
    val program = state.program
    val todayKey = today.toString()
    val todayLog = state.gymLog[todayKey]
    val unfinished = unfinishedSession(state.gymLog)
    val (title, detail, path) = when {
        todayLog != null -> {
            val label = logLabel(program, todayLog)
            val total = todayLog.exercises.size
            val done = todayLog.exercises.values.count { isAddressed(it) }
            when (dayStatus(todayLog)) {
                DayStatus.Rest -> Triple(label, "Recovery day — take it easy", "/gym")
                DayStatus.Complete -> Triple(label, "Workout complete · $total exercises", Routes.workoutDeepLink(todayKey, state.sessionExercise[todayKey]))
                DayStatus.Partial -> Triple(label, "$done of $total exercises done", Routes.workoutDeepLink(todayKey, state.sessionExercise[todayKey]))
                DayStatus.Empty -> Triple(label, "Not started yet", Routes.workoutDeepLink(todayKey, state.sessionExercise[todayKey]))
            }
        }
        unfinished != null -> {
            val date = parseDate(unfinished.first)?.let(::formatMediumDate) ?: unfinished.first
            Triple(
                "Continue ${logLabel(program, unfinished.second)}",
                "Unfinished from $date",
                Routes.workoutDeepLink(unfinished.first, state.sessionExercise[unfinished.first]),
            )
        }
        else -> Triple("Nothing logged today", "Tap Train to pick a workout", "/gym")
    }
    val (weekStart, weekEnd) = weekRange(today)
    val workouts = state.gymLog.count { (date, log) -> dateInRange(date, weekStart, weekEnd) && dayStatus(log) == DayStatus.Complete }
    val runs = currentWeekRuns(state.runLog, today).size
    val week = "$workouts workout${if (workouts == 1) "" else "s"} · $runs run${if (runs == 1) "" else "s"} this week"
    val rest = state.rest.let { r ->
        val owner = r.ownerExercise ?: return@let null
        val date = r.ownerDate ?: return@let null
        WidgetRest(
            exercise = owner,
            date = date,
            running = r.running,
            finished = r.announced || (!r.running && r.remainingSeconds <= 0),
            endsAt = r.endsAt,
            remainingSeconds = r.remainingSeconds,
            durationSeconds = r.durationSeconds,
        )
    }
    val run = state.activeRun?.takeIf { it.started && state.runCompletion == null }?.let {
        WidgetRun(phaseLabel(PHASES[it.phaseIndex.coerceIn(0, PHASES.lastIndex)]), it.running, it.phaseEndsAt, it.remainingSeconds)
    }
    return WidgetSnapshot(true, state.settings.theme, title, detail, path, week, rest, run)
}

internal object Widgets {
    fun update(context: Context, snapshot: WidgetSnapshot) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val today = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
        if (today.isNotEmpty()) manager.updateAppWidget(today, renderToday(context, snapshot))
        val rest = manager.getAppWidgetIds(ComponentName(context, RestWidget::class.java))
        if (rest.isNotEmpty()) manager.updateAppWidget(rest, renderRest(context, snapshot))
    }

    private fun renderToday(context: Context, s: WidgetSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_today)
        val palette = applyTheme(views, s.theme)
        views.setTextColor(R.id.widget_brand, palette.accent)
        views.setTextColor(R.id.widget_week, palette.muted)
        views.setTextColor(R.id.widget_title, palette.foreground)
        views.setTextColor(R.id.widget_detail, palette.muted)
        listOf(R.id.widget_live_title, R.id.widget_live_clock, R.id.widget_live_static).forEach { views.setTextColor(it, palette.foreground) }
        listOf(R.id.widget_btn_train, R.id.widget_btn_run, R.id.widget_btn_metrics).forEach { views.setTextColor(it, palette.accent) }

        views.setTextViewText(R.id.widget_week, s.week)
        views.setTextViewText(R.id.widget_title, s.title)
        views.setTextViewText(R.id.widget_detail, s.detail)
        views.setOnClickPendingIntent(android.R.id.background, openPath(context, s.todayPath))
        views.setOnClickPendingIntent(R.id.widget_btn_train, openPath(context, s.todayPath.takeIf { it.contains("/workout") } ?: "/gym"))
        views.setOnClickPendingIntent(R.id.widget_btn_run, openPath(context, if (s.run != null) "/running/session" else "/running"))
        views.setOnClickPendingIntent(R.id.widget_btn_metrics, openPath(context, "/metrics"))

        val rest = s.rest
        val run = s.run
        when {
            rest != null -> {
                views.setViewVisibility(R.id.widget_live, View.VISIBLE)
                views.setTextViewText(R.id.widget_live_title, if (rest.finished) "Rest complete · ${rest.exercise}" else "Resting · ${rest.exercise}")
                showClock(views, R.id.widget_live_clock, R.id.widget_live_static, rest.running, rest.endsAt, if (rest.finished) "GO" else formatClock(rest.remainingSeconds))
                views.setOnClickPendingIntent(R.id.widget_live, openPath(context, Routes.workoutDeepLink(rest.date, rest.exercise)))
            }
            run != null -> {
                views.setViewVisibility(R.id.widget_live, View.VISIBLE)
                views.setTextViewText(R.id.widget_live_title, if (run.running) "Run · ${run.phase}" else "Run paused · ${run.phase}")
                showClock(views, R.id.widget_live_clock, R.id.widget_live_static, run.running, run.endsAt, formatClock(run.remainingSeconds))
                views.setOnClickPendingIntent(R.id.widget_live, openPath(context, "/running/session"))
            }
            else -> views.setViewVisibility(R.id.widget_live, View.GONE)
        }
        return views
    }

    private fun renderRest(context: Context, s: WidgetSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_rest)
        val palette = applyTheme(views, s.theme)
        views.setTextColor(R.id.rest_label, palette.accent)
        views.setTextColor(R.id.rest_title, palette.foreground)
        views.setTextColor(R.id.rest_clock, palette.foreground)
        listOf(R.id.rest_btn_1, R.id.rest_btn_2, R.id.rest_btn_3).forEach { views.setTextColor(it, palette.accent) }

        val rest = s.rest
        if (rest == null) {
            views.setTextViewText(R.id.rest_title, if (s.todayPath.contains("/workout")) s.title else "No rest running")
            views.setTextColor(R.id.rest_static, palette.muted)
            showClock(views, R.id.rest_clock, R.id.rest_static, false, null, "—:——")
            views.setOnClickPendingIntent(android.R.id.background, openPath(context, s.todayPath))
            button(views, R.id.rest_btn_1, "OPEN WORKOUT", openPath(context, s.todayPath.takeIf { it.contains("/workout") } ?: "/gym"))
            button(views, R.id.rest_btn_2, null, null)
            button(views, R.id.rest_btn_3, null, null)
            return views
        }

        val workout = openPath(context, Routes.workoutDeepLink(rest.date, rest.exercise))
        views.setOnClickPendingIntent(android.R.id.background, workout)
        views.setTextViewText(R.id.rest_title, if (rest.finished) "Rest complete · back to ${rest.exercise}" else rest.exercise)
        when {
            rest.finished -> {
                views.setTextColor(R.id.rest_static, palette.accent)
                showClock(views, R.id.rest_clock, R.id.rest_static, false, null, "GO")
                button(views, R.id.rest_btn_1, "AGAIN ${formatClock(rest.durationSeconds)}", restActionIntent(context, ACTION_REST_AGAIN))
                button(views, R.id.rest_btn_2, "GOT IT", restActionIntent(context, ACTION_REST_SKIP))
                button(views, R.id.rest_btn_3, null, null)
            }
            else -> {
                views.setTextColor(R.id.rest_static, palette.muted)
                showClock(views, R.id.rest_clock, R.id.rest_static, rest.running, rest.endsAt, formatClock(rest.remainingSeconds))
                button(
                    views,
                    R.id.rest_btn_1,
                    if (rest.running) "PAUSE" else "RESUME",
                    restActionIntent(context, if (rest.running) ACTION_REST_PAUSE else ACTION_REST_RESUME),
                )
                button(views, R.id.rest_btn_2, "+15S", restActionIntent(context, ACTION_REST_ADD))
                button(views, R.id.rest_btn_3, "SKIP", restActionIntent(context, ACTION_REST_SKIP))
            }
        }
        return views
    }

    private fun showClock(views: RemoteViews, clockId: Int, staticId: Int, running: Boolean, endsAt: Long?, staticText: String) {
        if (running && endsAt != null && endsAt > System.currentTimeMillis()) {
            val base = SystemClock.elapsedRealtime() + (endsAt - System.currentTimeMillis())
            views.setChronometer(clockId, base, null, true)
            views.setChronometerCountDown(clockId, true)
            views.setViewVisibility(clockId, View.VISIBLE)
            views.setViewVisibility(staticId, View.GONE)
        } else {
            views.setChronometer(clockId, SystemClock.elapsedRealtime(), null, false)
            views.setTextViewText(staticId, if (running && endsAt != null) "0:00" else staticText)
            views.setViewVisibility(clockId, View.GONE)
            views.setViewVisibility(staticId, View.VISIBLE)
        }
    }

    private fun button(views: RemoteViews, id: Int, label: String?, intent: PendingIntent?) {
        if (label == null || intent == null) {
            views.setViewVisibility(id, View.GONE)
            return
        }
        views.setViewVisibility(id, View.VISIBLE)
        views.setTextViewText(id, label)
        views.setOnClickPendingIntent(id, intent)
    }

    private data class Palette(val background: Int, val foreground: Int, val muted: Int, val accent: Int)

    private fun applyTheme(views: RemoteViews, theme: String): Palette {
        val colors = colorsFor(theme)
        val palette = Palette(colors.card.toArgb(), colors.foreground.toArgb(), colors.mutedForeground.toArgb(), colors.neonOrange.toArgb())
        if (Build.VERSION.SDK_INT >= 31) {
            views.setColorStateList(android.R.id.background, "setBackgroundTintList", ColorStateList.valueOf(palette.background))
        } else {
            views.setInt(android.R.id.background, "setBackgroundColor", palette.background)
        }
        return palette
    }

    private fun openPath(context: Context, path: String): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("cinderblock://app$path"), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, path.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

private fun refresh(context: Context) {
    val controller = (context.applicationContext as CinderblockApp).controller
    Widgets.update(context, widgetSnapshot(controller.state.value))
}

class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)
}

class RestWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)
}
