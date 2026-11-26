package com.bayonetbarontech.cinderblock.data

import com.bayonetbarontech.cinderblock.domain.AI_CHAT_MAX_MESSAGE_CHARS
import com.bayonetbarontech.cinderblock.domain.AI_CHAT_MAX_RECENT_TURNS
import com.bayonetbarontech.cinderblock.domain.ActionResult
import com.bayonetbarontech.cinderblock.domain.ChatTurn
import com.bayonetbarontech.cinderblock.domain.STREAM_ERROR
import com.bayonetbarontech.cinderblock.domain.foldOverflowIntoSummary
import com.bayonetbarontech.cinderblock.domain.parseAssistant
import com.bayonetbarontech.cinderblock.domain.planMateriallyChanged
import com.bayonetbarontech.cinderblock.domain.SetupPhase
import com.bayonetbarontech.cinderblock.domain.hasSetupAnswers
import com.bayonetbarontech.cinderblock.domain.mergeAthleteSummary
import com.bayonetbarontech.cinderblock.domain.programToPlaintext
import com.bayonetbarontech.cinderblock.domain.setupBrief
import com.bayonetbarontech.cinderblock.domain.stripFlushPad
import com.bayonetbarontech.cinderblock.domain.stripPlanBlock
import com.bayonetbarontech.cinderblock.domain.trimRecentTurns
import com.bayonetbarontech.cinderblock.domain.validateProgram
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal suspend fun Controller.setChatMode(mode: String) {
    val normalized = when (mode) {
        "edit", "discuss", "create" -> mode
        "explain" -> "discuss"
        else -> "discuss"
    }
    edit { state ->
        val seeded = if (normalized == "create") "" else programToPlaintext(state.program)
        val baseline = state.chat.baselineDraft.ifBlank { seeded }
        val draft = state.chat.plaintextDraft.ifBlank { seeded }
        val summary = if (hasSetupAnswers(state.profile)) {
            mergeAthleteSummary(state.chat.runningSummary, setupBrief(state.profile))
        } else {
            state.chat.runningSummary
        }
        state.copy(
            chat = state.chat.copy(
                mode = normalized,
                baselineDraft = baseline,
                plaintextDraft = draft,
                runningSummary = summary,
            ),
        )
    }
}

internal fun Controller.stopChat() {
    chatStopped = true
    chatStream?.cancel()
}

