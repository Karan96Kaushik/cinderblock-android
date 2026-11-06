package app.cinderblock.data

import android.util.Base64
import app.cinderblock.domain.AppSettings
import app.cinderblock.domain.AuthSession
import app.cinderblock.domain.BackupSummary
import app.cinderblock.domain.ChatTurn
import app.cinderblock.domain.CloudConfig
import app.cinderblock.domain.DeviceSummary
import app.cinderblock.domain.ProgramDocument
import app.cinderblock.domain.ProgramVersionRecord
import app.cinderblock.domain.RunningPlan
import app.cinderblock.domain.TrainingBackup
import app.cinderblock.domain.TrainingLogPayload
import app.cinderblock.domain.stripFlushPad
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID

internal class CloudClient {
    fun signIn(config: CloudConfig, email: String, password: String): AuthSession =
        authSession(config, postAuth(config, "/auth/v1/token?grant_type=password", email, password), email)

    fun signUp(config: CloudConfig, email: String, password: String): AuthSession? {
        val body = postAuth(config, "/auth/v1/signup", email, password)
        val token = body.optString("access_token")
        if (token.isBlank()) return null
        return authSession(config, body, email)
    }

    fun recover(config: CloudConfig, email: String) {
        val payload = JSONObject().put("email", email).toString()
        httpRequest(
            config.supabaseUrl.trimEnd('/') + "/auth/v1/recover",
            "POST",
            payload,
            supabaseHeaders(config, null),
        )
    }

    fun updatePassword(config: CloudConfig, accessToken: String, password: String) {
        val payload = JSONObject().put("password", password).toString()
        httpRequest(
            config.supabaseUrl.trimEnd('/') + "/auth/v1/user",
            "PUT",
            payload,
            supabaseHeaders(config, accessToken),
        )
    }

    fun refresh(config: CloudConfig, session: AuthSession): AuthSession {
        val payload = JSONObject().put("refresh_token", session.refreshToken).toString()
        val body = JSONObject(
            httpRequest(
                config.supabaseUrl.trimEnd('/') + "/auth/v1/token?grant_type=refresh_token",
                "POST",
                payload,
                supabaseHeaders(config, null),
            ),
        )
        return authSession(config, body, session.email)
    }

    fun fetchSettings(config: CloudConfig, session: AuthSession): Pair<AppSettings, String>? {
        val rows = getArray(
            config,
            session,
            "user_settings?user_id=eq.${session.userId}&select=settings,updated_at",
        )
        if (rows.length() == 0) return null
        val row = rows.getJSONObject(0)
        val settings = AppJson.decodeFromString<AppSettings>(row.getJSONObject("settings").toString())
        return settings to row.optString("updated_at")
    }

    fun upsertSettings(config: CloudConfig, session: AuthSession, settings: AppSettings) {
        val body = JSONObject()
            .put("user_id", session.userId)
            .put("settings", JSONObject(AppJson.encodeToString(AppSettings.serializer(), settings)))
            .put("updated_at", Instant.now().toString())
        upsert(config, session, "user_settings?on_conflict=user_id", body)
    }

    fun fetchPlan(config: CloudConfig, session: AuthSession): Pair<JSONObject, String>? {
        val rows = getArray(
            config,
            session,
            "user_active_plan?user_id=eq.${session.userId}&select=plan,updated_at",
        )
        if (rows.length() == 0) return null
        val row = rows.getJSONObject(0)
        return row.getJSONObject("plan") to row.optString("updated_at")
    }

    fun upsertPlan(config: CloudConfig, session: AuthSession, plan: JSONObject) {
        val body = JSONObject()
            .put("user_id", session.userId)
            .put("plan", plan)
            .put("updated_at", Instant.now().toString())
        upsert(config, session, "user_active_plan?on_conflict=user_id", body)
    }

    fun fetchLogs(config: CloudConfig, session: AuthSession, deviceId: String): TrainingLogPayload? {
        val rows = getArray(
            config,
            session,
            "user_training_logs?user_id=eq.${session.userId}&device_id=eq.${encode(deviceId)}&select=payload",
        )
        if (rows.length() == 0) return null
        return AppJson.decodeFromString<TrainingLogPayload>(rows.getJSONObject(0).getJSONObject("payload").toString())
    }

