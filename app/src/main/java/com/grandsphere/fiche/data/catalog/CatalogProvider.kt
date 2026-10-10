package com.grandsphere.fiche.data.catalog

import com.grandsphere.fiche.data.remote.DiscoverPage
import com.grandsphere.fiche.data.remote.RemoteTitle
import com.grandsphere.fiche.data.remote.openlibrary.OpenLibraryDoc
import com.grandsphere.fiche.data.remote.openlibrary.openLibraryCover
import com.grandsphere.fiche.data.remote.openlibrary.workIdFromKey
import com.grandsphere.fiche.data.remote.rawg.RawgGame
import com.grandsphere.fiche.data.remote.rawg.RawgGameDetail
import com.grandsphere.fiche.data.remote.tmdb.TmdbMovie
import com.grandsphere.fiche.data.remote.tmdb.TmdbTvDetail
import com.grandsphere.fiche.data.remote.tmdb.TmdbTvShow
import com.grandsphere.fiche.data.remote.tmdb.tmdbPoster
import com.grandsphere.fiche.data.local.entity.LibraryPrefsEntity
import com.grandsphere.fiche.data.remote.tvmaze.TvMazeShow
import com.grandsphere.fiche.domain.model.DiscoverFilter
import com.grandsphere.fiche.domain.model.MediaType
import com.grandsphere.fiche.util.genresMatchAll
import com.grandsphere.fiche.util.genresMatchAny
import com.grandsphere.fiche.util.isCancelledStatus
import com.grandsphere.fiche.util.isEndedStatus
import com.grandsphere.fiche.util.stripHtml
import com.grandsphere.fiche.util.yearFromDate

/** Identifiers persisted in library_prefs.*Api columns. */
enum class CatalogProviderId {
    TVMAZE,
    TMDB,
    JIKAN,
    MAL,
    RAWG,
    OPEN_LIBRARY;

    companion object {
        fun fromRaw(raw: String?, default: CatalogProviderId): CatalogProviderId =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: default
    }
}

fun LibraryPrefsEntity.catalogProvider(type: MediaType): CatalogProviderId = when (type) {
    MediaType.SERIES -> CatalogProviderId.fromRaw(seriesApi, CatalogProviderId.TVMAZE)
    MediaType.MOVIE -> CatalogProviderId.fromRaw(moviesApi, CatalogProviderId.TMDB)
    MediaType.ANIME -> CatalogProviderId.fromRaw(animeApi, CatalogProviderId.TVMAZE)
    MediaType.GAME -> CatalogProviderId.fromRaw(gamesApi, CatalogProviderId.RAWG)
    MediaType.BOOK -> CatalogProviderId.fromRaw(booksApi, CatalogProviderId.OPEN_LIBRARY)
}

interface MediaCatalogProvider {
    val id: CatalogProviderId
    suspend fun search(query: String): List<RemoteTitle>
    suspend fun details(remoteId: String): RemoteTitle
}

internal fun paginateClient(all: List<RemoteTitle>, page: Int, pageSize: Int): DiscoverPage {
    val start = page * pageSize
    val slice = all.drop(start).take(pageSize)
    return DiscoverPage(items = slice, hasMore = start + pageSize < all.size)
}

internal fun RemoteTitle.matchesDiscover(filter: DiscoverFilter): Boolean {
    val yearOk = year?.let { y ->
        (filter.yearFrom == null || y >= filter.yearFrom) &&
            (filter.yearTo == null || y <= filter.yearTo)
    } ?: (filter.yearFrom == null && filter.yearTo == null)
    val genreOk = genresMatchAll(genres, filter.genres)
    val disallowOk = !genresMatchAny(genres, filter.disallowGenres)
    val queryOk = filter.query.isBlank() || title.contains(filter.query, true) ||
        author.orEmpty().contains(filter.query, true)
    val endedOk = !filter.endedOnly || isEndedStatus(status)
    return yearOk && genreOk && disallowOk && queryOk && endedOk
}

internal fun franchiseKey(title: String): String {
    val colon = title.substringBefore(":").trim()
    if (colon != title && colon.length >= 3) return colon
    val dash = title.substringBefore(" - ").trim()
    if (dash != title && dash.length >= 3) return dash
    return title.trim()
}

