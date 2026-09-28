package `as`.today.missyou.domain.model

import kotlinx.serialization.Serializable

/** Inline character-level emphasis. */
@Serializable
enum class TextMark { BOLD, ITALIC, UNDERLINE, STRIKETHROUGH, CODE }

/** The five highlight tones offered by the editor (§7). */
@Serializable
enum class HighlightTone { YELLOW, GREEN, BLUE, PINK, ORANGE }

/** Horizontal alignment. */
@Serializable
enum class TextAlign { START, CENTER, END }

/** How a paragraph participates in a list. */
@Serializable
enum class ListStyle {
    /** Not a list item. */
    NONE,

    /** Bulleted item. */
    BULLET,

    /** Numbered item. The number is derived from the preceding run of siblings. */
    NUMBERED,

    /** Checkable item. */
    CHECK,
}

@Serializable
enum class CalloutTone { INFO, SUCCESS, WARNING, DANGER, TIP }

/** A half-open `[start, end)` range carrying a single inline mark. */
@Serializable
data class MarkRange(
    val start: Int,
    val end: Int,
    val mark: TextMark,
) {
    val isEmpty: Boolean get() = end <= start
}

/** A half-open `[start, end)` range painted with a highlight colour. */
@Serializable
data class HighlightRange(
    val start: Int,
    val end: Int,
    val tone: HighlightTone,
) {
    val isEmpty: Boolean get() = end <= start
}

/** A half-open `[start, end)` range that is a tappable link. */
@Serializable
data class LinkRange(
    val start: Int,
    val end: Int,
    val url: String,
) {
    val isEmpty: Boolean get() = end <= start
}

/**
 * A run of text plus the ranges that style it.
 *
 * Ranges rather than per-character styles keep the document small and make editing
 * a single index-shifting operation, which is what allows the editor to stay smooth
 * on low-end hardware.
 */
