package `as`.today.missyou.core.text

import java.util.Locale

/**
 * Word / character / reading-time metrics.
 *
 * Counting is intentionally Unicode-aware: an emoji or a CJK character should not
 * be reported as a half-empty word, and apostrophes should not split "don't".
 */
object TextMetrics {

    private val WORD_SPLIT_REGEX = Regex("[\\s 　]+")
    private val WORD_REGEX = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}'’\\-]*")

    private const val WORDS_PER_MINUTE = 200

    fun wordCount(text: String?): Int {
        if (text.isNullOrEmpty()) return 0
        return WORD_REGEX.findAll(text).count()
    }

    fun characterCount(text: String?): Int = text?.length ?: 0

    fun characterCountNoSpaces(text: String?): Int {
        if (text.isNullOrEmpty()) return 0
        return text.count { !it.isWhitespace() }
    }

    fun readingTimeMinutes(wordCount: Int, wordsPerMinute: Int = WORDS_PER_MINUTE): Int {
        if (wordCount <= 0) return 0
        return ((wordCount + wordsPerMinute - 1) / wordsPerMinute).coerceAtLeast(1)
    }

    fun readingTimeLabel(wordCount: Int): String {
        val minutes = readingTimeMinutes(wordCount)
        return if (minutes < 1) "< 1 min read" else "$minutes min read"
    }

    /**
     * A short single-line preview that never breaks a word across a newline.
     */
    fun preview(text: String?, maxChars: Int = 180): String {
        if (text.isNullOrBlank()) return ""
        val collapsed = text.replace(Regex("\\s+"), " ").trim()
        if (collapsed.length <= maxChars) return collapsed
        val cut = collapsed.take(maxChars)
        val lastSpace = cut.lastIndexOf(' ')
        val head = if (lastSpace > maxChars / 2) cut.take(lastSpace) else cut
        return head.trimEnd() + "…"
    }

    /** Normalised form used for search tokenisation and duplicate detection. */
    fun normalizeForSearch(text: String): String =
        text.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /**
     * Splits [TextMetrics.normalizeForSearch] output into unique tokens, preserving
     * a deterministic order so the persisted token index is stable across saves.
     */
    fun searchTokens(text: String, limit: Int = MAX_TOKENS_PER_ENTRY): List<String> {
        val seen = LinkedHashSet<String>()
        for (chunk in normalizeForSearch(text).split(' ')) {
            if (chunk.isEmpty()) continue
            if (seen.size >= limit) break
            seen.add(chunk)
        }
        return seen.toList()
    }

    const val MAX_TOKENS_PER_ENTRY = 240
}