internal fun inferBookSeries(title: String?, subjects: List<String>?): String? {
    val fromSubject = subjects.orEmpty().firstOrNull {
        it.contains("series", true) && !it.equals("series", true)
    }
    return fromSubject ?: title?.substringBefore(":")?.takeIf { it != title }
}

internal fun TvMazeShow.isAnime(): Boolean =
    genres.orEmpty().any { it.equals("Anime", ignoreCase = true) }

internal fun TvMazeShow.toRemoteTitle(type: MediaType = MediaType.SERIES) = RemoteTitle(
    mediaType = type.name,
    remoteId = id.toString(),
    title = name,
    year = yearFromDate(premiered),
    posterUrl = image?.original ?: image?.medium,
    overview = stripHtml(summary),
    status = status,
    cancelled = isCancelledStatus(status),
    genres = genres.orEmpty(),
    firstDate = premiered,
    lastDate = ended,
    imdbId = externals?.imdb,
    runtimeMinutes = averageRuntime ?: runtime ?: 45
)

internal fun TmdbMovie.toRemoteTitle(genreMap: Map<Int, String> = emptyMap()) = RemoteTitle(
    mediaType = MediaType.MOVIE.name,
    remoteId = id.toString(),
    title = title.orEmpty(),
    year = yearFromDate(release_date),
    posterUrl = tmdbPoster(poster_path),
    overview = overview,
    genres = genre_ids.orEmpty().mapNotNull { id -> genreMap[id] },
    firstDate = release_date,
    lastDate = release_date
)

internal fun TmdbTvShow.toRemoteTitle(
    type: MediaType,
    genreMap: Map<Int, String> = emptyMap()
) = RemoteTitle(
    mediaType = type.name,
    remoteId = id.toString(),
    title = name.orEmpty(),
    year = yearFromDate(first_air_date),
    posterUrl = tmdbPoster(poster_path),
    overview = overview,
    genres = genre_ids.orEmpty().mapNotNull { id -> genreMap[id] },
    firstDate = first_air_date,
    lastDate = first_air_date
)

internal fun TmdbTvDetail.toRemoteTitle(type: MediaType) = RemoteTitle(
    mediaType = type.name,
    remoteId = id.toString(),
    title = name.orEmpty(),
    year = yearFromDate(first_air_date),
    posterUrl = tmdbPoster(poster_path),
    overview = overview,
    status = status,
    cancelled = isCancelledStatus(status),
    genres = genres.orEmpty().map { it.name },
    firstDate = first_air_date,
    lastDate = last_air_date,
    imdbId = external_ids?.imdb_id,
    runtimeMinutes = episode_run_time?.firstOrNull()?.takeIf { it > 0 } ?: 45,
    unitHint = seasons.orEmpty()
        .filter { (it.season_number ?: 0) > 0 }
        .sumOf { it.episode_count ?: 0 }
)

internal fun RawgGame.toRemoteTitle() = RemoteTitle(
    mediaType = MediaType.GAME.name,
    remoteId = slug?.ifBlank { null } ?: id.toString(),
    title = name.orEmpty(),
    year = yearFromDate(released),
    posterUrl = background_image,
    genres = genres.orEmpty().mapNotNull { it.name },
    firstDate = released,
    lastDate = released
)

internal fun RawgGameDetail.toRemoteTitle() = RemoteTitle(
    mediaType = MediaType.GAME.name,
    remoteId = slug?.ifBlank { null } ?: id.toString(),
    title = name.orEmpty(),
    year = yearFromDate(released),
    posterUrl = background_image,
    overview = description_raw,
    author = developers.orEmpty().mapNotNull { it.name }.joinToString(", ").ifBlank { null },
    genres = genres.orEmpty().mapNotNull { it.name },
    firstDate = released,
    lastDate = released
)

internal fun OpenLibraryDoc.toRemoteTitle(genreLimit: Int = 8) = RemoteTitle(
    mediaType = MediaType.BOOK.name,
    remoteId = workIdFromKey(key),
    title = title.orEmpty(),
    year = first_publish_year,
    posterUrl = openLibraryCover(cover_i),
    author = author_name?.joinToString(", "),
    genres = (subject ?: emptyList()).take(genreLimit),
    seriesName = inferBookSeries(title, subject),
    pageCount = number_of_pages_median ?: 0,
    unitHint = number_of_pages_median ?: 0
)

/** TMDB Animation genre id — used to filter anime searches/discovers. */
internal const val TMDB_ANIMATION_GENRE_ID = 16