internal suspend fun Controller.sendChat(text: String) {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return
    val state = snapshot()
    val runtime = state.chatRuntime
    if (runtime.streaming || runtime.judging || runtime.saving) return
    if (trimmed.length > AI_CHAT_MAX_MESSAGE_CHARS) {
        edit(persist = false) {
            it.copy(
                chatRuntime = it.chatRuntime.copy(
                    error = "Message is too long (${trimmed.length} characters, max $AI_CHAT_MAX_MESSAGE_CHARS). Try splitting it up.",
                    composer = trimmed,
                ),
            )
        }
        return
    }
    if (!state.cloud.aiConfigured) {
        edit(persist = false) {
            it.copy(
                chatRuntime = it.chatRuntime.copy(
                    error = "AI chat is not configured yet. Deploy the Amplify sandbox so the function URLs are set.",
                    composer = trimmed,
                ),
            )
        }
        return
    }
    if (state.auth == null) return
    val checkpoint = state.chat
    val userTurn = ChatTurn("user", trimmed)
    val history = state.chat.turns.filter { it.role == "user" || it.role == "assistant" }
    val recentBase = state.chat.recentTurns.ifEmpty { history.takeLast(AI_CHAT_MAX_RECENT_TURNS) }
    val trimmedTurns = trimRecentTurns(recentBase + userTurn)
    var summary = foldOverflowIntoSummary(state.chat.runningSummary, trimmedTurns.overflow)
    val planContext = state.chat.plaintextDraft.trim()
    edit {
        it.copy(
            chat = it.chat.copy(turns = it.chat.turns + userTurn),
            chatRuntime = it.chatRuntime.copy(
                composer = "",
                error = null,
                streaming = true,
                partial = "",
                hardFailure = null,
                reportAttempts = "[]",
                reportErrors = "[]",
                reportId = null,
            ),
        )
    }
    val handle = StreamHandle()
    chatStream = handle
    chatStopped = false
    val buffer = StringBuilder()
    try {
        val session = ensureAuth()
        val full = withContext(Dispatchers.IO) {
            cloud.streamChat(
                config = snapshot().cloud,
                session = session,
                mode = snapshot().chat.mode,
                summary = summary,
                planContext = planContext,
                turns = trimmedTurns.recent.dropLast(1),
                message = trimmed,
                handle = handle,
            ) { chunk ->
                buffer.append(chunk)
                scope.launch {
                    edit(persist = false) { current ->
                        current.copy(chatRuntime = current.chatRuntime.copy(partial = buffer.toString()))
                    }
                }
            }
        }
        val clean = stripFlushPad(full)
        if (clean.contains(STREAM_ERROR)) {
            error(clean.substringAfter(STREAM_ERROR).trim().ifBlank { "AI chat stream failed" })
        }
        val parsed = parseAssistant(clean)
        if (parsed.runningSummary != null) summary = parsed.runningSummary
        var displayText = parsed.displayText.ifBlank { clean }
        var nextDraft = parsed.plaintextDraft ?: snapshot().chat.plaintextDraft
        var hasPlanDraft = snapshot().chat.hasPlanDraft || parsed.plaintextDraft != null
        var planReady = snapshot().chat.planReady || parsed.planReady
        var rejection: String? = null
        val proposed = parsed.plaintextDraft
        val previousDraft = snapshot().chat.plaintextDraft
        if (
            proposed != null &&
            previousDraft.isNotBlank() &&
            planMateriallyChanged(previousDraft, proposed) &&
            snapshot().cloud.aiScopeJudgeUrl.isNotBlank()
        ) {
            edit(persist = false) {
                it.copy(chatRuntime = it.chatRuntime.copy(streaming = false, judging = true, partial = ""))
            }
            val judgement = try {
                withContext(Dispatchers.IO) {
                    cloud.judge(
                        snapshot().cloud,
                        ensureAuth(),
                        snapshot().chat.mode,
                        "apply",
                        summary,
                        trimmedTurns.recent,
                        previousDraft,
                        proposed,
                    )
                }
            } catch (_: Exception) {
                null
            }
            if (judgement != null && !judgement.accepted) {
                nextDraft = previousDraft
                hasPlanDraft = snapshot().chat.hasPlanDraft
                planReady = snapshot().chat.planReady
                displayText = parseAssistant(stripPlanBlock(clean)).displayText.ifBlank { displayText }
                summary = listOf(summary.trim(), "Scope auditor rejected the last plan rewrite: ${judgement.reason}")
                    .filter { it.isNotEmpty() }
                    .joinToString("\n")
                rejection = formatScopeRejection(judgement)
            }
        }
        val assistant = ChatTurn("assistant", displayText)
        val after = trimRecentTurns(trimmedTurns.recent + assistant)
        summary = foldOverflowIntoSummary(summary, after.overflow)
        val spoken = if (rejection == null) listOf(assistant) else listOf(assistant, ChatTurn("system", rejection))
        edit { current ->
            current.copy(
                chat = current.chat.copy(
                    turns = current.chat.turns + spoken,
                    recentTurns = after.recent,
                    runningSummary = summary,
                    plaintextDraft = nextDraft,
                    previousDraft = if (proposed != null && rejection == null) previousDraft else current.chat.previousDraft,
                    planReady = planReady,
                    hasPlanDraft = hasPlanDraft,
                ),
                chatRuntime = current.chatRuntime.copy(
                    streaming = false,
                    judging = false,
                    partial = "",
                    draftOpened = if (proposed != null && rejection == null) false else current.chatRuntime.draftOpened,
                    checkpoint = checkpoint,
                ),
            )
        }
    } catch (error: Exception) {
        if (error is CancellationException && !chatStopped) throw error
        val cancelled = chatStopped || error is CancellationException
        val message = if (cancelled) "Message cancelled." else cloudMessage(error, "AI chat failed")
        edit { current ->
            current.copy(
                chat = checkpoint,
                chatRuntime = current.chatRuntime.copy(
                    streaming = false,
                    judging = false,
                    partial = "",
                    error = message,
                    needsReauth = error is HttpException && error.code == 401,
                    composer = trimmed,
                ),
            )
        }
    }
}

internal suspend fun Controller.revertChat() {
    edit { state ->
        val checkpoint = state.chatRuntime.checkpoint ?: return@edit state
        state.copy(
            chat = checkpoint,
            chatRuntime = state.chatRuntime.copy(
                checkpoint = null,
                error = null,
                hardFailure = null,
                reportId = null,
                reportAttempts = "[]",
                reportErrors = "[]",
            ),
        )
    }
}

