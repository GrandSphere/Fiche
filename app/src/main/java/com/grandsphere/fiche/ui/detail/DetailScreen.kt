package com.grandsphere.fiche.ui.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import com.grandsphere.fiche.ui.components.FicheSnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import android.content.Intent
import android.net.Uri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grandsphere.fiche.data.remote.RemoteTitle
import com.grandsphere.fiche.domain.model.MediaType
import com.grandsphere.fiche.domain.model.displayCompleted
import com.grandsphere.fiche.domain.model.displayWatching
import com.grandsphere.fiche.ui.chrome.CollapsibleBlock
import com.grandsphere.fiche.ui.chrome.OverflowItem
import com.grandsphere.fiche.ui.chrome.OverflowMenu
import com.grandsphere.fiche.ui.components.CompletionBadge
import com.grandsphere.fiche.ui.components.Poster
import com.grandsphere.fiche.util.cleanCatalogOverview
import com.grandsphere.fiche.util.displayCatalogStatus
import com.grandsphere.fiche.util.formatDateRange
import com.grandsphere.fiche.util.rawgGameWebUrl
import com.grandsphere.fiche.util.steamSearchUrl
import com.grandsphere.fiche.util.formatWatchHours
import com.grandsphere.fiche.util.movieHasReleased
import com.grandsphere.fiche.util.seasonIsAvailable
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onSearchTitle: (query: String, type: MediaType) -> Unit = { _, _ -> },
    onOpenRelated: (query: String, type: MediaType, remoteId: String) -> Unit = { _, _, _ -> },
    onOpenSequel: (titleId: Long) -> Unit = {},
    viewModel: DetailViewModel = hiltViewModel()
) {
    val title by viewModel.title.collectAsStateWithLifecycle()
    val seasons by viewModel.seasons.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val dlcs by viewModel.dlcs.collectAsStateWithLifecycle()
    val related by viewModel.related.collectAsStateWithLifecycle()
    val sequels by viewModel.sequels.collectAsStateWithLifecycle()
    val catalogHits by viewModel.catalogHits.collectAsStateWithLifecycle()
    val catalogSearchLoading by viewModel.catalogSearchLoading.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.setPoster(it) }
    }
    var editingOverview by remember { mutableStateOf(false) }
    var overviewDraft by remember { mutableStateOf("") }
    var imageMenu by remember { mutableStateOf(false) }
    var textMenu by remember { mutableStateOf(false) }
    var titleMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf("") }
    var editingPage by remember { mutableStateOf(false) }
    var pageDraft by remember { mutableStateOf("") }
    var dlcMenuId by remember { mutableStateOf<Long?>(null) }
    var confirmRemoveCollection by remember { mutableStateOf(false) }
    var catalogPicker by remember { mutableStateOf(false) }
    var catalogQuery by remember { mutableStateOf("") }
    var seasonMenuId by remember { mutableStateOf<Long?>(null) }
    var sequelMenuId by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.message.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) {
        viewModel.openUrl.collect { url ->
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.pendingShare.collect { intent ->
            context.startActivity(Intent.createChooser(intent, "Share"))
        }
    }
    LaunchedEffect(catalogPicker) {
        if (catalogPicker) {
            viewModel.searchCurrentCatalog(catalogQuery)
        } else {
            viewModel.clearCatalogHits()
        }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {},
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title?.displayName.orEmpty(),
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    val current = title
                    if (current != null) {
                        val inCollection = current.mediaType == MediaType.MOVIE.name &&
                            !current.collectionId.isNullOrBlank()
                        OverflowMenu(
                            items = buildList {
                                add(
                                    OverflowItem("Search", icon = Icons.Default.Search) {
                                        val type = runCatching { MediaType.valueOf(current.mediaType) }.getOrNull()
                                            ?: return@OverflowItem
                                        onSearchTitle(current.displayName, type)
                                    }
                                )
                                add(
                                    OverflowItem("Open in browser", icon = Icons.Outlined.Public) {
                                        val url = current.catalogUrl?.takeIf { it.isNotBlank() } ?: return@OverflowItem
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    }
                                )
                                if (current.mediaType == MediaType.GAME.name) {
                                    add(
                                        OverflowItem("Search on Steam", icon = Icons.Default.Search) {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(
                                                        Intent.ACTION_VIEW,
                                                        Uri.parse(steamSearchUrl(current.displayName))
                                                    )
                                                )
                                            }
                                        }
                                    )
                                }
                                add(
                                    OverflowItem(
                                        "Fetch Info",
                                        icon = Icons.Default.Refresh,
                                        children = listOf(
                                            OverflowItem("Reload this listing") {
                                                viewModel.refreshTitle()
                                            },
                                            OverflowItem("Get info from current catalogue") {
                                                catalogQuery = current.displayName
                                                catalogPicker = true
                                            }
                                        )
                                    )
                                )
                                add(
                                    OverflowItem(
                                        if (current.hidden) "Unhide" else "Hide",
                                        icon = if (current.hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                    ) {
                                        viewModel.setHidden(!current.hidden)
                                    }
                                )
                                add(
                                    OverflowItem("Move to Interests", icon = Icons.Outlined.Visibility) {
                                        viewModel.moveToInterests()
                                        onBack()
                                    }
                                )
                                add(
                                    OverflowItem(
                                        "Share",
                                        icon = Icons.Default.Share,
                                        children = buildList {
                                            add(OverflowItem("Share name") { viewModel.shareName() })
                                            add(OverflowItem("Share recommendation") { viewModel.shareRecommendation() })
                                            if (inCollection) {
                                                add(OverflowItem("Share collection") { viewModel.shareCollection() })
                                            }
                                        }
                                    )
                                )
                                add(
                                    OverflowItem(
                                        "Dislike",
                                        icon = Icons.Outlined.ThumbDown,
                                        onLongClick = {
                                            viewModel.banTitle()
                                            onBack()
                                        }
                                    ) {
                                        viewModel.dislikeTitle()
                                    }
                                )
                                add(
                                    OverflowItem(
                                        "Remove",
                                        icon = Icons.Outlined.Delete,
                                        expandOnArrowOnly = inCollection,
                                        children = if (inCollection) {
                                            listOf(
                                                OverflowItem("Remove") {
                                                    viewModel.delete()
                                                    onBack()
                                                },
                                                OverflowItem("Remove collection") {
                                                    confirmRemoveCollection = true
                                                }
                                            )
                                        } else {
                                            emptyList()
                                        }
                                    ) {
                                        viewModel.delete()
                                        onBack()
                                    }
                                )
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        val item = title
        if (item == null) {
            Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background))
            return@Scaffold
        }
        val tracksEpisodes = item.mediaType == MediaType.SERIES.name || item.mediaType == MediaType.ANIME.name
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        item {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row {
                        Box {
                            Poster(
                                item.posterUrl?.takeIf { it.isNotEmpty() },
                                modifier = Modifier
                                    .height(180.dp)
                                    .width(120.dp)
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = { imageMenu = true }
                                    ),
                                localPath = item.localPosterPath
                            )
                            DropdownMenu(expanded = imageMenu, onDismissRequest = { imageMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Clear") },
                                    onClick = { viewModel.clearPoster(); imageMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Replace") },
                                    onClick = { picker.launch("image/*"); imageMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Fetch Info") },
                                    onClick = { viewModel.resetPoster(); imageMenu = false }
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Box {
                                Text(
                                    item.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.combinedClickable(
                                        onClick = {},
                                        onLongClick = { titleMenu = true }
                                    )
                                )
                                DropdownMenu(expanded = titleMenu, onDismissRequest = { titleMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        onClick = {
                                            titleMenu = false
                                            renameDraft = item.displayName
                                            renaming = true
                                        }
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            CompletionBadge(item.displayCompleted(), item.displayWatching())
                            if (item.mediaType == "BOOK" || item.mediaType == "GAME") {
                                item.author?.takeIf { it.isNotBlank() }?.let { author ->
                                    Text(author, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                            displayCatalogStatus(item.status)?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
                            when (item.mediaType) {
                                MediaType.MOVIE.name, MediaType.GAME.name, MediaType.BOOK.name -> {
                                    item.firstDate?.takeIf { it.isNotBlank() }?.let { release ->
                                        Text(release, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                else -> {
                                    val dates = formatDateRange(item.firstDate, item.lastDate, item.status)
                                    if (dates != "Dates unknown") {
                                        Text(dates, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            if (item.genres.isNotBlank()) {
                                Text(item.genres, style = MaterialTheme.typography.bodySmall)
                            }
                            val watchLine = when {
                                tracksEpisodes -> formatWatchHours(item.watchedMinutes, item.totalMinutes)
                                item.mediaType == "MOVIE" && (item.runtimeMinutes > 0 || item.totalUnits > 0) -> {
                                    val releasedSequels = sequels.filter { entry ->
                                        entry.title?.notInterested != true &&
                                            movieHasReleased(
                                                entry.title?.firstDate,
                                                entry.title?.year ?: entry.link.year
                                            )
                                    }
                                    val ownerReleased = movieHasReleased(item.firstDate, item.year)
                                    val franchise = if (item.countSequelsInCompletion && sequels.isNotEmpty()) {
                                        val totalMins = (if (ownerReleased) item.runtimeMinutes else 0) +
                                            releasedSequels.sumOf { (it.title?.runtimeMinutes ?: it.link.runtimeMinutes).coerceAtLeast(0) }
                                        val watchedMins = (if (item.completed) item.runtimeMinutes else 0) +
                                            releasedSequels.filter { it.isWatched }
                                                .sumOf { (it.title?.runtimeMinutes ?: it.link.runtimeMinutes).coerceAtLeast(0) }
                                        totalMins to watchedMins
                                    } else {
                                        item.runtimeMinutes to item.watchedMinutes
                                    }
                                    val hours = if (franchise.first > 0) {
                                        formatWatchHours(franchise.second, franchise.first)
                                    } else null
                                    val releasedCount = (if (ownerReleased) 1 else 0) + releasedSequels.size
                                    val watchedCount = (if (item.completed && ownerReleased) 1 else 0) +
                                        releasedSequels.count { it.isWatched }
                                    val pct = if (item.countSequelsInCompletion && releasedCount > 1) {
                                        "${((watchedCount.toFloat() / releasedCount) * 100).roundToInt()}% complete"
                                    } else if (!item.countSequelsInCompletion && item.completed) {
                                        "100% complete"
                                    } else null
                                    listOfNotNull(hours, pct).joinToString(" · ").ifBlank { null }
                                }
                                item.mediaType == "BOOK" && item.pageCount > 0 -> {
                                    "${item.watchedUnits}/${item.pageCount} pages"
                                }
                                else -> null
                            }
                            if (watchLine != null) {
                                Text(
                                    watchLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .then(
                                            if (item.mediaType == "BOOK" && item.pageCount > 0) {
                                                Modifier.clickable {
                                                    pageDraft = item.watchedUnits.toString()
                                                    editingPage = true
                                                }
                                            } else {
                                                Modifier
                                            }
                                        )
                                )
                            }
                        }
                    }
                }
            }
            Box {
                val overview = cleanCatalogOverview(item.overview)
                Text(
                    overview ?: "No description",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (overview.isNullOrBlank()) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = { textMenu = true }
                    )
                )
                DropdownMenu(expanded = textMenu, onDismissRequest = { textMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Clear") },
                        onClick = { viewModel.setOverview(""); textMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Replace") },
                        onClick = {
                            overviewDraft = cleanCatalogOverview(item.overview).orEmpty()
                            editingOverview = true
                            textMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Fetch Info") },
                        onClick = { viewModel.resetOverview(); textMenu = false }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Text(
                "Your rating: ${
                    when {
                        item.userRating == null -> "—"
                        (item.userRating ?: 0f).roundToInt() == 0 -> "0"
                        (item.userRating ?: 0f).roundToInt() == 11 -> "11"
                        else -> (item.userRating ?: 0f).roundToInt().toString()
                    }
                }/10",
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { viewModel.setRating(null) }
                )
            )
            RatingDots(
                rating = (item.userRating ?: -1f).roundToInt().let { if (item.userRating == null) -1 else it },
                onChange = { viewModel.setRating(it.toFloat()) }
            )
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("More of this", modifier = Modifier.weight(1f))
                Switch(checked = item.moreOfThis, onCheckedChange = viewModel::setMoreOfThis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recommended", modifier = Modifier.weight(1f))
                Switch(checked = item.recommended, onCheckedChange = viewModel::setRecommended)
            }
            if (!tracksEpisodes) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (item.mediaType) {
                            "BOOK" -> "Read"
                            "GAME" -> "Completed"
                            else -> "Watched"
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = item.completed, onCheckedChange = { viewModel.toggleWatched(item) })
                }
                item.seriesName?.takeIf { item.mediaType != MediaType.GAME.name }?.let { Text("Series: $it") }
                if (item.mediaType == MediaType.MOVIE.name && sequels.isNotEmpty()) {
                    HorizontalDivider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sequels", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Switch(
                            checked = item.countSequelsInCompletion,
                            onCheckedChange = viewModel::setCountSequelsInCompletion
                        )
                    }
                    sequels.forEach { entry ->
                        val sibling = entry.title
                        val released = movieHasReleased(sibling?.firstDate, sibling?.year ?: entry.link.year)
                        val watched = entry.isWatched
                        val banned = entry.banned
                        val untracked = sibling?.notInterested == true
                        val label = when (entry.link.relation) {
                            "PREQUEL" -> "Prequel"
                            "SEQUEL" -> "Sequel"
                            else -> null
                        }
                        val alpha = if (banned || untracked) 0.4f else 1f
                        Box {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .combinedClickable(
                                        onClick = { viewModel.openSequel(entry, onOpenSequel) },
                                        onLongClick = { sequelMenuId = entry.link.relatedRemoteId }
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    Poster(
                                        sibling?.posterUrl ?: entry.link.posterUrl,
                                        modifier = Modifier.size(width = 40.dp, height = 60.dp),
                                        localPath = sibling?.localPosterPath
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            buildString {
                                                append(sibling?.displayName ?: entry.link.relatedTitle)
                                                if (untracked) append(" · Untracked")
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                                        )
                                        val rating = sibling?.userRating
                                        val meta = listOfNotNull(
                                            label,
                                            sibling?.year?.toString() ?: entry.link.year?.toString(),
                                            if (!released) "Upcoming" else null,
                                            if (banned) "Banned" else null
                                        ).joinToString(" · ")
                                        if (meta.isNotBlank()) {
                                            Text(
                                                meta,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f * alpha)
                                            )
                                        }
                                        if (rating != null) {
                                            Text(
                                                "Rated ${rating.toInt()}/10",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                    Checkbox(
                                        checked = watched,
                                        enabled = released && !banned && !untracked,
                                        onCheckedChange = { viewModel.toggleSequel(entry) }
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = sequelMenuId == entry.link.relatedRemoteId,
                                onDismissRequest = { sequelMenuId = null }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(if (untracked) "Track" else "Untrack")
                                    },
                                    onClick = {
                                        sequelMenuId = null
                                        viewModel.setSequelNotInterested(entry, !untracked)
                                    }
                                )
                            }
                        }
                    }
                }
                if (item.mediaType == "GAME" && dlcs.isNotEmpty()) {
                    HorizontalDivider()
                    val dlcTracked = dlcs.count { it.completed }
                    CollapsibleBlock(
                        key = "dlc-${item.id}",
                        modifier = Modifier.padding(bottom = 8.dp),
                        header = {
                            Text(
                                "DLC · $dlcTracked/${dlcs.size} tracked",
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        content = {
                            dlcs.forEach { dlc ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Box {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .combinedClickable(
                                                    onClick = {},
                                                    onLongClick = { dlcMenuId = dlc.id }
                                                )
                                                .padding(8.dp)
                                        ) {
                                            Poster(
                                                dlc.posterUrl,
                                                modifier = Modifier.size(width = 40.dp, height = 60.dp)
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(dlc.name, style = MaterialTheme.typography.bodyMedium)
                                                dlc.released?.takeIf { it.isNotBlank() }?.let { released ->
                                                    Text(
                                                        released,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(
                                                            alpha = 0.7f
                                                        )
                                                    )
                                                }
                                            }
                                            Checkbox(
                                                checked = dlc.completed,
                                                onCheckedChange = { viewModel.toggleDlc(dlc) }
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = dlcMenuId == dlc.id,
                                            onDismissRequest = { dlcMenuId = null }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Open in browser") },
                                                onClick = {
                                                    dlcMenuId = null
                                                    rawgGameWebUrl(dlc.remoteId)?.let { url ->
                                                        runCatching {
                                                            context.startActivity(
                                                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                            )
                                                        }
                                                    }
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Search on Steam") },
                                                onClick = {
                                                    dlcMenuId = null
                                                    runCatching {
                                                        context.startActivity(
                                                            Intent(
                                                                Intent.ACTION_VIEW,
                                                                Uri.parse(steamSearchUrl(dlc.name))
                                                            )
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            } else {
                HorizontalDivider()
                Text("Seasons", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 4.dp))
                seasons.forEach { season ->
                    val seasonEps = episodes.filter { it.seasonNumber == season.seasonNumber }
                    val available = seasonIsAvailable(season.episodeCount, season.startDate, seasonEps.size)
                    val allWatched = available && seasonEps.isNotEmpty() && seasonEps.all { it.watched }
                    CollapsibleBlock(
                        key = "season-${season.seasonNumber}",
                        modifier = Modifier.padding(bottom = 8.dp),
                        headerStartPadding = 0.dp,
                        onHeaderLongClick = { seasonMenuId = season.id },
                        leading = {
                            Checkbox(
                                checked = allWatched,
                                onCheckedChange = { viewModel.toggleSeason(season, it) },
                                enabled = available && !season.notInterested
                            )
                        },
                        header = {
                            Box {
                                Column {
                                    Text(
                                        buildString {
                                            append(
                                                if (available) "Season ${season.seasonNumber}"
                                                else "Season ${season.seasonNumber} · Upcoming"
                                            )
                                            if (season.notInterested) append(" · Untracked")
                                        },
                                        color = when {
                                            season.notInterested -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                            available -> MaterialTheme.colorScheme.onSurface
                                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                        }
                                    )
                                    Text(
                                        formatDateRange(season.startDate, season.endDate),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (available) 1f else 0.45f)
                                    )
                                }
                                DropdownMenu(
                                    expanded = seasonMenuId == season.id,
                                    onDismissRequest = { seasonMenuId = null }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(if (season.notInterested) "Track Season" else "Untrack Season")
                                        },
                                        onClick = {
                                            seasonMenuId = null
                                            viewModel.setSeasonNotInterested(season, !season.notInterested)
                                        }
                                    )
                                }
                            }
                        },
                        content = {
                        if (available) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                seasonEps.forEach { episode ->
                                    FilterChip(
                                        selected = episode.watched,
                                        onClick = { viewModel.toggleEpisode(episode) },
                                        label = {
                                            Box(
                                                Modifier.width(56.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "E${episode.episodeNumber}",
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .width(56.dp)
                                            .pointerInput(episode.id) {
                                                awaitEachGesture {
                                                    val down = awaitFirstDown(
                                                        requireUnconsumed = false,
                                                        pass = PointerEventPass.Initial
                                                    )
                                                    val longPressed = withTimeoutOrNull(
                                                        viewConfiguration.longPressTimeoutMillis
                                                    ) {
                                                        waitForUpOrCancellation(
                                                            pass = PointerEventPass.Initial
                                                        )
                                                        false
                                                    } == null
                                                    if (longPressed) {
                                                        down.consume()
                                                        viewModel.openEpisode(episode)
                                                        waitForUpOrCancellation(
                                                            pass = PointerEventPass.Initial
                                                        )
                                                    }
                                                }
                                            }
                                    )
                                }
                            }
                        }
                        }
                    )
                }
            }
        } // Column
        } // item
        } // LazyColumn
    } // Scaffold
        FicheSnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    } // Box

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setDisplayTitle(renameDraft)
                    renaming = false
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (!title?.displayTitle.isNullOrBlank()) {
                        TextButton(onClick = {
                            viewModel.setDisplayTitle(null)
                            renaming = false
                        }) { Text("Reset") }
                    }
                    TextButton(onClick = { renaming = false }) { Text("Cancel") }
                }
            }
        )
    }

    if (editingPage) {
        AlertDialog(
            onDismissRequest = { editingPage = false },
            title = { Text("Current page") },
            text = {
                OutlinedTextField(
                    value = pageDraft,
                    onValueChange = { pageDraft = it.filter { ch -> ch.isDigit() }.take(6) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setBookPage(pageDraft.toIntOrNull() ?: 0)
                    editingPage = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { editingPage = false }) { Text("Cancel") }
            }
        )
    }

    if (confirmRemoveCollection) {
        AlertDialog(
            onDismissRequest = { confirmRemoveCollection = false },
            title = { Text("Remove collection") },
            text = { Text("Remove this movie and every other title in the same collection from your library?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemoveCollection = false
                    viewModel.removeCollection()
                    onBack()
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveCollection = false }) { Text("Cancel") }
            }
        )
    }
    if (editingOverview) {
        AlertDialog(
            onDismissRequest = { editingOverview = false },
            title = { Text("Description") },
            text = {
                OutlinedTextField(
                    value = overviewDraft,
                    onValueChange = { overviewDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setOverview(overviewDraft)
                    editingOverview = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingOverview = false }) { Text("Cancel") }
            }
        )
    }
    if (catalogPicker) {
        AlertDialog(
            onDismissRequest = { catalogPicker = false },
            title = { Text("Get info from current catalogue") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = catalogQuery,
                        onValueChange = { catalogQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { viewModel.searchCurrentCatalog(catalogQuery) }
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.searchCurrentCatalog(catalogQuery) }) {
                        Text("Search")
                    }
                    when {
                        catalogSearchLoading -> {
                            Box(
                                Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        catalogHits.isEmpty() -> {
                            Text("No results", style = MaterialTheme.typography.bodySmall)
                        }
                        else -> {
                            LazyColumn(Modifier.height(280.dp)) {
                                items(catalogHits, key = { it.remoteId }) { hit ->
                                    CatalogPickRow(hit) {
                                        viewModel.retargetTo(hit)
                                        catalogPicker = false
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { catalogPicker = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CatalogPickRow(hit: RemoteTitle, onPick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Poster(hit.posterUrl, modifier = Modifier.height(54.dp).width(36.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(hit.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(
                listOfNotNull(hit.year?.toString(), displayCatalogStatus(hit.status)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RatingDots(rating: Int, onChange: (Int) -> Unit) {
    val gold = Color(0xFFFFD700)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        (1..10).forEach { step ->
            val masterpiece = rating >= 11
            val zeroRated = rating == 0
            val filled = masterpiece || (rating > 0 && step <= rating) || (zeroRated && step == 1)
            val color = when {
                masterpiece && step == 10 -> gold
                masterpiece -> MaterialTheme.colorScheme.primary
                zeroRated && step == 1 -> Color(0xFFC62828)
                rating > 0 && step <= rating -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .combinedClickable(
                        onClick = { onChange(step) },
                        onLongClick = {
                            when (step) {
                                1 -> onChange(0)
                                10 -> onChange(11)
                                else -> onChange(step)
                            }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(if (filled) 18.dp else 12.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
    }
}
