package com.bayonetbarontech.cinderblock.data

import com.bayonetbarontech.cinderblock.domain.ActionResult
import com.bayonetbarontech.cinderblock.domain.ChatPersisted
import com.bayonetbarontech.cinderblock.domain.ChatRuntime
import com.bayonetbarontech.cinderblock.domain.SetupPhase
import com.bayonetbarontech.cinderblock.domain.SetupRuntime
import com.bayonetbarontech.cinderblock.domain.TrainingProfile
import com.bayonetbarontech.cinderblock.domain.UiEffect
import com.bayonetbarontech.cinderblock.domain.landingRoute
import com.bayonetbarontech.cinderblock.domain.mergeAthleteSummary
import com.bayonetbarontech.cinderblock.domain.setupBrief
import com.bayonetbarontech.cinderblock.domain.setupGenerationPrompt

internal suspend fun Controller.updateProfile(block: (TrainingProfile) -> TrainingProfile) {
    edit { it.copy(profile = block(it.profile)) }
}

internal suspend fun Controller.finishSetupWithoutPlan() {
    edit {
        it.copy(
            profile = it.profile.copy(completed = true),
            setupRuntime = SetupRuntime(),
        )
    }
    effect(UiEffect.Navigate(landingRoute(snapshot().profile)))
}

internal suspend fun Controller.generateSetupPlan(): ActionResult {
    val state = snapshot()
    val runtime = state.chatRuntime
    if (runtime.streaming || runtime.judging || runtime.saving) return ActionResult.Error("A plan is already being generated.")
    if (state.setupRuntime.phase != SetupPhase.Idle) return ActionResult.Error("A plan is already being generated.")
    if (state.auth == null) {
        failSetup("Sign in to generate a plan. Your answers are saved.")
        return ActionResult.Error("Sign in to generate a plan. Your answers are saved.")
    }
    if (!state.cloud.aiConfigured) {
        failSetup("AI chat is not configured yet. Deploy the Amplify sandbox so the function URLs are set.")
        return ActionResult.Error("AI chat is not configured yet.")
    }
    val brief = setupBrief(state.profile)
    val prompt = setupGenerationPrompt(state.profile)
    edit {
        it.copy(
            chat = ChatPersisted(
                sessionId = it.chat.sessionId.ifBlank { newId() },
                mode = "create",
                runningSummary = brief,
            ),
            chatRuntime = ChatRuntime(),
            setupRuntime = SetupRuntime(phase = SetupPhase.Writing),
        )
    }
    sendChat(prompt)
    val afterStream = snapshot()
    val streamError = afterStream.chatRuntime.error
    if (streamError != null) {
        failSetup(streamError)
        return ActionResult.Error(streamError)
    }
    if (afterStream.chat.plaintextDraft.isBlank() || (!afterStream.chat.planReady && !afterStream.chat.hasPlanDraft)) {
        failSetup("The coach did not return a plan. Try again.")
        return ActionResult.Error("The coach did not return a plan. Try again.")
    }
    edit {
        it.copy(
            setupRuntime = SetupRuntime(phase = SetupPhase.Checking),
            chat = it.chat.copy(runningSummary = mergeAthleteSummary(it.chat.runningSummary, brief)),
        )
    }
    val saved = saveChat(source = "ai-setup", note = "Saved from setup", goHome = false)
    if (saved is ActionResult.Error) {
        failSetup(saved.message)
        return saved
    }
    edit {
        it.copy(
            profile = it.profile.copy(completed = true),
            setupRuntime = SetupRuntime(),
            chat = ChatPersisted(
                sessionId = newId(),
                mode = "discuss",
                runningSummary = brief,
            ),
            chatRuntime = ChatRuntime(),
        )
    }
    effect(UiEffect.Navigate("home"))
    return ActionResult.Ok
}

private suspend fun Controller.failSetup(message: String) {
    edit(persist = false) {
        it.copy(
            setupRuntime = SetupRuntime(error = message),
            chatRuntime = it.chatRuntime.copy(
                streaming = false,
                judging = false,
                saving = false,
            ),
        )
    }
}
