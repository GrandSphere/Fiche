package com.grandsphere.fiche.ui.suggestions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grandsphere.fiche.data.catalog.catalogProvider
import com.grandsphere.fiche.ui.chrome.CollapsibleBlock
import com.grandsphere.fiche.ui.chrome.MediaTypeTabs
import com.grandsphere.fiche.ui.components.FicheSnackbarHost
import com.grandsphere.fiche.ui.components.FilterAddRow
import com.grandsphere.fiche.ui.components.RemoteResultRow
import com.grandsphere.fiche.ui.components.StatusMessageText

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SuggestionsScreen(
    onOpenDrawer: () -> Unit,
    suggestionSeeds: kotlinx.coroutines.flow.SharedFlow<Set<Long>>? = null,
    viewModel: SuggestionsViewModel = hiltViewModel()
) {
    val mediaType by viewModel.mediaType.collectAsStateWithLifecycle()
    val enabledTypes by viewModel.enabledMediaTypes.collectAsStateWithLifecycle()
    val filter by viewModel.seedFilter.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val libraryIds by viewModel.libraryIds.collectAsStateWithLifecycle()
    val watchlistIds by viewModel.watchlistIds.collectAsStateWithLifecycle()
    val banIds by viewModel.banIds.collectAsStateWithLifecycle()
    val dislikeIds by viewModel.dislikeIds.collectAsStateWithLifecycle()
    val completedIds by viewModel.completedIds.collectAsStateWithLifecycle()
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val selectedRemoteIds by viewModel.selectedRemoteIds.collectAsStateWithLifecycle()
    val libraryPrefs by viewModel.libraryPrefs.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var categoryMenu by remember { mutableStateOf(false) }
    var seedsMenu by remember { mutableStateOf(false) }
    var genreMenu by remember { mutableStateOf(false) }
    var ratingMenu by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { viewModel.clearSelection() } }
    BackHandler(enabled = selectionMode) { viewModel.clearSelection() }
    LaunchedEffect(Unit) { viewModel.message.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) {
        suggestionSeeds?.collect { ids ->
            viewModel.setSeedTitles(ids)
            viewModel.load()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = {},
            topBar = {
                TopAppBar(
                    title = {
                        Text(if (selectionMode) "${selectedRemoteIds.size} selected" else "Suggestions")
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (selectionMode) viewModel.clearSelection() else onOpenDrawer()
                        }) {
                            Icon(
                                if (selectionMode) Icons.Default.Close else Icons.Default.Menu,
                                if (selectionMode) "Cancel selection" else "Menu"
                            )
                        }
                    },
                    actions = {
                        if (selectionMode) {
                            IconButton(
                                onClick = viewModel::selectAllResults,
                                enabled = results.isNotEmpty() && selectedRemoteIds.size < results.size
                            ) { Icon(Icons.Default.DoneAll, "Select all") }
                            IconButton(
                                onClick = viewModel::markWatchedSelected,
                                enabled = selectedRemoteIds.isNotEmpty()
                            ) { Icon(Icons.Default.Check, "Mark watched") }
                        }
                    }
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                MediaTypeTabs(
                    current = mediaType,
                    enabledTypes = enabledTypes,
                    onSelect = viewModel::setMediaType
                )
                if (loading) {
                    StatusMessageText(if (results.isEmpty()) "Searching…" else "Loading more…")
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "filters") {
                        CollapsibleBlock(
                            key = "suggestions-filters",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            startExpanded = true,
                            header = {
                                Text(
                                    "Seed filters",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterAddRow(
                                    label = "Category",
                                    menuOpen = categoryMenu,
                                    onMenu = { categoryMenu = it },
                                    options = buildList {
                                        if (!filter.allCategories) {
                                            add("All" to {
                                                viewModel.updateFilter {
                                                    it.copy(allCategories = true, seedCategories = emptySet())
                                                }
                                            })
                                        }
                                        enabledTypes.forEach { type ->
                                            if (!filter.allCategories && type !in filter.seedCategories) {
                                                add(type.label to {
                                                    viewModel.updateFilter {
                                                        it.copy(seedCategories = it.seedCategories + type)
                                                    }
                                                })
                                            }
                                        }
                                    }
                                ) {
                                    if (filter.allCategories) {
                                        FilterChip(
                                            selected = true,
                                            onClick = { viewModel.updateFilter { it.copy(allCategories = false) } },
                                            label = { Text("All") }
                                        )
                                    }
                                    filter.seedCategories.forEach { type ->
                                        FilterChip(
                                            selected = true,
                                            onClick = {
                                                viewModel.updateFilter { it.copy(seedCategories = it.seedCategories - type) }
                                            },
                                            label = { Text(type.label) }
                                        )
                                    }
                                }
                                FilterAddRow(
                                    label = "Seeds",
                                    menuOpen = seedsMenu,
                                    onMenu = { seedsMenu = it },
                                    options = buildList {
                                        if (!filter.completed) add("Completed" to { viewModel.updateFilter { it.copy(completed = true) } })
                                        if (!filter.moreOfThis) add("More of this" to { viewModel.updateFilter { it.copy(moreOfThis = true) } })
                                        if (!filter.recommended) add("Recommended" to { viewModel.updateFilter { it.copy(recommended = true) } })
                                        if (!filter.useRating) add("Rating" to { viewModel.updateFilter { it.copy(useRating = true) } })
                                        if (!filter.useGenre) add("Genre" to { viewModel.updateFilter { it.copy(useGenre = true) } })
                                        if (!filter.allLibrary) add("All" to { viewModel.updateFilter { it.copy(allLibrary = true) } })
                                    }
                                ) {
                                    if (filter.completed) FilterChip(selected = true, onClick = { viewModel.updateFilter { it.copy(completed = false) } }, label = { Text("Completed") })
                                    if (filter.moreOfThis) FilterChip(selected = true, onClick = { viewModel.updateFilter { it.copy(moreOfThis = false) } }, label = { Text("More of this") })
                                    if (filter.recommended) FilterChip(selected = true, onClick = { viewModel.updateFilter { it.copy(recommended = false) } }, label = { Text("Recommended") })
                                    if (filter.useRating) FilterChip(selected = true, onClick = { viewModel.updateFilter { it.copy(useRating = false, minRating = null) } }, label = { Text("Rating") })
                                    if (filter.useGenre) FilterChip(selected = true, onClick = { viewModel.updateFilter { it.copy(useGenre = false, genres = emptySet()) } }, label = { Text("Genre") })
                                    if (filter.allLibrary) FilterChip(selected = true, onClick = { viewModel.updateFilter { it.copy(allLibrary = false) } }, label = { Text("All") })
                                }
                                if (filter.useGenre) {
                                    HorizontalDivider()
                                    FilterAddRow(
                                        label = "Genre",
                                        menuOpen = genreMenu,
                                        onMenu = { genreMenu = it },
                                        options = genres.filter { it !in filter.genres }.map { genre ->
                                            genre to { viewModel.updateFilter { it.copy(genres = it.genres + genre) } }
                                        }
                                    ) {
                                        filter.genres.forEach { genre ->
                                            FilterChip(
                                                selected = true,
                                                onClick = { viewModel.updateFilter { it.copy(genres = it.genres - genre) } },
                                                label = { Text(genre) }
                                            )
                                        }
                                    }
                                }
                                if (filter.useRating) {
                                    HorizontalDivider()
                                    FilterAddRow(
                                        label = "Rating",
                                        menuOpen = ratingMenu,
                                        onMenu = { ratingMenu = it },
                                        options = if (filter.minRating == null) {
                                            (1..10).map { score ->
                                                "$score+" to { viewModel.updateFilter { it.copy(minRating = score) } }
                                            }
                                        } else emptyList()
                                    ) {
                                        filter.minRating?.let { min ->
                                            FilterChip(
                                                selected = true,
                                                onClick = { viewModel.updateFilter { it.copy(minRating = null) } },
                                                label = { Text("$min+") }
                                            )
                                        }
                                    }
                                }
                                Button(onClick = viewModel::load, modifier = Modifier.fillMaxWidth()) {
                                    Text("Get suggestions")
                                }
                            }
                        }
                    }
                    items(results, key = { it.remoteId }) { item ->
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            RemoteResultRow(
                                item = item,
                                inLibrary = item.remoteId in libraryIds,
                                inWatchlist = item.remoteId in watchlistIds,
                                banned = item.remoteId in banIds,
                                disliked = item.remoteId in dislikeIds,
                                completed = item.remoteId in completedIds,
                                onAdd = { viewModel.track(item) },
                                onWatchlist = { viewModel.watchlist(item) },
                                onWatched = { viewModel.markWatched(item) },
                                onDislike = { viewModel.dislike(item) },
                                onBan = { viewModel.ban(item) },
                                selectionMode = selectionMode,
                                selected = item.remoteId in selectedRemoteIds,
                                onClick = { viewModel.onResultClick(item.remoteId) {} },
                                catalogProvider = libraryPrefs.catalogProvider(mediaType),
                                onLongClick = { viewModel.onLongPressResult(item.remoteId) }
                            )
                        }
                    }
                    if (hasMore && !loading) {
                        item(key = "more") {
                            Button(
                                onClick = viewModel::loadMore,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            ) { Text("More") }
                        }
                    }
                }
            }
        }
        FicheSnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.TopCenter))
    }
}
