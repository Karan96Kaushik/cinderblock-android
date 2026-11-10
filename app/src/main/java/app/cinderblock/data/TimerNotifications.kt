package app.cinderblock.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import app.cinderblock.CinderblockApp
import app.cinderblock.MainActivity
import app.cinderblock.R
import app.cinderblock.domain.AppState
import app.cinderblock.domain.DayStatus
import app.cinderblock.domain.ExerciseStatus
import app.cinderblock.domain.PHASES
import app.cinderblock.domain.dayStatus
import app.cinderblock.domain.exerciseStatus
import app.cinderblock.domain.logLabel
import java.time.LocalDate
import app.cinderblock.domain.RunLog
import app.cinderblock.domain.RunningPlan
import app.cinderblock.domain.formatClock
import app.cinderblock.domain.isAddressed
import app.cinderblock.domain.isTimedHold
import app.cinderblock.domain.loggedSetCount
import app.cinderblock.domain.phaseLabel
import app.cinderblock.domain.planTotalMinutes
import app.cinderblock.domain.resolveProgram
import app.cinderblock.ui.nav.Routes
import app.cinderblock.ui.theme.colorsFor
import kotlinx.coroutines.launch

internal const val ACTION_REST_PAUSE = "app.cinderblock.rest.PAUSE"
internal const val ACTION_REST_RESUME = "app.cinderblock.rest.RESUME"
internal const val ACTION_REST_ADD = "app.cinderblock.rest.ADD"
internal const val ACTION_REST_SKIP = "app.cinderblock.rest.SKIP"
internal const val ACTION_REST_AGAIN = "app.cinderblock.rest.AGAIN"
internal const val ACTION_REST_START = "app.cinderblock.rest.START"
internal const val ACTION_EXERCISE_DONE = "app.cinderblock.workout.DONE"
internal const val ACTION_RUN_PAUSE = "app.cinderblock.run.PAUSE"
internal const val ACTION_RUN_RESUME = "app.cinderblock.run.RESUME"
internal const val ACTION_RUN_SKIP = "app.cinderblock.run.SKIP"

internal data class TimerPalette(
    val accent: Int,
    val phases: List<Int>,
    val point: Int,
    val logged: Int,
    val skipped: Int,
    val pending: Int,
)

internal data class SessionNotice(
    val date: String,
    val label: String,
    val statuses: List<ExerciseStatus>,
    val current: String?,
    val currentDetail: String?,
    val defaultRestSeconds: Int,
    val rest: RestNotice?,
    val palette: TimerPalette,
) {
    val addressed: Int get() = statuses.count { it == ExerciseStatus.Done || it == ExerciseStatus.Skipped }
}

internal data class RestNotice(
    val exercise: String,
    val date: String,
    val running: Boolean,
    val finished: Boolean,
    val endsAt: Long?,
    val remainingSeconds: Int,
    val durationSeconds: Int,
    val cue: String,
    val palette: TimerPalette,
)

internal data class RunNotice(
    val plan: RunningPlan,
    val phaseIndex: Int,
    val running: Boolean,
    val phaseEndsAt: Long?,
    val remainingSeconds: Int,
    val endedEarly: Boolean,
    val completed: RunLog?,
    val palette: TimerPalette,
)

private fun paletteFor(theme: String): TimerPalette {
    val colors = colorsFor(theme)
    return TimerPalette(
        accent = colors.neonOrange.toArgb(),
        phases = listOf(colors.neonAmber.toArgb(), colors.neonOrange.toArgb(), colors.neonYellow.toArgb()),
        point = colors.foreground.toArgb(),
        logged = colors.neonYellow.toArgb(),
        skipped = colors.neonRed.toArgb(),
        pending = colors.mutedForeground.toArgb(),
    )
}

private const val ACTIVE_WINDOW_MS = 3 * 60 * 60 * 1000L

