package com.grandsphere.fiche.data.catalog.series

import com.grandsphere.fiche.data.catalog.CatalogProviderId
import com.grandsphere.fiche.data.catalog.MediaCatalogProvider
import com.grandsphere.fiche.data.catalog.TMDB_ANIMATION_GENRE_ID
import com.grandsphere.fiche.data.catalog.franchiseKey
import com.grandsphere.fiche.data.catalog.isAnime
import com.grandsphere.fiche.data.catalog.matchesDiscover
import com.grandsphere.fiche.data.catalog.paginateClient
import com.grandsphere.fiche.data.catalog.toRemoteTitle
import com.grandsphere.fiche.data.prefs.ApiKeyRepository
import com.grandsphere.fiche.data.prefs.UserPreferencesRepository
import com.grandsphere.fiche.data.remote.DiscoverPage
import com.grandsphere.fiche.data.remote.RemoteEpisode
import com.grandsphere.fiche.data.remote.RemoteRelated
import com.grandsphere.fiche.data.remote.RemoteSeason
import com.grandsphere.fiche.data.remote.RemoteTitle
import com.grandsphere.fiche.data.remote.tmdb.TmdbApi
import com.grandsphere.fiche.data.remote.tvmaze.TvMazeApi
import com.grandsphere.fiche.domain.model.DiscoverFilter
import com.grandsphere.fiche.domain.model.MediaType
import com.grandsphere.fiche.domain.model.RelationType
import com.grandsphere.fiche.util.isCancelledStatus
import com.grandsphere.fiche.util.yearFromDate
import kotlinx.coroutines.delay

interface SeriesCatalog : MediaCatalogProvider {
    suspend fun seasonsAndEpisodes(
        remoteId: String,
        fallbackRuntime: Int = 45
    ): Pair<List<RemoteSeason>, List<RemoteEpisode>>

    suspend fun discover(
        filter: DiscoverFilter,
        page: Int = 0,
        pageSize: Int = 20,
        tvScanCursor: Int = 0
    ): DiscoverPage

    suspend fun related(title: RemoteTitle): List<RemoteRelated>
}

class TvMazeSeriesCatalog(
    private val tvMaze: TvMazeApi,
    private val tmdb: TmdbApi,
    private val prefs: UserPreferencesRepository,
    private val apiKeys: ApiKeyRepository
) : SeriesCatalog {
    override val id: CatalogProviderId = CatalogProviderId.TVMAZE

    override suspend fun search(query: String): List<RemoteTitle> {
        if (query.isBlank()) return emptyList()
        return tvMaze.search(query).map { it.show }.filter { !it.isAnime() }
            .map { it.toRemoteTitle(MediaType.SERIES) }
    }

    override suspend fun details(remoteId: String): RemoteTitle {
        val show = tvMaze.show(remoteId).toRemoteTitle(MediaType.SERIES)
        return enrichCancelledFromTmdb(show)
    }

    override suspend fun seasonsAndEpisodes(
        remoteId: String,
        fallbackRuntime: Int
    ): Pair<List<RemoteSeason>, List<RemoteEpisode>> =
        tvMazeSeasonsAndEpisodes(tvMaze, remoteId, fallbackRuntime)

    override suspend fun discover(
        filter: DiscoverFilter,
        page: Int,
        pageSize: Int,
        tvScanCursor: Int
    ): DiscoverPage = discoverTvMaze(
        tvMaze = tvMaze,
        filter = filter,
        anime = false,
        page = page,
        pageSize = pageSize,
        tvScanCursor = tvScanCursor,
        searchFn = { search(it) }
    )

    override suspend fun related(title: RemoteTitle): List<RemoteRelated> =
        tvMazeFranchiseRelated(tvMaze, MediaType.SERIES, title)

    private suspend fun enrichCancelledFromTmdb(show: RemoteTitle): RemoteTitle {
        val key = apiKeys.get("TMDB").ifBlank { null } ?: return show
        val imdb = show.imdbId ?: return show.copy(cancelled = isCancelledStatus(show.status))
        val found = runCatching { tmdb.find(imdb, key) }.getOrNull()?.tv_results?.firstOrNull() ?: return show
        val tv = runCatching { tmdb.tv(found.id.toString(), key) }.getOrNull() ?: return show
        return show.copy(
            status = tv.status ?: show.status,
            cancelled = isCancelledStatus(tv.status)
        )
    }
}

