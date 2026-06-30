package org.ukky.recobook

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.ukky.recobook.data.Book
import recobook.sharedui.generated.resources.*

@Composable
fun ManagementScreen(
    books: List<Book>,
    borrowerHistoryCount: Int,
    onBack: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onExportBooksJsonl: () -> Unit,
    onExportMarkdown: () -> Unit,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loanedCount = books.count { it.loanInfo != null }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(Res.string.back_to_list))
            }
            Text(
                text = stringResource(Res.string.admin_manage),
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        ManagementSectionCard(
            title = stringResource(Res.string.admin_overview_title),
            description = null,
        ) {
            ManagementMetricRow(
                label = stringResource(Res.string.admin_book_count),
                value = books.size.toString(),
            )
            ManagementMetricRow(
                label = stringResource(Res.string.admin_loaned_count),
                value = loanedCount.toString(),
            )
            ManagementMetricRow(
                label = stringResource(Res.string.admin_borrower_history_count),
                value = borrowerHistoryCount.toString(),
            )
        }

        ManagementSectionCard(
            title = stringResource(Res.string.admin_backup_title),
            description = stringResource(Res.string.admin_backup_description),
        ) {
            Text(
                text = stringResource(Res.string.admin_backup_import_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onExportBackup,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.admin_export_backup))
                }
                OutlinedButton(
                    onClick = onImportBackup,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.admin_import_backup))
                }
            }
        }

        ManagementSectionCard(
            title = stringResource(Res.string.admin_jsonl_books_title),
            description = stringResource(Res.string.admin_jsonl_books_description),
        ) {
            Button(
                onClick = onExportBooksJsonl,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.admin_export_books_jsonl))
            }
        }

        ManagementSectionCard(
            title = stringResource(Res.string.admin_share_title),
            description = stringResource(Res.string.admin_share_description),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onExportMarkdown,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.admin_export_markdown))
                }
                OutlinedButton(
                    onClick = onExportCsv,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.admin_export_csv))
                }
            }
        }
    }
}

@Composable
private fun ManagementSectionCard(
    title: String,
    description: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                content()
            },
        )
    }
}

@Composable
private fun ManagementMetricRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
