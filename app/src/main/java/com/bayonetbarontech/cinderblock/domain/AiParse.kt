package com.bayonetbarontech.cinderblock.domain

data class ParsedAssistant(
    val displayText: String,
    val runningSummary: String?,
    val plaintextDraft: String?,
    val planReady: Boolean,
)

private const val SUMMARY_START = "<<SUMMARY>>"
private const val SUMMARY_END = "<<END_SUMMARY>>"
private const val PLAN_START = "<<PLAN>>"
private const val PLAN_END = "<<END_PLAN>>"
private const val PLAN_READY = "<<PLAN_READY>>"
const val STREAM_ERROR = "<<STREAM_ERROR>>"
const val AI_CHAT_SCHEMA_VERSION = "1.0"
const val AI_CHAT_MAX_MESSAGE_CHARS = 4_000
const val AI_CHAT_MAX_RECENT_TURNS = 7

fun parseAssistant(raw: String, streaming: Boolean = false): ParsedAssistant {
    var working = raw
    var summary: String? = null
    var draft: String? = null
    var ready = false
    val summaryMatch = Regex("$SUMMARY_START([\\s\\S]*?)$SUMMARY_END").find(working)
    if (summaryMatch != null) {
        summary = summaryMatch.groupValues[1].trim().ifBlank { null }
        working = working.replace(summaryMatch.value, "")
    }
    val planMatch = Regex("$PLAN_START([\\s\\S]*?)$PLAN_END").find(working)
    if (planMatch != null) {
        draft = planMatch.groupValues[1].trim().ifBlank { null }
        working = working.replace(planMatch.value, "\n${planMatch.groupValues[1]}\n")
    }
    if (working.contains(PLAN_READY)) {
        ready = true
        working = working.replace(PLAN_READY, "")
    }
    if (streaming) working = scrubPartial(working)
    return ParsedAssistant(
        displayText = working.replace(Regex("\n{3,}"), "\n\n").trim(),
        runningSummary = summary,
        plaintextDraft = draft,
        planReady = ready,
    )
}

private fun scrubPartial(text: String): String {
    var working = text
    val summaryOpen = working.lastIndexOf(SUMMARY_START)
    if (summaryOpen >= 0 && !working.startsWith(SUMMARY_END, summaryOpen) &&
        !working.substring(summaryOpen).contains(SUMMARY_END)
    ) {
        working = working.substring(0, summaryOpen)
    }
    val planOpen = working.lastIndexOf(PLAN_START)
    if (planOpen >= 0 && !working.substring(planOpen).contains(PLAN_END)) {
        working = working.substring(0, planOpen) + working.substring(planOpen + PLAN_START.length)
    }
    return working.replace(
        Regex("""<<(?:PLAN(?:_READY)?)?$|<<(?:END_)?(?:PLAN|SUMMARY)?$""", RegexOption.IGNORE_CASE),
        "",
    )
}

fun stripPlanBlock(raw: String): String =
    raw.replace(Regex("$PLAN_START[\\s\\S]*?$PLAN_END"), "").trim()

data class TrimmedTurns(val recent: List<ChatTurn>, val overflow: List<ChatTurn>)

fun trimRecentTurns(turns: List<ChatTurn>, maxTurns: Int = AI_CHAT_MAX_RECENT_TURNS): TrimmedTurns {
    if (turns.size <= maxTurns) return TrimmedTurns(turns, emptyList())
    val overflowCount = turns.size - maxTurns
    return TrimmedTurns(turns.drop(overflowCount), turns.take(overflowCount))
}

fun foldOverflowIntoSummary(currentSummary: String, overflow: List<ChatTurn>): String {
    if (overflow.isEmpty()) return currentSummary
    val overflowText = overflow.joinToString(" | ") { turn ->
        "${turn.role}: ${turn.content.take(240)}"
    }
    val addition = "Earlier context: $overflowText"
    val base = currentSummary.trim()
    return if (base.isEmpty()) addition else "$base\n$addition"
}

fun planMateriallyChanged(previous: String, proposed: String): Boolean {
    val before = planLines(previous)
    val after = planLines(proposed)
    return before != after
}

private fun planLines(text: String): Set<String> =
    text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("version:", ignoreCase = true) }
        .toSet()

fun stripFlushPad(chunk: String): String = chunk.replace("\u200b", "")
