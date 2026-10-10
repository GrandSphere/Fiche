package com.grandsphere.fiche.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.grandsphere.fiche.data.catalog.CatalogProviderId
import com.grandsphere.fiche.data.local.entity.TitleEntity
import com.grandsphere.fiche.data.remote.RemoteTitle
import com.grandsphere.fiche.domain.model.MediaType
import com.grandsphere.fiche.domain.model.displayCompleted
import com.grandsphere.fiche.domain.model.displayWatching
import com.grandsphere.fiche.util.displayCatalogStatus
import com.grandsphere.fiche.util.posterThumb
import com.grandsphere.fiche.ui.theme.LocalAlternateColor
import com.grandsphere.fiche.util.remoteWebUrl
import java.io.File
import kotlin.math.max

@Composable
fun Poster(
    url: String?,
    modifier: Modifier = Modifier.size(width = 72.dp, height = 108.dp),
    localPath: String? = null
) {
    val model: Any? = if (!localPath.isNullOrBlank() && File(localPath).exists()) {
        File(localPath)
    } else {
        posterThumb(url)
    }
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

@Composable
fun CompletionBadge(completed: Boolean, watching: Boolean) {
    val label = when {
        completed -> "Completed"
        watching -> "Watching"
        else -> "Not completed"
    }
    Text(label, style = MaterialTheme.typography.labelMedium)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TitleCard(
    item: TitleEntity,
    compact: Boolean = false,
    showCompletedMark: Boolean = true,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val progress = if (item.totalUnits == 0) 0f else item.watchedUnits.toFloat() / item.totalUnits
    val scale = max(1f, LocalDensity.current.fontScale)
    val posterWidth = if (compact) 40.dp * scale else 72.dp * scale
    val posterHeight = if (compact) 40.dp * scale else 108.dp * scale
    val pad = if (compact) 6.dp else 8.dp
    val gap = if (compact) 8.dp else 12.dp
    val tracksEpisodes = item.mediaType == MediaType.SERIES.name || item.mediaType == MediaType.ANIME.name
    val tracksMovieProgress = item.mediaType == MediaType.MOVIE.name && item.totalUnits > 0
    val showsProgress = tracksEpisodes || tracksMovieProgress
    val cardModifier = Modifier.fillMaxWidth().then(
        if (onLongClick != null) {
            Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
        } else {
            Modifier.clickable(onClick = onClick)
        }
    )
    Card(
        modifier = cardModifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier
                .padding(pad)
                .height(posterHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Poster(
                item.posterUrl,
                modifier = Modifier.size(width = posterWidth, height = posterHeight),
                localPath = item.localPosterPath
            )
            Spacer(Modifier.width(gap))
            Column(Modifier.weight(1f).clipToBounds()) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        item.displayName,
                        style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (item.hidden) {
                        Text(
                            "Hidden",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
                if (!compact) {
                    if (item.mediaType == "BOOK" || item.mediaType == "GAME") {
                        item.author?.takeIf { it.isNotBlank() }?.let { author ->
                            Text(author, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    val meta = listOfNotNull(item.year?.toString(), displayCatalogStatus(item.status)).joinToString(" · ")
                    if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        CompletionBadge(item.displayCompleted(), item.displayWatching())
                    }
                }
                if (showsProgress) {
                    Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
                    SolidProgressBar(progress = progress, modifier = Modifier.fillMaxWidth())
                    if (!compact) {
                        Spacer(Modifier.height(4.dp))
                        val progressLabel = if (tracksMovieProgress) {
                            "${item.watchedUnits}/${item.totalUnits} films"
                        } else {
                            "${item.watchedUnits}/${item.totalUnits} episodes"
                        }
                        Text(progressLabel, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (!compact) {
                    item.userRating?.let { Text("Rated ${it.toInt()}/10", style = MaterialTheme.typography.labelSmall) }
                }
            }
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() }
                )
            } else if (showCompletedMark) {
                Icon(
                    imageVector = if (item.displayCompleted()) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (item.displayCompleted()) "Completed" else "Not completed",
                    tint = if (item.displayCompleted()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(if (compact) 18.dp else 28.dp)
                )
            }
        }
    }
}

@Composable
fun SolidProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val fraction = progress.coerceIn(0f, 1f)
    Box(
        modifier
            .height(4.8.dp)
            .clip(RoundedCornerShape(50))
            .background(LocalAlternateColor.current)
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RemoteResultRow(
    item: RemoteTitle,
    inLibrary: Boolean,
    inWatchlist: Boolean,
    banned: Boolean,
    disliked: Boolean = false,
    completed: Boolean = false,
    onAdd: () -> Unit,
    onWatchlist: () -> Unit,
    onWatched: () -> Unit,
    onDislike: () -> Unit,
    onBan: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    catalogProvider: CatalogProviderId? = null,
    onLongClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val url = remoteWebUrl(item.mediaType, item.remoteId, catalogProvider)
    val inactive = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val active = Color.White
    val cardModifier = Modifier.fillMaxWidth().then(
        if (onLongClick != null) {
            Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
        } else if (selectionMode) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        }
    )
    Card(
        cardModifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Poster(item.posterUrl)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                item.author?.takeIf { it.isNotBlank() }?.let { author ->
                    Text(author, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(listOfNotNull(item.year?.toString(), displayCatalogStatus(item.status)).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                Text(item.genres.take(3).joinToString(", "), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                if (!selectionMode) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ResultAction(
                            icon = Icons.Outlined.Add,
                            label = "Add",
                            tint = if (inLibrary) active else inactive,
                            onClick = onAdd
                        )
                        ResultAction(
                            icon = Icons.Outlined.Check,
                            label = "Watched",
                            tint = if (completed) active else inactive,
                            onClick = onWatched
                        )
                        ResultAction(
                            icon = Icons.Outlined.ThumbDown,
                            label = "Dislike",
                            tint = if (disliked || banned) active else inactive,
                            onClick = onDislike,
                            onLongClick = onBan
                        )
                        ResultAction(
                            icon = Icons.Outlined.Visibility,
                            label = "Interests",
                            tint = if (inWatchlist) active else inactive,
                            onClick = onWatchlist
                        )
                        if (url != null) {
                            ResultAction(
                                icon = Icons.Outlined.Public,
                                label = "Open",
                                tint = inactive,
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                }
                            )
                        }
                    }
                }
            }
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp), tint = tint)
    }
}
