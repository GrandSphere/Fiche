package com.grandsphere.fiche.ui.home

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grandsphere.fiche.ui.chrome.MediaTypeTabs
import com.grandsphere.fiche.ui.chrome.OverflowItem
import com.grandsphere.fiche.ui.chrome.OverflowMenu
import com.grandsphere.fiche.ui.components.FicheSnackbarHost
import com.grandsphere.fiche.ui.components.FilterSheet
import com.grandsphere.fiche.ui.components.StatusMessageText
import com.grandsphere.fiche.ui.components.TitleCard
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDrawer: () -> Unit,
    onAdd: () -> Unit,
    onOpenTitle: (Long) -> Unit,
    onSearchCatalog: (String) -> Unit = {},
    onGetSuggestions: (Set<Long>) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val titles by viewModel.titles.collectAsStateWithLifecycle()
    val mediaType by viewModel.mediaType.collectAsStateWithLifecycle()
    val enabledTypes by viewModel.enabledMediaTypes.collectAsStateWithLifecycle()
    val compact by viewModel.compactMode.collectAsStateWithLifecycle()
    val showCompletedMark by viewModel.showCompletedMark.collectAsStateWithLifecycle()
    val showCategoryTabs by viewModel.showCategoryTabs.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val refreshProgress by viewModel.refreshProgress.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    var showFilter by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var confirmCollectionId by remember { mutableStateOf<Long?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val allIds = remember(titles) { titles.map { it.id } }
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    DisposableEffect(Unit) {
        onDispose { viewModel.clearSelection() }
    }

    BackHandler(enabled = selectionMode || showSearch) {
        when {
            selectionMode -> viewModel.clearSelection()
            showSearch -> {
                showSearch = false
                viewModel.clearQuery()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.message.collect { snackbar.showSnackbar(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.pendingShare.collect { intent ->
            shareLauncher.launch(Intent.createChooser(intent, "Share titles"))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigateSuggestions.collect { ids -> onGetSuggestions(ids) }
    }

    LaunchedEffect(showSearch) {
        if (showSearch) {
            delay(80)
            searchFocus.requestFocus()
            keyboard?.show()
        } else {
            viewModel.clearQuery()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = {},
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (selectionMode) "${selectedIds.size} selected"
                            else mediaType.label
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (selectionMode) viewModel.clearSelection() else onOpenDrawer()
                            }
                        ) {
                            Icon(
                                if (selectionMode) Icons.Default.Close else Icons.Default.Menu,
                                if (selectionMode) "Cancel selection" else "Menu"
                            )
                        }
                    },
                    actions = {
                        if (selectionMode) {
                            IconButton(
                                onClick = { viewModel.selectAll(allIds) },
                                enabled = allIds.isNotEmpty() && selectedIds.size < allIds.size
                            ) {
                                Icon(Icons.Default.DoneAll, "Select all")
                            }
                            IconButton(
                                onClick = viewModel::markSelectedWatched,
                                enabled = selectedIds.isNotEmpty()
                            ) {
                                Icon(Icons.Default.Check, "Mark watched")
                            }
                            OverflowMenu(
                                items = buildList {
                                    if (selectedIds.isNotEmpty()) {
                                        val inCollection = viewModel.canRemoveCollection(titles)
                                        add(
                                            OverflowItem(
                                                "Share",
                                                icon = Icons.Default.Share,
                                                children = buildList {
                                                    add(OverflowItem("Share name") { viewModel.shareSelectedNames() })
                                                    add(OverflowItem("Share recommendation") {
                                                        viewModel.shareSelectedRecommendations()
                                                    })
                                                    if (inCollection) {
                                                        add(OverflowItem("Share collection") {
                                                            viewModel.shareSelectedCollection(titles)
                                                        })
                                                    }
                                                }
                                            )
                                        )
                                        add(
                                            OverflowItem("Get suggestions", icon = Icons.Default.Lightbulb) {
                                                viewModel.getSuggestionsForSelected()
                                            }
                                        )
                                        add(
                                            OverflowItem("Move to Interests", icon = Icons.Outlined.Visibility) {
                                                viewModel.moveSelectedToInterests()
                                            }
                                        )
                                        add(
                                            OverflowItem(
                                                "Dislike",
                                                icon = Icons.Outlined.ThumbDown,
                                                onLongClick = { viewModel.banSelected() }
                                            ) {
                                                viewModel.dislikeSelected()
                                            }
                                        )
                                    }
                                    if (viewModel.canHideSelected(titles)) {
                                        add(
                                            OverflowItem("Hide", icon = Icons.Default.VisibilityOff) {
                                                viewModel.hideSelected()
                                            }
                                        )
                                    }
                                    if (viewModel.canUnhideSelected(titles)) {
                                        add(
                                            OverflowItem("Unhide", icon = Icons.Default.Visibility) {
                                                viewModel.unhideSelected()
                                            }
                                        )
                                    }
                                    if (selectedIds.isNotEmpty()) {
                                        val inCollection = viewModel.canRemoveCollection(titles)
                                        add(
                                            OverflowItem(
                                                "Remove",
                                                icon = Icons.Outlined.Delete,
                                                expandOnArrowOnly = inCollection,
                                                children = if (inCollection) {
                                                    listOf(
                                                        OverflowItem("Remove") { viewModel.deleteSelected() },
                                                        OverflowItem("Remove collection") {
                                                            val id = titles.firstOrNull {
                                                                it.id in selectedIds && !it.collectionId.isNullOrBlank()
                                                            }?.id
                                                            if (id != null) confirmCollectionId = id
                                                        }
                                                    )
                                                } else {
                                                    emptyList()
                                                }
                                            ) {
                                                viewModel.deleteSelected()
                                            }
                                        )
                                    }
                                }
                            )
                        } else {
                            IconButton(onClick = {
                                if (showSearch) {
                                    showSearch = false
                                    viewModel.clearQuery()
                                } else {
                                    showSearch = true
                                }
                            }) {
                                Icon(
                                    if (showSearch) Icons.Default.Close else Icons.Default.Search,
                                    if (showSearch) "Hide search" else "Search"
                                )
                            }
                            IconButton(onClick = onAdd) {
                                Icon(Icons.Default.Add, "Add", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            OverflowMenu(
                                items = listOf(
                                    OverflowItem("Filter", icon = Icons.Default.FilterList) { showFilter = true }
                                )
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (!selectionMode && showCategoryTabs) {
                    MediaTypeTabs(
                        current = mediaType,
                        enabledTypes = enabledTypes,
                        onSelect = viewModel::setMediaType
                    )
                }
                if (showSearch && !selectionMode) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .focusRequester(searchFocus),
                        label = { Text("Search your ${mediaType.label.lowercase()}") },
                        singleLine = true
                    )
                }
                refreshProgress?.let { progress ->
                    StatusMessageText("${progress.label} ${progress.current}/${progress.total}…")
                }
                if (titles.isEmpty() && query.isNotBlank()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("No results in your library.")
                            androidx.compose.material3.Button(onClick = { onSearchCatalog(query) }) {
                                Text("Search the catalog")
                            }
                        }
                    }
                } else if (titles.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nothing here yet. Tap + to add a title.")
                    }
                } else {
                    val groups = viewModel.grouped(titles)
                    LazyColumn(
                        contentPadding = PaddingValues(if (compact) 8.dp else 16.dp),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 10.dp)
                    ) {
                        groups.forEach { (header, items) ->
                            if (!header.isNullOrBlank()) {
                                item(header) {
                                    Text(
                                        header,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                            items(items, key = { it.id }) { title ->
                                TitleCard(
                                    item = title,
                                    compact = compact,
                                    showCompletedMark = showCompletedMark,
                                    selectionMode = selectionMode,
                                    selected = title.id in selectedIds,
                                    onClick = { viewModel.onTitleClick(title.id) { onOpenTitle(title.id) } },
                                    onLongClick = { viewModel.onLongPressTitle(title.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
        FicheSnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
    if (showFilter) {
        FilterSheet(
            filter = filter,
            mediaType = mediaType,
            onDismiss = { showFilter = false },
            onApply = {
                viewModel.applyFilter(it)
                showFilter = false
            },
            onShowHiddenChange = { viewModel.applyFilter(filter.copy(showHidden = it)) }
        )
    }
    if (confirmCollectionId != null) {
        AlertDialog(
            onDismissRequest = { confirmCollectionId = null },
            title = { Text("Remove collection") },
            text = { Text("Remove this movie and every other title in the same collection from your library?") },
            confirmButton = {
                TextButton(onClick = {
                    val id = confirmCollectionId ?: return@TextButton
                    confirmCollectionId = null
                    viewModel.clearSelection()
                    viewModel.removeCollection(id)
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCollectionId = null }) { Text("Cancel") }
            }
        )
    }
}
