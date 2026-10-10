package com.grandsphere.fiche.data.repository

import com.google.gson.JsonParser
import com.grandsphere.fiche.data.catalog.CatalogProviderId
import com.grandsphere.fiche.data.catalog.CatalogRouter
import com.grandsphere.fiche.data.catalog.anime.JikanAnimeCatalog
import com.grandsphere.fiche.data.catalog.games.RawgGamesCatalog
import com.grandsphere.fiche.data.catalog.isAnime
import com.grandsphere.fiche.data.catalog.toRemoteTitle
import com.grandsphere.fiche.data.prefs.UserPreferencesRepository
import com.grandsphere.fiche.data.remote.DiscoverPage
import com.grandsphere.fiche.data.remote.RemoteDlc
import com.grandsphere.fiche.data.remote.RemoteEpisode
import com.grandsphere.fiche.data.remote.RemoteRelated
import com.grandsphere.fiche.data.remote.RemoteSeason
import com.grandsphere.fiche.data.remote.RemoteTitle
import com.grandsphere.fiche.data.remote.tmdb.TmdbApi
import com.grandsphere.fiche.data.remote.tvmaze.TvMazeApi
import com.grandsphere.fiche.domain.model.DiscoverFilter
import com.grandsphere.fiche.domain.model.MediaType
import com.grandsphere.fiche.util.CatalogLinkParser
import com.grandsphere.fiche.util.catalogProviderFromUrl
import com.grandsphere.fiche.util.defaultCatalogProvider
import com.grandsphere.fiche.util.remoteWebUrl
import com.grandsphere.fiche.util.yearFromDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade over media-type catalog providers. Callers stay provider-agnostic;
 * [CatalogRouter] picks the active API from library_prefs.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val router: CatalogRouter,
    private val tvMaze: TvMazeApi,
    private val tmdb: TmdbApi,
    private val jikanAnime: JikanAnimeCatalog,
    private val rawgGames: RawgGamesCatalog,
    private val prefs: UserPreferencesRepository,
    private val http: OkHttpClient
) {
    suspend fun providerIdFor(type: MediaType): CatalogProviderId = router.providerIdFor(type)

    suspend fun search(type: MediaType, query: String): List<RemoteTitle> {
        if (query.isBlank()) return emptyList()
        return router.forType(type).search(query)
    }

    suspend fun details(type: MediaType, remoteId: String): RemoteTitle =
        router.forType(type).details(remoteId)

    suspend fun seasonsAndEpisodes(
        remoteId: String,
        fallbackRuntime: Int = 45
    ): Pair<List<RemoteSeason>, List<RemoteEpisode>> =
        seasonsAndEpisodes(MediaType.SERIES, remoteId, fallbackRuntime)

    suspend fun seasonsAndEpisodes(
        type: MediaType,
        remoteId: String,
        fallbackRuntime: Int = 45
    ): Pair<List<RemoteSeason>, List<RemoteEpisode>> {
        return when (type) {
            MediaType.ANIME -> router.anime().seasonsAndEpisodes(remoteId, fallbackRuntime)
            else -> router.series().seasonsAndEpisodes(remoteId, fallbackRuntime)
        }
    }

    suspend fun movieCollectionParts(collectionId: String): List<RemoteRelated> =
        router.movies().collectionParts(collectionId)

    suspend fun related(type: MediaType, title: RemoteTitle): List<RemoteRelated> {
        return when (type) {
            MediaType.MOVIE -> router.movies().related(title)
            MediaType.SERIES -> router.series().related(title)
            MediaType.ANIME -> router.anime().related(title)
            MediaType.BOOK, MediaType.GAME -> emptyList()
        }
    }

    suspend fun discover(
        type: MediaType,
        filter: DiscoverFilter,
        excludeIds: Set<String>,
        page: Int = 0,
        pageSize: Int = VISIBLE_PAGE,
        tvScanCursor: Int = 0
    ): DiscoverPage {
        val raw = when (type) {
            MediaType.SERIES -> router.series().discover(filter, page, pageSize, tvScanCursor)
            MediaType.ANIME -> router.anime().discover(filter, page, pageSize, tvScanCursor)
            MediaType.MOVIE -> router.movies().discover(filter, page, pageSize)
            MediaType.BOOK -> router.books().discover(filter, page, pageSize)
            MediaType.GAME -> router.games().discover(filter, page, pageSize)
        }
        val items = raw.items.filter { it.remoteId !in excludeIds }.distinctBy { it.remoteId }
        return raw.copy(items = items)
    }

    /**
     * Keep fetching catalog pages until [target] visible items are collected after
     * exclusions, or the catalog is exhausted. One extra empty page is retried.
     */
    suspend fun fillDiscover(
        type: MediaType,
        filter: DiscoverFilter,
        excludeIds: Set<String>,
        startPage: Int = 0,
        tvScanCursor: Int = 0,
        target: Int = VISIBLE_PAGE
    ): FilledCatalogPage {
        val acc = ArrayList<RemoteTitle>()
        val seen = excludeIds.toMutableSet()
        var page = startPage
        var cursor = tvScanCursor
        var hasMore = true
        var emptyRetries = 0
        while (acc.size < target && hasMore) {
            val raw = discover(type, filter, seen, page, target, cursor)
            val fresh = raw.items.filter { it.remoteId !in seen }.distinctBy { it.remoteId }
            fresh.forEach { seen += it.remoteId }
            if (fresh.isEmpty()) {
                emptyRetries++
                hasMore = raw.hasMore
                cursor = raw.tvScanCursor
                page++
                if (emptyRetries > 1 || !hasMore) break
                continue
            }
            emptyRetries = 0
            acc += fresh
            hasMore = raw.hasMore
            cursor = raw.tvScanCursor
            page++
        }
        return FilledCatalogPage(
            items = acc,
            hasMore = hasMore,
            tvScanCursor = cursor,
            nextPage = page,
            noNewResults = acc.isEmpty()
        )
    }

    suspend fun fillSearch(
        type: MediaType,
        query: String,
        excludeIds: Set<String>,
        alreadyShown: Set<String> = emptySet(),
        target: Int = VISIBLE_PAGE
    ): FilledCatalogPage {
        val seen = (excludeIds + alreadyShown).toMutableSet()
        fun unused(hits: List<RemoteTitle>) =
            hits.filter { it.remoteId !in seen }.distinctBy { it.remoteId }
        var unused = unused(search(type, query))
        if (unused.isEmpty()) {
            unused = unused(search(type, query))
        }
        val items = unused.take(target)
        return FilledCatalogPage(
            items = items,
            hasMore = unused.size > items.size,
            noNewResults = items.isEmpty()
        )
    }

    /** Build an episode web URL at long-press time. Nothing is persisted. */
    suspend fun episodeWebUrl(
        catalogUrl: String?,
        mediaType: String,
        remoteId: String,
        season: Int,
        episode: Int
    ): String? {
        val base = catalogUrl?.takeIf { it.isNotBlank() }
            ?: remoteWebUrl(
                mediaType,
                remoteId,
                catalogProviderFromUrl(catalogUrl) ?: defaultCatalogProvider(mediaType)
            )
            ?: return null
        val lower = base.lowercase()
        return when {
            "themoviedb.org" in lower || "tmdb.org" in lower ->
                "https://www.themoviedb.org/tv/$remoteId/season/$season/episode/$episode"
            "myanimelist.net" in lower ->
                "https://myanimelist.net/anime/$remoteId/episode/$episode"
            "tvmaze.com" in lower -> {
                val showId = Regex("""/shows/(\d+)""").find(base)?.groupValues?.getOrNull(1)
                    ?: remoteId.filter { it.isDigit() }.ifBlank { remoteId }
                runCatching { tvMaze.episodeByNumber(showId, season, episode).url }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
            }
            else -> base
        }
    }

    suspend fun remoteFromTvMazeId(id: String): RemoteTitle {
        val show = tvMaze.show(id)
        val type = if (show.isAnime()) MediaType.ANIME else MediaType.SERIES
        return show.toRemoteTitle(type)
    }

    suspend fun lookupImdbTitle(imdb: String): RemoteTitle? {
        val show = runCatching { tvMaze.lookupByImdb(imdb) }.getOrNull()
        if (show != null) {
            val type = if (show.isAnime()) MediaType.ANIME else MediaType.SERIES
            return show.toRemoteTitle(type)
        }
        val key = prefs.tmdbApiKey.first().trim().ifBlank { null } ?: return null
        val movie = runCatching { tmdb.find(imdb, key) }.getOrNull()?.movie_results?.firstOrNull()
        return movie?.toRemoteTitle()
    }

    suspend fun workIdFromEdition(editionId: String): String? =
        router.books().workIdFromEdition(editionId)

    suspend fun malAnimeLookup(id: String): MalShareLookup? {
        val provider = router.providerIdFor(MediaType.ANIME)
        val remote = runCatching {
            when (provider) {
                CatalogProviderId.MAL, CatalogProviderId.JIKAN -> router.anime().details(id)
                else -> jikanAnime.details(id)
            }
        }.getOrNull() ?: return null
        return MalShareLookup(
            imdbId = remote.imdbId,
            title = CatalogLinkParser.stripYearSuffix(remote.title)
        )
    }

    suspend fun dlcs(remoteId: String): List<RemoteDlc> =
        router.games().dlcs(remoteId)

    data class SteamAppInfo(val name: String, val releaseYear: Int?)

    suspend fun resolveSteamApp(appId: String): RemoteTitle? {
        val steam = steamAppInfo(appId) ?: return null
        if (rawgGames.keyOrNull() == null) return null
        val queries = buildList {
            add(steam.name)
            steam.releaseYear?.let { add("${steam.name} $it") }
        }.distinct()
        val hits = queries.flatMap { query ->
            runCatching { rawgGames.searchRaw(query, pageSize = 20) }.getOrNull()?.results.orEmpty()
        }.distinctBy { it.slug?.ifBlank { null } ?: it.id.toString() }
        if (hits.isEmpty()) return null

        val ranked = hits.map { hit ->
            val id = hit.slug?.ifBlank { null } ?: hit.id.toString()
            var score = 0
            val stores = runCatching { rawgGames.stores(id) }.getOrNull()?.results.orEmpty()
            if (stores.any { store -> store.url.orEmpty().contains("/app/$appId", ignoreCase = true) }) {
                score += 100
            }
            val year = yearFromDate(hit.released)
            if (steam.releaseYear != null && year == steam.releaseYear) score += 50
            if (hit.name.equals(steam.name, ignoreCase = true)) score += 20
            hit to score
        }.sortedByDescending { it.second }

        val best = ranked.firstOrNull { it.second > 0 }?.first ?: ranked.first().first
        val id = best.slug?.ifBlank { null } ?: best.id.toString()
        return runCatching { details(MediaType.GAME, id) }.getOrNull() ?: best.toRemoteTitle()
    }

    suspend fun steamAppName(appId: String): String? = steamAppInfo(appId)?.name

    suspend fun steamAppInfo(appId: String): SteamAppInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://store.steampowered.com/api/appdetails?appids=$appId")
            .get()
            .build()
        runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                val root = JsonParser.parseString(body).asJsonObject
                val entry = root.getAsJsonObject(appId) ?: return@use null
                if (entry.get("success")?.asBoolean != true) return@use null
                val data = entry.getAsJsonObject("data") ?: return@use null
                val name = data.get("name")?.asString?.takeIf { it.isNotBlank() } ?: return@use null
                val releaseDate = data.getAsJsonObject("release_date")?.get("date")?.asString
                SteamAppInfo(name = name, releaseYear = yearFromSteamDate(releaseDate))
            }
        }.getOrNull()
    }

    private fun yearFromSteamDate(date: String?): Int? {
        if (date.isNullOrBlank()) return null
        return Regex("""\b(19|20)\d{2}\b""").findAll(date).lastOrNull()?.value?.toIntOrNull()
    }
}

data class MalShareLookup(val imdbId: String?, val title: String)

data class FilledCatalogPage(
    val items: List<RemoteTitle>,
    val hasMore: Boolean,
    val tvScanCursor: Int = 0,
    val nextPage: Int = 0,
    val noNewResults: Boolean = false
)

const val VISIBLE_PAGE = 10
