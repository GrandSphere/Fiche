package com.grandsphere.fiche.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grandsphere.fiche.data.catalog.catalogProvider
import com.grandsphere.fiche.ui.chrome.MediaTypeTabs
import com.grandsphere.fiche.ui.components.FicheSnackbarHost
import com.grandsphere.fiche.ui.components.RemoteResultRow
import com.grandsphere.fiche.ui.components.StatusMessageText
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSearchScreen(
    onBack: () -> Unit,
    viewModel: AddSearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val mediaType by viewModel.mediaType.collectAsStateWithLifecycle()
    val enabledTypes by viewModel.enabledMediaTypes.collectAsStateWithLifecycle()
    val libraryIds by viewModel.libraryIds.collectAsStateWithLifecycle()
    val watchlistIds by viewModel.watchlistIds.collectAsStateWithLifecycle()
    val banIds by viewModel.banIds.collectAsStateWithLifecycle()
    val dislikeIds by viewModel.dislikeIds.collectAsStateWithLifecycle()
    val completedIds by viewModel.completedIds.collectAsStateWithLifecycle()
    val libraryPrefs by viewModel.libraryPrefs.collectAsStateWithLifecycle()
    val fromShare by viewModel.fromShare.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { viewModel.message.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(fromShare, loading) {
        if (!fromShare && !loading) {
            delay(80)
            searchFocus.requestFocus()
            keyboard?.show()
        }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = {},
        topBar = {
            TopAppBar(
                title = { Text("Add to ${mediaType.label.lowercase()}") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
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
            if (fromShare || loading) {
                StatusMessageText(if (fromShare) "Loading…" else "Searching…")
            } else if (!statusMessage.isNullOrBlank()) {
                StatusMessageText(statusMessage!!)
            }
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .focusRequester(searchFocus),
                label = { Text("Search") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = viewModel::search) { Icon(Icons.Default.Search, "Search") }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() })
            )
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(results, key = { it.remoteId }) { item ->
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
                        catalogProvider = libraryPrefs.catalogProvider(mediaType)
                    )
                }
                if (hasMore && !loading) {
                    item(key = "more") {
                        Button(
                            onClick = viewModel::loadMore,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("More") }
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
}
