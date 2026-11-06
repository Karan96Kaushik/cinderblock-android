package app.cinderblock.data

import app.cinderblock.domain.ActionResult
import app.cinderblock.domain.ChatTurn
import app.cinderblock.domain.ProgramDocument
import app.cinderblock.domain.STREAM_ERROR
import app.cinderblock.domain.parseAssistant
import app.cinderblock.domain.programToPlaintext
import app.cinderblock.domain.stripFlushPad
import app.cinderblock.domain.validateProgram
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal suspend fun Controller.setChatMode(mode: String) {
    edit { it.copy(chat = it.chat.copy(mode = mode)) }
}

internal fun Controller.stopChat() {
    chatStopped = true
    chatStream?.cancel()
}

internal suspend fun Controller.sendChat(text: String) {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.length > 4_000) return
    val state = snapshot()
    val runtime = state.chatRuntime
    if (runtime.streaming || runtime.judging || runtime.saving) return
    if (!state.cloud.aiConfigured) {
        edit(persist = false) {
            it.copy(
                chatRuntime = it.chatRuntime.copy(
                    error = "Deploy the Amplify sandbox and set CEREBRAS_API_KEY.",
                    composer = trimmed,
                ),
            )
        }
        return
    }
    if (state.auth == null) return
    val priorTurns = state.chat.turns
    edit {
        it.copy(
            chat = it.chat.copy(turns = it.chat.turns + ChatTurn("user", trimmed)),
            chatRuntime = it.chatRuntime.copy(composer = "", error = null, streaming = true, partial = "", hardFailure = null),
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
                summary = snapshot().chat.runningSummary,
                planContext = programToPlaintext(snapshot().program),
                turns = priorTurns.filter { it.role == "user" || it.role == "assistant" },
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
        if (!parsed.plaintextDraft.isNullOrBlank() && snapshot().cloud.aiScopeJudgeUrl.isNotBlank()) {
            edit(persist = false) {
                it.copy(chatRuntime = it.chatRuntime.copy(streaming = false, judging = true, partial = ""))
            }
            val reason = withContext(Dispatchers.IO) {
                cloud.judge(
                    snapshot().cloud,
                    ensureAuth(),
                    snapshot().chat.mode,
                    "apply",
                    snapshot().chat.runningSummary,
                    snapshot().chat.turns,
                    programToPlaintext(snapshot().program),
                    parsed.plaintextDraft,
                )
            }
            if (reason != null) {
                edit { current ->
                    current.copy(
                        chat = current.chat.copy(turns = current.chat.turns + ChatTurn("assistant", parsed.displayText)),
                        chatRuntime = current.chatRuntime.copy(streaming = false, judging = false, partial = "", error = reason),
                    )
                }
                return
            }
        }
        edit { current ->
            val previous = current.chat.plaintextDraft.takeIf { it.isNotBlank() }
            val draft = parsed.plaintextDraft
            current.copy(
                chat = current.chat.copy(
                    turns = current.chat.turns + ChatTurn("assistant", parsed.displayText.ifBlank { clean }),
                    runningSummary = parsed.runningSummary ?: current.chat.runningSummary,
                    plaintextDraft = draft ?: current.chat.plaintextDraft,
                    previousDraft = if (draft != null) previous else current.chat.previousDraft,
                    planReady = current.chat.planReady || parsed.planReady,
                    hasPlanDraft = current.chat.hasPlanDraft || draft != null,
                ),
                chatRuntime = current.chatRuntime.copy(
                    streaming = false,
                    judging = false,
                    partial = "",
                    draftOpened = if (draft != null) false else current.chatRuntime.draftOpened,
                ),
            )
        }
    } catch (error: Exception) {
        if (error is CancellationException && !chatStopped) throw error
        val partial = buffer.toString().ifBlank { snapshot().chatRuntime.partial }
        if (chatStopped) {
            edit { current ->
                val turns = if (partial.isBlank()) {
                    current.chat.turns
                } else {
                    current.chat.turns + ChatTurn("assistant", parseAssistant(partial, streaming = true).displayText)
                }
                current.copy(
                    chat = current.chat.copy(turns = turns),
                    chatRuntime = current.chatRuntime.copy(streaming = false, judging = false, partial = ""),
                )
            }
            return
        }
        val message = cloudMessage(error, "AI chat failed")
        edit { current ->
            val stuck = partial.isBlank()
            val turns = if (stuck) {
                priorTurns
            } else {
                current.chat.turns + ChatTurn("assistant", parseAssistant(partial, streaming = true).displayText)
            }
            current.copy(
                chat = current.chat.copy(turns = turns),
                chatRuntime = current.chatRuntime.copy(
                    streaming = false,
                    judging = false,
                    partial = "",
                    error = message,
                    needsReauth = error is HttpException && error.code == 401,
                    hardFailure = if (!stuck || current.chat.plaintextDraft.isNotBlank()) message else null,
                    composer = if (stuck) trimmed else "",
                ),
            )
        }
    }
}

internal suspend fun Controller.revertChat() {
    edit { state ->
        val previous = state.chat.previousDraft ?: return@edit state
        state.copy(
            chat = state.chat.copy(plaintextDraft = previous, previousDraft = null, planReady = false, hasPlanDraft = previous.isNotBlank()),
        )
    }
}

internal suspend fun Controller.saveChat(): ActionResult {
    val state = snapshot()
    if (state.auth == null) return ActionResult.Error("You must be logged in to save changes")
    val draft = state.chat.plaintextDraft
    if (draft.isBlank() || (!state.chat.planReady && !state.chat.hasPlanDraft)) {
        return ActionResult.Error("There are no AI changes to save yet.")
    }
    edit(persist = false) { it.copy(chatRuntime = it.chatRuntime.copy(saving = true, error = null)) }
    return try {
        if (state.cloud.aiScopeJudgeUrl.isNotBlank()) {
            edit(persist = false) { it.copy(chatRuntime = it.chatRuntime.copy(judging = true)) }
            val reason = withContext(Dispatchers.IO) {
                cloud.judge(
                    state.cloud,
                    ensureAuth(),
                    state.chat.mode,
                    "save",
                    state.chat.runningSummary,
                    state.chat.turns,
                    programToPlaintext(state.program),
                    draft,
                )
            }
            if (reason != null) {
                edit(persist = false) {
                    it.copy(chatRuntime = it.chatRuntime.copy(saving = false, judging = false, preview = false, error = reason))
                }
                return ActionResult.Error(reason)
            }
        }
        val extracted = programFromDraft(draft) ?: withContext(Dispatchers.IO) {
            cloud.extractPlan(state.cloud, ensureAuth(), draft, state.chat.runningSummary)
        }
        edit(persist = false) { it.copy(chatRuntime = it.chatRuntime.copy(saving = false, judging = false, preview = false)) }
        saveProgram(extracted, "AI program edit", "ai-chat")
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
                state.chat.turns,
                state.chat.plaintextDraft,
                state.chat.runningSummary,
                state.chatRuntime.hardFailure ?: state.chatRuntime.error.orEmpty(),
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

private fun programFromDraft(text: String): ProgramDocument? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return try {
        val program = AppJson.decodeFromString<ProgramDocument>(text.substring(start, end + 1))
        if (validateProgram(program) == null && program.name.isNotBlank()) program else null
    } catch (_: Exception) {
        null
    }
}