class TmdbSeriesCatalog(
    private val tmdb: TmdbApi,
    private val apiKeys: ApiKeyRepository
) : SeriesCatalog {
    override val id: CatalogProviderId = CatalogProviderId.TMDB
    private var tvGenreMap: Map<Int, String> = emptyMap()

    override suspend fun search(query: String): List<RemoteTitle> {
        if (query.isBlank()) return emptyList()
        val key = requireTmdbKey()
        ensureTvGenres(key)
        return tmdb.searchTv(key, query).results
            .filter { show ->
                val genres = show.genre_ids.orEmpty()
                TMDB_ANIMATION_GENRE_ID !in genres ||
                    show.origin_country.orEmpty().none { it.equals("JP", true) }
            }
            .map { it.toRemoteTitle(MediaType.SERIES, tvGenreMap) }
    }

    override suspend fun details(remoteId: String): RemoteTitle {
        val key = requireTmdbKey()
        return tmdb.tv(remoteId, key).toRemoteTitle(MediaType.SERIES)
    }

    override suspend fun seasonsAndEpisodes(
        remoteId: String,
        fallbackRuntime: Int
    ): Pair<List<RemoteSeason>, List<RemoteEpisode>> {
        val key = requireTmdbKey()
        return tmdbSeasonsAndEpisodes(tmdb, key, remoteId, fallbackRuntime)
    }

    override suspend fun discover(
        filter: DiscoverFilter,
        page: Int,
        pageSize: Int,
        tvScanCursor: Int
    ): DiscoverPage {
        val key = requireTmdbKey()
        ensureTvGenres(key)
        if (filter.query.isNotBlank()) {
            val all = search(filter.query).filter { it.matchesDiscover(filter) }
            return paginateClient(all, page, pageSize)
        }
        val from = filter.yearFrom?.let { "$it-01-01" }
        val to = filter.yearTo?.let { "$it-12-31" }
        val status = if (filter.endedOnly) "3|4|5" else null
        val response = tmdb.discoverTv(
            apiKey = key,
            genres = null,
            from = from,
            to = to,
            originCountry = null,
            status = status,
            page = page + 1
        )
        val items = response.results
            .map { it.toRemoteTitle(MediaType.SERIES, tvGenreMap) }
            .filter { it.matchesDiscover(filter) }
        return DiscoverPage(items = items, hasMore = response.page < response.total_pages)
    }

    override suspend fun related(title: RemoteTitle): List<RemoteRelated> {
        val key = requireTmdbKey()
        val franchise = franchiseKey(title.title)
        if (franchise.isBlank()) return emptyList()
        ensureTvGenres(key)
        val ownerYear = title.year ?: 0
        return tmdb.searchTv(key, franchise).results
            .filter { it.id.toString() != title.remoteId }
            .map { it.toRemoteTitle(MediaType.SERIES, tvGenreMap) }
            .filter {
                val showKey = franchiseKey(it.title)
                it.title.startsWith(franchise, ignoreCase = true) ||
                    showKey.equals(franchise, ignoreCase = true) ||
                    franchise.startsWith(showKey, ignoreCase = true)
            }
            .map { hit ->
                val relation = if (hit.year != null && ownerYear > 0 && hit.year < ownerYear) {
                    RelationType.PREQUEL.name
                } else {
                    RelationType.SPIN_OFF.name
                }
                RemoteRelated(
                    remoteId = hit.remoteId,
                    mediaType = MediaType.SERIES.name,
                    title = hit.title,
                    year = hit.year,
                    posterUrl = hit.posterUrl,
                    relation = relation
                )
            }
    }

    private suspend fun ensureTvGenres(key: String) {
        if (tvGenreMap.isNotEmpty()) return
        tvGenreMap = runCatching { tmdb.tvGenres(key).genres.associate { it.id to it.name } }
            .getOrDefault(emptyMap())
    }

    private suspend fun requireTmdbKey(): String =
        apiKeys.require("TMDB", "TMDB")
}

