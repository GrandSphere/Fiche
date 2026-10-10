package com.grandsphere.fiche.data.catalog.anime

import com.grandsphere.fiche.data.catalog.CatalogProviderId
import com.grandsphere.fiche.data.catalog.MediaCatalogProvider
import com.grandsphere.fiche.data.catalog.TMDB_ANIMATION_GENRE_ID
import com.grandsphere.fiche.data.catalog.franchiseKey
import com.grandsphere.fiche.data.catalog.isAnime
import com.grandsphere.fiche.data.catalog.matchesDiscover
import com.grandsphere.fiche.data.catalog.paginateClient
import com.grandsphere.fiche.data.catalog.toRemoteTitle
import com.grandsphere.fiche.data.catalog.series.discoverTvMaze
import com.grandsphere.fiche.data.catalog.series.tmdbSeasonsAndEpisodes
import com.grandsphere.fiche.data.catalog.series.tvMazeFranchiseRelated
import com.grandsphere.fiche.data.catalog.series.tvMazeSeasonsAndEpisodes
import com.grandsphere.fiche.data.prefs.ApiKeyRepository
import com.grandsphere.fiche.data.prefs.UserPreferencesRepository
import com.grandsphere.fiche.data.remote.DiscoverPage
import com.grandsphere.fiche.data.remote.RemoteEpisode
import com.grandsphere.fiche.data.remote.RemoteRelated
import com.grandsphere.fiche.data.remote.RemoteSeason
import com.grandsphere.fiche.data.remote.RemoteTitle
import com.grandsphere.fiche.data.remote.jikan.JikanAnime
import com.grandsphere.fiche.data.remote.jikan.JikanApi
import com.grandsphere.fiche.data.remote.mal.MalAnime
import com.grandsphere.fiche.data.remote.mal.MalApi
import com.grandsphere.fiche.data.remote.tmdb.TmdbApi
import com.grandsphere.fiche.data.remote.tvmaze.TvMazeApi
import com.grandsphere.fiche.domain.model.DiscoverFilter
import com.grandsphere.fiche.domain.model.MediaType
import com.grandsphere.fiche.domain.model.RelationType
import com.grandsphere.fiche.util.cleanCatalogOverview
import com.grandsphere.fiche.util.displayCatalogStatus
import com.grandsphere.fiche.util.isCancelledStatus
import com.grandsphere.fiche.util.yearFromDate

