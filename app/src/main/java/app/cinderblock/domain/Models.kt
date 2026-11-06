package app.cinderblock.domain

import kotlinx.serialization.Serializable

const val REST_KEY = "rest"
const val REST_LABEL = "Rest day"
const val DEFAULT_PROGRAM_ID = "foundation-7-june"
const val APP_VERSION_LABEL = "2.0.26"

@Serializable
data class ProgramExercise(
    val name: String = "",
    val sets: Int = 1,
    val reps: String? = null,
    val duration: String? = null,
    val seconds: String? = null,
    val muscles: List<String> = emptyList(),
    val refVideo: String? = null,
    val notes: List<String> = emptyList(),
)

@Serializable
data class ProgramWorkout(
    val name: String = "",
    val exercises: List<ProgramExercise> = emptyList(),
)

@Serializable
data class ProgramSchedule(
    val day1: String = "",
    val day2: String = "",
    val day3: String = "",
    val day4: String = "",
    val day5: String = "",
    val day6: String = "",
    val day7: String = "",
) {
    fun entries(): List<Pair<String, String>> = listOf(
        "day1" to day1,
        "day2" to day2,
        "day3" to day3,
        "day4" to day4,
        "day5" to day5,
        "day6" to day6,
        "day7" to day7,
    )
}

@Serializable
data class ProgramProgressionExample(
    val exercise: String = "",
    val week1: List<String> = emptyList(),
    val week2: List<String> = emptyList(),
    val week3: List<String> = emptyList(),
    val nextStep: String = "",
)

@Serializable
data class ProgramProgression(
    val method: String = "",
    val example: ProgramProgressionExample = ProgramProgressionExample(),
)

@Serializable
data class ProgramDocument(
    val name: String = "",
    val version: String = "1.0",
    val goal: List<String> = emptyList(),
    val schedule: ProgramSchedule = ProgramSchedule(),
    val globalNotes: List<String> = emptyList(),
    val progression: ProgramProgression = ProgramProgression(),
    val weeks: Map<String, List<String>> = emptyMap(),
    val workouts: Map<String, ProgramWorkout> = emptyMap(),
    val successMetrics: List<String> = emptyList(),
)

data class ProgramIssue(val path: String, val message: String)

@Serializable
data class RunningPlan(
    val warmupMinutes: Int = 5,
    val runMinutes: Int = 30,
    val cooldownMinutes: Int = 5,
)

@Serializable
data class SetLog(
    val weight: String = "",
    val reps: String = "",
    val seconds: String = "",
)

@Serializable
data class ExerciseLog(
    val sets: List<SetLog> = emptyList(),
    val completed: Boolean = false,
    val skipped: Boolean = false,
)

@Serializable
data class DayLog(
    val workoutKey: String = "",
    val exercises: Map<String, ExerciseLog> = emptyMap(),
    val programVersion: String? = null,
    val workoutName: String? = null,
    val updatedAt: Long = 0L,
)

enum class DayStatus { Empty, Rest, Partial, Complete }

enum class ExerciseStatus { Pending, Logged, Done, Skipped }

@Serializable
data class RunLog(
    val id: String,
    val date: String,
    val plan: RunningPlan,
    val completedAt: Long,
    val endedEarly: Boolean = false,
)

@Serializable
data class ActiveRun(
    val plan: RunningPlan,
    val phaseIndex: Int = 0,
    val phaseEndsAt: Long? = null,
    val remainingSeconds: Int = 0,
    val running: Boolean = false,
    val started: Boolean = false,
    val updatedAt: Long = 0L,
    val runEndedEarly: Boolean = false,
    val earlyLoggedPlan: RunningPlan? = null,
)

enum class RunPhase { Warmup, Run, Cooldown }

@Serializable
data class MetricEntry(
    val date: String,
    val weight: String = "",
    val waist: String = "",
    val chest: String = "",
    val hips: String = "",
    val bodyFat: String = "",
    val notes: String = "",
    val updatedAt: Long = 0L,
)

@Serializable
data class AppSettings(
    val fontSize: String = "md",
    val fontPreset: String = "cinderblock",
    val theme: String = "orange",
    val alwaysAwake: Boolean = true,
    val autoStartRestTimer: Boolean = false,
    val restTimerMinutes: Double = 2.0,
    val soundEnabled: Boolean = true,
    val soundVolume: Double = 0.7,
)

@Serializable
data class AuthSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long,
)

@Serializable
data class CloudConfig(
    val supabaseUrl: String = "",
    val supabaseAnonKey: String = "",
    val testFunctionUrl: String = "",
    val aiChatUrl: String = "",
    val aiExtractJsonUrl: String = "",
    val aiScopeJudgeUrl: String = "",
    val reportIssueUrl: String = "",
) {
    val configured: Boolean get() = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()
    val aiConfigured: Boolean get() = aiChatUrl.isNotBlank() && aiExtractJsonUrl.isNotBlank()
}

@Serializable
data class ProgramVersionRecord(
    val id: String,
    val programId: String,
    val version: String,
    val program: ProgramDocument,
    val source: String = "manual",
    val note: String? = null,
    val createdAt: String,
)