@Serializable
data class StyledText(
    val text: String = "",
    val marks: List<MarkRange> = emptyList(),
    val highlights: List<HighlightRange> = emptyList(),
    val links: List<LinkRange> = emptyList(),
) {

    val isBlank: Boolean get() = text.isBlank() && !hasDecoration

    private val hasDecoration: Boolean
        get() = marks.any { !it.isEmpty } ||
            highlights.any { !it.isEmpty } ||
            links.any { !it.isEmpty }

    val length: Int get() = text.length

    /** True when [mark] covers the whole selection, used for toolbar toggle state. */
    fun isFullyMarked(start: Int, end: Int, mark: TextMark): Boolean {
        if (end <= start) return false
        return marks.any { it.mark == mark && it.start <= start && it.end >= end }
    }

    fun isFullyHighlighted(start: Int, end: Int): HighlightTone? {
        if (end <= start) return null
        return highlights.firstOrNull { it.start <= start && it.end >= end }?.tone
    }

    fun linkAt(position: Int): LinkRange? =
        links.firstOrNull { position >= it.start && position < it.end }

    /**
     * Applies a text edit and shifts every stored range to match.
     *
     * This is the single place where text and styling can get out of sync, so it is
     * covered directly by unit tests including deletions, multi-character
     * replacement and insertion exactly on a range boundary.
     *
     * @param startInclusive start of the replaced region.
     * @param endExclusive end of the replaced region.
     * @param replacement text inserted in its place ("" for a deletion).
     */
    fun applyEdit(
        startInclusive: Int,
        endExclusive: Int,
        replacement: String,
    ): StyledText {
        val safeStart = startInclusive.coerceIn(0, text.length)
        val safeEnd = endExclusive.coerceIn(safeStart, text.length)
        if (safeStart == 0 && safeEnd == text.length && replacement == text) return this

        val insertedLength = replacement.length
        val delta = insertedLength - (safeEnd - safeStart)
        val newText = buildString(text.length + insertedLength) {
            append(text, 0, safeStart)
            append(replacement)
            append(text, safeEnd, text.length)
        }

        fun mapPosition(position: Int): Int = when {
            position >= safeEnd -> position + delta
            position <= safeStart -> position
            else -> safeStart + insertedLength
        }

        val newMarks = marks
            .map { it.copy(start = mapPosition(it.start), end = mapPosition(it.end)) }
            .filterNot { it.isEmpty }
            .sortedWith(compareBy({ it.start }, { it.end }))
            .let(::mergeMarks)

        val newHighlights = highlights
            .map { it.copy(start = mapPosition(it.start), end = mapPosition(it.end)) }
            .filterNot { it.isEmpty }
            .sortedWith(compareBy({ it.start }, { it.end }))
            .let(::mergeHighlights)

        val newLinks = links
            .map { it.copy(start = mapPosition(it.start), end = mapPosition(it.end)) }
            .filter { !it.isEmpty && it.url.isNotBlank() }
            .map { it.copy(url = it.url.trim()) }
            .distinctBy { it.start to it.end }

        return StyledText(newText, newMarks, newHighlights, newLinks)
    }

    /** Adds or removes [mark] across `[start, end)`. Returns the new styled text. */
    fun toggleMark(start: Int, end: Int, mark: TextMark): StyledText {
        if (end <= start) return copy(marks = marks + MarkRange(start, start, mark))
        val existing = marks.firstOrNull { it.mark == mark && it.start <= start && it.end >= end }
        val next = if (existing != null) {
            marks - existing
        } else {
            (marks - marks.filter { it.mark == mark && it.start < end && it.end > start }) +
                MarkRange(start, end, mark)
        }
        return copy(marks = next.sortedWith(compareBy({ it.start }, { it.end })))
    }

    fun setHighlight(start: Int, end: Int, tone: HighlightTone?): StyledText {
        if (end <= start) return this
        val cleared = highlights.filterNot { it.start < end && it.end > start }
        val added = if (tone == null) emptyList() else listOf(HighlightRange(start, end, tone))
        return copy(highlights = (cleared + added).sortedWith(compareBy({ it.start }, { it.end })))
    }

    fun setLink(start: Int, end: Int, url: String?): StyledText {
        if (end <= start) return this
        val cleared = links.filterNot { it.start < end && it.end > start }
        val added = if (url.isNullOrBlank()) emptyList() else listOf(LinkRange(start, end, url.trim()))
        return copy(links = (cleared + added).sortedBy { it.start })
    }

    /** Removes every decoration, used by "clear formatting". */
    fun clearFormatting(): StyledText = copy(marks = emptyList(), highlights = emptyList(), links = emptyList())

    /** True when the styled text carries no formatting worth exporting. */
    fun isPlain(): Boolean = marks.isEmpty() && highlights.isEmpty() && links.isEmpty()

    private fun mergeMarks(ranges: List<MarkRange>): List<MarkRange> {
        val result = mutableListOf<MarkRange>()
        for (range in ranges) {
            if (range.isEmpty) continue
            val previous = result.lastOrNull()
            if (previous != null && previous.mark == range.mark && range.start <= previous.end) {
                result[result.lastIndex] = previous.copy(end = maxOf(previous.end, range.end))
            } else {
                result.add(range)
            }
        }
        return result
    }

    private fun mergeHighlights(ranges: List<HighlightRange>): List<HighlightRange> {
        val result = mutableListOf<HighlightRange>()
        for (range in ranges) {
            if (range.isEmpty) continue
            val previous = result.lastOrNull()
            if (previous != null && previous.tone == range.tone && range.start <= previous.end) {
                result[result.lastIndex] = previous.copy(end = maxOf(previous.end, range.end))
            } else {
                result.add(range)
            }
        }
        return result
    }

    companion object {
        val Empty = StyledText()

        fun plain(text: String): StyledText = StyledText(text = text)
    }
}
