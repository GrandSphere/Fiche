package com.grandsphere.fiche.util

import android.os.Build
import android.text.Html
import com.grandsphere.fiche.data.catalog.CatalogProviderId
import com.grandsphere.fiche.data.remote.RemoteTitle
import com.grandsphere.fiche.domain.model.MediaType

fun stripHtml(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val parsed = if (Build.VERSION.SDK_INT >= 24) {
        Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY)
    } else {
        @Suppress("DEPRECATION")
        Html.fromHtml(raw)
    }
    return parsed.toString().trim()
}

fun yearFromDate(date: String?): Int? = date?.take(4)?.toIntOrNull()

fun csv(items: List<String>): String = items.filter { it.isNotBlank() }.joinToString(", ")

fun splitCsv(value: String): List<String> =
    value.split(",").map { it.trim() }.filter { it.isNotBlank() }

fun normalizeGenre(genre: String): String {
    val lower = genre.trim().lowercase()
    return when {
        lower in setOf("sci-fi", "science fiction", "scifi") -> "science-fiction"
        lower.contains("action") && lower.contains("adventure") -> "action"
        else -> lower
            .replace("&", "and")
            .replace(Regex("""[^a-z0-9]+"""), "-")
            .trim('-')
    }
}

fun genresMatchAll(itemGenres: List<String>, wanted: Set<String>): Boolean {
    if (wanted.isEmpty()) return true
    val normalized = itemGenres.map { normalizeGenre(it) }.toSet()
    return wanted.all { w ->
        val target = normalizeGenre(w)
        normalized.any { item ->
            item == target || item.contains(target) || target.contains(item)
        }
    }
}

fun genresMatchAny(itemGenres: List<String>, unwanted: Set<String>): Boolean {
    if (unwanted.isEmpty()) return false
    val normalized = itemGenres.map { normalizeGenre(it) }.toSet()
    return unwanted.any { w ->
        val target = normalizeGenre(w)
        normalized.any { item ->
            item == target || item.contains(target) || target.contains(item)
        }
    }
}

fun titleSearchVariants(name: String): List<String> {
    val base = name.trim()
    val stripped = base.replace(Regex("""\s*\([^)]*\)"""), "").trim()
    val noThe = stripped.removePrefix("The ").removePrefix("the ").trim()
    return listOf(base, stripped, noThe).distinct().filter { it.isNotBlank() }
}

fun MediaType.storageName(): String = name

fun catalogStatusKey(status: String?): String =
    status?.trim()?.lowercase()?.replace(' ', '_')?.replace('-', '_').orEmpty()

fun isCancelledStatus(status: String?): Boolean =
    catalogStatusKey(status).contains("cancel")

fun isEndedStatus(status: String?): Boolean {
    val key = catalogStatusKey(status)
    return key in setOf("ended", "finished_airing", "finished") || isCancelledStatus(status)
}

fun isRunningStatus(status: String?): Boolean {
    val key = catalogStatusKey(status)
    return key in setOf(
        "running",
        "currently_airing",
        "currently_publishing",
        "in_development",
        "in_production"
    )
}

fun displayCatalogStatus(status: String?): String? {
    val raw = status?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return when (catalogStatusKey(raw)) {
        "finished_airing", "finished" -> "Ended"
        "currently_airing", "currently_publishing" -> "Running"
        "not_yet_aired", "not_yet_published" -> "Not yet aired"
        "in_development" -> "In Development"
        else -> raw.replace('_', ' ')
    }
}

fun cleanCatalogOverview(raw: String?): String? {
    if (raw.isNullOrBlank()) return raw
    var text = stripHtml(raw)
    text = text.replace(Regex("""\[Written by MAL Rewrite]""", RegexOption.IGNORE_CASE), "")
    text = text.replace(Regex("""\(Written by MAL Rewrite\)""", RegexOption.IGNORE_CASE), "")
    return text.replace(Regex("""\n{3,}"""), "\n\n").trim().ifBlank { null }
}

fun formatDateRange(start: String?, end: String?, status: String? = null): String {
    val running = isRunningStatus(status)
    val ended = isEndedStatus(status)
    val ongoing = !ended && (running || end.isNullOrBlank())
    return when {
        start.isNullOrBlank() && end.isNullOrBlank() -> "Dates unknown"
        ongoing -> if (start.isNullOrBlank()) "present" else "$start - present"
        start.isNullOrBlank() -> end.orEmpty()
        else -> "$start - $end"
    }
}

fun minutesToHoursLabel(minutes: Int): String {
    val safe = minutes.coerceAtLeast(0)
    val hours = safe / 60
    val mins = safe % 60
    return when {
        hours == 0 -> "${mins}m"
        mins == 0 -> "${hours}h"
        else -> "${hours}h ${mins}m"
    }
}

fun formatHoursCompact(minutes: Int): String {
    val hours = minutes.coerceAtLeast(0) / 60.0
    val rounded = kotlin.math.round(hours)
    return if (kotlin.math.abs(hours - rounded) < 0.05) {
        rounded.toInt().toString()
    } else {
        String.format(java.util.Locale.US, "%.1f", hours)
    }
}

fun formatWatchHours(watchedMinutes: Int, totalMinutes: Int): String =
    "${formatHoursCompact(watchedMinutes)}/${formatHoursCompact(totalMinutes)}h"

