package `as`.today.missyou.data.export

import `as`.today.missyou.core.text.TextMetrics
import `as`.today.missyou.domain.model.Attachment
import `as`.today.missyou.domain.model.BackupAttachment
import `as`.today.missyou.domain.model.BackupEntry
import `as`.today.missyou.domain.model.BackupFile
import `as`.today.missyou.domain.model.CalloutBlock
import `as`.today.missyou.domain.model.CodeBlock
import `as`.today.missyou.domain.model.DividerBlock
import `as`.today.missyou.domain.model.ExportFormat
import `as`.today.missyou.domain.model.FileBlock
import `as`.today.missyou.domain.model.HeadingBlock
import `as`.today.missyou.domain.model.ImageBlock
import `as`.today.missyou.domain.model.JournalEntry
import `as`.today.missyou.domain.model.ListStyle
import `as`.today.missyou.domain.model.ParagraphBlock
import `as`.today.missyou.domain.model.QuoteBlock
import `as`.today.missyou.domain.model.RichBlock
import `as`.today.missyou.domain.model.RichDocument
import `as`.today.missyou.domain.model.StyledText
import `as`.today.missyou.domain.model.TableBlock
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64

/**
 * Turning entries into portable files.
 *
 * All exporters are pure functions over the domain model. None of them touch the
 * database, the network, or the Keystore, which is what makes the
 * "export must not depend on a subscription, account, server or internet" guarantee
 * (§53) structurally true rather than merely intended.
 */
object Exporters {

    private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun fileName(format: ExportFormat, prefix: String = "journal"): String {
        val stamp = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"))
        return "${prefix}_$stamp.${format.extension}"
    }

    // ------------------------------------------------------------------ markdown

    fun toMarkdown(entries: List<JournalEntry>, attachmentResolver: (String) -> String? = { it }): String =
        buildString {
            entries.forEachIndexed { index, entry ->
                if (index > 0) append("\n\n---\n\n")
                appendMarkdownEntry(this, entry, attachmentResolver)
            }
        }

