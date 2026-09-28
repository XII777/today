/**
 * `flatMapLatest` is still marked experimental in coroutines 1.9. Only the two view
 * models that switch an observed window need it, so they opt in at file scope
 * rather than the whole module.
 */
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package `as`.today.missyou.ui.journal

import androidx.lifecycle.viewModelScope
import `as`.today.missyou.AppContainer
import `as`.today.missyou.core.text.TextMetrics
import `as`.today.missyou.domain.model.JournalEntrySummary
import `as`.today.missyou.domain.repository.JournalRepository
import `as`.today.missyou.ui.common.AppViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** One month of the archive, already grouped for rendering. */
data class JournalMonth(
    val month: YearMonth,
    val entries: List<JournalEntrySummary>,
) {
    val title: String get() = month.format(MONTH_FORMAT)
    val wordTotal: Int get() = entries.sumOf { it.wordCount }
}

private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

data class JournalUiState(
    val loading: Boolean = true,
    val query: String = "",
    val months: List<JournalMonth> = emptyList(),
    val searchResults: List<SearchResultRow> = emptyList(),
    val searching: Boolean = false,
    val totalEntries: Int = 0,
) {
    val isEmpty: Boolean get() = !loading && months.isEmpty() && searchResults.isEmpty()
}

/** A search hit shaped for the archive list. */
data class SearchResultRow(
    val entryId: String,
    val date: LocalDate,
    val title: String,
    val snippet: String,
    val locked: Boolean,
)

/**
 * The archive screen (§5).
 *
 * Reading is paged: the repository only ever loads [PAGE_SIZE] summaries at a time
 * and the list grows as the user scrolls, so a journal with thousands of entries
 * never inflates more than the visible window. Search runs against the sealed token
 * index rather than by scanning content.
 */
class JournalViewModel(container: AppContainer) : AppViewModel(container) {

    private val query = MutableStateFlow("")
    private val pageSize = MutableStateFlow(PAGE_SIZE)
    private val searchResults = MutableStateFlow<List<SearchResultRow>>(emptyList())
    private val searching = MutableStateFlow(false)

    private val rows = pageSize.flatMapLatest { size ->
        container.journal.observeArchive(limit = size, offset = 0)
    }

    val uiState: StateFlow<JournalUiState> = combine(
        rows,
        query,
        searchResults,
        searching,
        container.journal.observeEntryCount(),
    ) { summaries, text, results, isSearching, total ->
        JournalUiState(
            loading = false,
            query = text,
            months = groupByMonth(summaries),
            searchResults = results,
            searching = isSearching,
            totalEntries = total,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = JournalUiState(),
    )

    init {
        viewModelScope.launch {
            query.collect { text ->
                if (text.isBlank()) {
                    searchResults.value = emptyList()
                    searching.value = false
                } else {
                    searching.value = true
                    val results = container.journal.search(text)
                    searchResults.value = results.map { result ->
                        SearchResultRow(
                            entryId = result.entryId,
                            date = result.journalDate,
                            title = result.title,
                            snippet = result.snippet.plainText,
                            locked = false,
                        )
                    }
                    searching.value = false
                }
            }
        }
    }

    fun onQueryChange(text: String) {
        query.value = text
    }

    /** Loads the next page once the user approaches the end of the list. */
    fun loadMore() {
        if (pageSize.value >= MAX_PAGES * PAGE_SIZE) return
        pageSize.value += PAGE_SIZE
    }

    private fun groupByMonth(rows: List<JournalEntrySummary>): List<JournalMonth> =
        rows.groupBy { YearMonth.from(it.journalDate) }
            .toSortedMap(compareByDescending { it })
            .map { (month, entries) -> JournalMonth(month, entries.sortedByDescending { it.journalDate }) }

    companion object {
        const val PAGE_SIZE = 30
        const val MAX_PAGES = 40
    }
}
