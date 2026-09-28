package `as`.today.missyou.ui.editor

import androidx.lifecycle.viewModelScope
import `as`.today.missyou.AppContainer
import `as`.today.missyou.core.text.TextMetrics
import `as`.today.missyou.domain.model.CodeBlock
import `as`.today.missyou.domain.model.CalloutBlock
import `as`.today.missyou.domain.model.DailyPrompts
import `as`.today.missyou.domain.model.DividerBlock
import `as`.today.missyou.domain.model.HeadingBlock
import `as`.today.missyou.domain.model.JournalEntry
import `as`.today.missyou.domain.model.ListStyle
import `as`.today.missyou.domain.model.ParagraphBlock
import `as`.today.missyou.domain.model.QuoteBlock
import `as`.today.missyou.domain.model.RichBlock
import `as`.today.missyou.domain.model.RichDocument
import `as`.today.missyou.domain.model.StyledText
import `as`.today.missyou.domain.model.TextMark
import `as`.today.missyou.domain.model.ToolbarItem
import `as`.today.missyou.ui.common.AppViewModel
import `as`.today.missyou.ui.common.readableMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** What the editor is doing right now. */
sealed interface EditorStatus {
    data object Loading : EditorStatus
    data object Idle : EditorStatus

    /** Saved a moment ago; the UI shows a brief confirmation. */
    data object Saved : EditorStatus

    /** An autosave or manual save failed. */
    data class Failed(val message: String) : EditorStatus
}

/** A transient message for the editor's own snackbar-style slot. */
data class EditorNotice(val message: String, val isError: Boolean = false)

data class EditorUiState(
    val status: EditorStatus = EditorStatus.Loading,
    val loading: Boolean = true,
    val date: java.time.LocalDate = java.time.LocalDate.now(),
    val title: String = "",
    val document: RichDocument = RichDocument(),
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val prompt: String? = null,
    val focusedBlockId: String? = null,
    val selection: TextRange = TextRange(),
    val isLocked: Boolean = false,
    val isToday: Boolean = true,
    val wordCount: Int = 0,
    val characterCount: Int = 0,
    val readingTime: String = "",
    val writingMillis: Long = 0,
    val toolbar: List<ToolbarItem> = emptyList(),
    val notices: List<EditorNotice> = emptyList(),
    val pendingAttachmentPicker: Boolean = false,
) {
    val canEdit: Boolean get() = !isLocked
}

/** A selection inside the focused block's text. */
data class TextRange(val start: Int = 0, val end: Int = 0) {
    val collapsed: Boolean get() = start == end
    val hasSelection: Boolean get() = !collapsed
    fun normalised(): TextRange = if (start <= end) this else TextRange(end, start)
}

/**
 * Intermediate state holders, so the UI state can be assembled with typed
 * `combine` overloads rather than by unboxing `Array`s.
 */
private data class EditorContent(
    val title: String,
    val document: RichDocument,
    val tags: List<String>,
)

private data class EditorMeta(
    val tagInput: String,
    val prompt: String?,
    val status: EditorStatus,
)

private data class EditorFocus(
    val blockId: String?,
    val selection: TextRange,
    val notices: List<EditorNotice>,
)

/**
 * The editor's state holder (§9).
 *
 * Three decisions shape this class:
 *
 *  1. **Edits are local and optimistic.** Every keystroke mutates an in-memory
 *     [RichDocument] immediately; the database is only consulted on save. A journal
 *     that lags behind the keyboard is unusable.
 *  2. **Autosave is debounced, not periodic.** A coroutine restarts on every edit
 *     and only actually writes once the user pauses, so holding a key down never
 *     produces a write storm.
 *  3. **The repository is the only writer.** Nothing here can mark an entry locked
 *     or change its date; if the day rolls over mid-edit the save is *rejected* by
 *     the repository and the UI tells the user, rather than the editor deciding on
 *     its own that the day is over.
 */