fun todayIsoDate(): String = java.time.LocalDate.now().toString()

fun seasonHasStarted(startDate: String?): Boolean {
    val start = startDate?.take(10).orEmpty()
    return start.isBlank() || start <= todayIsoDate()
}

fun movieHasReleased(firstDate: String?, year: Int? = null): Boolean {
    val date = firstDate?.take(10).orEmpty()
    if (date.isNotBlank()) return date <= todayIsoDate()
    if (year != null) return year < java.time.LocalDate.now().year
    return false
}

fun seasonIsAvailable(episodeCount: Int, startDate: String?, knownEpisodes: Int): Boolean {
    if (knownEpisodes == 0 || episodeCount == 0) return false
    return seasonHasStarted(startDate)
}

fun rawgGameWebUrl(remoteId: String): String? {
    val id = remoteId.trim().takeIf { it.isNotBlank() } ?: return null
    return "https://rawg.io/games/$id"
}

fun steamSearchUrl(query: String): String {
    val encoded = java.net.URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
    return "https://store.steampowered.com/search/?term=$encoded"
}

fun defaultCatalogProvider(mediaType: String): CatalogProviderId = when (mediaType) {
    MediaType.MOVIE.name -> CatalogProviderId.TMDB
    MediaType.GAME.name -> CatalogProviderId.RAWG
    MediaType.BOOK.name -> CatalogProviderId.OPEN_LIBRARY
    else -> CatalogProviderId.TVMAZE
}

/** MAL and Jikan share MyAnimeList ids and listing URLs. */
fun catalogListingFamily(id: CatalogProviderId): CatalogProviderId =
    if (id == CatalogProviderId.JIKAN) CatalogProviderId.MAL else id

fun catalogProviderFromUrl(url: String?): CatalogProviderId? {
    val lower = url?.lowercase()?.trim().orEmpty()
    if (lower.isBlank()) return null
    return when {
        "tvmaze.com" in lower -> CatalogProviderId.TVMAZE
        "themoviedb.org" in lower || "tmdb.org" in lower -> CatalogProviderId.TMDB
        "myanimelist.net" in lower -> CatalogProviderId.MAL
        "rawg.io" in lower -> CatalogProviderId.RAWG
        "openlibrary.org" in lower -> CatalogProviderId.OPEN_LIBRARY
        else -> null
    }
}

fun listingBelongsToProvider(catalogUrl: String?, mediaType: String, provider: CatalogProviderId): Boolean {
    val stored = catalogProviderFromUrl(catalogUrl) ?: defaultCatalogProvider(mediaType)
    return catalogListingFamily(stored) == catalogListingFamily(provider)
}

fun remoteWebUrl(
    mediaType: String,
    remoteId: String,
    provider: CatalogProviderId? = null
): String? {
    val id = remoteId.trim().takeIf { it.isNotBlank() } ?: return null
    return when (mediaType) {
        MediaType.MOVIE.name -> "https://www.themoviedb.org/movie/$id"
        MediaType.BOOK.name -> {
            val path = when {
                id.startsWith("/") -> id
                id.startsWith("OL") -> "/works/$id"
                else -> "/works/$id"
            }
            "https://openlibrary.org$path"
        }
        MediaType.GAME.name -> "https://rawg.io/games/$id"
        MediaType.SERIES.name, MediaType.ANIME.name -> {
            val p = provider ?: defaultCatalogProvider(mediaType)
            when (catalogListingFamily(p)) {
                CatalogProviderId.TMDB -> "https://www.themoviedb.org/tv/$id"
                CatalogProviderId.MAL -> "https://myanimelist.net/anime/$id"
                else -> "https://www.tvmaze.com/shows/$id"
            }
        }
        else -> null
    }
}

fun normalizedTitleKey(name: String): String =
    name.lowercase().replace(Regex("""[^a-z0-9]"""), "")

fun titleSimilarity(query: String, candidate: String): Int {
    val normalizedQuery = normalizedTitleKey(query)
    val normalizedCandidate = normalizedTitleKey(candidate)
    if (normalizedQuery.isBlank() || normalizedCandidate.isBlank()) return 0
    return when {
        normalizedQuery == normalizedCandidate -> 100
        normalizedCandidate.contains(normalizedQuery) || normalizedQuery.contains(normalizedCandidate) -> 60
        else -> 10
    }
}

fun catalogMatchScore(localTitle: String, localYear: Int?, hit: RemoteTitle): Int {
    var score = titleSimilarity(localTitle, hit.title)
    val hitYear = hit.year
    if (localYear != null && hitYear != null) {
        val delta = kotlin.math.abs(localYear - hitYear)
        score += when {
            delta == 0 -> 30
            delta == 1 -> 15
            else -> -50
        }
    }
    return score
}

fun isStrongCatalogMatch(bestScore: Int, secondScore: Int?, yearKnown: Boolean): Boolean {
    val minimum = if (yearKnown) 85 else 100
    if (bestScore < minimum) return false
    if (secondScore != null && bestScore - secondScore < 16) return false
    return true
}

fun unitsMismatch(localUnits: Int, catalogUnits: Int): Boolean {
    if (localUnits <= 0 || catalogUnits <= 0) return false
    val delta = kotlin.math.abs(localUnits - catalogUnits)
    val larger = maxOf(localUnits, catalogUnits)
    return delta > 2 && delta.toFloat() / larger > 0.2f
}
