package `as`.today.missyou.ui.backup

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `as`.today.missyou.AppContainer
import `as`.today.missyou.domain.model.ConflictResolution
import `as`.today.missyou.domain.model.ExportFormat
import `as`.today.missyou.domain.model.ExportScope
import `as`.today.missyou.domain.model.ImportPlan
import `as`.today.missyou.domain.model.JournalEntrySummary
import `as`.today.missyou.ui.common.containerViewModel
import `as`.today.missyou.ui.components.ChoiceRow
import `as`.today.missyou.ui.components.IconPillButton
import `as`.today.missyou.ui.components.NoticeCard
import `as`.today.missyou.ui.components.PrimaryButton
import `as`.today.missyou.ui.components.SecondaryButton
import `as`.today.missyou.ui.components.SettingsSection
import `as`.today.missyou.ui.components.SettingsSwitchRow
import `as`.today.missyou.ui.components.TodayCard
import `as`.today.missyou.ui.theme.LayoutMetrics
import `as`.today.missyou.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

private val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")

/**
 * Import and export (§18).
 *
 * Reading a backup is a separate, reviewable step from applying it. The screen never
 * writes on the strength of a file the user merely opened: it shows what the file
 * contains, which days already hold a sealed entry, and only writes when the user
 * confirms. That is the only way the "a sealed entry is never replaced" promise can
 * be checked from the outside.
 */
