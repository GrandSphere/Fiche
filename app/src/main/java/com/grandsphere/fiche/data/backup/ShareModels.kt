package com.grandsphere.fiche.data.backup

import com.grandsphere.fiche.data.local.entity.TitleEntity
import com.grandsphere.fiche.domain.model.MediaType
import com.grandsphere.fiche.util.remoteWebUrl

/** Minimal payload for sharing titles with friends (import via share intent). */
data class SharePayload(
    val kind: String = "share",
    val titles: List<SharedTitle> = emptyList()
)

data class SharedTitle(
    val title: String,
    val category: String,
    val author: String? = null,
    val url: String? = null,
    val firstDate: String? = null,
    val lastDate: String? = null,
    val genres: String? = null,
    val overview: String? = null
)

fun TitleEntity.toSharedTitle(): SharedTitle {
    val tracksEpisodes = mediaType == MediaType.SERIES.name || mediaType == MediaType.ANIME.name
    return SharedTitle(
        title = displayName,
        category = mediaType,
        author = author?.takeIf { it.isNotBlank() },
        url = catalogUrl?.takeIf { it.isNotBlank() } ?: remoteWebUrl(mediaType, remoteId),
        firstDate = firstDate?.takeIf { it.isNotBlank() },
        lastDate = if (tracksEpisodes) lastDate?.takeIf { it.isNotBlank() } else null,
        genres = genres.takeIf { it.isNotBlank() },
        overview = overview?.takeIf { it.isNotBlank() }
    )
}

fun TitleEntity.toAppendTitle(): AppendTitle = AppendTitle(
    title = title,
    year = year,
    mediaType = mediaType,
    remoteId = remoteId,
    url = catalogUrl?.takeIf { it.isNotBlank() } ?: remoteWebUrl(mediaType, remoteId)
)

fun formatSharedNames(titles: List<TitleEntity>): String {
    return MediaType.entries.mapNotNull { type ->
        val items = titles.filter { it.mediaType == type.name }
        if (items.isEmpty()) null
        else buildString {
            append(type.label)
            append(":\n")
            append(
                items.joinToString("\n") { title ->
                    val year = title.year?.let { " ($it)" }.orEmpty()
                    "${title.displayName}$year"
                }
            )
        }
    }.joinToString("\n")
}
