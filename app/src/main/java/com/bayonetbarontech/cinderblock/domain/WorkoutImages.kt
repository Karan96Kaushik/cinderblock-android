package com.bayonetbarontech.cinderblock.domain

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

@Serializable
data class WorkoutImageCatalog(
    val default: String = "push",
    val minScore: Double = 0.45,
    val images: List<WorkoutImageRule> = emptyList(),
)

@Serializable
data class WorkoutImageRule(
    val drawable: String = "",
    val match: List<String> = emptyList(),
)

fun WorkoutImageCatalog.drawableFor(label: String): String {
    val fallback = default.ifBlank { "push" }
    var winner = fallback
    var bestScore = 0.0
    var bestLength = -1
    images.forEach { rule ->
        val drawable = rule.drawable.trim()
        if (drawable.isEmpty()) return@forEach
        val terms = (listOf(drawable) + rule.match).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        for (term in terms) {
            val score = matchScore(label, term)
            val length = normalizeMatch(term).length
            val better = score > bestScore + 0.001 || (kotlin.math.abs(score - bestScore) <= 0.001 && length > bestLength)
            if (better) {
                bestScore = score
                bestLength = length
                winner = drawable
            }
        }
    }
    return if (bestScore >= minScore) winner else fallback
}

internal fun matchScore(label: String, term: String): Double {
    val haystack = normalizeMatch(label)
    val needle = normalizeMatch(term)
    if (haystack.isEmpty() || needle.length < 2) return 0.0
    var best = pairScore(haystack, needle)
    for (token in haystack.split(' ')) {
        if (token.length < 3) continue
        best = max(best, pairScore(token, needle))
    }
    return best
}

private fun pairScore(haystack: String, needle: String): Double {
    if (haystack == needle) return 1.0
    val contained = haystack.contains(needle) || (needle.contains(haystack) && haystack.length >= 3)
    if (contained) {
        val shorter = min(haystack.length, needle.length).toDouble()
        val longer = max(haystack.length, needle.length).toDouble()
        return 0.70 + 0.30 * (shorter / longer)
    }
    val longest = max(haystack.length, needle.length)
    if (longest == 0) return 0.0
    val ratio = 1.0 - levenshtein(haystack, needle).toDouble() / longest
    return if (ratio < 0.5) 0.0 else ratio
}

private fun normalizeMatch(value: String): String =
    value.lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex(" +"), " ")

private fun levenshtein(left: String, right: String): Int {
    if (left == right) return 0
    if (left.isEmpty()) return right.length
    if (right.isEmpty()) return left.length
    val prev = IntArray(right.length + 1) { it }
    val curr = IntArray(right.length + 1)
    for (i in left.indices) {
        curr[0] = i + 1
        for (j in right.indices) {
            val cost = if (left[i] == right[j]) 0 else 1
            curr[j + 1] = minOf(curr[j] + 1, prev[j + 1] + 1, prev[j] + cost)
        }
        for (j in prev.indices) prev[j] = curr[j]
    }
    return prev[right.length]
}