internal suspend fun tvMazeSeasonsAndEpisodes(
    tvMaze: TvMazeApi,
    remoteId: String,
    fallbackRuntime: Int
): Pair<List<RemoteSeason>, List<RemoteEpisode>> {
    val fallback = fallbackRuntime.takeIf { it > 0 } ?: 45
    val seasons = tvMaze.seasons(remoteId)
        .filter { (it.number ?: 0) > 0 }
        .map {
            RemoteSeason(
                seasonNumber = it.number ?: 0,
                episodeCount = it.episodeOrder ?: 0,
                startDate = it.premiereDate,
                endDate = it.endDate
            )
        }
    val episodes = tvMaze.episodes(remoteId)
        .filter { (it.season ?: 0) > 0 && (it.number ?: 0) > 0 }
        .map {
            RemoteEpisode(
                seasonNumber = it.season ?: 0,
                episodeNumber = it.number ?: 0,
                airDate = it.airdate,
                runtimeMinutes = it.runtime?.takeIf { mins -> mins > 0 } ?: fallback
            )
        }
    val mergedSeasons = seasons.map { season ->
        val count = episodes.count { it.seasonNumber == season.seasonNumber }
        val dates = episodes.filter { it.seasonNumber == season.seasonNumber }.mapNotNull { it.airDate }
        season.copy(
            episodeCount = if (count > 0) count else season.episodeCount,
            startDate = season.startDate ?: dates.minOrNull(),
            endDate = season.endDate ?: dates.maxOrNull()
        )
    }
    return mergedSeasons to episodes
}

internal suspend fun tmdbSeasonsAndEpisodes(
    tmdb: TmdbApi,
    apiKey: String,
    remoteId: String,
    fallbackRuntime: Int
): Pair<List<RemoteSeason>, List<RemoteEpisode>> {
    val fallback = fallbackRuntime.takeIf { it > 0 } ?: 45
    val detail = tmdb.tv(remoteId, apiKey)
    val seasonNumbers = detail.seasons.orEmpty()
        .mapNotNull { it.season_number }
        .filter { it > 0 }
        .distinct()
        .sorted()
    val episodes = mutableListOf<RemoteEpisode>()
    val seasons = mutableListOf<RemoteSeason>()
    for (number in seasonNumbers) {
        val season = runCatching { tmdb.tvSeason(remoteId, number, apiKey) }.getOrNull()
        val eps = season?.episodes.orEmpty()
            .mapNotNull { ep ->
                val epNum = ep.episode_number?.takeIf { it > 0 } ?: return@mapNotNull null
                RemoteEpisode(
                    seasonNumber = number,
                    episodeNumber = epNum,
                    airDate = ep.air_date,
                    runtimeMinutes = ep.runtime?.takeIf { it > 0 } ?: fallback
                )
            }
        episodes += eps
        val dates = eps.mapNotNull { it.airDate }
        seasons += RemoteSeason(
            seasonNumber = number,
            episodeCount = eps.size,
            startDate = dates.minOrNull(),
            endDate = dates.maxOrNull()
        )
    }
    return seasons to episodes
}