internal suspend fun Controller.saveChat(
    source: String = "ai-chat",
    note: String? = null,
    goHome: Boolean = true,
): ActionResult {
    val state = snapshot()
    if (state.auth == null) return ActionResult.Error("You must be logged in to save changes")
    val draft = state.chat.plaintextDraft
    if (draft.isBlank() || (!state.chat.planReady && !state.chat.hasPlanDraft)) {
        return ActionResult.Error("There are no AI changes to save yet.")
    }
    edit(persist = false) { it.copy(chatRuntime = it.chatRuntime.copy(saving = true, judging = false, error = null)) }
    return try {
        val extracted = withContext(Dispatchers.IO) {
            cloud.extractPlan(state.cloud, ensureAuth(), draft, state.chat.runningSummary)
        }
        if (extracted is ExtractResult.Rejected) {
            return failSave(extracted.message, extracted.attemptsJson, extracted.errorsJson)
        }
        val ready = extracted as ExtractResult.Ready
        val issue = validateProgram(ready.plan)
        if (issue != null) {
            val errors = """[{"path":${JSONObject.quote(issue.path)},"message":${JSONObject.quote(issue.message)}}]"""
            return failSave("Server returned a plan that failed client validation.", ready.attemptsJson, errors)
        }
        if (state.cloud.aiScopeJudgeUrl.isNotBlank()) {
            edit(persist = false) { it.copy(chatRuntime = it.chatRuntime.copy(judging = true)) }
            val userTurns = state.chat.turns.filter { it.role == "user" }.takeLast(8)
            val baseline = state.chat.baselineDraft.ifBlank {
                if (state.chat.mode == "create") "" else programToPlaintext(state.program)
            }
            val judgement = try {
                withContext(Dispatchers.IO) {
                    cloud.judge(
                        state.cloud,
                        ensureAuth(),
                        state.chat.mode,
                        "save",
                        state.chat.runningSummary,
                        userTurns,
                        baseline,
                        draft,
                    )
                }
            } catch (_: Exception) {
                null
            }
            if (judgement != null && !judgement.accepted) {
                val errors = JSONArray().apply {
                    val extras = judgement.extraChanges.ifEmpty { listOf(judgement.reason) }
                    extras.forEachIndexed { index, message ->
                        put(
                            JSONObject()
                                .put("path", "scope")
                                .put("message", if (extras.size > 1) "${index + 1}. $message" else message),
                        )
                    }
                }
                val message = "The converted plan changes more than you asked for, so it was not saved. ${judgement.reason}"
                return failSave(message, ready.attemptsJson, errors.toString())
            }
        }
        edit(persist = false) {
            val setup = if (it.setupRuntime.phase == SetupPhase.Checking) {
                it.setupRuntime.copy(phase = SetupPhase.Saving)
            } else {
                it.setupRuntime
            }
            it.copy(
                chatRuntime = it.chatRuntime.copy(saving = false, judging = false, preview = false),
                setupRuntime = setup,
            )
        }
        saveProgram(
            ready.plan,
            note ?: "Saved from AI ${state.chat.mode}",
            source,
            goHome,
        )
    } catch (error: Exception) {
        val message = cloudMessage(error, "Failed to save program. Please try again.")
        edit(persist = false) {
            it.copy(
                chatRuntime = it.chatRuntime.copy(
                    saving = false,
                    judging = false,
                    preview = false,
                    error = message,
                    hardFailure = message,
                ),
            )
        }
        ActionResult.Error(message)
    }
}

internal suspend fun Controller.reportChat(): ActionResult {
    val state = snapshot()
    if (state.cloud.reportIssueUrl.isBlank()) return ActionResult.Error("Report URL is not set.")
    val session = state.auth ?: return ActionResult.Error("Sign in again")
    return try {
        val id = withContext(Dispatchers.IO) {
            cloud.reportIssue(
                state.cloud,
                session,
                state.chat.turns.filter { it.role == "user" || it.role == "assistant" },
                state.chat.plaintextDraft,
                state.chat.runningSummary,
                state.chatRuntime.reportAttempts,
                state.chatRuntime.reportErrors,
            )
        }
        edit(persist = false) { it.copy(chatRuntime = it.chatRuntime.copy(reportId = id)) }
        ActionResult.Ok
    } catch (error: Exception) {
        ActionResult.Error(cloudMessage(error, "Report failed"))
    }
}

internal suspend fun Controller.setChatPreview(open: Boolean) {
    edit(persist = false) { state ->
        state.copy(
            chatRuntime = state.chatRuntime.copy(
                preview = open,
                draftOpened = state.chatRuntime.draftOpened || open,
            ),
        )
    }
}

internal suspend fun Controller.setComposer(value: String) {
    edit(persist = false) { it.copy(chatRuntime = it.chatRuntime.copy(composer = value)) }
}

private suspend fun Controller.failSave(message: String, attempts: String, errors: String): ActionResult {
    edit(persist = false) {
        it.copy(
            chatRuntime = it.chatRuntime.copy(
                saving = false,
                judging = false,
                preview = false,
                error = message,
                hardFailure = message,
                reportAttempts = attempts.ifBlank { "[]" },
                reportErrors = errors.ifBlank { "[]" },
            ),
        )
    }
    return ActionResult.Error(message)
}

private fun formatScopeRejection(result: JudgeResult): String {
    val extras = result.extraChanges.take(8)
    val extraBlock = if (extras.isEmpty()) {
        ""
    } else {
        "\n\nExtra changes that were not applied:\n" + extras.joinToString("\n") { "- $it" }
    }
    return "Those edits were too broad, so the plan was not updated.\n\n${result.reason}$extraBlock\n\nAsk for a smaller change, or confirm the extras if you actually want them."
}
