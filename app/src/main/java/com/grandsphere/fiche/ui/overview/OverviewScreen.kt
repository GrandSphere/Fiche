package com.grandsphere.fiche.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grandsphere.fiche.data.repository.OverviewStats
import com.grandsphere.fiche.domain.model.MediaType
import com.grandsphere.fiche.ui.chrome.MediaTypeTabs
import com.grandsphere.fiche.util.minutesToHoursLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    onOpenDrawer: () -> Unit,
    viewModel: OverviewViewModel = hiltViewModel()
) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val tabs by viewModel.enabledMediaTypes.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Overview") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, "Menu") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MediaTypeTabs(
                current = tab,
                enabledTypes = tabs,
                onSelect = viewModel::select
            )
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OverviewBlock(tab, stats)
            }
        }
    }
}

@Composable
private fun OverviewBlock(type: MediaType, stats: OverviewStats) {
    val total = stats.total.coerceAtLeast(0)
    fun pct(count: Int): String {
        if (total <= 0) return "0%"
        return "${((count * 100f) / total).toInt()}%"
    }
    val favouriteLabel = when (type) {
        MediaType.MOVIE -> "Favourite Movie"
        MediaType.SERIES -> "Favourite Series"
        MediaType.ANIME -> "Favourite Anime"
        MediaType.BOOK -> "Favourite Book"
        MediaType.GAME -> "Favourite Game"
    }
    val favouriteTitles = stats.favouriteTitle.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    val watchedGenresLabel = when (type) {
        MediaType.BOOK -> "Most read genres"
        MediaType.GAME -> "Most played genres"
        else -> "Most watched genres"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OverviewRow(type.label, stats.total.toString(), title = true)
            OverviewDivider()
            OverviewRow("Completed:", stats.completed.toString(), pct(stats.completed))
            OverviewRow("In Progress:", stats.inProgress.toString(), pct(stats.inProgress))
            OverviewRow("Not Started:", stats.notStarted.toString(), pct(stats.notStarted))
            OverviewDivider()
            val hasWatchOrPages = type == MediaType.BOOK || type != MediaType.GAME
            if (type == MediaType.BOOK) {
                OverviewRow("Pages read:", stats.pagesRead.toString())
            } else if (type != MediaType.GAME) {
                OverviewRow("Watch Time:", minutesToHoursLabel(stats.watchedMinutes))
            }
            if (hasWatchOrPages) OverviewDivider()
            OverviewGenreBlock("Favourite genres", stats.favouriteGenres)
            OverviewDivider()
            OverviewGenreBlock(watchedGenresLabel, stats.genresByHours)
            if (favouriteTitles.isNotEmpty()) {
                OverviewDivider()
                OverviewNamedList(favouriteLabel, favouriteTitles)
            }
        }
    }
}

@Composable
private fun OverviewDivider() {
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
}

@Composable
private fun OverviewRow(
    label: String,
    value: String,
    percent: String? = null,
    title: Boolean = false
) {
    val style = if (title) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = style, modifier = Modifier.width(168.dp))
        Text(value, style = style, modifier = Modifier.weight(1f))
        if (percent != null) {
            Text(percent, style = style, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun OverviewGenreBlock(label: String, items: List<String>) {
    Column {
        Text("$label:", style = MaterialTheme.typography.bodyLarge)
        if (items.isEmpty()) {
            OverviewRow("  —", "")
        } else {
            items.forEach { line ->
                val (name, percent) = splitGenrePercent(line)
                OverviewRow("  $name", "", percent)
            }
        }
    }
}

@Composable
private fun OverviewNamedList(label: String, items: List<String>) {
    Column {
        Text("$label:", style = MaterialTheme.typography.bodyLarge)
        items.forEach { line ->
            val (name, rating) = splitFavouriteRating(line)
            OverviewRow("  $name", "", rating)
        }
    }
}

private fun splitGenrePercent(line: String): Pair<String, String> {
    val match = Regex("""^(.*)\s+\((\d+%)\)$""").find(line.trim())
    return if (match != null) {
        match.groupValues[1] to match.groupValues[2]
    } else {
        line to ""
    }
}

private fun splitFavouriteRating(line: String): Pair<String, String> {
    val match = Regex("""^(.*)\s+(\d+/10)$""").find(line.trim())
    return if (match != null) {
        match.groupValues[1] to match.groupValues[2]
    } else {
        line to ""
    }
}
