package app.cinderblock.ui.ai

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.cinderblock.CinderViewModel
import app.cinderblock.domain.ChatTurn
import app.cinderblock.domain.formatVersionLabel
import app.cinderblock.domain.parseAssistant
import app.cinderblock.domain.programToPlaintext
import app.cinderblock.ui.components.DisplayText
import app.cinderblock.ui.components.FeatureBar
import app.cinderblock.ui.components.Mono
import app.cinderblock.ui.components.OutlineButton
import app.cinderblock.ui.components.PrimaryButton
import app.cinderblock.ui.nav.Routes
import app.cinderblock.ui.nav.goHome
import app.cinderblock.ui.theme.LocalColors
import app.cinderblock.ui.theme.LocalType
import kotlinx.coroutines.launch

@Composable
fun AiChatScreen(mode: String, nav: NavHostController, vm: CinderViewModel) {
    val state by vm.controller.state.collectAsState()
    val colors = LocalColors.current
    val scope = rememberCoroutineScope()
    val signedIn = state.auth != null && state.cloud.configured
    LaunchedEffect(mode, signedIn) {
        if (signedIn) vm.controller.setChatMode(mode)
    }
    if (!signedIn) {
        Column(Modifier.fillMaxSize().background(colors.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureBar("DISCUSS", { nav.goHome() })
            DisplayText("Discuss with AI", size = 22.sp)
            Mono("${state.program.name} · ${formatVersionLabel(state.program.version)}")
            Mono("AI workout chat requires a signed-in Supabase account so your plans can sync securely.", color = colors.foreground)
            PrimaryButton("Sign in to continue", { nav.navigate(Routes.Login) })
            OutlineButton("Back", { nav.goHome() })
        }
    } else {
    val runtime = state.chatRuntime
    val chat = state.chat
    var text by remember { mutableStateOf(runtime.composer) }
    LaunchedEffect(runtime.composer) { text = runtime.composer }
    val last = chat.turns.lastOrNull()
    val finishedAssistant = last?.role == "assistant" && !runtime.streaming && !runtime.judging
    val canSave = chat.plaintextDraft.isNotBlank() && (chat.planReady || chat.hasPlanDraft) &&
        !runtime.streaming && !runtime.judging && !runtime.saving
    val sendBlocked = text.isBlank() || runtime.streaming || runtime.judging || runtime.saving
    fun send() {
        val value = text.trim()
        if (value.isEmpty() || runtime.streaming || runtime.judging || runtime.saving) return
        text = ""
        scope.launch { vm.controller.sendChat(value) }
    }
    BackHandler(enabled = runtime.preview) { scope.launch { vm.controller.setChatPreview(false) } }
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize()) {
            FeatureBar("DISCUSS", { nav.goHome() })
            Column(Modifier.padding(horizontal = 16.dp)) {
                DisplayText("Discuss with AI", size = 22.sp)
                Mono("${state.program.name} · ${formatVersionLabel(state.program.version)}")
            }
            if (!state.cloud.aiConfigured) {
                Mono(
                    "Deploy the Amplify sandbox and set CEREBRAS_API_KEY.",
                    color = colors.neonYellow,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (chat.turns.isEmpty() && runtime.partial.isBlank()) {
                    Mono("Ask about the current plan or describe a change. The assistant confirms before rewriting. Approve with Confirm, then View Plan and Save.")
                }
                chat.turns.forEach { turn -> Bubble(turn) }
                runtime.notices.forEach { Bubble(it, system = true) }
                if (runtime.streaming) {
                    val partial = parseAssistant(runtime.partial, streaming = true).displayText
                    Bubble(ChatTurn("assistant", if (partial.isBlank()) "Thinking…" else "$partial ▍"))
                }
                if (runtime.judging) Mono("Checking that these changes stay within what you asked for…", color = colors.neonYellow)
                runtime.error?.let { Mono(it, color = colors.destructive) }
                if (runtime.needsReauth) {
                    OutlineButton("Sign in again", { nav.navigate(Routes.Login) })
                }
                runtime.hardFailure?.let {
                    Mono(it, color = colors.destructive)
                    Mono("Your draft is still saved locally — you can keep chatting or retry Save", color = colors.foreground)
                    if (runtime.reportId != null) {
                        Mono("Report sent (${runtime.reportId.take(8)}…)", color = colors.neonOrange)
                    } else {
                        OutlineButton("Report for review", { scope.launch { vm.controller.reportChat() } })
                    }
                }
            }
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (finishedAssistant) Chip("Confirm") { sendConfirm(vm, scope) }
                Chip("View Plan", dot = chat.hasPlanDraft && !runtime.draftOpened) {
                    scope.launch { vm.controller.setChatPreview(true) }
                }
                if (chat.previousDraft != null) Chip("Revert") { scope.launch { vm.controller.revertChat() } }
                if (canSave) Chip("Save") { scope.launch { vm.controller.saveChat() } }
            }
            Row(
                Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    text,
                    { text = it },
                    placeholder = { Mono("Ask anything, or say what to change…") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                            send()
                            true
                        } else {
                            false
                        }
                    },
                )
                when {
                    runtime.judging || runtime.saving -> CircularProgressIndicator(Modifier.size(28.dp), color = colors.neonOrange, strokeWidth = 2.dp)
                    runtime.streaming -> TextButton({ vm.controller.stopChat() }) { Mono("Stop", color = colors.neonOrange) }
                    else -> TextButton({ if (!sendBlocked) send() }, enabled = !sendBlocked) { Mono("Send", color = colors.neonOrange) }
                }
            }
        }
        if (runtime.preview) {
            val revised = chat.plaintextDraft.isNotBlank()
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.72f)).clickable {
                    scope.launch { vm.controller.setChatPreview(false) }
                })
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .background(colors.card, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .padding(16.dp),
                ) {
                    DisplayText(if (revised) "Revised plan (draft)" else "Current plan", size = 18.sp)
                    Mono(
                        if (revised) "Not saved yet — review before saving" else "${state.program.name} · ${formatVersionLabel(state.program.version)}",
                    )
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Text(
                            if (revised) chat.plaintextDraft else programToPlaintext(state.program),
                            color = colors.foreground,
                            fontFamily = LocalType.current.body,
                            fontSize = 13.sp,
                        )
                    }
                    Mono(
                        when {
                            canSave -> "Saving will replace the active program."
                            runtime.streaming || runtime.judging -> "The chat still needs to finish."
                            else -> "There are no AI changes yet."
                        },
                    )
                    PrimaryButton("Save", { scope.launch { vm.controller.saveChat() } }, enabled = canSave, loading = runtime.saving)
                    OutlineButton("Close", { scope.launch { vm.controller.setChatPreview(false) } })
                }
            }
        }
    }
    }
}

private fun sendConfirm(vm: CinderViewModel, scope: kotlinx.coroutines.CoroutineScope) {
    scope.launch { vm.controller.sendChat("Confirm") }
}

@Composable
private fun Chip(label: String, dot: Boolean = false, onClick: () -> Unit) {
    val colors = LocalColors.current
    Row(
        Modifier
            .border(1.dp, colors.neonOrange, RoundedCornerShape(99.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) Box(Modifier.padding(end = 6.dp).size(8.dp).background(colors.neonOrange, CircleShape))
        Mono(label, color = colors.neonOrange)
    }
}

@Composable
private fun Bubble(turn: ChatTurn, system: Boolean = false) {
    val colors = LocalColors.current
    val type = LocalType.current
    val user = turn.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .background(if (user) colors.neonOrange else colors.card, RoundedCornerShape(12.dp))
                .border(
                    1.dp,
                    if (system) colors.neonYellow else if (user) colors.neonOrange else colors.border,
                    RoundedCornerShape(12.dp),
                )
                .padding(10.dp),
        ) {
            Text(
                turn.content,
                color = if (user) colors.primaryForeground else colors.foreground,
                fontFamily = type.body,
                fontWeight = if (user) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp,
            )
        }
    }
}