    fun upsertLogs(config: CloudConfig, session: AuthSession, deviceId: String, payload: TrainingLogPayload) {
        val body = JSONObject()
            .put("user_id", session.userId)
            .put("device_id", deviceId)
            .put("payload", JSONObject(AppJson.encodeToString(TrainingLogPayload.serializer(), payload)))
            .put("updated_at", Instant.now().toString())
        upsert(config, session, "user_training_logs?on_conflict=user_id,device_id", body)
    }

    fun listDevices(config: CloudConfig, session: AuthSession): List<DeviceSummary> {
        val rows = getArray(
            config,
            session,
            "user_training_logs?user_id=eq.${session.userId}&select=device_id,updated_at,payload&order=updated_at.desc",
        )
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val payload = row.optJSONObject("payload") ?: JSONObject()
            DeviceSummary(
                deviceId = row.optString("device_id"),
                updatedAt = row.optString("updated_at"),
                gymDays = payload.optJSONObject("gymLog")?.length() ?: 0,
                runs = payload.optJSONArray("runLog")?.length() ?: 0,
                metrics = payload.optJSONArray("bodyMetrics")?.length() ?: 0,
            )
        }
    }

    fun listBackups(config: CloudConfig, session: AuthSession, deviceId: String): List<BackupSummary> {
        val rows = getArray(
            config,
            session,
            "user_backups?user_id=eq.${session.userId}&device_id=eq.${encode(deviceId)}&select=id,label,created_at,device_id,payload&order=created_at.desc&limit=20",
        )
        return (0 until rows.length()).map { index -> backupSummary(rows.getJSONObject(index)) }
    }

    fun uploadBackup(
        config: CloudConfig,
        session: AuthSession,
        deviceId: String,
        backup: TrainingBackup,
        label: String,
    ): BackupSummary {
        val body = JSONObject()
            .put("user_id", session.userId)
            .put("device_id", deviceId)
            .put("label", label)
            .put("payload", JSONObject(AppJson.encodeToString(TrainingBackup.serializer(), backup)))
        val text = httpRequest(
            config.supabaseUrl.trimEnd('/') + "/rest/v1/user_backups",
            "POST",
            body.toString(),
            supabaseHeaders(config, session.accessToken) + mapOf("Prefer" to "return=representation"),
        )
        val row = JSONArray(text).getJSONObject(0)
        return backupSummary(row)
    }

    fun fetchBackup(config: CloudConfig, session: AuthSession, id: String): TrainingBackup {
        val rows = getArray(config, session, "user_backups?id=eq.$id&select=payload")
        if (rows.length() == 0) error("Backup not found")
        return AppJson.decodeFromString(rows.getJSONObject(0).getJSONObject("payload").toString())
    }

    fun deleteBackup(config: CloudConfig, session: AuthSession, id: String) {
        httpRequest(
            config.supabaseUrl.trimEnd('/') + "/rest/v1/user_backups?id=eq.$id",
            "DELETE",
            headers = supabaseHeaders(config, session.accessToken),
        )
    }

    fun listVersions(config: CloudConfig, session: AuthSession): List<ProgramVersionRecord> {
        val rows = getArray(
            config,
            session,
            "user_program_versions?user_id=eq.${session.userId}&select=id,program_id,version,program,source,note,created_at&order=created_at.desc&limit=20",
        )
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            ProgramVersionRecord(
                id = row.optString("id"),
                programId = row.optString("program_id"),
                version = row.optString("version"),
                program = AppJson.decodeFromString(row.getJSONObject("program").toString()),
                source = row.optString("source"),
                note = row.optString("note").ifBlank { null },
                createdAt = row.optString("created_at"),
            )
        }
    }

    fun insertVersion(config: CloudConfig, session: AuthSession, record: ProgramVersionRecord) {
        val body = JSONObject()
            .put("id", record.id)
            .put("user_id", session.userId)
            .put("program_id", record.programId)
            .put("version", record.version)
            .put("program", JSONObject(AppJson.encodeToString(ProgramDocument.serializer(), record.program)))
            .put("source", record.source)
            .put("note", record.note ?: JSONObject.NULL)
            .put("created_at", record.createdAt)
        httpRequest(
            config.supabaseUrl.trimEnd('/') + "/rest/v1/user_program_versions",
            "POST",
            body.toString(),
            supabaseHeaders(config, session.accessToken) + mapOf("Prefer" to "return=minimal"),
        )
    }

    fun testFunction(config: CloudConfig, session: AuthSession): String {
        val url = config.testFunctionUrl
        if (url.isBlank()) error("Test function URL is not set")
        return httpRequest(url, "POST", "{}", mapOf("Authorization" to "Bearer ${session.accessToken}", "Content-Type" to "application/json"))
    }

    fun streamChat(
        config: CloudConfig,
        session: AuthSession,
        mode: String,
        summary: String,
        planContext: String,
        turns: List<ChatTurn>,
        message: String,
        handle: StreamHandle,
        onChunk: (String) -> Unit,
    ): String {
        val body = JSONObject()
            .put("mode", mode)
            .put("runningSummary", summary)
            .put("currentPlanContext", planContext)
            .put("recentTurns", JSONArray().apply {
                turns.takeLast(7).forEach { turn ->
                    put(JSONObject().put("role", turn.role).put("content", turn.content))
                }
            })
            .put("newMessage", message)
        return httpRequest(
            config.aiChatUrl,
            "POST",
            body.toString(),
            mapOf("Authorization" to "Bearer ${session.accessToken}", "Content-Type" to "application/json"),
            handle,
        ) { chunk -> onChunk(stripFlushPad(chunk)) }
    }

    fun extractPlan(config: CloudConfig, session: AuthSession, draft: String, summary: String): ProgramDocument {
        val body = JSONObject()
            .put("plaintextDraft", draft)
            .put("runningSummary", summary)
            .put("schemaVersion", "1.0")
        val text = httpRequest(
            config.aiExtractJsonUrl,
            "POST",
            body.toString(),
            mapOf("Authorization" to "Bearer ${session.accessToken}", "Content-Type" to "application/json"),
        )
        val json = JSONObject(text)
        if (!json.optBoolean("ok")) {
            error(json.optString("error").ifBlank { "The plan could not be read" })
        }
        return AppJson.decodeFromString(json.getJSONObject("plan").toString())
    }

    fun judge(
        config: CloudConfig,
        session: AuthSession,
        mode: String,
        stage: String,
        summary: String,
        turns: List<ChatTurn>,
        previous: String,
        proposed: String,
    ): String? {
        val body = JSONObject()
            .put("mode", mode)
            .put("stage", stage)
            .put("runningSummary", summary)
            .put("recentTurns", JSONArray().apply {
                turns.takeLast(7).forEach { put(JSONObject().put("role", it.role).put("content", it.content)) }
            })
            .put("previousPlan", previous)
            .put("proposedPlan", proposed)
        val text = httpRequest(
            config.aiScopeJudgeUrl,
            "POST",
            body.toString(),
            mapOf("Authorization" to "Bearer ${session.accessToken}", "Content-Type" to "application/json"),
        )
        val json = JSONObject(text)
        return if (json.optString("verdict") == "reject") {
            json.optString("reason").ifBlank { "Those changes go beyond the request" }
        } else {
            null
        }
    }

    fun reportIssue(
        config: CloudConfig,
        session: AuthSession,
        turns: List<ChatTurn>,
        draft: String,
        summary: String,
        failure: String,
    ): String {
        val body = JSONObject()
            .put("chatHistoryFull", JSONArray().apply {
                turns.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) }
            })
            .put("plaintextDraft", draft)
            .put("runningSummary", summary)
            .put("jsonAttempts", JSONArray())
            .put("validatorErrors", JSONArray().put(failure))
            .put("schemaVersion", "1.0")
        val text = httpRequest(
            config.reportIssueUrl,
            "POST",
            body.toString(),
            mapOf("Authorization" to "Bearer ${session.accessToken}", "Content-Type" to "application/json"),
        )
        val json = JSONObject(text)
        return json.optString("reportId").ifBlank { error(json.optString("error").ifBlank { "Report failed" }) }
    }

    private fun backupSummary(row: JSONObject): BackupSummary {
        val payload = row.optJSONObject("payload") ?: JSONObject()
        return BackupSummary(
            id = row.optString("id"),
            label = row.optString("label").ifBlank { "Backup" },
            createdAt = row.optString("created_at"),
            deviceId = row.optString("device_id"),
            gymDays = payload.optJSONObject("gymLog")?.length() ?: 0,
            runs = payload.optJSONArray("runLog")?.length() ?: 0,
            metrics = payload.optJSONArray("bodyMetrics")?.length() ?: 0,
        )
    }

    private fun getArray(config: CloudConfig, session: AuthSession, query: String): JSONArray {
        val text = httpRequest(
            config.supabaseUrl.trimEnd('/') + "/rest/v1/$query",
            "GET",
            headers = supabaseHeaders(config, session.accessToken),
        )
        if (text.isBlank()) return JSONArray()
        return JSONArray(text)
    }

    private fun upsert(config: CloudConfig, session: AuthSession, query: String, body: JSONObject) {
        httpRequest(
            config.supabaseUrl.trimEnd('/') + "/rest/v1/$query",
            "POST",
            body.toString(),
            supabaseHeaders(config, session.accessToken) + mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
        )
    }

    private fun postAuth(config: CloudConfig, path: String, email: String, password: String): JSONObject {
        val payload = JSONObject().put("email", email).put("password", password).toString()
        val text = httpRequest(config.supabaseUrl.trimEnd('/') + path, "POST", payload, supabaseHeaders(config, null))
        return JSONObject(text.ifBlank { "{}" })
    }

    private fun authSession(config: CloudConfig, body: JSONObject, fallbackEmail: String): AuthSession {
        val token = body.optString("access_token")
        if (token.isBlank()) error(errorText(body.toString(), "Sign in failed"))
        val user = body.optJSONObject("user")
        val userId = user?.optString("id").orEmpty().ifBlank { jwtSubject(token).orEmpty() }
        if (userId.isBlank()) error("Sign in did not return a user")
        val email = user?.optString("email").orEmpty().ifBlank { fallbackEmail }
        val expiresIn = body.optLong("expires_in", 3600L)
        return AuthSession(
            userId = userId,
            email = email,
            accessToken = token,
            refreshToken = body.optString("refresh_token"),
            expiresAtEpochMs = System.currentTimeMillis() + expiresIn * 1000L,
        )
    }

    private fun supabaseHeaders(config: CloudConfig, token: String?): Map<String, String> {
        val auth = token ?: config.supabaseAnonKey
        return mapOf(
            "apikey" to config.supabaseAnonKey,
            "Authorization" to "Bearer $auth",
            "Content-Type" to "application/json",
        )
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

internal fun errorText(body: String, fallback: String): String = try {
    val json = JSONObject(body)
    json.optString("error_description").ifBlank {
        json.optString("msg").ifBlank {
            json.optString("message").ifBlank {
                json.optString("error").ifBlank { fallback }
            }
        }
    }
} catch (_: Exception) {
    fallback
}

internal fun jwtSubject(token: String): String? = try {
    val parts = token.split('.')
    if (parts.size < 2) null
    else {
        var payload = parts[1]
        while (payload.length % 4 != 0) payload += "="
        val decoded = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
        JSONObject(decoded).optString("sub").ifBlank { null }
    }
} catch (_: Exception) {
    null
}

internal fun activePlanJson(programId: String, program: ProgramDocument, running: RunningPlan): JSONObject =
    JSONObject()
        .put("programId", programId)
        .put("program", JSONObject(AppJson.encodeToString(ProgramDocument.serializer(), program)))
        .put("running", JSONObject(AppJson.encodeToString(RunningPlan.serializer(), running)))

internal fun decodePlan(json: JSONObject): Triple<String, ProgramDocument, RunningPlan>? = try {
    val program = AppJson.decodeFromString<ProgramDocument>(json.getJSONObject("program").toString())
    val running = if (json.has("running")) {
        AppJson.decodeFromString<RunningPlan>(json.getJSONObject("running").toString())
    } else {
        RunningPlan()
    }
    val id = json.optString("programId").ifBlank { app.cinderblock.domain.DEFAULT_PROGRAM_ID }
    Triple(id, program, running)
} catch (_: Exception) {
    null
}

internal fun newId(): String = UUID.randomUUID().toString()
