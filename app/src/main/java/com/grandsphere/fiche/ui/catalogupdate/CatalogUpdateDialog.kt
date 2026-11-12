package com.grandsphere.fiche.ui.catalogupdate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grandsphere.fiche.domain.model.MediaType

@Composable
fun CatalogUpdateDialog(viewModel: CatalogUpdateViewModel) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    if (phase == CatalogUpdatePhase.IDLE) return
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val mediaType by viewModel.mediaType.collectAsStateWithLifecycle()
    val busy = phase == CatalogUpdatePhase.CHECKING || phase == CatalogUpdatePhase.APPLYING
    val upToDate = phase == CatalogUpdatePhase.RESULTS && rows.isEmpty()
    val selectedCount = rows.count { it.selected }

    AlertDialog(
        onDismissRequest = { if (!busy) viewModel.dismiss() },
        title = { Text(mediaType.checkUpdatesLabel) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (busy) {
                    val label = progress?.let { "${it.label}… (${it.current}/${it.total})" }
                        ?: if (phase == CatalogUpdatePhase.APPLYING) "Updating…" else "Checking…"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (upToDate) {
                    Text("All shows up to date", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "Untick anything you do not want to update.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    rows.forEach { row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggle(row.hit.titleId) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = row.selected,
                                onCheckedChange = { viewModel.toggle(row.hit.titleId) }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(row.hit.title, style = MaterialTheme.typography.bodyMedium)
                                Text(row.hit.detail, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                busy -> { }
                upToDate -> TextButton(onClick = viewModel::dismiss) { Text("OK") }
                else -> Button(
                    onClick = viewModel::apply,
                    enabled = selectedCount > 0
                ) { Text("Apply") }
            }
        },
        dismissButton = {
            if (!busy && !upToDate) {
                TextButton(onClick = viewModel::dismiss) { Text("Cancel") }
            }
        }
    )
}
