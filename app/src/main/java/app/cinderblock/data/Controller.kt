package app.cinderblock.data

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import app.cinderblock.domain.ActiveRun
import app.cinderblock.domain.AppState
import app.cinderblock.domain.AuthSession
import app.cinderblock.domain.CloudConfig
import app.cinderblock.domain.HapticKind
import app.cinderblock.domain.ProgramDocument
import app.cinderblock.domain.UiEffect
import app.cinderblock.domain.normalizeFontPreset
import app.cinderblock.domain.normalizeFontSize
import app.cinderblock.domain.normalizeTheme
import app.cinderblock.domain.projectRun
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

class Controller(private val context: Context) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(AppState())
    val state = _state.asStateFlow()
    private val _effects = MutableSharedFlow<UiEffect>(extraBufferCapacity = 32)
    val effects = _effects.asSharedFlow()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal val cloud = CloudClient()
    private val sounds = SoundPlayer()
    private val haptics = Haptics(context)
    private val file = File(context.filesDir, "cinderblock-state.json")
    private var syncJob: kotlinx.coroutines.Job? = null
    private var cloudOverride = CloudConfig()
    internal var chatStream: StreamHandle? = null
    internal var chatJob: kotlinx.coroutines.Job? = null
    internal var chatStopped = false

    fun start(override: CloudConfig) {
        cloudOverride = override
        createNotificationChannel()
        scope.launch { load() }
        scope.launch {
            while (isActive) {
                delay(250)
                tick(System.currentTimeMillis())
            }
        }
    }

    fun haptic(kind: HapticKind) = haptics.play(kind)

    fun playSound(kind: String) {
        val settings = _state.value.settings
        sounds.play(kind, settings.soundEnabled, settings.soundVolume)
    }

    internal fun effect(value: UiEffect) {
        _effects.tryEmit(value)
    }

    internal suspend fun edit(
        persist: Boolean = true,
        sync: Boolean = false,
        block: (AppState) -> AppState,
    ): AppState {
        val next = mutex.withLock {
            val updated = block(_state.value)
            _state.value = updated
            if (persist && updated.ready) writeBlob(updated)
            updated
        }
        if (sync) scheduleSync()
        return next
    }

    internal fun snapshot(): AppState = _state.value

    private suspend fun load() {
        val foundation = withContext(Dispatchers.IO) { readFoundation() }
        val cloud = withContext(Dispatchers.IO) { mergeCloud(readCloudAsset(), cloudOverride) }
        val blob = withContext(Dispatchers.IO) { readBlob() }
        val base = (blob?.into(cloud, foundation) ?: AppState(cloud = cloud, program = foundation)).let { state ->
            state.copy(
                settings = state.settings.copy(
                    theme = normalizeTheme(state.settings.theme),
                    fontSize = normalizeFontSize(state.settings.fontSize),
                    fontPreset = normalizeFontPreset(state.settings.fontPreset),
                ),
                deviceId = state.deviceId.ifBlank { newId() },
                chat = state.chat.copy(sessionId = state.chat.sessionId.ifBlank { newId() }),
            )
        }
        val now = System.currentTimeMillis()
        val active = base.activeRun
        val projected = if (active != null) projectRun(active, now) else null
        var ready = base
        if (projected?.finished == true && active != null) {
            val log = completedLog(active, now)
            ready = base.copy(
                activeRun = null,
                runLog = listOf(log) + base.runLog,
                runCompletion = app.cinderblock.domain.RunCompletion(log, active.runEndedEarly),
            )
        } else if (projected?.run != null) {
            ready = base.copy(
                activeRun = projected.run,
                runRestoredUntil = if (projected.run.started) now + 5_000 else 0L,
            )
        }
        edit(persist = true, sync = false) { ready.copy(ready = true, cloud = cloud) }
    }

    private fun tick(now: Long) {
        val state = _state.value
        if (!state.ready) return
        val run = state.activeRun
        if (run != null && run.running && run.phaseEndsAt != null && now >= run.phaseEndsAt) {
            scope.launch { catchUpRun(now) }
        }
        val rest = state.rest
        if (rest.running && rest.endsAt != null && now >= rest.endsAt && !rest.announced) {
            scope.launch { completeRest() }
        }
    }

    internal fun scheduleSync() {
        val state = _state.value
        if (state.auth == null || !state.cloud.configured || state.syncPhase == app.cinderblock.domain.SyncPhase.Syncing) return
        syncJob?.cancel()
        syncJob = scope.launch {
            delay(1200)
            runCatching { pushAll(quiet = true) }
        }
    }

    private suspend fun writeBlob(state: AppState) = withContext(Dispatchers.IO) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(AppJson.encodeToString(PersistedBlob.serializer(), state.toBlob()))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun readBlob(): PersistedBlob? = try {
        if (!file.exists()) null else AppJson.decodeFromString<PersistedBlob>(file.readText())
    } catch (_: Exception) {
        if (file.exists()) file.renameTo(File(file.parentFile, "cinderblock-state.corrupt.json"))
        null
    }

    private fun readFoundation(): ProgramDocument =
        AppJson.decodeFromString(context.assets.open("foundation-7-june.json").bufferedReader().readText())

    private fun readCloudAsset(): CloudConfig = try {
        AppJson.decodeFromString(context.assets.open("cloud.json").bufferedReader().readText())
    } catch (_: Exception) {
        CloudConfig()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        val channel = android.app.NotificationChannel(
            "cinderblock",
            "CINDERBLOCK",
            android.app.NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }

    internal suspend fun ensureAuth(): AuthSession {
        val state = snapshot()
        val session = state.auth ?: error("Sign in with Supabase to continue.")
        if (System.currentTimeMillis() < session.expiresAtEpochMs - 60_000) return session
        val refreshed = withContext(Dispatchers.IO) { cloud.refresh(state.cloud, session) }
        edit { it.copy(auth = refreshed) }
        return refreshed
    }

    internal fun accountOk(state: AppState, userId: String): Boolean =
        state.boundAccountId == null || state.boundAccountId == userId
}