// A workout counts as active while today's (or last night's) session is unfinished and was touched recently,
// or while a rest timer is running for it.
internal fun sessionNotice(state: AppState, now: Long): SessionNotice? {
    if (!state.ready) return null
    val rest = restNotice(state)?.takeIf { !it.finished }
    val today = LocalDate.now()
    val recentDays = setOf(today.toString(), today.minusDays(1).toString())
    val date = rest?.date ?: state.gymLog.entries
        .filter { (key, log) -> key in recentDays && dayStatus(log) == DayStatus.Partial && now - log.updatedAt < ACTIVE_WINDOW_MS }
        .maxByOrNull { it.value.updatedAt }
        ?.key
        ?: return null
    val day = state.gymLog[date] ?: return null
    val exercises = resolveProgram(state, day).first.workouts[day.workoutKey]?.exercises.orEmpty()
    if (exercises.isEmpty()) return null
    val current = rest?.let { r -> exercises.firstOrNull { it.name == r.exercise } }
        ?: exercises.firstOrNull { it.name == state.sessionExercise[date] }
        ?: exercises.firstOrNull { !isAddressed(day.exercises[it.name]) }
    val detail = current?.let { definition ->
        day.exercises[definition.name]?.let { log ->
            val next = loggedSetCount(log, isTimedHold(definition)) + 1
            if (next <= definition.sets) "set $next of ${definition.sets}" else "all ${definition.sets} sets logged"
        }
    }
    return SessionNotice(
        date = date,
        label = logLabel(resolveProgram(state, day).first, day),
        statuses = exercises.map { exerciseStatus(day.exercises[it.name]) },
        current = current?.name,
        currentDetail = detail,
        defaultRestSeconds = (state.settings.restTimerMinutes * 60).toInt().coerceAtLeast(1),
        rest = rest,
        palette = paletteFor(state.settings.theme),
    )
}

internal fun restNotice(state: AppState): RestNotice? {
    val rest = state.rest
    val owner = rest.ownerExercise ?: return null
    val date = rest.ownerDate ?: return null
    val day = state.gymLog[date]
    val definition = day?.let { resolveProgram(state, it).first.workouts[it.workoutKey] }?.exercises?.firstOrNull { it.name == owner }
    val log = day?.exercises?.get(owner)
    val cue = when {
        definition == null || log == null -> "Back to $owner"
        isAddressed(log) -> "On to the next exercise"
        else -> {
            val next = loggedSetCount(log, isTimedHold(definition)) + 1
            if (next <= definition.sets) "Up next: set $next of ${definition.sets}" else "Finish $owner"
        }
    }
    return RestNotice(
        exercise = owner,
        date = date,
        running = rest.running,
        finished = rest.announced || (!rest.running && rest.remainingSeconds <= 0),
        endsAt = rest.endsAt,
        remainingSeconds = rest.remainingSeconds,
        durationSeconds = rest.durationSeconds,
        cue = cue,
        palette = paletteFor(state.settings.theme),
    )
}

internal fun runNotice(state: AppState): RunNotice? {
    val palette = paletteFor(state.settings.theme)
    state.runCompletion?.let { completion ->
        return RunNotice(completion.log.plan, PHASES.lastIndex, false, null, 0, completion.endedEarly, completion.log, palette)
    }
    val run = state.activeRun?.takeIf { it.started } ?: return null
    return RunNotice(
        plan = run.plan,
        phaseIndex = run.phaseIndex.coerceIn(0, PHASES.lastIndex),
        running = run.running,
        phaseEndsAt = run.phaseEndsAt,
        remainingSeconds = run.remainingSeconds,
        endedEarly = run.runEndedEarly,
        completed = null,
        palette = palette,
    )
}

