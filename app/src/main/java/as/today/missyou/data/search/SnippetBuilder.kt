package `as`.today.missyou.data.search

import `as`.today.missyou.core.text.TextMetrics
import `as`.today.missyou.domain.model.Snippet

/**
 * Builds a highlighted excerpt for a search hit.
 *
 * Match offsets are computed once, here, against a single normalised string, and
 * handed to the UI. The UI therefore never has to re-run a regex while composing,
 * and the matching rules stay testable without Compose.
 */
object SnippetBuilder {

    private const val CONTEXT_RADIUS = 70

    fun build(text: String, queryTokens: List<String>, maxLength: Int = 160): Snippet {
        if (text.isBlank()) return Snippet("", emptyList())

        val lowered = text.lowercase()
        val ranges = mutableListOf<IntRange>()

        for (token in queryTokens) {
            if (token.isBlank()) continue
            var from = 0
            while (true) {
                val index = lowered.indexOf(token, from)
                if (index < 0) break
                ranges.add(index until (index + token.length))
                from = index + token.length
                if (ranges.size >= MAX_MATCHES) break
            }
            if (ranges.size >= MAX_MATCHES) break
        }

        if (ranges.isEmpty()) {
            val preview = TextMetrics.preview(text, maxLength)
            return Snippet(preview, emptyList())
        }

        val merged = mergeRanges(ranges)
        val window = windowAround(merged.first(), text, maxLength)
        val slice = text.substring(window.first, window.last + 1)
        val offset = window.first
        val adjusted = merged
            .filter { it.last >= offset && it.first <= window.last }
            .map { (it.first - offset).coerceAtLeast(0)..(it.last - offset).coerceAtMost(slice.length - 1) }

        val prefix = if (offset > 0) "…" else ""
        val suffix = if (window.last < text.length - 1) "…" else ""
        return Snippet(prefix + slice + suffix, adjusted)
    }

    private fun windowAround(firstMatch: IntRange, text: String, maxLength: Int): IntRange {
        val textLength = text.length
        if (textLength <= maxLength) return 0..(textLength - 1)
        var start = (firstMatch.first - CONTEXT_RADIUS).coerceAtLeast(0)
        if (start + maxLength > textLength) {
            start = (textLength - maxLength).coerceAtLeast(0)
        }
        // Prefer a word boundary so the snippet does not start mid-word.
        val space = text.indexOf(' ', start)
        if (space in (start + 1)..(start + CONTEXT_RADIUS)) start = space + 1
        val end = (start + maxLength - 1).coerceAtMost(textLength - 1)
        return start..end
    }

    private fun mergeRanges(ranges: List<IntRange>): List<IntRange> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedBy { it.first }
        val result = mutableListOf(sorted.first())
        for (range in sorted.drop(1)) {
            val last = result.last()
            if (range.first <= last.last + 1) {
                result[result.lastIndex] = last.first..maxOf(last.last, range.last)
            } else {
                result.add(range)
            }
        }
        return result
    }

    private const val MAX_MATCHES = 50
}
