package com.bayonetbarontech.cinderblock.data

import com.bayonetbarontech.cinderblock.domain.ActiveRun
import com.bayonetbarontech.cinderblock.domain.AppSettings
import com.bayonetbarontech.cinderblock.domain.AppState
import com.bayonetbarontech.cinderblock.domain.AuthSession
import com.bayonetbarontech.cinderblock.domain.ChatPersisted
import com.bayonetbarontech.cinderblock.domain.CloudConfig
import com.bayonetbarontech.cinderblock.domain.DayLog
import com.bayonetbarontech.cinderblock.domain.DEFAULT_PROGRAM_ID
import com.bayonetbarontech.cinderblock.domain.MetricEntry
import com.bayonetbarontech.cinderblock.domain.ProgramDocument
import com.bayonetbarontech.cinderblock.domain.ProgramVersionRecord
import com.bayonetbarontech.cinderblock.domain.RunLog
import com.bayonetbarontech.cinderblock.domain.RunningPlan
import com.bayonetbarontech.cinderblock.domain.TrainingProfile
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
    val sessionExercise: Map<String, String> = emptyMap(),
    val runLog: List<RunLog> = emptyList(),
    val activeRun: ActiveRun? = null,
    val bodyMetrics: List<MetricEntry> = emptyList(),
    val videos: Map<String, String> = emptyMap(),
    val versions: List<ProgramVersionRecord> = emptyList(),
    val auth: AuthSession? = null,
    val deviceId: String = "",
    val boundAccountId: String? = null,
    val chat: ChatPersisted = ChatPersisted(),
    val profile: TrainingProfile? = null,
    val settingsUpdatedAt: Long = 0L,
    val planUpdatedAt: Long = 0L,
    val notificationPrompted: Boolean = false,
)

internal fun AppState.toBlob(): PersistedBlob = PersistedBlob(
    settings = settings,
    programId = programId,
    program = program,
    runningPlan = runningPlan,
    gymLog = gymLog,
    sessionExercise = sessionExercise,
    runLog = runLog,
    activeRun = activeRun,
    bodyMetrics = bodyMetrics,
    videos = videos,
    versions = versions,
    auth = auth,
    deviceId = deviceId,
    boundAccountId = boundAccountId,
    chat = chat,
    profile = profile,
    settingsUpdatedAt = settingsUpdatedAt,
    planUpdatedAt = planUpdatedAt,
    notificationPrompted = notificationPrompted,
)

internal fun PersistedBlob.into(cloud: CloudConfig, foundation: ProgramDocument): AppState = AppState(
    settings = settings,
    programId = programId.ifBlank { DEFAULT_PROGRAM_ID },
    program = program ?: foundation,
    runningPlan = runningPlan,
    gymLog = gymLog,
    sessionExercise = sessionExercise,
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
    profile = profile ?: TrainingProfile(completed = true),
    settingsUpdatedAt = settingsUpdatedAt,
    planUpdatedAt = planUpdatedAt,
    notificationPrompted = notificationPrompted,
)