@Composable
fun BackupScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel = containerViewModel { BackupViewModel(container) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // The system create-document picker gives a location without the app ever
    // holding a storage permission.
    val createDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            viewModel.writeExportTo(uri)
        }
    }

    val openDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { viewModel.readImportFrom(uri) }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = Spacing.xs, bottom = LayoutMetrics.contentBottomInset),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item(key = "top") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconPillButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                    )
                }
            }

            item(key = "tabs") {
                ChoiceRow(
                    label = "",
                    options = BackupTab.entries,
                    selected = state.tab,
                    onSelect = viewModel::setTab,
                    labelOf = { if (it == BackupTab.EXPORT) "Export" else "Import" },
                    modifier = Modifier.padding(horizontal = LayoutMetrics.horizontalMargin),
                )
            }

            state.message?.let { text ->
                item(key = "message") {
                    NoticeCard(
                        text = text,
                        accent = if (state.isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.padding(horizontal = LayoutMetrics.horizontalMargin),
                    )
                }
            }

            val plan = state.importPlan
            if (plan != null) {
                importReview(
                    plan = plan,
                    busy = state.busy,
                    onConfirm = viewModel::confirmImport,
                    onCancel = viewModel::cancelImport,
                )
            } else if (state.tab == BackupTab.EXPORT) {
                exportPane(state = state, viewModel = viewModel, onPickFile = { createDocument.launch(viewModel.suggestedFileName()) })
            } else {
                importPane(
                    state = state,
                    onPickFile = { openDocument.launch(arrayOf("application/json", "text/*", "*/*")) },
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.exportPane(
    state: BackupUiState,
    viewModel: BackupViewModel,
    onPickFile: () -> Unit,
) {
    item(key = "export-format") {
        SettingsSection(
            title = "Format",
            modifier = Modifier.padding(horizontal = LayoutMetrics.horizontalMargin),
        )
    }
    item(key = "export-format-row") {
        TodayCard(Modifier.padding(horizontal = LayoutMetrics.horizontalMargin)) {
            Column(Modifier.padding(Spacing.md)) {
                ChoiceRow(
                    label = "",
                    options = ExportFormat.entries,
                    selected = state.export.format,
                    onSelect = viewModel::setFormat,
                    labelOf = { it.name.lowercase().replace('_', ' ').replaceFirstChar { c -> c.uppercase() } },
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "Markdown and plain text are for reading elsewhere. " +
                        "JSON keeps every feature and is the only format an import can " +
                        "read losslessly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    item(key = "export-scope") {
        SettingsSection(
            title = "What to include",
            modifier = Modifier.padding(horizontal = LayoutMetrics.horizontalMargin),
        )
    }
    item(key = "export-scope-row") {
        TodayCard(Modifier.padding(horizontal = LayoutMetrics.horizontalMargin)) {
            Column(Modifier.padding(Spacing.md)) {
                ChoiceRow(
                    label = "",
                    options = ExportScope.entries,
                    selected = state.export.scope,
                    onSelect = viewModel::setScope,
                    labelOf = {
                        when (it) {
                            ExportScope.ALL_ENTRIES -> "Everything"
                            ExportScope.SELECTED_DATES -> "Chosen days"
                            ExportScope.SINGLE_ENTRY -> "One entry"
                        }
                    },
                )
                Spacer(Modifier.height(Spacing.sm))
                SettingsSwitchRow(
                    title = "Include attachments",
                    subtitle = "Embedded images travel with the export",
                    checked = state.export.includeAttachments,
                    onCheckedChange = viewModel::setIncludeAttachments,
                )
                SettingsSwitchRow(
                    title = "Encrypt the file",
                    subtitle = "You will be asked for a passphrase",
                    checked = state.export.encrypt,
                    onCheckedChange = viewModel::setEncrypt,
                )
            }
        }
    }

    if (state.export.scope == ExportScope.SELECTED_DATES) {
        item(key = "export-dates-header") {
            SettingsSection(
                title = "Choose days · ${state.export.dates.size} selected",
                modifier = Modifier.padding(horizontal = LayoutMetrics.horizontalMargin),
            )
        }
        state.entriesByMonth.forEach { (month, summaries) ->
            item(key = "month-$month") {
                Text(
                    text = month.toString(),
                    modifier = Modifier
                        .padding(
                            start = LayoutMetrics.horizontalMargin,
                            end = LayoutMetrics.horizontalMargin,
                            top = Spacing.sm,
                        )
                        .semantics { heading() },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(summaries, key = { "pick-${it.id}" }) { summary ->
                DatePickRow(
                    summary = summary,
                    selected = viewModel.isDateSelected(summary.journalDate),
                    onToggle = { viewModel.toggleDate(summary.journalDate) },
                )
            }
        }
    }

    item(key = "export-action") {
        Column(
            Modifier.padding(horizontal = LayoutMetrics.horizontalMargin, vertical = Spacing.md),
        ) {
            PrimaryButton(
                text = if (state.busy) "Preparing…" else "Choose where to save",
                enabled = state.export.canExport && !state.busy,
                onClick = onPickFile,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "${state.totalEntries} entries available. The file is written " +
                    "where you choose, and never anywhere else.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.importPane(
    state: BackupUiState,
    onPickFile: () -> Unit,
) {
    item(key = "import-intro") {
        TodayCard(Modifier.padding(horizontal = LayoutMetrics.horizontalMargin)) {
            Column(Modifier.padding(Spacing.md)) {
                Text(
                    text = "Restore from a backup",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "A backup is read and summarised first. Nothing is written " +
                        "until you have seen exactly which days would be added, and " +
                        "an entry you already sealed is never replaced.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    item(key = "import-action") {
        Column(Modifier.padding(horizontal = LayoutMetrics.horizontalMargin, vertical = Spacing.md)) {
            PrimaryButton(
                text = if (state.busy) "Reading…" else "Choose a backup file",
                enabled = !state.busy,
                onClick = onPickFile,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.importReview(
    plan: ImportPlan,
    busy: Boolean,
    onConfirm: (ConflictResolution) -> Unit,
    onCancel: () -> Unit,
) {
    item(key = "review-summary") {
        TodayCard(Modifier.padding(horizontal = LayoutMetrics.horizontalMargin)) {
            Column(Modifier.padding(Spacing.md)) {
                Text(
                    text = "Review before importing",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = buildString {
                        append("${plan.entries.size} entries in the file. ")
                        append("${plan.insertCount} would be added. ")
                        if (plan.conflictCount > 0) {
                            append("${plan.conflictCount} already have a sealed entry here and will be kept as they are.")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (plan.conflicts.isNotEmpty()) {
        item(key = "conflict-header") {
            SettingsSection(
                title = "Days that already have a sealed entry",
                subtitle = "These cannot be overwritten. That is the app's central promise.",
                modifier = Modifier.padding(horizontal = LayoutMetrics.horizontalMargin),
            )
        }
        items(plan.conflicts, key = { "conflict-${it.entry.id}" }) { decision ->
            TodayCard(Modifier.padding(horizontal = LayoutMetrics.horizontalMargin)) {
                Column(Modifier.padding(Spacing.md)) {
                    Text(
                        text = decision.journalDate.format(DAY_LABEL),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = "Kept: the entry already sealed on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    item(key = "review-actions") {
        Column(
            Modifier.padding(horizontal = LayoutMetrics.horizontalMargin, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            PrimaryButton(
                text = if (busy) "Importing…" else "Import ${plan.insertCount} entries",
                enabled = !busy && plan.insertCount > 0,
                onClick = { onConfirm(ConflictResolution.SKIP) },
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                text = "Cancel",
                enabled = !busy,
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DatePickRow(
    summary: JournalEntrySummary,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    TodayCard(
        modifier = Modifier.padding(horizontal = LayoutMetrics.horizontalMargin),
        onClick = onToggle,
        containerColor = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = summary.journalDate.format(DAY_LABEL),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (summary.title.isNotBlank()) {
                    Text(
                        text = summary.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = if (selected) "Selected" else "Tap to add",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
