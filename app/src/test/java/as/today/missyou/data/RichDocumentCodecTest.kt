package `as`.today.missyou.data

import `as`.today.missyou.domain.model.CalloutBlock
import `as`.today.missyou.domain.model.CodeBlock
import `as`.today.missyou.domain.model.HeadingBlock
import `as`.today.missyou.domain.model.JournalEntry
import `as`.today.missyou.domain.model.ListStyle
import `as`.today.missyou.domain.model.ParagraphBlock
import `as`.today.missyou.domain.model.QuoteBlock
import `as`.today.missyou.domain.model.RichDocument
import `as`.today.missyou.domain.model.StyledText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Serialisation round-trips and the integrity hash.
 *
 * Both are worth testing directly because a failure in either is silent and
 * permanent: a document that will not round-trip loses formatting, and a hash that
 * does not cover a field lets a tampered entry pass verification.
 */
class RichDocumentCodecTest {

    @Test
    fun `a document survives a round trip unchanged`() {
        val original = RichDocument(
            blocks = listOf(
                ParagraphBlock(id = "b1", text = StyledText.plain("plain"), listStyle = ListStyle.BULLET),
                HeadingBlock(id = "b2", level = 2, text = StyledText.plain("heading")),
                QuoteBlock(id = "b3", text = StyledText.plain("quoted")),
                CalloutBlock(id = "b4", text = StyledText.plain("noted")),
                CodeBlock(id = "b5", code = "val x = 1", language = "kotlin"),
            ),
        )

        val restored = RichDocumentCodec.decode(RichDocumentCodec.encode(original))

        assertEquals(original, restored)
    }

    @Test
    fun `an unparseable document degrades to readable text instead of throwing`() {
        val restored = RichDocumentCodec.decode("this is not json at all")

        assertEquals("this is not json at all", restored.plainText)
        assertTrue(
            "Recovered text must still count as content, not be treated as empty.",
            !restored.isEmpty,
        )
    }

    @Test
    fun `unknown block types from a newer version are ignored rather than fatal`() {
        // A future version adds a block type this build has never heard of. The
        // journal must still open; that is the whole point of `ignoreUnknownKeys`.
        val json = """{"version":1,"blocks":[{"type":"paragraph","id":"a","text":{"text":"kept"}}]}"""

        val restored = RichDocumentCodec.decode(json)

        assertEquals("kept", restored.plainText)
    }
}

class IntegrityHasherTest {

    private val base = IntegrityHasher.compute(
        id = "entry-1",
        journalDate = "2026-02-01",
        createdAt = 1_700_000_000_000L,
        lockedAt = 1_700_086_400_000L,
        title = "A day",
        contentJson = """{"version":1,"blocks":[]}""",
        tags = listOf("one", "two"),
        prompt = "What happened?",
    )

    private fun with(
        id: String = "entry-1",
        journalDate: String = "2026-02-01",
        createdAt: Long = 1_700_000_000_000L,
        lockedAt: Long? = 1_700_086_400_000L,
        title: String = "A day",
        contentJson: String = """{"version":1,"blocks":[]}""",
        tags: List<String> = listOf("one", "two"),
        prompt: String? = "What happened?",
    ) = IntegrityHasher.compute(id, journalDate, createdAt, lockedAt, title, contentJson, tags, prompt)

    @Test
    fun `the same inputs always produce the same hash`() {
        assertEquals(base, with())
    }

    @Test
    fun `every covered field changes the hash`() {
        assertNotEquals("id must be covered", base, with(id = "entry-2"))
        assertNotEquals("journal date must be covered", base, with(journalDate = "2026-02-02"))
        assertNotEquals("creation time must be covered", base, with(createdAt = 1L))
        assertNotEquals("lock time must be covered", base, with(lockedAt = null))
        assertNotEquals("title must be covered", base, with(title = "Another day"))
        assertNotEquals("content must be covered", base, with(contentJson = """{"version":1,"blocks":[{"type":"divider","id":"z"}]}"""))
        assertNotEquals("tags must be covered", base, with(tags = listOf("one")))
        assertNotEquals("prompt must be covered", base, with(prompt = "A different prompt"))
    }

    @Test
    fun `fields cannot be confused with one another`() {
        // Without a separator, ("ab", "c") and ("a", "bc") would hash identically.
        // This is the case that makes naive concatenation unsafe for integrity.
        val left = with(id = "ab", journalDate = "c2026-02-01")
        val right = with(id = "a", journalDate = "bc2026-02-01")
        assertNotEquals(left, right)
    }

    @Test
    fun `tag order is significant because it is stored order`() {
        assertNotEquals(with(tags = listOf("a", "b")), with(tags = listOf("b", "a")))
    }

    @Test
    fun `verification accepts an untouched entry`() {
        val entry = JournalEntry(
            id = "entry-1",
            journalDate = LocalDate.of(2026, 2, 1),
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L,
            lockedAt = 1_700_086_400_000L,
            title = "A day",
            document = RichDocument(),
            contentFormat = "application/vnd.today.rich+json",
            tags = listOf("one"),
            prompt = "What happened?",
            writingDurationMillis = 0L,
            wordCount = 0,
            integrityHash = "",
        )
        val contentJson = RichDocumentCodec.encode(entry.document)
        val stored = IntegrityHasher.compute(entry, contentJson)

        assertTrue(IntegrityHasher.verify(entry.copy(integrityHash = stored), contentJson))
    }

    @Test
    fun `verification rejects an entry whose text was altered behind the app's back`() {
        val original = JournalEntry(
            id = "entry-1",
            journalDate = LocalDate.of(2026, 2, 1),
            createdAt = 1L,
            updatedAt = 1L,
            lockedAt = 2L,
            title = "Original",
            document = RichDocument(
                blocks = listOf(ParagraphBlock(id = "b", text = StyledText.plain("what I actually wrote"))),
            ),
            contentFormat = "application/vnd.today.rich+json",
            tags = emptyList(),
            prompt = null,
            writingDurationMillis = 0L,
            wordCount = 5,
            integrityHash = "",
        )
        val stored = IntegrityHasher.compute(original, RichDocumentCodec.encode(original.document))
        val sealed = original.copy(integrityHash = stored)

        val tampered = sealed.copy(
            title = "Rewritten",
            document = RichDocument(
                blocks = listOf(ParagraphBlock(id = "b", text = StyledText.plain("something else"))),
            ),
        )

        assertTrue(
            "An altered sealed entry must fail its integrity check.",
            !IntegrityHasher.verify(tampered, RichDocumentCodec.encode(tampered.document)),
        )
    }
}
