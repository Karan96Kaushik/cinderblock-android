package app.cinderblock.data

import app.cinderblock.domain.ActiveRun
import app.cinderblock.domain.AppSettings
import app.cinderblock.domain.AppState
import app.cinderblock.domain.AuthSession
import app.cinderblock.domain.ChatPersisted
import app.cinderblock.domain.CloudConfig
import app.cinderblock.domain.DayLog
import app.cinderblock.domain.DEFAULT_PROGRAM_ID
import app.cinderblock.domain.MetricEntry
import app.cinderblock.domain.ProgramDocument
import app.cinderblock.domain.ProgramVersionRecord
import app.cinderblock.domain.RunLog
import app.cinderblock.domain.RunningPlan
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    prettyPrint = true
}

@Serializable
internal data class PersistedBlob(
    val settings: AppSettings = AppSettings(),
    val programId: String = DEFAULT_PROGRAM_ID,
    val program: ProgramDocument? = null,
    val runningPlan: RunningPlan = RunningPlan(),
    val gymLog: Map<String, DayLog> = emptyMap(),
    val runLog: List<RunLog> = emptyList(),
    val activeRun: ActiveRun? = null,
    val bodyMetrics: List<MetricEntry> = emptyList(),
    val videos: Map<String, String> = emptyMap(),
    val versions: List<ProgramVersionRecord> = emptyList(),
    val auth: AuthSession? = null,
    val deviceId: String = "",
    val boundAccountId: String? = null,
    val chat: ChatPersisted = ChatPersisted(),
    val settingsUpdatedAt: Long = 0L,
    val planUpdatedAt: Long = 0L,
)

internal fun AppState.toBlob(): PersistedBlob = PersistedBlob(
    settings = settings,
    programId = programId,
    program = program,
    runningPlan = runningPlan,
    gymLog = gymLog,
    runLog = runLog,
    activeRun = activeRun,
    bodyMetrics = bodyMetrics,
    videos = videos,
    versions = versions,
    auth = auth,
    deviceId = deviceId,
    boundAccountId = boundAccountId,
    chat = chat,
    settingsUpdatedAt = settingsUpdatedAt,
    planUpdatedAt = planUpdatedAt,
)

internal fun PersistedBlob.into(cloud: CloudConfig, foundation: ProgramDocument): AppState = AppState(
    settings = settings,
    programId = programId.ifBlank { DEFAULT_PROGRAM_ID },
    program = program ?: foundation,
    runningPlan = runningPlan,
    gymLog = gymLog,
    runLog = runLog,
    activeRun = activeRun,
    bodyMetrics = bodyMetrics,
    videos = videos,
    versions = versions,
    auth = auth,
    cloud = cloud,
    deviceId = deviceId,
    boundAccountId = boundAccountId,
    chat = chat,
    settingsUpdatedAt = settingsUpdatedAt,
    planUpdatedAt = planUpdatedAt,
)
