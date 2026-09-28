package `as`.today.missyou.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.statusBarsPadding

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `as`.today.missyou.AppContainer
import `as`.today.missyou.domain.model.JournalEntrySummary
import `as`.today.missyou.ui.components.EmptyState
import `as`.today.missyou.ui.components.LockBadge
import `as`.today.missyou.ui.components.TagPill
import `as`.today.missyou.ui.components.TodayCard
import `as`.today.missyou.ui.common.containerViewModel
import `as`.today.missyou.ui.navigation.Destination
import `as`.today.missyou.ui.navigation.FloatingPillNavigation
import `as`.today.missyou.ui.theme.LayoutMetrics
import `as`.today.missyou.ui.theme.Radii
import `as`.today.missyou.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")

/**
 * The journal archive (§5).
 *
 * A chronological list grouped by month, with an offline search field. Search is
 * debounced by the caller and answered from the sealed token index, so typing stays
 * responsive on a low-end device even with a large journal.
 */
@Composable
fun JournalScreen(
    container: AppContainer,
    onOpenEntry: (LocalDate) -> Unit,
    onNavigate: (Destination) -> Unit,
) {
    val viewModel = containerViewModel { JournalViewModel(it) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(
        initialValue = `as`.today.missyou.domain.model.AppSettings(),
    )
    val listState = rememberLazyListState()

    // Prefetch one page before the user reaches the bottom so scrolling never stalls.
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 5
        }
    }
    androidx.compose.runtime.LaunchedEffect(shouldLoadMore, state.months.size) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                top = Spacing.lg,
                bottom = LayoutMetrics.contentBottomInset,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item(key = "title") {
                Text(
                    text = "Journal",
                    modifier = Modifier
                        .padding(horizontal = LayoutMetrics.horizontalMargin)
                        .semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            item(key = "search") {
                SearchField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.padding(horizontal = LayoutMetrics.horizontalMargin),
                )
            }

            if (state.query.isNotBlank()) {
                if (state.searchResults.isEmpty() && !state.searching) {
                    item(key = "no-results") {
                        EmptyState(
                            title = "No matches",
                            body = "Nothing in your journal matches “${state.query}”.",
                        )
                    }
                } else {
                    items2(state.searchResults) { result ->
                        SearchRow(
                            result = result,
                            onClick = { onOpenEntry(result.date) },
                        )
                    }
                }
            } else {
                if (state.months.isEmpty() && !state.loading) {
                    item(key = "empty") {
                        EmptyState(
                            title = "Nothing written yet",
                            body = "Your archive fills up as you write.",
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                        )
                    }
                }

                state.months.forEach { month ->
                    item(key = "month-${month.month}") {
                        MonthHeader(
                            title = month.title,
                            entryCount = month.entries.size,
                            words = month.wordTotal,
                        )
                    }
                    items2(month.entries) { summary ->
                        ArchiveRow(
                            summary = summary,
                            hidePreviews = settings.hidePreviews,
                            onClick = { onOpenEntry(summary.journalDate) },
                        )
                    }
                }
            }
        }

        FloatingPillNavigation(
            current = Destination.JOURNAL,
            onSelect = onNavigate,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private inline fun <T> LazyListScope.items2(
    data: List<T>,
    crossinline itemContent: @Composable (T) -> Unit,
) = items(data.size, key = { index -> data[index].hashCode().toString() + index }) { index ->
    itemContent(data[index])
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search your journal") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Clear search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onValueChange("") },
                )
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(Radii.pill),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

@Composable
private fun MonthHeader(title: String, entryCount: Int, words: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = LayoutMetrics.horizontalMargin,
                end = LayoutMetrics.horizontalMargin,
                top = Spacing.lg,
                bottom = Spacing.xxs,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "$entryCount ${if (entryCount == 1) "entry" else "entries"} · $words words",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ArchiveRow(
    summary: JournalEntrySummary,
    hidePreviews: Boolean,
    onClick: () -> Unit,
) {
    TodayCard(
        modifier = Modifier.padding(horizontal = LayoutMetrics.horizontalMargin),
        onClick = onClick,
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = summary.journalDate.format(DAY_FORMAT),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LockBadge(locked = summary.isLocked, compact = true)
            }
            if (summary.title.isNotBlank()) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!hidePreviews && summary.preview.isNotBlank()) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = summary.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (hidePreviews) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = "${summary.wordCount} words",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (summary.tags.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    summary.tags.take(3).forEach { tag ->
                        TagPill(tag)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(result: SearchResultRow, onClick: () -> Unit) {
    TodayCard(
        modifier = Modifier.padding(horizontal = LayoutMetrics.horizontalMargin),
        onClick = onClick,
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text(
                text = result.date.format(DAY_FORMAT),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (result.title.isNotBlank()) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = result.snippet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