internal suspend fun tvMazeFranchiseRelated(
    tvMaze: TvMazeApi,
    type: MediaType,
    title: RemoteTitle
): List<RemoteRelated> {
    val key = franchiseKey(title.title)
    if (key.isBlank()) return emptyList()
    val hits = runCatching { tvMaze.search(key) }.getOrDefault(emptyList())
    val ownerYear = title.year ?: 0
    return hits.map { it.show }
        .filter { it.id.toString() != title.remoteId }
        .filter { show ->
            val name = show.name
            val showKey = franchiseKey(name)
            name.startsWith(key, ignoreCase = true) ||
                showKey.equals(key, ignoreCase = true) ||
                key.startsWith(showKey, ignoreCase = true)
        }
        .map { show ->
            val year = yearFromDate(show.premiered)
            val relation = if (year != null && ownerYear > 0 && year < ownerYear) {
                RelationType.PREQUEL.name
            } else {
                RelationType.SPIN_OFF.name
            }
            RemoteRelated(
                remoteId = show.id.toString(),
                mediaType = type.name,
                title = show.name,
                year = year,
                posterUrl = show.image?.medium ?: show.image?.original,
                relation = relation
            )
        }
}

internal suspend fun discoverTvMaze(
    tvMaze: TvMazeApi,
    filter: DiscoverFilter,
    anime: Boolean,
    page: Int,
    pageSize: Int,
    tvScanCursor: Int,
    searchFn: suspend (String) -> List<RemoteTitle>
): DiscoverPage {
    val type = if (anime) MediaType.ANIME else MediaType.SERIES
    val personQuery = filter.producer.ifBlank { filter.actor }
    if (personQuery.isNotBlank()) {
        val person = runCatching { tvMaze.searchPeople(personQuery) }.getOrNull()?.firstOrNull()?.person
            ?: return DiscoverPage(emptyList(), hasMore = false)
        val credits = if (filter.producer.isNotBlank()) {
            runCatching { tvMaze.personCrew(person.id.toString()) }.getOrDefault(emptyList())
        } else {
            runCatching { tvMaze.personCast(person.id.toString()) }.getOrDefault(emptyList())
        }
        val all = credits.mapNotNull { it._embedded?.show }
            .filter { it.isAnime() == anime }
            .map { it.toRemoteTitle(type) }
            .distinctBy { it.remoteId }
            .filter { it.matchesDiscover(filter) }
        return paginateClient(all, page, pageSize)
    }
    if (filter.newReleases) {
        val all = tvMaze.schedule().mapNotNull { it.show }
            .filter { it.isAnime() == anime }
            .map { it.toRemoteTitle(type) }
            .filter { it.matchesDiscover(filter) }
        return paginateClient(all, page, pageSize)
    }
    if (filter.query.isNotBlank()) {
        val all = searchFn(filter.query).filter { it.matchesDiscover(filter) }
        return paginateClient(all, page, pageSize)
    }
    return scanTvMaze(tvMaze, filter, anime, type, pageSize, tvScanCursor)
}

private suspend fun scanTvMaze(
    tvMaze: TvMazeApi,
    filter: DiscoverFilter,
    anime: Boolean,
    type: MediaType,
    pageSize: Int,
    tvScanCursor: Int
): DiscoverPage {
    val maxPagesPerBatch = 20
    val matched = mutableListOf<RemoteTitle>()
    var cursor = tvScanCursor
    var pagesScanned = 0
    var exhausted = false
    while (matched.size < pageSize && pagesScanned < maxPagesPerBatch) {
        val shows = runCatching { tvMaze.showsPage(cursor) }.getOrDefault(emptyList())
        if (shows.isEmpty()) {
            exhausted = true
            break
        }
        shows.filter { it.isAnime() == anime }
            .map { it.toRemoteTitle(type) }
            .filter { it.matchesDiscover(filter) }
            .forEach { item ->
                if (matched.size < pageSize) matched += item
            }
        cursor++
        pagesScanned++
        delay(100)
    }
    val hasMore = !exhausted && (matched.size >= pageSize || pagesScanned >= maxPagesPerBatch)
    return DiscoverPage(items = matched, hasMore = hasMore, tvScanCursor = cursor)
}
