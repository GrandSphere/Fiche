package com.grandsphere.fiche.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grandsphere.fiche.data.backup.SharedTitle
import com.grandsphere.fiche.data.repository.LibraryRepository
import com.grandsphere.fiche.data.repository.ShareImportComparison
import com.grandsphere.fiche.domain.model.MediaType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShareImportViewModel @Inject constructor(
    private val library: LibraryRepository
) : ViewModel() {
    var rows by mutableStateOf<List<ShareImportComparison>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set

    fun prepare(titles: List<SharedTitle>) {
        viewModelScope.launch {
            loading = true
            rows = library.compareSharedTitles(titles)
            loading = false
        }
    }

    fun toggle(index: Int) {
        rows = rows.mapIndexed { i, row ->
            if (i == index && !row.alreadyInLibrary) row.copy(selected = !row.selected) else row
        }
    }

    fun selectedTitles(): List<SharedTitle> =
        rows.filter { it.selected && !it.alreadyInLibrary }.map { it.shared }
}

@Composable
fun ShareImportDialog(
    titles: List<SharedTitle>,
    onConfirm: (List<SharedTitle>) -> Unit,
    onDismiss: () -> Unit,
    viewModel: ShareImportViewModel = hiltViewModel()
) {
    LaunchedEffect(titles) { viewModel.prepare(titles) }
    val rows = viewModel.rows
    val loading = viewModel.loading

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compare shared titles") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (loading) {
                    Text("Loading…", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "Already watched/added titles are shown. Unadded titles are ticked by default.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    rows.forEachIndexed { index, row ->
                        val category = runCatching { MediaType.valueOf(row.shared.category) }
                            .getOrNull()?.label ?: row.shared.category
                        val status = when {
                            row.alreadyWatched -> "Already watched"
                            row.alreadyInLibrary -> "Already added"
                            else -> "Not in library"
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !row.alreadyInLibrary) { viewModel.toggle(index) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = row.selected,
                                onCheckedChange = { viewModel.toggle(index) },
                                enabled = !row.alreadyInLibrary
                            )
                            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                                Text(
                                    "${row.shared.title} · $category",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(status, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(viewModel.selectedTitles()) },
                enabled = !loading && viewModel.selectedTitles().isNotEmpty()
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
