package com.grandsphere.fiche.ui.discover

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedTextField
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
import com.grandsphere.fiche.domain.model.DiscoverCriterion
import com.grandsphere.fiche.domain.model.DiscoverFilter
import com.grandsphere.fiche.ui.chrome.CollapsibleBlock
import com.grandsphere.fiche.ui.chrome.MediaTypeTabs
import com.grandsphere.fiche.ui.components.FicheSnackbarHost
import com.grandsphere.fiche.ui.components.FilterAddRow
import com.grandsphere.fiche.ui.components.RemoteResultRow
import com.grandsphere.fiche.ui.components.StatusMessageText

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(
    onOpenDrawer: () -> Unit,
    viewModel: DiscoverViewModel = hiltViewModel()
) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val findType by viewModel.findType.collectAsStateWithLifecycle()
    val enabledTypes by viewModel.enabledMediaTypes.collectAsStateWithLifecycle()
    val criteria by viewModel.criteria.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val libraryIds by viewModel.libraryIds.collectAsStateWithLifecycle()
    val watchlistIds by viewModel.watchlistIds.collectAsStateWithLifecycle()
    val banIds by viewModel.banIds.collectAsStateWithLifecycle()
    val dislikeIds by viewModel.dislikeIds.collectAsStateWithLifecycle()
    val completedIds by viewModel.completedIds.collectAsStateWithLifecycle()
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val selectedRemoteIds by viewModel.selectedRemoteIds.collectAsStateWithLifecycle()
    val libraryPrefs by viewModel.libraryPrefs.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    DisposableEffect(Unit) { onDispose { viewModel.clearSelection() } }
    BackHandler(enabled = selectionMode) { viewModel.clearSelection() }
    LaunchedEffect(Unit) { viewModel.message.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = {},
            topBar = {
                TopAppBar(
                    title = {
                        Text(if (selectionMode) "${selectedRemoteIds.size} selected" else "Find New")
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
                    current = findType,
                    enabledTypes = enabledTypes,
                    onSelect = viewModel::setFindType
                )
                if (loading) {
                    StatusMessageText(if (results.isEmpty()) "Searching…" else "Loading more…")
                } else if (!statusMessage.isNullOrBlank()) {
                    StatusMessageText(statusMessage!!)
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "filters") {
                        DiscoverFilterPanel(
                            filter = filter,
                            criteria = criteria,
                            genres = genres,
                            availableCriteria = viewModel.availableCriteria(),
                            onAddCriterion = viewModel::addCriterion,
                            onRemoveCriterion = viewModel::removeCriterion,
                            onAddGenre = viewModel::addGenre,
                            onRemoveGenre = viewModel::removeGenre,
                            onAddDisallowGenre = viewModel::addDisallowGenre,
                            onRemoveDisallowGenre = viewModel::removeDisallowGenre,
                            onSetYearText = viewModel::setYearText,
                            onSetQuery = viewModel::setQuery,
                            onSetActor = viewModel::setActor,
                            onSetAuthor = viewModel::setAuthor,
                            onSetProducer = viewModel::setProducer,
                            onSetDeveloper = viewModel::setDeveloper,
                            onSetPublisher = viewModel::setPublisher,
                            onSearch = viewModel::load
                        )
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
                                onAdd = { viewModel.add(item) },
                                onWatchlist = { viewModel.watchlist(item) },
                                onWatched = { viewModel.markWatched(item) },
                                onDislike = { viewModel.dislike(item) },
                                onBan = { viewModel.ban(item) },
                                selectionMode = selectionMode,
                                selected = item.remoteId in selectedRemoteIds,
                                onClick = { viewModel.onResultClick(item.remoteId) },
                                catalogProvider = libraryPrefs.catalogProvider(findType),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoverFilterPanel(
    filter: DiscoverFilter,
    criteria: Set<DiscoverCriterion>,
    genres: List<String>,
    availableCriteria: List<DiscoverCriterion>,
    onAddCriterion: (DiscoverCriterion) -> Unit,
    onRemoveCriterion: (DiscoverCriterion) -> Unit,
    onAddGenre: (String) -> Unit,
    onRemoveGenre: (String) -> Unit,
    onAddDisallowGenre: (String) -> Unit,
    onRemoveDisallowGenre: (String) -> Unit,
    onSetYearText: (String, String) -> Unit,
    onSetQuery: (String) -> Unit,
    onSetActor: (String) -> Unit,
    onSetAuthor: (String) -> Unit,
    onSetProducer: (String) -> Unit,
    onSetDeveloper: (String) -> Unit,
    onSetPublisher: (String) -> Unit,
    onSearch: () -> Unit
) {
    var criterionMenuOpen by remember { mutableStateOf(false) }
    var genreMenuOpen by remember { mutableStateOf(false) }
    var disallowGenreMenuOpen by remember { mutableStateOf(false) }

    CollapsibleBlock(
        key = "discover-filters",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        startExpanded = true,
        header = {
            Text(
                "Filters",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = filter.query,
                onValueChange = onSetQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title search") },
                singleLine = true
            )
            FilterAddRow(
                label = "Active filters",
                menuOpen = criterionMenuOpen,
                onMenu = { criterionMenuOpen = it },
                options = availableCriteria.map { c -> c.label to { onAddCriterion(c) } }
            ) {
                criteria.forEach { criterion ->
                    FilterChip(
                        selected = true,
                        onClick = { onRemoveCriterion(criterion) },
                        label = { Text(criterion.label) }
                    )
                }
            }
            if (DiscoverCriterion.GENRE in criteria) {
                HorizontalDivider()
                FilterAddRow(
                    label = "Genre",
                    menuOpen = genreMenuOpen,
                    onMenu = { genreMenuOpen = it },
                    options = genres.filter { it !in filter.genres }.map { g -> g to { onAddGenre(g) } }
                ) {
                    filter.genres.forEach { genre ->
                        FilterChip(
                            selected = true,
                            onClick = { onRemoveGenre(genre) },
                            label = { Text(genre) }
                        )
                    }
                }
            }
            if (DiscoverCriterion.DISALLOW_GENRE in criteria) {
                HorizontalDivider()
                FilterAddRow(
                    label = "Disallow genre",
                    menuOpen = disallowGenreMenuOpen,
                    onMenu = { disallowGenreMenuOpen = it },
                    options = genres.filter { it !in filter.disallowGenres }
                        .map { g -> g to { onAddDisallowGenre(g) } }
                ) {
                    filter.disallowGenres.forEach { genre ->
                        FilterChip(
                            selected = true,
                            onClick = { onRemoveDisallowGenre(genre) },
                            label = { Text(genre) }
                        )
                    }
                }
            }
            if (DiscoverCriterion.YEAR in criteria) {
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = filter.yearFromText,
                        onValueChange = { v -> onSetYearText(v.filter(Char::isDigit).take(4), filter.yearToText) },
                        label = { Text("From year") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = filter.yearToText,
                        onValueChange = { v -> onSetYearText(filter.yearFromText, v.filter(Char::isDigit).take(4)) },
                        label = { Text("To year") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
            if (DiscoverCriterion.ACTOR in criteria) {
                HorizontalDivider()
                OutlinedTextField(value = filter.actor, onValueChange = onSetActor, modifier = Modifier.fillMaxWidth(), label = { Text("Actor") }, singleLine = true)
            }
            if (DiscoverCriterion.AUTHOR in criteria) {
                HorizontalDivider()
                OutlinedTextField(value = filter.author, onValueChange = onSetAuthor, modifier = Modifier.fillMaxWidth(), label = { Text("Author") }, singleLine = true)
            }
            if (DiscoverCriterion.PRODUCER in criteria) {
                HorizontalDivider()
                OutlinedTextField(value = filter.producer, onValueChange = onSetProducer, modifier = Modifier.fillMaxWidth(), label = { Text("Producer") }, singleLine = true)
            }
            if (DiscoverCriterion.DEVELOPER in criteria) {
                HorizontalDivider()
                OutlinedTextField(value = filter.developer, onValueChange = onSetDeveloper, modifier = Modifier.fillMaxWidth(), label = { Text("Developer") }, singleLine = true)
            }
            if (DiscoverCriterion.PUBLISHER in criteria) {
                HorizontalDivider()
                OutlinedTextField(value = filter.publisher, onValueChange = onSetPublisher, modifier = Modifier.fillMaxWidth(), label = { Text("Publisher") }, singleLine = true)
            }
            Button(onClick = onSearch, modifier = Modifier.fillMaxWidth()) { Text("Search") }
        }
    }
}
