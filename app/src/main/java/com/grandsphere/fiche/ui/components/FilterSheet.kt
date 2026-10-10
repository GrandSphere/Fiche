package com.grandsphere.fiche.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.grandsphere.fiche.domain.model.GenreCatalog
import com.grandsphere.fiche.domain.model.LibraryFilter
import com.grandsphere.fiche.domain.model.MediaType
import com.grandsphere.fiche.domain.model.SortOption

private data class StatusChoice(
    val label: String,
    val selected: (LibraryFilter) -> Boolean,
    val apply: (LibraryFilter) -> LibraryFilter
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    filter: LibraryFilter,
    mediaType: MediaType,
    onDismiss: () -> Unit,
    onApply: (LibraryFilter) -> Unit,
    onShowHiddenChange: (Boolean) -> Unit = {}
) {
    var local by remember {
        mutableStateOf(
            filter.copy(sorts = filter.sortChain().ifEmpty { listOf(SortOption.RATING) })
        )
    }
    var showHidden by remember { mutableStateOf(filter.showHidden) }
    var yearFrom by remember { mutableStateOf(filter.yearFrom?.toString().orEmpty()) }
    var yearTo by remember { mutableStateOf(filter.yearTo?.toString().orEmpty()) }
    var dateVisible by remember { mutableStateOf(filter.yearFrom != null || filter.yearTo != null) }
    var sortMenu by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }
    var statusMenu by remember { mutableStateOf(false) }
    var genreMenu by remember { mutableStateOf(false) }
    var showStatus by remember {
        mutableStateOf(
            filter.completed != null || filter.cancelled != null ||
                filter.moreOfThis != null || filter.ratedOnly == true || filter.recommended != null
        )
    }
    var showGenre by remember { mutableStateOf(filter.genres.isNotEmpty()) }
    val statusChoices = listOf(
        StatusChoice("Completed", { it.completed == true }) {
            it.copy(completed = if (it.completed == true) null else true)
        },
        StatusChoice("Not completed", { it.completed == false }) {
            it.copy(completed = if (it.completed == false) null else false)
        },
        StatusChoice("Cancelled", { it.cancelled == true }) {
            it.copy(cancelled = if (it.cancelled == true) null else true)
        },
        StatusChoice("More of this", { it.moreOfThis == true }) {
            it.copy(moreOfThis = if (it.moreOfThis == true) null else true)
        },
        StatusChoice("Rated", { it.ratedOnly == true }) {
            it.copy(ratedOnly = if (it.ratedOnly == true) null else true)
        },
        StatusChoice("Recommended", { it.recommended == true }) {
            it.copy(recommended = if (it.recommended == true) null else true)
        }
    )
    val catalog = GenreCatalog.forType(mediaType)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Sort & filter")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show hidden")
                Switch(
                    checked = showHidden,
                    onCheckedChange = {
                        showHidden = it
                        onShowHiddenChange(it)
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Hide completed")
                Switch(
                    checked = local.hideCompleted,
                    onCheckedChange = { local = local.copy(hideCompleted = it) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Hide incomplete")
                Switch(
                    checked = local.hideIncomplete,
                    onCheckedChange = { local = local.copy(hideIncomplete = it) }
                )
            }
            FilterAddRow(
                label = "Sort",
                menuOpen = sortMenu,
                onMenu = { sortMenu = it },
                options = SortOption.entries.filter { it !in local.sorts }
                    .map { it.label to { local = local.copy(sorts = local.sorts + it) } }
            ) {
                local.sorts.forEach { option ->
                    FilterChip(
                        selected = true,
                        onClick = { local = local.copy(sorts = local.sorts - option) },
                        label = { Text(option.label) }
                    )
                }
            }
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            FilterAddRow(
                label = "Filter",
                menuOpen = filterMenu,
                onMenu = { filterMenu = it },
                options = buildList {
                    if (!showStatus) add("Status" to { showStatus = true })
                    if (!showGenre) add("Genre" to { showGenre = true })
                    if (!dateVisible) add("Date" to { dateVisible = true })
                }
            ) { }
            if (showStatus) {
                FilterAddRow(
                    label = "Status",
                    menuOpen = statusMenu,
                    onMenu = { statusMenu = it },
                    options = statusChoices.filterNot { it.selected(local) }.map { choice ->
                        choice.label to { local = choice.apply(local) }
                    }
                ) {
                    statusChoices.filter { it.selected(local) }.forEach { choice ->
                        FilterChip(
                            selected = true,
                            onClick = { local = choice.apply(local) },
                            label = { Text(choice.label) }
                        )
                    }
                }
            }
            if (showGenre) {
                FilterAddRow(
                    label = "Genre",
                    menuOpen = genreMenu,
                    onMenu = { genreMenu = it },
                    options = catalog.filter { it !in local.genres }.map { genre ->
                        genre to { local = local.copy(genres = local.genres + genre) }
                    }
                ) {
                    local.genres.forEach { genre ->
                        FilterChip(
                            selected = true,
                            onClick = { local = local.copy(genres = local.genres - genre) },
                            label = { Text(genre) }
                        )
                    }
                }
            }
            if (dateVisible) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Date")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = yearFrom,
                            onValueChange = { yearFrom = it.filter(Char::isDigit).take(4) },
                            label = { Text("Year from") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = yearTo,
                            onValueChange = { yearTo = it.filter(Char::isDigit).take(4) },
                            label = { Text("Year to") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    local = LibraryFilter()
                    showHidden = false
                    yearFrom = ""
                    yearTo = ""
                    dateVisible = false
                    showStatus = false
                    showGenre = false
                }) { Text("Clear") }
                Button(onClick = {
                    onApply(
                        local.copy(
                            showHidden = showHidden,
                            yearFrom = yearFrom.toIntOrNull(),
                            yearTo = yearTo.toIntOrNull(),
                            sort = local.sorts.firstOrNull() ?: SortOption.RATING
                        )
                    )
                }) { Text("Apply") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterAddRow(
    label: String,
    menuOpen: Boolean,
    onMenu: (Boolean) -> Unit,
    options: List<Pair<String, () -> Unit>>,
    chips: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label)
            Box {
                CompactAddIcon(onClick = { onMenu(true) }, contentDescription = "Add $label")
                DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }) {
                    if (options.isEmpty()) {
                        DropdownMenuItem(text = { Text("All added") }, onClick = { onMenu(false) })
                    } else {
                        options.forEach { (name, action) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    action()
                                    onMenu(false)
                                }
                            )
                        }
                    }
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            chips()
        }
    }
}

@Composable
fun CompactAddIcon(onClick: () -> Unit, contentDescription: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription,
            modifier = Modifier.size(22.dp)
        )
    }
}
