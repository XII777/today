package `as`.today.missyou.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Every block the editor can produce.
 *
 * The model is block-based rather than a single flat rich-text blob because that is
 * what makes the requested feature set (nested lists, checklists, callouts, tables,
 * dividers, code blocks) expressible without a full document layout engine, and
 * because block identity keeps Compose recomposition cheap on low-end devices.
 */
@Serializable
sealed interface RichBlock {
    val id: String
}

/** A normal paragraph, optionally a list item. */
@Serializable
@SerialName("paragraph")
data class ParagraphBlock(
    override val id: String,
    val text: StyledText = StyledText.Empty,
    val alignment: TextAlign = TextAlign.START,
    /** 0 = flush, 1..n = nesting level for bullet/numbered lists. */
    val indentLevel: Int = 0,
    val listStyle: ListStyle = ListStyle.NONE,
    val checked: Boolean = false,
) : RichBlock {
    val isChecklistItem: Boolean get() = listStyle == ListStyle.CHECK
    val isListItem: Boolean get() = listStyle != ListStyle.NONE
}

/** Heading levels 1..3 (§7 "Headings"). */
@Serializable
@SerialName("heading")
data class HeadingBlock(
    override val id: String,
    val level: Int,
    val text: StyledText = StyledText.Empty,
) : RichBlock {
    init {
        require(level in 1..3) { "Heading level must be 1..3, was $level" }
    }
}

/** Block quote. */
@Serializable
@SerialName("quote")
data class QuoteBlock(
    override val id: String,
    val text: StyledText = StyledText.Empty,
) : RichBlock

/** Callout: a tinted, emphasised block. */
@Serializable
@SerialName("callout")
data class CalloutBlock(
    override val id: String,
    val tone: CalloutTone = CalloutTone.INFO,
    val text: StyledText = StyledText.Empty,
) : RichBlock

/** Monospace block, used for pasted code. */
@Serializable
@SerialName("code")
data class CodeBlock(
    override val id: String,
    val code: String = "",
    val language: String? = null,
) : RichBlock

/** Horizontal rule. */
@Serializable
@SerialName("divider")
data class DividerBlock(
    override val id: String,
) : RichBlock

/** A simple table. Row 0 is the header when [hasHeaderRow] is true. */
@Serializable
@SerialName("table")
data class TableBlock(
    override val id: String,
    val rows: List<List<StyledText>> = listOf(listOf(StyledText.Empty, StyledText.Empty)),
    val hasHeaderRow: Boolean = true,
) : RichBlock {

    val columnCount: Int get() = rows.maxOfOrNull { it.size } ?: 0
    val rowCount: Int get() = rows.size

    fun resized(newRowCount: Int, newColumnCount: Int): TableBlock {
        val columns = newColumnCount.coerceAtLeast(1)
        val currentColumns = columnCount.coerceAtLeast(1)
        val rebuilt = (0 until newRowCount.coerceAtLeast(1)).map { rowIndex ->
            val existing = rows.getOrNull(rowIndex).orEmpty()
            (0 until columns).map { columnIndex ->
                existing.getOrNull(columnIndex)
                    ?: existing.getOrNull(currentColumns - 1)
                    ?: StyledText.Empty
            }
        }
        return copy(rows = rebuilt)
    }
}

/** A locally stored image. The bytes live in the app's encrypted internal storage. */
@Serializable
@SerialName("image")
data class ImageBlock(
    override val id: String,
    val attachmentId: String,
    val caption: StyledText = StyledText.Empty,
) : RichBlock

/** A non-image local file the user attached. */
@Serializable
@SerialName("file")
data class FileBlock(
    override val id: String,
    val attachmentId: String,
) : RichBlock

/**
 * The persisted editor document.
 *
 * [version] is written into every stored record so future format changes can be
 * migrated without guessing.
 */
@Serializable
data class RichDocument(
    val version: Int = CURRENT_VERSION,
    val blocks: List<RichBlock> = emptyList(),
) {

    val isEmpty: Boolean
        get() = blocks.isEmpty() || blocks.all { it.isVisuallyEmpty() }

    /** Word count across every textual block, including checklist and table cells. */
    val wordCount: Int get() = blocks.sumOf { it.plainTextWordCount() }

    val plainText: String
        get() = blocks.joinToString(separator = "\n") { it.plainText() }

    fun withBlock(id: String, transform: (RichBlock) -> RichBlock): RichDocument {
        val index = blocks.indexOfFirst { it.id == id }
        if (index < 0) return this
        return copy(blocks = blocks.toMutableList().also { it[index] = transform(blocks[index]) })
    }

    fun withoutBlock(id: String): RichDocument = copy(blocks = blocks.filterNot { it.id == id })

    companion object {
        const val CURRENT_VERSION = 1

        fun empty(blockId: String): RichDocument =
            RichDocument(blocks = listOf(ParagraphBlock(id = blockId)))

        fun ofPlainText(plainText: String, idFactory: () -> String): RichDocument {
            val lines = plainText.split("\n")
            return RichDocument(
                blocks = lines.map { line -> ParagraphBlock(id = idFactory(), text = StyledText.plain(line)) },
            )
        }
    }
}


fun RichBlock.isVisuallyEmpty(): Boolean = when (this) {
    is ParagraphBlock -> text.isBlank
    is HeadingBlock -> text.isBlank
    is QuoteBlock -> text.isBlank
    is CalloutBlock -> text.isBlank
    is CodeBlock -> code.isBlank()
    is TableBlock -> rows.all { row -> row.all { it.isBlank } }
    is ImageBlock -> attachmentId.isBlank()
    is FileBlock -> attachmentId.isBlank()
    is DividerBlock -> false
}

fun RichBlock.plainText(): String = when (this) {
    is ParagraphBlock -> text.text
    is HeadingBlock -> text.text
    is QuoteBlock -> text.text
    is CalloutBlock -> text.text
    is CodeBlock -> code
    is TableBlock -> rows.joinToString(" ") { row -> row.joinToString(" ") { it.text } }
    is ImageBlock -> caption.text
    is FileBlock -> ""
    is DividerBlock -> ""
}

fun RichBlock.plainTextWordCount(): Int = when (this) {
    is TableBlock -> rows.sumOf { row -> row.sumOf { `as`.today.missyou.core.text.TextMetrics.wordCount(it.text) } }
    is CodeBlock -> `as`.today.missyou.core.text.TextMetrics.wordCount(code)
    else -> `as`.today.missyou.core.text.TextMetrics.wordCount(plainText())
}

/** Blocks the user can put the caret into. */
val RichBlock.isEditable: Boolean
    get() = this !is DividerBlock
