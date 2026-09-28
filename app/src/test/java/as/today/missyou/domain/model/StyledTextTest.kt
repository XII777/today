package `as`.today.missyou.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Text editing and range maintenance.
 *
 * `StyledText` is where formatting and text are kept in step. Every one of these
 * cases corresponds to something a user does with a keyboard; a regression here
 * silently corrupts formatting rather than throwing, so the cases are written out
 * individually rather than parameterised.
 */
class StyledTextTest {

    private fun marked(text: String, vararg ranges: Triple<Int, Int, TextMark>): StyledText =
        StyledText(text, marks = ranges.map { MarkRange(it.first, it.second, it.third) })

    // ------------------------------------------------------------------ editing

    @Test
    fun `inserting inside a mark extends the mark`() {
        val bold = StyledText("hello", marks = listOf(MarkRange(0, 5, TextMark.BOLD)))

        val edited = bold.applyEdit(2, 2, "XX")

        assertEquals("heXXllo", edited.text)
        assertEquals(listOf(MarkRange(0, 7, TextMark.BOLD)), edited.marks)
    }

    @Test
    fun `deleting inside a mark shrinks the mark`() {
        val bold = StyledText("hello", marks = listOf(MarkRange(0, 5, TextMark.BOLD)))

        val edited = bold.applyEdit(1, 3, "")

        assertEquals("hlo", edited.text)
        assertEquals(listOf(MarkRange(0, 3, TextMark.BOLD)), edited.marks)
    }

    @Test
    fun `deleting a whole mark removes it rather than leaving an empty range`() {
        val bold = StyledText("ab", marks = listOf(MarkRange(0, 2, TextMark.BOLD)))

        val edited = bold.applyEdit(0, 2, "")

        assertEquals("", edited.text)
        assertTrue(
            "An emptied mark must be dropped, not kept as a zero-width range.",
            edited.marks.none { it.isEmpty },
        )
    }

    @Test
    fun `typing at a mark's start does not inherit it, but typing at its end does`() {
        val italic = StyledText("abcd", marks = listOf(MarkRange(1, 3, TextMark.ITALIC)))

        // Typing *before* an italic run must not drag the caret's character into it,
        // otherwise starting a sentence mid-paragraph silently reformats the word.
        val atStart = italic.applyEdit(1, 1, "Z")
        assertEquals("aZbcd", atStart.text)
        assertEquals(
            "The character typed at the start of an italic run must stay unformatted.",
            listOf(MarkRange(2, 4, TextMark.ITALIC)),
            atStart.marks,
        )

        // Typing at the *end* of a run continues it, which is what every word
        // processor does and what makes italic text feel continuous to write.
        val atEnd = italic.applyEdit(3, 3, "Z")
        assertEquals("abcZd", atEnd.text)
        assertEquals(
            "The character typed at the end of an italic run should inherit it.",
            listOf(MarkRange(1, 4, TextMark.ITALIC)),
            atEnd.marks,
        )
    }

    @Test
    fun `replacing a multi-character range replaces all of it`() {
        val source = StyledText("the quick fox", marks = listOf(MarkRange(4, 9, TextMark.BOLD)))

        val edited = source.applyEdit(4, 9, "slow")

        assertEquals("the slow fox", edited.text)
        assertEquals(listOf(MarkRange(4, 8, TextMark.BOLD)), edited.marks)
    }

    @Test
    fun `out of range offsets are clamped rather than throwing`() {
        val plain = StyledText("abc")

        // A buggy or racing selection must not crash the editor mid-keystroke.
        assertEquals("abcXYZ", plain.applyEdit(99, 400, "XYZ").text)
        assertEquals("", plain.applyEdit(-5, 99, "").text)
    }

    @Test
    fun `editing no-ops when nothing actually changes`() {
        val plain = StyledText("abc")

        assertTrue(plain.applyEdit(0, 3, "abc") === plain)
    }