internal fun mergeCloud(asset: CloudConfig, override: CloudConfig) = CloudConfig(
    supabaseUrl = override.supabaseUrl.ifBlank { asset.supabaseUrl }.trim(),
    supabaseAnonKey = override.supabaseAnonKey.ifBlank { asset.supabaseAnonKey }.trim(),
    testFunctionUrl = override.testFunctionUrl.ifBlank { asset.testFunctionUrl }.trim(),
    aiChatUrl = override.aiChatUrl.ifBlank { asset.aiChatUrl }.trim(),
    aiExtractJsonUrl = override.aiExtractJsonUrl.ifBlank { asset.aiExtractJsonUrl }.trim(),
    aiScopeJudgeUrl = override.aiScopeJudgeUrl.ifBlank { asset.aiScopeJudgeUrl }.trim(),
    reportIssueUrl = override.reportIssueUrl.ifBlank { asset.reportIssueUrl }.trim(),
)

internal fun completedLog(run: ActiveRun, now: Long) = app.cinderblock.domain.RunLog(
    id = "${java.time.LocalDate.now()}-$now",
    date = java.time.LocalDate.now().toString(),
    plan = if (run.runEndedEarly && run.earlyLoggedPlan != null) run.earlyLoggedPlan else run.plan,
    completedAt = now,
    endedEarly = run.runEndedEarly,
)

internal class SoundPlayer {
    fun play(kind: String, enabled: Boolean, volume: Double) {
        if (!enabled) return
        val type = when (kind) {
            "timer" -> ToneGenerator.TONE_PROP_ACK
            "phase" -> ToneGenerator.TONE_PROP_BEEP2
            else -> ToneGenerator.TONE_PROP_BEEP
        }
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, (volume * 100).toInt().coerceIn(0, 100))
        tone.startTone(type, 200)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tone.release() }, 500)
    }
}

internal class Haptics(context: Context) {
    private val vibrator = context.getSystemService(Vibrator::class.java)

    fun play(kind: HapticKind) {
        if (vibrator?.hasVibrator() != true) return
        val millis = when (kind) {
            HapticKind.Selection -> 20L
            HapticKind.Success -> 45L
            HapticKind.Warning -> 35L
            HapticKind.Light -> 12L
            HapticKind.Error -> 70L
        }
        vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

internal fun isoNow(): String = Instant.now().toString()