class EditorViewModel(
    container: AppContainer,
    private val date: java.time.LocalDate,
) : AppViewModel(container) {

    private val title = MutableStateFlow("")
    private val document = MutableStateFlow(RichDocument())
    private val tags: MutableStateFlow<List<String>> = MutableStateFlow(emptyList<String>())
    private val tagInput = MutableStateFlow("")
    private val prompt = MutableStateFlow<String?>(null)
    private val status = MutableStateFlow<EditorStatus>(EditorStatus.Loading)
    private val focusedBlockId = MutableStateFlow<String?>(null)
    private val selection = MutableStateFlow(TextRange())
    private val notices: MutableStateFlow<List<EditorNotice>> =
        MutableStateFlow(emptyList<EditorNotice>())

    private var entryId: String? = null
    private var locked = false
    private var loadedFromRepository = false

    /** Wall-clock time in the editor, accumulated only while actually typing. */
    private var writingStartedAt: Long? = null
    private var accumulatedWritingMillis: Long = 0

    private var autosaveJob: Job? = null

    val uiState: StateFlow<EditorUiState> = combine(
        combine(title, document, tags) { t, d, g -> EditorContent(t, d, g) },
        combine(tagInput, prompt, status) { i, p, s -> EditorMeta(i, p, s) },
        combine(focusedBlockId, selection, notices) { f, s, n -> EditorFocus(f, s, n) },
        container.settings.settings,
    ) { content, meta, focus, settings ->
        val words = content.document.wordCount
        EditorUiState(
            status = meta.status,
            loading = meta.status is EditorStatus.Loading,
            date = date,
            title = content.title,
            document = content.document,
            tags = content.tags,
            tagInput = meta.tagInput,
            prompt = meta.prompt,
            focusedBlockId = focus.blockId,
            selection = focus.selection,
            isLocked = locked,
            isToday = date == container.journal.currentJournalDate(),
            wordCount = words,
            characterCount = TextMetrics.characterCount(content.document.plainText),
            readingTime = if (settings.showReadingTime && words > 0) {
                TextMetrics.readingTimeLabel(words)
            } else {
                ""
            },
            writingMillis = accumulatedWritingMillis,
            toolbar = settings.toolbarItems,
            notices = focus.notices,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EditorUiState(date = date),
    )

    init {
        viewModelScope.launch {
            val settings = container.settings.settings.first()
            autosaveEnabled = settings.autoSaveEnabled
            autosaveDelay = settings.autoSaveSeconds.coerceIn(1, 60)
            load()
        }
    }

    private var autosaveEnabled: Boolean = true
    private var autosaveDelay: Int = 5

    private suspend fun load() {
        val existing = container.journal.entryForDate(date)
        if (existing != null) {
            entryId = existing.id
            locked = existing.isLocked
            title.value = existing.title
            document.value = existing.document
            tags.value = existing.tags
            prompt.value = existing.prompt
            accumulatedWritingMillis = existing.writingDurationMillis
        } else {
            entryId = null
            locked = false
            prompt.value = defaultPrompt()
            document.value = seedDocument()
        }
        loadedFromRepository = true
        status.value = EditorStatus.Idle
    }

    /**
     * A new day starts with the user's template, or one empty paragraph. Creating the
     * row happens on the first save rather than on open, so backing out of an
     * untouched editor leaves no empty entry behind.
     */
    private suspend fun seedDocument(): RichDocument {
        val template = container.settings.settings.first().defaultTemplate
        return if (template.isBlank()) {
            RichDocument.empty(newId())
        } else {
            RichDocument.ofPlainText(template) { newId() }
        }
    }

    private suspend fun defaultPrompt(): String? {
        val enabled = container.settings.settings.first().dailyPromptEnabled
        return if (enabled) DailyPrompts.forEpochDay(date.toEpochDay()) else null
    }

    // ----------------------------------------------------------------- focus

    fun onBlockFocused(blockId: String) {
        if (focusedBlockId.value == blockId) return
        focusedBlockId.value = blockId
        selection.value = TextRange()
        if (writingStartedAt == null && !locked) writingStartedAt = System.currentTimeMillis()
    }

    fun onSelectionChanged(range: TextRange) {
        selection.value = range.normalised()
    }

    // ----------------------------------------------------------------- text

    fun onTextChanged(blockId: String, newText: String) {
        if (locked) return
        val block = document.value.blocks.firstOrNull { it.id == blockId } ?: return
        val oldText = block.styledText().text
        if (oldText == newText) return
        val range = selection.value.normalised()
        val edit = TextRange(
            start = range.start.coerceIn(0, oldText.length),
            end = range.end.coerceIn(0, oldText.length),
        )
        replaceBlockText(blockId) { it.applyEdit(edit.start, edit.end, newText) }
        touch()
    }

    fun onTitleChanged(newTitle: String) {
        if (locked) return
        title.value = newTitle
        touch()
    }

    fun onPromptChanged(newPrompt: String) {
        if (locked) return
        prompt.value = newPrompt
        touch()
    }

    // ----------------------------------------------------------------- marks

    fun toggleMark(mark: TextMark) = withSelection { range ->
        replaceBlockText(focusedId()) { it.toggleMark(range.start, range.end, mark) }
    }

    fun setHighlight(tone: `as`.today.missyou.domain.model.HighlightTone?) =
        withSelection { range -> replaceBlockText(focusedId()) { it.setHighlight(range.start, range.end, tone) } }

    fun setLink(url: String?) = withSelection { range ->
        replaceBlockText(focusedId()) { it.setLink(range.start, range.end, url) }
    }

    fun clearFormatting() = withSelection { _ ->
        replaceBlockText(focusedId()) { it.clearFormatting() }
    }

    // ----------------------------------------------------------------- blocks

    fun toggleListStyle(style: ListStyle) {
        if (locked) return
        val id = focusedId() ?: return
        val block = document.value.blocks.firstOrNull { it.id == id } as? ParagraphBlock ?: return
        val nextStyle = if (block.listStyle == style) ListStyle.NONE else style
        updateParagraph(id) { paragraph ->
            paragraph.copy(
                listStyle = nextStyle,
                checked = if (nextStyle == ListStyle.CHECK) paragraph.checked else false,
            )
        }
        touch()
    }

    fun toggleChecked(blockId: String) {
        if (locked) return
        val block = document.value.blocks.firstOrNull { it.id == blockId } as? ParagraphBlock ?: return
        if (!block.isChecklistItem) return
        updateParagraph(blockId) { it.copy(checked = !block.checked) }
        touch()
    }

    fun indentFocused() {
        if (locked) return
        val id = focusedId() ?: return
        val block = document.value.blocks.firstOrNull { it.id == id } as? ParagraphBlock ?: return
        if (!block.isListItem) return
        val next = (block.indentLevel + 1).coerceAtMost(MAX_INDENT)
        updateParagraph(id) { it.copy(indentLevel = next) }
        touch()
    }

    fun outdentFocused() {
        if (locked) return
        val id = focusedId() ?: return
        val block = document.value.blocks.firstOrNull { it.id == id } as? ParagraphBlock ?: return
        val next = (block.indentLevel - 1).coerceAtLeast(0)
        updateParagraph(id) { it.copy(indentLevel = next) }
        touch()
    }

    /**
     * Cycles the focused block through paragraph → H1 → H2 → H3 → paragraph.
     *
     * Working on the block rather than on a selection is deliberate: a heading in a
     * journal is a property of the line, so a partial selection should not be able
     * to split one into two blocks with different levels.
     */
    fun cycleHeading() {
        if (locked) return
        val id = focusedId() ?: return
        val current = document.value.blocks.firstOrNull { it.id == id } ?: return
        val replacement: RichBlock = when (current) {
            is ParagraphBlock -> HeadingBlock(id = id, level = 1, text = current.text)
            is HeadingBlock -> if (current.level < 3) {
                current.copy(level = current.level + 1)
            } else {
                ParagraphBlock(id = id, text = current.text)
            }
            // Headings only cycle from the two shapes that can become one. A quote
            // or a code block is left alone rather than silently losing its style.
            else -> return
        }
        updateBlock(id) { replacement }
        touch()
    }

    fun toggleQuote() {
        if (locked) return
        val id = focusedId() ?: return
        val current = document.value.blocks.firstOrNull { it.id == id } ?: return
        val replacement: RichBlock = when (current) {
            is QuoteBlock -> ParagraphBlock(id = id, text = current.text)
            is ParagraphBlock -> QuoteBlock(id = id, text = current.text)
            else -> return
        }
        updateBlock(id) { replacement }
        touch()
    }

    fun insertDivider() {
        if (locked) return
        val id = focusedId() ?: return
        val dividerId = newId()
        val index = document.value.blocks.indexOfFirst { it.id == id }
        document.value = document.value.copy(
            blocks = document.value.blocks.toMutableList().also { it.add(index + 1, DividerBlock(dividerId)) },
        )
        focusedBlockId.value = dividerId
        touch()
    }

    fun insertCodeBlock() {
        if (locked) return
        val id = focusedId() ?: return
        val codeId = newId()
        val index = document.value.blocks.indexOfFirst { it.id == id }
        document.value = document.value.copy(
            blocks = document.value.blocks.toMutableList().also {
                it.add(index + 1, CodeBlock(id = codeId, code = ""))
            },
        )
        focusedBlockId.value = codeId
        touch()
    }

    fun onCodeChanged(blockId: String, code: String) {
        if (locked) return
        val block = document.value.blocks.firstOrNull { it.id == blockId } as? CodeBlock ?: return
        if (block.code == code) return
        updateBlock(blockId) { (it as CodeBlock).copy(code = code) }
        touch()
    }

    // ----------------------------------------------------------------- tags

    fun onTagInputChanged(value: String) {
        tagInput.value = value
    }

    /** Commits the tag currently being typed. Blank and duplicate tags are ignored. */
    fun commitTag() {
        val candidate = tagInput.value.trim().removePrefix("#")
        if (candidate.isEmpty()) {
            tagInput.value = ""
            return
        }
        if (tags.value.any { it.equals(candidate, ignoreCase = true) }) {
            tagInput.value = ""
            return
        }
        tags.value = tags.value + candidate
        tagInput.value = ""
        touch()
    }

    fun removeTag(tag: String) {
        if (locked) return
        tags.value = tags.value - tag
        touch()
    }

    // ----------------------------------------------------------------- save

    /** Manual save. Always allowed while the day is open. */
    fun save() {
        autosaveJob?.cancel()
        viewModelScope.launch { persist(manual = true) }
    }

    /**
     * Records an edit and schedules a debounced autosave.
     *
     * The job is restarted rather than awaited, so a burst of keystrokes collapses
     * into a single write once typing pauses.
     */
    private fun touch() {
        if (locked || !loadedFromRepository) return
        autosaveJob?.cancel()
        if (!autosaveEnabled) return
        autosaveJob = viewModelScope.launch {
            delay(autosaveDelay * 1_000L)
            persist(manual = false)
        }
    }

    private suspend fun persist(manual: Boolean) {
        if (locked) return
        val currentTitle = title.value
        val currentDocument = document.value
        val currentTags = tags.value
        val currentPrompt = prompt.value
        if (!currentDocument.hasContent() && currentTitle.isBlank() && currentTags.isEmpty()) {
            // Nothing worth storing yet. Do not create a row for an untouched editor.
            if (manual) status.value = EditorStatus.Idle
            return
        }
        settleWritingTime()

        // Resolve (or create) the row before writing. A creation failure is a real
        // failure, not something to retry silently on the next autosave tick.
        val id = entryId ?: createEntryIfNeeded(currentTitle, currentPrompt)?.also {
            entryId = it
        } ?: return

        container.journal.saveEntry(
            id = id,
            title = currentTitle,
            document = currentDocument,
            tags = currentTags,
            prompt = currentPrompt,
            writingDurationMillis = accumulatedWritingMillis,
        ).onSuccess {
            status.value = EditorStatus.Saved
            viewModelScope.launch {
                delay(SAVED_FEEDBACK_MILLIS)
                if (status.value == EditorStatus.Saved) status.value = EditorStatus.Idle
            }
        }.onFailure { error ->
            // A rejected save is almost always the rollover. Say so plainly rather
            // than showing a generic error, because the user's text is now sealed.
            val reason = error.message.orEmpty()
            val message = if (reason.contains("LOCKED", ignoreCase = true) ||
                reason.contains("locked", ignoreCase = true)
            ) {
                "This day has been sealed and can no longer be changed."
            } else {
                error.readableMessage()
            }
            status.value = EditorStatus.Failed(message)
            locked = true
        }
    }

    /** Converts the active typing stretch into accumulated time and stops the clock. */
    private fun settleWritingTime() {
        writingStartedAt?.let { start ->
            accumulatedWritingMillis += (System.currentTimeMillis() - start).coerceAtLeast(0)
        }
        writingStartedAt = null
    }

    fun onLeaveEditor() {
        autosaveJob?.cancel()
        settleWritingTime()
    }

    fun consumeNotice(index: Int) {
        notices.value = notices.value.filterIndexed { position, _ -> position != index }
    }

    fun report(message: String, isError: Boolean = false) {
        notices.value = notices.value + EditorNotice(message, isError)
    }

    // ----------------------------------------------------------------- helpers

    private fun withSelection(block: (TextRange) -> Unit) {
        if (locked) return
        val id = focusedId() ?: return
        val range = selection.value.normalised()
        block(range)
        touch()
    }

    private fun focusedId(): String? = focusedBlockId.value ?: document.value.blocks.firstOrNull()?.id

    /**
     * Applies a text transformation to whichever text-carrying block it names.
     *
     * Every branch carries the *existing* block's text into a copy of its own type,
     * so formatting a quote never downgrades it to a paragraph, and a block kind
     * that carries no editable text is left alone.
     */
    private fun replaceBlockText(blockId: String?, transform: (StyledText) -> StyledText) {
        if (blockId == null) return
        document.value = document.value.withBlock(blockId) { block ->
            when (block) {
                is ParagraphBlock -> block.copy(text = transform(block.text))
                is HeadingBlock -> block.copy(text = transform(block.text))
                is QuoteBlock -> block.copy(text = transform(block.text))
                is CalloutBlock -> block.copy(text = transform(block.text))
                else -> block
            }
        }
    }

    /**
     * Creates the row for this day if it does not exist yet.
     *
     * Today's entry is created through the repository's idempotent create; a past
     * day is created *already sealed*, because backfilling a day that has rolled
     * over must not produce something the user can still edit.
     */
    private suspend fun createEntryIfNeeded(title: String, prompt: String?): String? {
        val result = if (date == container.journal.currentJournalDate()) {
            container.journal.createTodaysEntry(title.ifBlank { null }, prompt)
        } else {
            container.journal.backfillEntry(date, title.ifBlank { null }, prompt)
        }
        return result.fold(
            onSuccess = { it.id },
            onFailure = { error ->
                status.value = EditorStatus.Failed(error.readableMessage())
                null
            },
        )
    }

    private fun updateBlock(blockId: String, transform: (RichBlock) -> RichBlock) {
        document.value = document.value.withBlock(blockId, transform)
    }

    /**
     * Typed variant of [updateBlock] for paragraph-shaped edits.
     *
     * The cast only succeeds against a block that is already a [ParagraphBlock],
     * which every call site has just checked, so a kind mismatch is a no-op rather
     * than silent data loss.
     */
    private fun updateParagraph(blockId: String, transform: (ParagraphBlock) -> ParagraphBlock) {
        document.value = document.value.withBlock(blockId) { block ->
            (block as? ParagraphBlock)?.let(transform) ?: block
        }
    }

    private fun newId(): String = UUID.randomUUID().toString()

    companion object {
        const val MAX_INDENT = 4
        const val SAVED_FEEDBACK_MILLIS = 1_600L
    }
}

/** The text a block carries, regardless of its concrete type. */
internal fun RichBlock.styledText(): StyledText = when (this) {
    is ParagraphBlock -> text
    is HeadingBlock -> text
    is QuoteBlock -> text
    is CalloutBlock -> text
    else -> StyledText.Empty
}

/** True when the document holds anything worth persisting. */
private fun RichDocument.hasContent(): Boolean = !isEmpty

/** A load of an existing entry, used when the editor opens read-only. */
fun JournalEntry.isEditable(): Boolean = !isLocked