    private fun appendMarkdownEntry(
        out: StringBuilder,
        entry: JournalEntry,
        attachmentResolver: (String) -> String?,
    ) {
        out.append("# ").append(formatDate(entry)).append('\n')
        if (entry.title.isNotBlank()) out.append("\n**").append(entry.title).append("**\n")
        if (entry.prompt != null) out.append("\n> ").append(entry.prompt).append('\n')
        if (entry.tags.isNotEmpty()) out.append("\n").append(entry.tags.joinToString(" ") { it }).append('\n')

        out.append("\n<!-- locked: ").append(entry.lockedAt != null)
            .append(" | words: ").append(entry.wordCount)
            .append(" | hash: ").append(entry.integrityHash).append(" -->\n\n")

        appendMarkdownBlocks(out, entry.document.blocks, attachmentResolver)

        out.append("\n\n")
        out.append("_Locked on ")
            .append(entry.lockedAt?.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() })
            .append("._")
    }

    private fun appendMarkdownBlocks(
        out: StringBuilder,
        blocks: List<RichBlock>,
        attachmentResolver: (String) -> String?,
    ) {
        var numberedCounter = 0
        var previousWasList = false
        for (block in blocks) {
            when (block) {
                is HeadingBlock -> {
                    out.append('\n').append("#".repeat(block.level + 1)).append(' ')
                    out.append(inlineMarkdown(block.text)).append("\n\n")
                    previousWasList = false
                }

                is ParagraphBlock -> {
                    if (block.listStyle == ListStyle.NONE) {
                        if (!block.text.isBlank) out.append(inlineMarkdown(block.text)).append("\n\n")
                        previousWasList = false
                    } else {
                        if (!previousWasList || block.listStyle != ListStyle.NUMBERED) numberedCounter = 0
                        numberedCounter++
                        val indent = "  ".repeat(block.indentLevel.coerceAtLeast(0))
                        when (block.listStyle) {
                            ListStyle.BULLET -> out.append(indent).append("- ")
                            ListStyle.NUMBERED -> out.append(indent).append(numberedCounter).append(". ")
                            ListStyle.CHECK -> out.append(indent)
                                .append(if (block.checked) "- [x] " else "- [ ] ")
                            ListStyle.NONE -> Unit
                        }
                        out.append(inlineMarkdown(block.text)).append('\n')
                        previousWasList = true
                    }
                }

                is QuoteBlock -> {
                    out.append('\n')
                    block.text.text.lines().forEach { line -> out.append("> ").append(line).append('\n') }
                    out.append('\n')
                    previousWasList = false
                }

                is CalloutBlock -> {
                    out.append("\n> **").append(block.tone.name.lowercase().replaceFirstChar(Char::uppercase))
                        .append("** — ").append(inlineMarkdown(block.text)).append("\n\n")
                    previousWasList = false
                }

                is CodeBlock -> {
                    out.append("\n```").append(block.language.orEmpty()).append('\n')
                    out.append(block.code).append("\n```\n\n")
                    previousWasList = false
                }

                is DividerBlock -> {
                    out.append("\n---\n\n")
                    previousWasList = false
                }

                is TableBlock -> {
                    block.rows.forEachIndexed { rowIndex, row ->
                        out.append('|').append(row.joinToString(" | ") { inlineMarkdown(it) }).append("|\n")
                        if (rowIndex == 0 && block.hasHeaderRow) {
                            out.append('|').append(row.joinToString(" | ") { "---" }).append("|\n")
                        }
                    }
                    out.append('\n')
                    previousWasList = false
                }

                is ImageBlock -> {
                    val name = block.attachmentId
                    out.append("\n![").append(block.caption.text).append("](attachment:").append(name).append(")\n\n")
                    previousWasList = false
                }

                is FileBlock -> {
                    val target = attachmentResolver(block.attachmentId)
                    out.append("\n[Attachment: ").append(block.attachmentId).append(']')
                    if (target != null) out.append('(').append(target).append(')')
                    out.append("\n\n")
                    previousWasList = false
                }
            }
        }
    }

    /** Applies inline marks using the most portable Markdown that still round-trips. */
    fun inlineMarkdown(text: StyledText): String {
        if (text.isPlain()) return text.text
        val out = StringBuilder(text.text.length + 32)
        var index = 0
        while (index < text.text.length) {
            val active = text.marks.filter { it.start <= index && index < it.end }
            val link = text.linkAt(index)
            val highlight = text.highlights.firstOrNull { it.start <= index && index < it.end }
            if (link != null) {
                out.append('[')
            } else if (highlight != null) {
                out.append("==")
            }
            active.forEach { mark ->
                out.append(
                    when (mark.mark) {
                        `as`.today.missyou.domain.model.TextMark.BOLD -> "**"
                        `as`.today.missyou.domain.model.TextMark.ITALIC -> "_"
                        `as`.today.missyou.domain.model.TextMark.UNDERLINE -> "<u>"
                        `as`.today.missyou.domain.model.TextMark.STRIKETHROUGH -> "~~"
                        `as`.today.missyou.domain.model.TextMark.CODE -> "`"
                    },
                )
            }
            out.append(text.text[index])
            active.asReversed().forEach { mark ->
                out.append(
                    when (mark.mark) {
                        `as`.today.missyou.domain.model.TextMark.BOLD -> "**"
                        `as`.today.missyou.domain.model.TextMark.ITALIC -> "_"
                        `as`.today.missyou.domain.model.TextMark.UNDERLINE -> "</u>"
                        `as`.today.missyou.domain.model.TextMark.STRIKETHROUGH -> "~~"
                        `as`.today.missyou.domain.model.TextMark.CODE -> "`"
                    },
                )
            }
            if (link != null) {
                out.append("](").append(link.url).append(')')
            } else if (highlight != null) {
                out.append("==")
            }
            index++
        }
        return out.toString()
    }

    // ------------------------------------------------------------------ plain text

    fun toPlainText(entries: List<JournalEntry>): String = buildString {
        entries.forEachIndexed { index, entry ->
            if (index > 0) append("\n\n========================================\n\n")
            append(formatDate(entry)).append('\n')
            if (entry.title.isNotBlank()) append(entry.title).append("\n\n")
            if (entry.prompt != null) append("Prompt: ").append(entry.prompt).append("\n\n")
            if (entry.tags.isNotEmpty()) append(entry.tags.joinToString(" ")).append("\n\n")
            append(plainTextOf(entry.document))
            append("\n\n[")
                .append(entry.wordCount).append(" words")
                .append(", ").append(TextMetrics.readingTimeMinutes(entry.wordCount)).append(" min read")
                .append(", ").append(if (entry.isLocked) "locked" else "editable")
                .append(']')
        }
    }

    fun plainTextOf(document: RichDocument): String = buildString {
        var numbered = 0
        var inList = false
        for (block in document.blocks) {
            when (block) {
                is ParagraphBlock -> when (block.listStyle) {
                    ListStyle.NONE -> {
                        if (!block.text.isBlank) append(block.text.text).append("\n\n")
                        inList = false
                    }
                    ListStyle.BULLET -> {
                        append("  ".repeat(block.indentLevel)).append("* ")
                            .append(block.text.text).append('\n')
                        inList = true
                    }
                    ListStyle.NUMBERED -> {
                        if (!inList) numbered = 0
                        numbered++
                        append("  ".repeat(block.indentLevel)).append(numbered).append(". ")
                            .append(block.text.text).append('\n')
                        inList = true
                    }
                    ListStyle.CHECK -> {
                        append(if (block.checked) "[x] " else "[ ] ").append(block.text.text).append('\n')
                        inList = true
                    }
                }
                is HeadingBlock -> append(block.text.text.uppercase()).append("\n\n")
                is QuoteBlock -> block.text.text.lines().forEach { append("    ").append(it).append('\n') }.also { append('\n') }
                is CalloutBlock -> append("[!").append(block.tone.name).append("] ")
                    .append(block.text.text).append("\n\n")
                is CodeBlock -> append(block.code).append("\n\n")
                is DividerBlock -> append("------------------------------------\n\n")
                is TableBlock -> block.rows.forEach { row ->
                    append(row.joinToString("  |  ") { it.text }).append('\n')
                }.also { append('\n') }
                is ImageBlock -> append("[image]").append('\n')
                is FileBlock -> append("[attachment]").append('\n')
            }
        }
    }

    // ------------------------------------------------------------------ html

    fun toHtml(entries: List<JournalEntry>, attachmentResolver: (String) -> String? = { it }): String =
        buildString {
            append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
            append("<meta charset=\"utf-8\">\n")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
            append("<title>Journal export</title>\n")
            append("<style>\n").append(CSS).append("\n</style>\n</head>\n<body>\n")
            append("<h1>Journal export</h1>\n")
            append("<p class=\"meta\">").append(entries.size).append(" entries, exported ")
                .append(java.time.LocalDate.now().format(ISO_DATE)).append("</p>\n")
            entries.forEach { entry ->
                append("<article>\n")
                append("<h2>").append(escape(formatDate(entry))).append("</h2>\n")
                if (entry.title.isNotBlank()) append("<h3>").append(escape(entry.title)).append("</h3>\n")
                if (entry.prompt != null) append("<p class=\"prompt\">").append(escape(entry.prompt)).append("</p>\n")
                if (entry.tags.isNotEmpty()) {
                    append("<p class=\"tags\">")
                    entry.tags.forEach { append("<span>#").append(escape(it.removePrefix("#"))).append("</span>") }
                    append("</p>\n")
                }
                appendHtmlBlocks(this, entry.document.blocks, attachmentResolver)
                append("<p class=\"locked\">")
                    .append(if (entry.isLocked) "Locked — preserved as originally written" else "Editable")
                    .append(" · ").append(entry.wordCount).append(" words · integrity ")
                    .append(escape(entry.integrityHash.take(16))).append("</p>\n")
                append("</article>\n")
            }
            append("</body>\n</html>\n")
        }

    private fun appendHtmlBlocks(
        out: StringBuilder,
        blocks: List<RichBlock>,
        attachmentResolver: (String) -> String?,
    ) {
        // Consecutive list paragraphs are grouped into a single <ul>/<ol>, which is
        // what makes nesting and numbering render correctly rather than emitting one
        // list element per item.
        var openListTag: String? = null
        var listDepth = 0

        fun closeList() {
            while (listDepth > 0) {
                out.append("</").append(openListTag).append(">\n")
                listDepth--
            }
            openListTag = null
        }

        for (block in blocks) {
            when (block) {
                is HeadingBlock -> {
                    closeList()
                    out.append("<h").append(block.level + 1).append('>')
                        .append(htmlInline(block.text)).append("</h").append(block.level + 1).append(">\n")
                }
                is ParagraphBlock -> {
                    if (block.listStyle == ListStyle.NONE) {
                        closeList()
                        out.append("<p>").append(htmlInline(block.text)).append("</p>\n")
                    } else {
                        val tag = if (block.listStyle == ListStyle.NUMBERED) "ol" else "ul"
                        if (openListTag != tag) {
                            closeList()
                            openListTag = tag
                            out.append('<').append(tag).append(">\n")
                            listDepth = 1
                        }
                        while (listDepth < block.indentLevel + 1) {
                            out.append('<').append(tag).append(">\n")
                            listDepth++
                        }
                        while (listDepth > block.indentLevel + 1) {
                            out.append("</").append(tag).append(">\n")
                            listDepth--
                        }
                        val classes = if (block.listStyle == ListStyle.CHECK) " class=\"check\"" else ""
                        out.append("<li").append(classes).append('>')
                        if (block.listStyle == ListStyle.CHECK) {
                            out.append("<input type=\"checkbox\" disabled")
                                .append(if (block.checked) " checked" else "").append("> ")
                        }
                        out.append(htmlInline(block.text)).append("</li>\n")
                    }
                }
                is QuoteBlock -> {
                    closeList()
                    out.append("<blockquote>").append(htmlInline(block.text)).append("</blockquote>\n")
                }
                is CalloutBlock -> {
                    closeList()
                    out.append("<div class=\"callout ").append(block.tone.name.lowercase())
                        .append("\">").append(htmlInline(block.text)).append("</div>\n")
                }
                is CodeBlock -> {
                    closeList()
                    out.append("<pre><code>").append(escape(block.code)).append("</code></pre>\n")
                }
                is DividerBlock -> {
                    closeList()
                    out.append("<hr>\n")
                }
                is TableBlock -> {
                    closeList()
                    out.append("<table>\n")
                    block.rows.forEachIndexed { index, row ->
                        val cell = if (index == 0 && block.hasHeaderRow) "th" else "td"
                        out.append("<tr>")
                        row.forEach { out.append('<').append(cell).append('>').append(htmlInline(it)).append("</").append(cell).append('>') }
                        out.append("</tr>\n")
                    }
                    out.append("</table>\n")
                }
                is ImageBlock -> {
                    closeList()
                    out.append("<figure><img alt=\"").append(escape(block.caption.text))
                        .append("\" src=\"").append(escape("attachment:" + block.attachmentId)).append("\">")
                        .append("<figcaption>").append(escape(block.caption.text)).append("</figcaption></figure>\n")
                }
                is FileBlock -> {
                    closeList()
                    out.append("<p class=\"file\">[attachment: ")
                        .append(escape(block.attachmentId)).append("]</p>\n")
                }
            }
        }
        closeList()
    }

    fun htmlInline(text: StyledText): String {
        val out = StringBuilder()
        var index = 0
        while (index < text.text.length) {
            val marks = text.marks.filter { it.start <= index && index < it.end }
            val highlight = text.highlights.firstOrNull { it.start <= index && index < it.end }
            val link = text.linkAt(index)
            marks.forEach { out.append("<").append(it.mark.name.lowercase()).append(">") }
            highlight?.let { out.append("<mark class=\"").append(it.tone.name.lowercase()).append("\">") }
            link?.let { out.append("<a href=\"").append(escape(it.url)).append("\">") }
            out.append(escape(text.text[index].toString()))
            link?.let { out.append("</a>") }
            highlight?.let { out.append("</mark>") }
            marks.asReversed().forEach { out.append("</").append(it.mark.name.lowercase()).append(">") }
            index++
        }
        return out.toString()
    }

    fun escape(text: String): String = buildString(text.length + 16) {
        text.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
        }
    }

    // ------------------------------------------------------------------ backup

    suspend fun toBackup(
        entries: List<JournalEntry>,
        appVersion: String,
        contentEncoder: (RichDocument) -> String,
        attachmentBytes: suspend (Attachment) -> ByteArray?,
    ): BackupFile = BackupFile(
        appVersion = appVersion,
        exportedAt = System.currentTimeMillis(),
        entries = entries.map { entry ->
            val attachmentPayloads = entry.attachments.map { attachment ->
                val bytes = attachmentBytes(attachment)
                BackupAttachment(
                    id = attachment.id,
                    displayName = attachment.displayName,
                    mimeType = attachment.mimeType,
                    byteSize = attachment.byteSize,
                    data = bytes?.let { Base64.getEncoder().encodeToString(it) }.orEmpty(),
                    contentHash = attachment.contentHash,
                    createdAt = attachment.createdAt,
                    width = attachment.width,
                    height = attachment.height,
                )
            }
            BackupEntry(
                id = entry.id,
                journalDate = entry.journalDate.toString(),
                createdAt = entry.createdAt,
                updatedAt = entry.updatedAt,
                lockedAt = entry.lockedAt,
                title = entry.title,
                contentFormat = entry.contentFormat,
                document = contentEncoder(entry.document),
                tags = entry.tags,
                prompt = entry.prompt,
                writingDurationMillis = entry.writingDurationMillis,
                wordCount = entry.wordCount,
                integrityHash = entry.integrityHash,
                attachments = attachmentPayloads,
            )
        },
    )

    /** A minimal single-entry backup, used when archiving a conflicting import. */
    fun toJsonBackup(entries: List<BackupEntry>, appVersion: String = ""): ByteArray =
        toJson(BackupFile(appVersion = appVersion, entries = entries))

    fun toJson(backup: BackupFile): ByteArray =
        `as`.today.missyou.data.RichDocumentCodec.json
            .encodeToString(BackupFile.serializer(), backup)
            .toByteArray(Charsets.UTF_8)

    fun formatDate(entry: JournalEntry): String = entry.journalDate.format(ISO_DATE)

    private val CSS = """
        :root { color-scheme: light dark; }
        body { font-family: -apple-system, system-ui, 'Segoe UI', Roboto, sans-serif;
               line-height: 1.6; max-width: 44rem; margin: 0 auto; padding: 2rem 1.25rem; }
        h1 { font-size: 1.75rem; } h2 { font-size: 1.3rem; margin-top: 2.5rem; }
        h3 { font-size: 1.05rem; font-weight: 600; margin: 0.5rem 0; }
        article { border-bottom: 1px solid rgba(128,128,128,0.25); padding-bottom: 1.5rem; margin-bottom: 1.5rem; }
        .meta, .locked { font-size: 0.8rem; opacity: 0.7; }
        .prompt { font-style: italic; opacity: 0.85; }
        .tags span { display: inline-block; margin-right: 0.5rem; }
        blockquote { border-left: 3px solid rgba(128,128,128,0.4); margin: 0.5rem 0; padding: 0.25rem 1rem; }
        .callout { border-radius: 8px; padding: 0.6rem 0.9rem; margin: 0.5rem 0; background: rgba(128,128,128,0.12); }
        pre { background: rgba(128,128,128,0.14); padding: 0.75rem; border-radius: 8px; overflow-x: auto; }
        code { font-family: ui-monospace, 'SFMono-Regular', Menlo, monospace; font-size: 0.9em; }
        table { border-collapse: collapse; width: 100%; }
        th, td { border: 1px solid rgba(128,128,128,0.3); padding: 0.4rem 0.6rem; text-align: left; }
        figure img { max-width: 100%; height: auto; border-radius: 8px; }
        mark { padding: 0 0.1em; }
        mark.yellow { background: #fff59d; } mark.green { background: #a5d6a7; }
        mark.blue { background: #90caf9; } mark.pink { background: #f8bbd0; }
        mark.orange { background: #ffcc80; }
    """.trimIndent()
}