@Serializable
data class ChatTurn(val role: String, val content: String)

@Serializable
data class ChatPersisted(
    val sessionId: String = "",
    val mode: String = "discuss",
    val runningSummary: String = "",
    val turns: List<ChatTurn> = emptyList(),
    val plaintextDraft: String = "",
    val previousDraft: String? = null,
    val planReady: Boolean = false,
    val hasPlanDraft: Boolean = false,
)

@Serializable
data class TrainingBackup(
    val version: Int = 2,
    val exportedAt: String = "",
    val settings: AppSettings = AppSettings(),
    val defaultRunningPlan: RunningPlan = RunningPlan(),
    val gymLog: Map<String, DayLog> = emptyMap(),
    val runLog: List<RunLog> = emptyList(),
    val bodyMetrics: List<MetricEntry> = emptyList(),
)

@Serializable
data class TrainingLogPayload(
    val gymLog: Map<String, DayLog> = emptyMap(),
    val runLog: List<RunLog> = emptyList(),
    val bodyMetrics: List<MetricEntry> = emptyList(),
    val settings: AppSettings? = null,
    val defaultRunningPlan: RunningPlan? = null,
    val exportedAt: String? = null,
    val version: Int? = null,
)

data class BackupSummary(
    val id: String,
    val label: String,
    val createdAt: String,
    val deviceId: String,
    val gymDays: Int,
    val runs: Int,
    val metrics: Int,
)

data class DeviceSummary(
    val deviceId: String,
    val updatedAt: String,
    val gymDays: Int,
    val runs: Int,
    val metrics: Int,
)

data class RestTimer(
    val ownerExercise: String? = null,
    val ownerDate: String? = null,
    val endsAt: Long? = null,
    val remainingSeconds: Int = 0,
    val running: Boolean = false,
    val durationSeconds: Int = 0,
    val announced: Boolean = false,
    val autoStarted: Map<String, Set<Int>> = emptyMap(),
) {
    fun displayRemaining(now: Long): Int {
        if (!running || endsAt == null) return remainingSeconds
        return ((endsAt - now) / 1000L).toInt().coerceAtLeast(0)
    }

    val phaseLabel: String
        get() = when {
            ownerExercise == null -> "Pick a preset"
            announced -> "Rest complete"
            running -> "Running"
            remainingSeconds > 0 -> "Paused"
            durationSeconds > 0 -> "Rest complete"
            else -> "Pick a preset"
        }
}

data class RunCompletion(val log: RunLog, val endedEarly: Boolean)

data class ChatRuntime(
    val streaming: Boolean = false,
    val partial: String = "",
    val judging: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val needsReauth: Boolean = false,
    val hardFailure: String? = null,
    val reportId: String? = null,
    val composer: String = "",
    val preview: Boolean = false,
    val draftOpened: Boolean = false,
    val notices: List<ChatTurn> = emptyList(),
)

data class VersionHistoryUi(
    val expanded: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val rows: List<ProgramVersionRecord> = emptyList(),
)

enum class SyncPhase { Idle, Syncing, Error }

data class AppState(
    val ready: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val programId: String = DEFAULT_PROGRAM_ID,
    val program: ProgramDocument = ProgramDocument(),
    val runningPlan: RunningPlan = RunningPlan(),
    val gymLog: Map<String, DayLog> = emptyMap(),
    val runLog: List<RunLog> = emptyList(),
    val activeRun: ActiveRun? = null,
    val bodyMetrics: List<MetricEntry> = emptyList(),
    val videos: Map<String, String> = emptyMap(),
    val versions: List<ProgramVersionRecord> = emptyList(),
    val auth: AuthSession? = null,
    val cloud: CloudConfig = CloudConfig(),
    val deviceId: String = "",
    val boundAccountId: String? = null,
    val chat: ChatPersisted = ChatPersisted(),
    val skipLogin: Boolean = false,
    val recoveryAccessToken: String? = null,
    val launchRoute: String? = null,
    val syncPhase: SyncPhase = SyncPhase.Idle,
    val syncError: String? = null,
    val settingsUpdatedAt: Long = 0L,
    val planUpdatedAt: Long = 0L,
    val rest: RestTimer = RestTimer(),
    val runCompletion: RunCompletion? = null,
    val runRestoredUntil: Long = 0L,
    val chatRuntime: ChatRuntime = ChatRuntime(),
    val versionHistory: VersionHistoryUi = VersionHistoryUi(),
    val backups: List<BackupSummary> = emptyList(),
    val devices: List<DeviceSummary> = emptyList(),
    val cloudListsLoading: Boolean = false,
    val authBusy: Boolean = false,
)

sealed interface ActionResult {
    data object Ok : ActionResult
    data class Error(val message: String) : ActionResult
    data class Info(val message: String) : ActionResult
}

sealed interface UiEffect {
    data class Toast(val message: String) : UiEffect
    data class Navigate(val route: String) : UiEffect
}

enum class HapticKind { Selection, Success, Warning, Light, Error }