internal class TimerNotifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)
    private val live = mutableMapOf<Int, android.app.Notification>()
    private var session: SessionNotice? = null
    private var alertRest: RestNotice? = null
    private var run: RunNotice? = null
    private var lastRefresh = 0L

    fun createChannels() {
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        // Older builds used a low-importance channel, which Android hides from the lock screen
        // and will not upgrade in place.
        system.deleteNotificationChannel("rest_timer")
        system.deleteNotificationChannel("rest_done")
        system.createNotificationChannel(
            NotificationChannel(LIVE_CHANNEL, "Live workout & timers", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Workout progress, rest and run countdowns while they are active"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
        system.createNotificationChannel(
            NotificationChannel(DONE_CHANNEL, "Timer alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alert when a rest or run finishes"
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
    }

    fun renderSession(notice: SessionNotice?) {
        session = notice
        if (notice == null || !manager.areNotificationsEnabled()) {
            cancelLive(SESSION_ID)
        } else {
            postLive(SESSION_ID, sessionLive(notice, System.currentTimeMillis()))
        }
    }

    fun renderRestAlert(notice: RestNotice?, inForeground: Boolean) {
        val previous = alertRest
        alertRest = notice
        when {
            notice == null || !notice.finished -> manager.cancel(ALERT_ID)
            inForeground || previous == null || previous.finished || !manager.areNotificationsEnabled() -> Unit
            else -> post(ALERT_ID, restDone(notice))
        }
    }

    fun renderRun(notice: RunNotice?, inForeground: Boolean) {
        val previous = run
        run = notice
        when {
            notice == null || !manager.areNotificationsEnabled() -> cancelLive(RUN_ID)
            notice.completed == null -> postLive(RUN_ID, runLive(notice, System.currentTimeMillis()))
            inForeground || previous == null || previous.completed != null -> if (inForeground) cancelLive(RUN_ID)
            else -> {
                live.remove(RUN_ID)
                post(RUN_ID, runDone(notice, notice.completed))
                syncForeground()
            }
        }
    }

    // The chronometer ticks on its own, but the progress bar only moves when the notification is re-posted.
    // Once the end time is reached the countdown is replaced immediately so it cannot keep counting below zero.
    fun tick(now: Long) {
        if (!manager.areNotificationsEnabled()) return
        val restExpired = session?.rest?.let { it.running && it.endsAt != null && it.endsAt <= now } == true
        val runExpired = run?.let { it.running && it.completed == null && it.phaseEndsAt != null && it.phaseEndsAt <= now } == true
        if (!restExpired && !runExpired && now - lastRefresh < REFRESH_MS) return
        lastRefresh = now
        session?.takeIf { it.rest?.running == true }?.let { postLive(SESSION_ID, sessionLive(it, now)) }
        run?.takeIf { it.running && it.completed == null }?.let { postLive(RUN_ID, runLive(it, now)) }
    }

    private fun sessionLive(session: SessionNotice, now: Long): android.app.Notification {
        val total = session.statuses.size
        val summary = "${session.label} · ${session.addressed}/$total"
        session.rest?.let { return restLive(it, summary, now) }
        val builder = liveBuilder(R.drawable.ic_stat_workout, session.palette.accent, Routes.workoutDeepLink(session.date, session.current))
            .setContentTitle("${session.label} · ${session.addressed} of $total done")
            .setContentText(
                session.current?.let { name -> "Now: $name" + (session.currentDetail?.let { " · $it" } ?: "") }
                    ?: "All exercises addressed — finish up",
            )
            .setSubText("Workout")
            .setShowWhen(false)
            .setShortCriticalText("${session.addressed}/$total")
        if (session.current != null) {
            builder.addAction(action("Rest ${formatClock(session.defaultRestSeconds)}", ACTION_REST_START))
                .addAction(action("Mark done", ACTION_EXERCISE_DONE))
        }
        val segments = if (total <= MAX_SEGMENTS) {
            session.statuses.map { status ->
                1 to when (status) {
                    ExerciseStatus.Done -> session.palette.accent
                    ExerciseStatus.Skipped -> session.palette.skipped
                    ExerciseStatus.Logged -> session.palette.logged
                    ExerciseStatus.Pending -> session.palette.pending
                }
            }
        } else {
            listOf(total to session.palette.accent)
        }
        return builder.progress(
            segments = segments,
            points = emptyList(),
            elapsed = session.addressed,
            tracker = R.drawable.ic_stat_workout,
            styledByProgress = total > MAX_SEGMENTS,
        ).build()
    }

    private fun restLive(notice: RestNotice, summary: String, now: Long): android.app.Notification {
        val counting = notice.running && notice.endsAt != null && notice.endsAt > now
        val remaining = when {
            counting -> secondsUntil(notice.endsAt!!, now)
            notice.running -> 0
            else -> notice.remainingSeconds.coerceAtLeast(0)
        }
        val duration = notice.durationSeconds.coerceAtLeast(1)
        val elapsed = (duration - remaining).coerceIn(0, duration)
        val builder = liveBuilder(R.drawable.ic_stat_rest, notice.palette.accent, Routes.workoutDeepLink(notice.date, notice.exercise))
            .setContentTitle(
                when {
                    counting -> "Resting · ${notice.exercise}"
                    notice.running -> "Rest · ${notice.exercise}"
                    else -> "Rest paused · ${notice.exercise}"
                },
            )
            .setContentText(if (counting) notice.cue else "${formatClock(remaining)} left · ${notice.cue}")
            .setSubText(summary)
        if (counting) {
            builder.countdownTo(notice.endsAt!!).addAction(action("Pause", ACTION_REST_PAUSE))
        } else {
            builder.setShowWhen(false).setUsesChronometer(false).setShortCriticalText(formatClock(remaining))
                .addAction(action(if (notice.running) "Pause" else "Resume", if (notice.running) ACTION_REST_PAUSE else ACTION_REST_RESUME))
        }
        builder.addAction(action("+15s", ACTION_REST_ADD)).addAction(action("Skip", ACTION_REST_SKIP))
        return builder.progress(
            segments = listOf(duration to notice.palette.accent),
            points = emptyList(),
            elapsed = elapsed,
            tracker = R.drawable.ic_stat_rest,
        ).build()
    }

    private fun restDone(notice: RestNotice): android.app.Notification =
        alertBuilder(R.drawable.ic_stat_rest, notice.palette.accent, Routes.workoutDeepLink(notice.date, notice.exercise))
            .setContentTitle("Rest complete — go!")
            .setContentText("${notice.exercise} · ${notice.cue}")
            .addAction(action("Again ${formatClock(notice.durationSeconds)}", ACTION_REST_AGAIN))
            .addAction(action("Got it", ACTION_REST_SKIP))
            .build()

    private fun runLive(notice: RunNotice, now: Long): android.app.Notification {
        val lengths = phaseSeconds(notice.plan)
        val index = notice.phaseIndex
        val counting = notice.running && notice.phaseEndsAt != null && notice.phaseEndsAt > now
        val remaining = when {
            counting -> secondsUntil(notice.phaseEndsAt!!, now)
            notice.running -> 0
            else -> notice.remainingSeconds.coerceAtLeast(0)
        }
        val elapsed = lengths.take(index).sum() + (lengths[index] - remaining).coerceIn(0, lengths[index])
        val next = PHASES.getOrNull(index + 1)
        val builder = liveBuilder(R.drawable.ic_stat_run, notice.palette.accent, "/running/session")
            .setContentTitle(if (notice.running) phaseLabel(PHASES[index]) else "${phaseLabel(PHASES[index])} · paused")
            .setContentText(
                when {
                    !notice.running -> "${formatClock(remaining)} left in this phase"
                    next != null -> "Then ${phaseLabel(next).lowercase()} · ${lengths[index + 1] / 60} min"
                    else -> "Last stretch — cool down"
                },
            )
            .setSubText("Run ${notice.plan.warmupMinutes} · ${notice.plan.runMinutes} · ${notice.plan.cooldownMinutes}")
        if (counting) {
            builder.countdownTo(notice.phaseEndsAt!!).addAction(action("Pause", ACTION_RUN_PAUSE))
        } else {
            builder.setShowWhen(false).setUsesChronometer(false).setShortCriticalText(formatClock(remaining))
                .addAction(action(if (notice.running) "Pause" else "Resume", if (notice.running) ACTION_RUN_PAUSE else ACTION_RUN_RESUME))
        }
        builder.addAction(action(if (next != null) "Skip to ${phaseLabel(next).lowercase()}" else "Finish", ACTION_RUN_SKIP))
        val boundaries = lengths.runningFold(0) { total, length -> total + length }.drop(1).dropLast(1)
        return builder.progress(
            segments = lengths.mapIndexed { i, length -> length to notice.palette.phases[i] },
            points = boundaries.map { it to notice.palette.point },
            elapsed = elapsed,
            tracker = R.drawable.ic_stat_run,
        ).build()
    }

    private fun runDone(notice: RunNotice, log: RunLog): android.app.Notification =
        alertBuilder(R.drawable.ic_stat_run, notice.palette.accent, "/running/session")
            .setContentTitle(if (notice.endedEarly) "Run ended" else "Run complete")
            .setContentText("${planTotalMinutes(log.plan)} min · warmup ${log.plan.warmupMinutes}, run ${log.plan.runMinutes}, cooldown ${log.plan.cooldownMinutes}")
            .build()

    private fun liveBuilder(icon: Int, accent: Int, path: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, LIVE_CHANNEL)
            .setSmallIcon(icon)
            .setColor(accent)
            .setContentIntent(open(path))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setRequestPromotedOngoing(true)

    private fun alertBuilder(icon: Int, accent: Int, path: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, DONE_CHANNEL)
            .setSmallIcon(icon)
            .setColor(accent)
            .setContentIntent(open(path))
            .setAutoCancel(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setTimeoutAfter(5 * 60_000L)

    private fun NotificationCompat.Builder.countdownTo(endsAt: Long): NotificationCompat.Builder =
        setWhen(endsAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)

    private fun NotificationCompat.Builder.progress(
        segments: List<Pair<Int, Int>>,
        points: List<Pair<Int, Int>>,
        elapsed: Int,
        tracker: Int,
        styledByProgress: Boolean = true,
    ): NotificationCompat.Builder {
        if (Build.VERSION.SDK_INT >= 36) {
            setStyle(
                NotificationCompat.ProgressStyle()
                    .setStyledByProgress(styledByProgress)
                    .setProgressSegments(segments.map { (length, color) -> NotificationCompat.ProgressStyle.Segment(length).setColor(color) })
                    .setProgressPoints(points.map { (position, color) -> NotificationCompat.ProgressStyle.Point(position).setColor(color) })
                    .setProgress(elapsed)
                    .setProgressTrackerIcon(IconCompat.createWithResource(context, tracker))
                    .setProgressEndIcon(IconCompat.createWithResource(context, R.drawable.ic_flag)),
            )
        } else {
            setProgress(segments.sumOf { it.first }, elapsed, false)
        }
        return this
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun post(id: Int, notification: android.app.Notification) {
        runCatching { manager.notify(id, notification) }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun postLive(id: Int, notification: android.app.Notification) {
        live[id] = notification
        post(id, notification)
        syncForeground()
    }

    private fun cancelLive(id: Int) {
        live.remove(id)
        manager.cancel(id)
        syncForeground()
    }

    private fun syncForeground() {
        val id = when {
            session?.rest?.running == true && SESSION_ID in live -> SESSION_ID
            run?.running == true && run?.completed == null && RUN_ID in live -> RUN_ID
            SESSION_ID in live -> SESSION_ID
            RUN_ID in live -> RUN_ID
            else -> null
        }
        val notification = id?.let { live[it] }
        if (id == null || notification == null) LiveUpdateService.stop() else LiveUpdateService.promote(context, id, notification)
    }

    private fun open(path: String): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("cinderblock://app$path"), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, path.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun action(label: String, name: String): NotificationCompat.Action =
        NotificationCompat.Action.Builder(null as IconCompat?, label, restActionIntent(context, name)).build()

    private companion object {
        const val LIVE_CHANNEL = "workout_live"
        const val DONE_CHANNEL = "timer_alerts"
        const val SESSION_ID = 7001
        const val RUN_ID = 7002
        const val ALERT_ID = 7003
        const val REFRESH_MS = 2_000L
        const val MAX_SEGMENTS = 15

        fun phaseSeconds(plan: RunningPlan) =
            listOf(plan.warmupMinutes, plan.runMinutes, plan.cooldownMinutes).map { (it * 60).coerceAtLeast(1) }

        fun secondsUntil(endsAt: Long, now: Long) = ((endsAt - now + 999L) / 1000L).toInt().coerceAtLeast(0)
    }
}

internal fun restActionIntent(context: Context, name: String): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        name.hashCode(),
        Intent(context, TimerActionReceiver::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

class LiveUpdateService : Service() {
    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = pendingId
        val notification = pendingNotification
        if (id == null || notification == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!showForeground(id, notification) || stopping) {
            retire()
            return START_NOT_STICKY
        }
        running = true
        foregroundId = id
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        foregroundId = null
        if (instance == this) instance = null
        super.onDestroy()
    }

    private fun showForeground(id: Int, notification: android.app.Notification): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(id, notification)
        }
        true
    } catch (_: Exception) {
        false
    }

    private fun retire() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) runCatching { stopForeground(STOP_FOREGROUND_DETACH) }
        running = false
        foregroundId = null
        stopping = false
        stopSelf()
    }

    companion object {
        @Volatile var pendingId: Int? = null
        @Volatile var pendingNotification: android.app.Notification? = null
        @Volatile var running = false
        @Volatile var foregroundId: Int? = null
        @Volatile var stopping = false
        @Volatile private var instance: LiveUpdateService? = null

        fun promote(context: Context, id: Int, notification: android.app.Notification) {
            stopping = false
            pendingId = id
            pendingNotification = notification
            val active = instance
            if (active != null && running && foregroundId == id) return
            runCatching {
                if (active != null && running) {
                    if (active.showForeground(id, notification)) foregroundId = id
                } else {
                    ContextCompat.startForegroundService(context, Intent(context, LiveUpdateService::class.java))
                }
            }
        }

        fun stop() {
            if (!running && instance == null && pendingId == null) return
            stopping = true
            instance?.retire()
        }
    }
}

class TimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val controller = (context.applicationContext as CinderblockApp).controller
        val pending = goAsync()
        controller.scope.launch {
            try {
                controller.handleTimerAction(intent.action)
            } finally {
                pending.finish()
            }
        }
    }
}
