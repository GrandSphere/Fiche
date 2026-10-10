package com.grandsphere.fiche.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.grandsphere.fiche.domain.model.MediaType
import com.grandsphere.fiche.ui.chrome.MediaTypeTabs
import com.grandsphere.fiche.ui.components.FicheSnackbarHost
import com.grandsphere.fiche.ui.components.RemoteResultRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    onOpenDrawer: () -> Unit,
    onOpenNotes: () -> Unit,
    viewModel: ListsViewModel = hiltViewModel()
) {
    val items by viewModel.watchlist.collectAsStateWithLifecycle()
    val mediaType by viewModel.mediaType.collectAsStateWithLifecycle()
    val enabledTypes by viewModel.enabledMediaTypes.collectAsStateWithLifecycle()
    val libraryIds by viewModel.libraryIds.collectAsStateWithLifecycle()
    val watchlistIds by viewModel.watchlistIds.collectAsStateWithLifecycle()
    val banIds by viewModel.banIds.collectAsStateWithLifecycle()
    val dislikeIds by viewModel.dislikeIds.collectAsStateWithLifecycle()
    val completedIds by viewModel.completedIds.collectAsStateWithLifecycle()
    val libraryPrefs by viewModel.libraryPrefs.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.message.collect { snackbar.showSnackbar(it) } }
    Box(Modifier.fillMaxSize()) {
        ListScaffold(
            title = "Interests",
            onOpenDrawer = onOpenDrawer,
            mediaType = mediaType,
            onMediaType = viewModel::setMediaType,
            enabled = enabledTypes,
            extraActions = {
                IconButton(onClick = onOpenNotes) { Icon(Icons.Default.EditNote, "Notes") }
            }
        ) {
            if (items.isEmpty()) item { Text("Your interests list is empty.") }
            items(items, key = { it.id }) { item ->
                val remote = item.toRemoteTitle()
                RemoteResultRow(
                    item = remote,
                    inLibrary = remote.remoteId in libraryIds,
                    inWatchlist = remote.remoteId in watchlistIds,
                    banned = remote.remoteId in banIds,
                    disliked = remote.remoteId in dislikeIds,
                    completed = remote.remoteId in completedIds,
                    onAdd = { viewModel.add(remote) },
                    onWatchlist = { viewModel.watchlist(remote) },
                    onWatched = { viewModel.markWatched(remote) },
                    onDislike = { viewModel.dislike(remote) },
                    onBan = { viewModel.ban(remote) },
                    catalogProvider = libraryPrefs.catalogProvider(mediaType)
                )
            }
        }
        FicheSnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterestNotesScreen(
    onBack: () -> Unit,
    viewModel: ListsViewModel = hiltViewModel()
) {
    val mediaType by viewModel.mediaType.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    var text by remember(notes, mediaType) { mutableStateOf(notes) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${mediaType.label} notes") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.saveNotes(text)
                        onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                viewModel.saveNotes(it)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            placeholder = { Text("Titles you know are coming but are not announced yet") }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DislikeListScreen(
    onOpenDrawer: () -> Unit,
    onOpenBanList: () -> Unit,
    viewModel: ListsViewModel = hiltViewModel()
) {
    val items by viewModel.dislikes.collectAsStateWithLifecycle()
    val mediaType by viewModel.mediaType.collectAsStateWithLifecycle()
    val enabledTypes by viewModel.enabledMediaTypes.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    ListScaffold(
        title = "Dislike list",
        onOpenDrawer = onOpenDrawer,
        mediaType = mediaType,
        onMediaType = viewModel::setMediaType,
        enabled = enabledTypes,
        extraActions = {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Ban list") },
                    onClick = {
                        menu = false
                        onOpenBanList()
                    }
                )
            }
        }
    ) {
        if (items.isEmpty()) item { Text("No disliked titles.") }
        items(items, key = { it.id }) { item ->
            NameYearRow(
                title = item.title,
                year = item.year,
                actionLabel = "Remove",
                onAction = { viewModel.undislike(item) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BanListScreen(onBack: () -> Unit, viewModel: ListsViewModel = hiltViewModel()) {
    val items by viewModel.bans.collectAsStateWithLifecycle()
    val mediaType by viewModel.mediaType.collectAsStateWithLifecycle()
    val enabledTypes by viewModel.enabledMediaTypes.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ban list") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (items.isEmpty()) item { Text("No banned titles.") }
                items(items, key = { it.id }) { item ->
                    NameYearRow(
                        title = item.title,
                        year = item.year,
                        actionLabel = "Unban",
                        onAction = { viewModel.unban(item) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListScaffold(
    title: String,
    onOpenDrawer: () -> Unit,
    mediaType: MediaType,
    onMediaType: (MediaType) -> Unit,
    enabled: List<MediaType>,
    extraActions: @Composable (() -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, "Menu") }
                },
                actions = { extraActions?.invoke() }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MediaTypeTabs(
                current = mediaType,
                enabledTypes = enabled,
                onSelect = onMediaType
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}

@Composable
private fun NameYearRow(title: String, year: Int?, actionLabel: String, onAction: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                year?.let { Text(it.toString(), style = MaterialTheme.typography.bodySmall) }
            }
            AssistChip(onClick = onAction, label = { Text(actionLabel) })
        }
    }
}