interface AnimeCatalog : MediaCatalogProvider {
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

class TvMazeAnimeCatalog(
    private val tvMaze: TvMazeApi,
    private val tmdb: TmdbApi,
    private val prefs: UserPreferencesRepository,
    private val apiKeys: ApiKeyRepository
) : AnimeCatalog {
    override val id: CatalogProviderId = CatalogProviderId.TVMAZE

    override suspend fun search(query: String): List<RemoteTitle> {
        if (query.isBlank()) return emptyList()
        return tvMaze.search(query).map { it.show }.filter { it.isAnime() }
            .map { it.toRemoteTitle(MediaType.ANIME) }
    }

    override suspend fun details(remoteId: String): RemoteTitle {
        val show = tvMaze.show(remoteId).toRemoteTitle(MediaType.ANIME)
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
        anime = true,
        page = page,
        pageSize = pageSize,
        tvScanCursor = tvScanCursor,
        searchFn = { search(it) }
    )

    override suspend fun related(title: RemoteTitle): List<RemoteRelated> =
        tvMazeFranchiseRelated(tvMaze, MediaType.ANIME, title)

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

class TmdbAnimeCatalog(
    private val tmdb: TmdbApi,
    private val apiKeys: ApiKeyRepository
) : AnimeCatalog {
    override val id: CatalogProviderId = CatalogProviderId.TMDB
    private var tvGenreMap: Map<Int, String> = emptyMap()

    override suspend fun search(query: String): List<RemoteTitle> {
        if (query.isBlank()) return emptyList()
        val key = requireTmdbKey()
        ensureTvGenres(key)
        return tmdb.searchTv(key, query).results
            .filter { TMDB_ANIMATION_GENRE_ID in it.genre_ids.orEmpty() }
            .map { it.toRemoteTitle(MediaType.ANIME, tvGenreMap) }
    }

    override suspend fun details(remoteId: String): RemoteTitle {
        val key = requireTmdbKey()
        return tmdb.tv(remoteId, key).toRemoteTitle(MediaType.ANIME)
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
            genres = TMDB_ANIMATION_GENRE_ID.toString(),
            from = from,
            to = to,
            originCountry = "JP",
            status = status,
            page = page + 1
        )
        val items = response.results
            .map { it.toRemoteTitle(MediaType.ANIME, tvGenreMap) }
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
            .filter { TMDB_ANIMATION_GENRE_ID in it.genre_ids.orEmpty() }
            .filter { it.id.toString() != title.remoteId }
            .map { it.toRemoteTitle(MediaType.ANIME, tvGenreMap) }
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
                    mediaType = MediaType.ANIME.name,
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

class JikanAnimeCatalog(
    private val jikan: JikanApi
) : AnimeCatalog {
    override val id: CatalogProviderId = CatalogProviderId.JIKAN

    override suspend fun search(query: String): List<RemoteTitle> {
        if (query.isBlank()) return emptyList()
        return jikan.searchAnime(query).data.map { it.toRemoteTitle() }
    }

    override suspend fun details(remoteId: String): RemoteTitle {
        val anime = jikan.animeFull(remoteId).data
            ?: error("Anime not found on Jikan: $remoteId")
        return anime.toRemoteTitle()
    }

    override suspend fun seasonsAndEpisodes(
        remoteId: String,
        fallbackRuntime: Int
    ): Pair<List<RemoteSeason>, List<RemoteEpisode>> {
        val fallback = fallbackRuntime.takeIf { it > 0 } ?: 24
        val episodes = mutableListOf<RemoteEpisode>()
        var page = 1
        while (page <= 20) {
            val response = runCatching { jikan.animeEpisodes(remoteId, page) }.getOrNull()
                ?: break
            val batch = response.data
            if (batch.isEmpty()) break
            batch.forEach { ep ->
                episodes += RemoteEpisode(
                    seasonNumber = 1,
                    episodeNumber = episodes.size + 1,
                    airDate = ep.aired?.take(10),
                    runtimeMinutes = fallback
                )
            }
            if (response.pagination?.has_next_page != true) break
            page++
        }
        if (episodes.isEmpty()) {
            val count = runCatching { jikan.animeFull(remoteId).data?.episodes }.getOrNull() ?: 0
            if (count > 0) {
                repeat(count) { i ->
                    episodes += RemoteEpisode(
                        seasonNumber = 1,
                        episodeNumber = i + 1,
                        airDate = null,
                        runtimeMinutes = fallback
                    )
                }
            }
        }
        val dates = episodes.mapNotNull { it.airDate }
        val seasons = if (episodes.isEmpty()) {
            emptyList()
        } else {
            listOf(
                RemoteSeason(
                    seasonNumber = 1,
                    episodeCount = episodes.size,
                    startDate = dates.minOrNull(),
                    endDate = dates.maxOrNull()
                )
            )
        }
        return seasons to episodes
    }

    override suspend fun discover(
        filter: DiscoverFilter,
        page: Int,
        pageSize: Int,
        tvScanCursor: Int
    ): DiscoverPage {
        if (filter.query.isNotBlank()) {
            val all = search(filter.query).filter { it.matchesDiscover(filter) }
            return paginateClient(all, page, pageSize)
        }
        val apiPage = page + 1
        val response = if (filter.newReleases) {
            runCatching { jikan.seasonNow(page = apiPage, limit = pageSize) }.getOrNull()
        } else {
            runCatching { jikan.topAnime(page = apiPage, limit = pageSize) }.getOrNull()
        } ?: return DiscoverPage(emptyList(), hasMore = false)
        val items = response.data.map { it.toRemoteTitle() }.filter { it.matchesDiscover(filter) }
        val hasMore = response.pagination?.has_next_page == true
        return DiscoverPage(items = items, hasMore = hasMore)
    }

    override suspend fun related(title: RemoteTitle): List<RemoteRelated> {
        val anime = runCatching { jikan.animeFull(title.remoteId).data }.getOrNull() ?: return emptyList()
        return anime.relations.orEmpty()
            .flatMap { rel ->
                val relationName = when {
                    rel.relation.equals("Prequel", true) -> RelationType.PREQUEL.name
                    rel.relation.equals("Sequel", true) -> RelationType.SEQUEL.name
                    else -> RelationType.SPIN_OFF.name
                }
                rel.entry.orEmpty()
                    .filter { it.type.equals("anime", true) }
                    .mapNotNull { entry ->
                        val id = entry.mal_id?.toString() ?: return@mapNotNull null
                        if (id == title.remoteId) return@mapNotNull null
                        RemoteRelated(
                            remoteId = id,
                            mediaType = MediaType.ANIME.name,
                            title = entry.name.orEmpty(),
                            year = null,
                            posterUrl = null,
                            relation = relationName
                        )
                    }
            }
    }

    private fun JikanAnime.toRemoteTitle(): RemoteTitle {
        val displayTitle = title_english?.takeIf { it.isNotBlank() } ?: title.orEmpty()
        val poster = images?.jpg?.large_image_url
            ?: images?.jpg?.image_url
            ?: images?.webp?.large_image_url
            ?: images?.webp?.image_url
        val runtime = duration
            ?.let { Regex("""(\d+)\s*min""").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            ?: 24
        return RemoteTitle(
            mediaType = MediaType.ANIME.name,
            remoteId = (mal_id ?: 0L).toString(),
            title = displayTitle,
            year = year ?: yearFromDate(aired?.from),
            posterUrl = poster,
            overview = cleanCatalogOverview(synopsis),
            status = displayCatalogStatus(status),
            cancelled = isCancelledStatus(status),
            genres = genres.orEmpty().mapNotNull { it.name },
            firstDate = aired?.from?.take(10),
            lastDate = aired?.to?.take(10),
            runtimeMinutes = runtime,
            unitHint = episodes ?: 0
        )
    }
}

class MalAnimeCatalog(
    private val mal: MalApi,
    private val apiKeys: ApiKeyRepository
) : AnimeCatalog {
    override val id: CatalogProviderId = CatalogProviderId.MAL

    private suspend fun clientId(): String = apiKeys.require("MAL", "MAL")

    override suspend fun search(query: String): List<RemoteTitle> {
        if (query.isBlank()) return emptyList()
        return mal.search(clientId(), query, limit = 20).data.mapNotNull { it.node?.toRemoteTitle() }
    }

    override suspend fun details(remoteId: String): RemoteTitle =
        mal.anime(clientId(), remoteId).toRemoteTitle()

    override suspend fun seasonsAndEpisodes(
        remoteId: String,
        fallbackRuntime: Int
    ): Pair<List<RemoteSeason>, List<RemoteEpisode>> {
        val anime = mal.anime(clientId(), remoteId)
        val fallback = fallbackRuntime.takeIf { it > 0 }
            ?: ((anime.average_episode_duration ?: 0) / 60).takeIf { it > 0 }
            ?: 24
        val count = anime.num_episodes ?: 0
        if (count <= 0) return emptyList<RemoteSeason>() to emptyList()
        val episodes = (1..count).map { i ->
            RemoteEpisode(
                seasonNumber = 1,
                episodeNumber = i,
                airDate = null,
                runtimeMinutes = fallback
            )
        }
        val seasons = listOf(
            RemoteSeason(
                seasonNumber = 1,
                episodeCount = episodes.size,
                startDate = anime.start_date?.take(10),
                endDate = anime.end_date?.take(10)
            )
        )
        return seasons to episodes
    }

    override suspend fun discover(
        filter: DiscoverFilter,
        page: Int,
        pageSize: Int,
        tvScanCursor: Int
    ): DiscoverPage {
        if (filter.query.isNotBlank()) {
            val all = search(filter.query).filter { it.matchesDiscover(filter) }
            return paginateClient(all, page, pageSize)
        }
        val offset = page * pageSize
        val response = runCatching {
            mal.ranking(clientId(), limit = pageSize, offset = offset)
        }.getOrNull() ?: return DiscoverPage(emptyList(), hasMore = false)
        val items = response.data.mapNotNull { it.node?.toRemoteTitle() }.filter { it.matchesDiscover(filter) }
        return DiscoverPage(items = items, hasMore = !response.paging?.next.isNullOrBlank())
    }

    override suspend fun related(title: RemoteTitle): List<RemoteRelated> {
        val anime = runCatching { mal.anime(clientId(), title.remoteId) }.getOrNull() ?: return emptyList()
        return anime.related_anime.orEmpty().mapNotNull { rel ->
            val node = rel.node ?: return@mapNotNull null
            val id = node.id?.toString() ?: return@mapNotNull null
            if (id == title.remoteId) return@mapNotNull null
            val relationName = when (rel.relation_type?.lowercase()) {
                "prequel" -> RelationType.PREQUEL.name
                "sequel" -> RelationType.SEQUEL.name
                else -> RelationType.SPIN_OFF.name
            }
            RemoteRelated(
                remoteId = id,
                mediaType = MediaType.ANIME.name,
                title = node.alternative_titles?.en?.takeIf { it.isNotBlank() } ?: node.title.orEmpty(),
                year = yearFromDate(node.start_date),
                posterUrl = node.main_picture?.large ?: node.main_picture?.medium,
                relation = relationName
            )
        }
    }

    private fun MalAnime.toRemoteTitle(): RemoteTitle {
        val displayTitle = alternative_titles?.en?.takeIf { it.isNotBlank() } ?: title.orEmpty()
        val poster = main_picture?.large ?: main_picture?.medium
        val runtime = ((average_episode_duration ?: 0) / 60).takeIf { it > 0 } ?: 24
        return RemoteTitle(
            mediaType = MediaType.ANIME.name,
            remoteId = (id ?: 0L).toString(),
            title = displayTitle,
            year = yearFromDate(start_date),
            posterUrl = poster,
            overview = cleanCatalogOverview(synopsis),
            status = displayCatalogStatus(status),
            cancelled = isCancelledStatus(status),
            genres = genres.orEmpty().mapNotNull { it.name },
            firstDate = start_date?.take(10),
            lastDate = end_date?.take(10),
            runtimeMinutes = runtime,
            unitHint = num_episodes ?: 0
        )
    }
}
