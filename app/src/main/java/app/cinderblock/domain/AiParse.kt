package app.cinderblock.domain

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
    val summary = working.indexOf(SUMMARY_START)
    val summaryEnd = working.indexOf(SUMMARY_END)
    if (summary >= 0 && (summaryEnd < 0 || summaryEnd < summary)) {
        working = working.substring(0, summary)
    }
    val plan = working.indexOf(PLAN_START)
    val planEnd = working.indexOf(PLAN_END)
    if (plan >= 0 && (planEnd < 0 || planEnd < plan)) {
        working = working.substring(0, plan)
    }
    val last = working.lastIndexOf("<<")
    if (last >= 0 && !working.substring(last).contains(">>")) {
        working = working.substring(0, last)
    }
    return working
}

fun stripFlushPad(chunk: String): String = chunk.replace("\u200b", "")