    @Test
    fun `two marks separated by an edit merge only when they touch`() {
        val source = StyledText("abcd", marks = listOf(MarkRange(0, 1, TextMark.BOLD), MarkRange(3, 4, TextMark.BOLD)))

        // Collapse the gap: the two bold runs become contiguous and must merge.
        val merged = source.applyEdit(1, 3, "")
        assertEquals("ad", merged.text)
        assertEquals(listOf(MarkRange(0, 2, TextMark.BOLD)), merged.marks)

        val split = source.applyEdit(1, 3, "ZZ")
        assertEquals("aZZd", split.text)
        assertEquals(2, split.marks.size)
    }

    @Test
    fun `highlights shift with the text like marks do`() {
        val source = StyledText(
            text = "important",
            highlights = listOf(HighlightRange(0, 9, HighlightTone.YELLOW)),
        )

        val edited = source.applyEdit(0, 0, "very ")

        assertEquals("very important", edited.text)
        assertEquals(listOf(HighlightRange(5, 14, HighlightTone.YELLOW)), edited.highlights)
    }

    @Test
    fun `links shift with the text and drop themselves when emptied`() {
        val source = StyledText("see here", links = listOf(LinkRange(4, 8, "https://example.com")))

        val kept = source.applyEdit(0, 0, "> ")
        assertEquals(listOf(LinkRange(6, 10, "https://example.com")), kept.links)

        val removed = source.applyEdit(4, 8, "")
        assertTrue("A link whose text is gone must not survive.", removed.links.isEmpty())
    }

    // ------------------------------------------------------------------- marks

    @Test
    fun `toggling a mark on adds it and toggling again removes it`() {
        val plain = StyledText("word")

        val bold = plain.toggleMark(0, 4, TextMark.BOLD)
        assertTrue(bold.isFullyMarked(0, 4, TextMark.BOLD))

        val unbold = bold.toggleMark(0, 4, TextMark.BOLD)
        assertFalse(unbold.isFullyMarked(0, 4, TextMark.BOLD))
    }

    @Test
    fun `a collapsed selection records a pending mark rather than doing nothing`() {
        val plain = StyledText("word")

        val pending = plain.toggleMark(2, 2, TextMark.ITALIC)

        assertEquals(
            "A caret with no selection must remember what the user just tapped, " +
                "so the next typed character inherits the mark.",
            listOf(MarkRange(2, 2, TextMark.ITALIC)),
            pending.marks,
        )
    }

    @Test
    fun `isFullyMarked distinguishes a covering mark from a partial one`() {
        val source = StyledText("abcdef", marks = listOf(MarkRange(0, 3, TextMark.BOLD)))

        assertTrue(source.isFullyMarked(1, 2, TextMark.BOLD))
        assertFalse(source.isFullyMarked(0, 5, TextMark.BOLD))
        assertFalse(source.isFullyMarked(3, 6, TextMark.BOLD))
    }

    @Test
    fun `setting a highlight over a selection replaces any overlap`() {
        val source = StyledText("abcdef", highlights = listOf(HighlightRange(0, 6, HighlightTone.YELLOW)))

        val updated = source.setHighlight(2, 4, HighlightTone.PINK)

        assertEquals(listOf(HighlightRange(2, 4, HighlightTone.PINK)), updated.highlights)
    }

    @Test
    fun `clearing formatting leaves the text untouched`() {
        val source = StyledText(
            text = "styled",
            marks = listOf(MarkRange(0, 6, TextMark.BOLD)),
            highlights = listOf(HighlightRange(0, 6, HighlightTone.YELLOW)),
            links = listOf(LinkRange(0, 6, "https://example.com")),
        )

        val cleared = source.clearFormatting()

        assertEquals("styled", cleared.text)
        assertTrue(cleared.isPlain())
    }

    @Test
    fun `a blank text field is not considered content`() {
        assertTrue(StyledText("").isBlank)
        assertTrue(StyledText("   ").isBlank)
        assertFalse(StyledText("x").isBlank)
    }
}
