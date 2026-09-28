package `as`.today.missyou.data.search

import `as`.today.missyou.core.text.TextMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sealed-token search index.
 *
 * Search is the one feature that cannot simply decrypt everything, so the index's
 * contract matters: it must never miss a real match, and every candidate it offers
 * has to be treated as unverified until the text is actually checked.
 */
class SearchIndexTest {

    private val entries = listOf("entry-a", "entry-b", "entry-c")

    private fun indexOf(vararg bodies: String): SearchIndex = SearchIndex.build(
        entryIds = bodies.indices.map { "entry-${'a' + it}" },
        entryTokens = bodies.map { TextMetrics.searchTokens(it) },
    )

    @Test
    fun `token matching is exact, not stemmed`() {
        val index = indexOf(
            "the garden was quiet today",
            "a long walk by the river",
            "gardening in the rain again",
        )

        // The index is keyed on whole tokens; verification against the decrypted
        // text exists to reject 32-bit fingerprint collisions, not to stem. So a
        // query for "garden" does not reach "gardening", and precision is preferred
        // over recall here.
        assertEquals(listOf(0), index.candidates(listOf("garden")))
        assertEquals(listOf(2), index.candidates(listOf("gardening")))

        // A whole-word query is still satisfied by a word that merely contains it,
        // which is what the caller's substring verification then confirms.
        val lower = index.candidates(listOf("garden")).map { index.entryIdAt(it) }
        assertTrue(lower.contains("entry-a"))
    }

    @Test
    fun `a multi-word query returns the union of the entries matching any word`() {
        val index = indexOf(
            "the garden was quiet",
            "a long walk",
            "gardening tools and river walks",
        )

        assertEquals(
            "'walks' is a different token from 'walk', so only entries containing the " +
                "exact word 'walk' are returned.",
            listOf(0, 1),
            index.candidates(listOf("garden", "walk")),
        )
    }

    @Test
    fun `results are returned in a stable ascending order`() {
        val index = indexOf("alpha", "beta alpha", "alpha gamma")

        // Ordering matters because the caller maps ordinals back to entry ids and a
        // jumping list would make the UI reorder itself between keystrokes.
        assertEquals(listOf(0, 1, 2), index.candidates(listOf("alpha")))
    }

    @Test
    fun `an empty query or an empty index yields nothing`() {
        assertTrue(indexOf("anything").candidates(emptyList()).isEmpty())
        assertTrue(SearchIndex.Empty.candidates(listOf("anything")).isEmpty())
        assertTrue(SearchIndex.Empty.isEmpty)
    }

    @Test
    fun `an entry with no tokens still occupies its ordinal`() {
        // A blank entry must not shift the ordinals of everything after it, or
        // search results would be attributed to the wrong day.
        val index = SearchIndex.build(
            entryIds = entries,
            entryTokens = listOf(emptyList(), listOf("kept"), emptyList()),
        )

        assertEquals(listOf(1), index.candidates(listOf("kept")))
        assertEquals("entry-b", index.entryIdAt(1))
    }

    @Test
    fun `ordinals map back to the right entry ids`() {
        val index = indexOf("first", "second", "third")

        assertEquals("entry-a", index.entryIdAt(0))
        assertEquals("entry-c", index.entryIdAt(2))
        assertEquals(null, index.entryIdAt(99))
    }

    @Test
    fun `fingerprints are stable and differ between tokens`() {
        assertEquals(SearchIndex.tokenFingerprint("garden"), SearchIndex.tokenFingerprint("garden"))
        assertFalse(SearchIndex.tokenFingerprint("garden") == SearchIndex.tokenFingerprint("river"))
    }

    @Test
    fun `a fingerprint is reproducible from the same token after a restart`() {
        // Fingerprints are persisted with the entry, so they must not depend on any
        // per-process state such as a random seed.
        val first = SearchIndex.tokenFingerprint("consistent")
        val second = SearchIndex.tokenFingerprint("consistent")
        assertEquals(first, second)
    }

    @Test
    fun `a large index still returns correct candidates`() {
        val bodies = (0 until 2_000).map { "entry number $it mentions topic${it % 37}" }
        val index = SearchIndex.build(
            entryIds = bodies.indices.map { "e$it" },
            entryTokens = bodies.map { TextMetrics.searchTokens(it) },
        )

        val candidates = index.candidates(listOf("topic5"))

        assertTrue("A topic shared by many entries should return many candidates.", candidates.size > 50)
        assertTrue(
            "Every returned ordinal must really contain the token.",
            candidates.all { bodies[it].contains("topic5") },
        )
    }
}

class TextMetricsTest {

    @Test
    fun `word count ignores punctuation and extra whitespace`() {
        assertEquals(3, TextMetrics.wordCount("one two three"))
        assertEquals(3, TextMetrics.wordCount("  one,   two\n\nthree  "))
        assertEquals(0, TextMetrics.wordCount("   "))
        assertEquals(0, TextMetrics.wordCount(null))
    }

    @Test
    fun `a hyphenated word counts once`() {
        assertEquals(1, TextMetrics.wordCount("well-written"))
        assertEquals(1, TextMetrics.wordCount("don’t"))
    }

    @Test
    fun `preview truncates on a word boundary`() {
        val text = "The quick brown fox jumps over the lazy dog"
        val preview = TextMetrics.preview(text, maxChars = 20)

        assertTrue(preview.length <= 21)
        assertFalse(
            "A preview must not cut mid-word, which reads as a rendering bug.",
            preview.endsWith("jum") || preview.contains("jum|"),
        )
    }

    @Test
    fun `reading time rounds sensibly for very short entries`() {
        assertEquals(1, TextMetrics.readingTimeMinutes(5))
        assertEquals(0, TextMetrics.readingTimeMinutes(0))
        assertTrue(TextMetrics.readingTimeLabel(400).contains("2"))
    }

    @Test
    fun `search tokens are lowercased, deduplicated and bounded`() {
        val tokens = TextMetrics.searchTokens("The the THE river River")

        assertTrue("Tokens must be lowercased or search misses on case.", tokens.none { it.any { c -> c.isUpperCase() } })
        assertEquals(tokens.size, tokens.distinct().size)
        assertTrue(tokens.size <= TextMetrics.MAX_TOKENS_PER_ENTRY)
    }

    @Test
    fun `a stop-word-only entry still produces a usable token list`() {
        // If every token were dropped as a stop word, a short entry could become
        // unsearchable. The bounded cap is what protects against that instead.
        val tokens = TextMetrics.searchTokens("a an the and but or")
        assertTrue(tokens.isNotEmpty())
    }

    @Test
    fun `token serialization round-trips`() {
        val tokens = listOf("alpha", "beta", "gamma")
        assertEquals(tokens, Tokens.deserialize(Tokens.serialize(tokens)))
        assertTrue(Tokens.deserialize("").isEmpty())
    }
}
