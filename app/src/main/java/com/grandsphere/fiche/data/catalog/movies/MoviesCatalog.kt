package com.grandsphere.fiche.data.catalog.movies

import com.grandsphere.fiche.data.catalog.CatalogProviderId
import com.grandsphere.fiche.data.catalog.MediaCatalogProvider
import com.grandsphere.fiche.data.catalog.matchesDiscover
import com.grandsphere.fiche.data.catalog.paginateClient
import com.grandsphere.fiche.data.catalog.toRemoteTitle
import com.grandsphere.fiche.data.prefs.ApiKeyRepository
import com.grandsphere.fiche.data.remote.DiscoverPage
import com.grandsphere.fiche.data.remote.RemoteRelated
import com.grandsphere.fiche.data.remote.RemoteTitle
import com.grandsphere.fiche.data.remote.tmdb.TmdbApi
import com.grandsphere.fiche.data.remote.tmdb.tmdbPoster
import com.grandsphere.fiche.domain.model.DiscoverFilter
import com.grandsphere.fiche.domain.model.MediaType
import com.grandsphere.fiche.domain.model.RelationType
import com.grandsphere.fiche.util.isCancelledStatus
import com.grandsphere.fiche.util.yearFromDate

interface MoviesCatalog : MediaCatalogProvider {
    suspend fun discover(
        filter: DiscoverFilter,
        page: Int = 0,
        pageSize: Int = 20
    ): DiscoverPage

    suspend fun related(title: RemoteTitle): List<RemoteRelated>

    suspend fun collectionParts(collectionId: String): List<RemoteRelated>
}

class TmdbMoviesCatalog(
    private val tmdb: TmdbApi,
    private val apiKeys: ApiKeyRepository
) : MoviesCatalog {
    override val id: CatalogProviderId = CatalogProviderId.TMDB
    private var movieGenreMap: Map<Int, String> = emptyMap()

    override suspend fun search(query: String): List<RemoteTitle> {
        if (query.isBlank()) return emptyList()
        val key = requireTmdbKey()
        ensureMovieGenres(key)
        return tmdb.searchMovies(key, query).results.map { it.toRemoteTitle(movieGenreMap) }
    }

    override suspend fun details(remoteId: String): RemoteTitle {
        val key = requireTmdbKey()
        val movie = tmdb.movie(remoteId, key)
        return RemoteTitle(
            mediaType = MediaType.MOVIE.name,
            remoteId = movie.id.toString(),
            title = movie.title.orEmpty(),
            year = yearFromDate(movie.release_date),
            posterUrl = tmdbPoster(movie.poster_path),
            overview = movie.overview,
            status = movie.status,
            cancelled = isCancelledStatus(movie.status),
            genres = movie.genres?.map { it.name }.orEmpty(),
            collectionId = movie.belongs_to_collection?.id?.toString(),
            collectionName = movie.belongs_to_collection?.name,
            firstDate = movie.release_date,
            lastDate = movie.release_date,
            imdbId = movie.imdb_id,
            runtimeMinutes = movie.runtime ?: 0
        )
    }

    override suspend fun discover(
        filter: DiscoverFilter,
        page: Int,
        pageSize: Int
    ): DiscoverPage {
        val key = requireTmdbKey()
        ensureMovieGenres(key)
        val actorId = if (filter.actor.isNotBlank()) {
            runCatching { tmdb.searchPerson(key, filter.actor).results.firstOrNull()?.id?.toString() }.getOrNull()
        } else {
            null
        }
        val crewId = if (filter.producer.isNotBlank()) {
            runCatching { tmdb.searchPerson(key, filter.producer).results.firstOrNull()?.id?.toString() }.getOrNull()
        } else {
            null
        }
        if (filter.newReleases && actorId == null && crewId == null) {
            val all = (tmdb.nowPlaying(key).results + tmdb.upcoming(key).results)
                .map { it.toRemoteTitle(movieGenreMap) }
                .filter { it.matchesDiscover(filter) }
            return paginateClient(all, page, pageSize)
        }
        if (filter.query.isNotBlank()) {
            val all = search(filter.query).filter { it.matchesDiscover(filter) }
            return paginateClient(all, page, pageSize)
        }
        val genreIds = filter.genres.mapNotNull { name ->
            movieGenreMap.entries.firstOrNull { it.value.equals(name, true) }?.key
        }
        val from = filter.yearFrom?.let { "$it-01-01" }
        val to = filter.yearTo?.let { "$it-12-31" }
        val response = tmdb.discoverMovies(
            apiKey = key,
            genres = genreIds.takeIf { it.isNotEmpty() }?.joinToString(","),
            from = from,
            to = to,
            people = actorId,
            crew = crewId,
            page = page + 1
        )
        val items = response.results.map { it.toRemoteTitle(movieGenreMap) }.filter { it.matchesDiscover(filter) }
        return DiscoverPage(items = items, hasMore = response.page < response.total_pages)
    }

    override suspend fun related(title: RemoteTitle): List<RemoteRelated> {
        val collectionId = title.collectionId ?: return emptyList()
        val ownerYear = title.year
        return collectionParts(collectionId)
            .filter { it.remoteId != title.remoteId }
            .map { part ->
                val relation = when {
                    ownerYear != null && part.year != null && part.year < ownerYear -> RelationType.PREQUEL.name
                    ownerYear != null && part.year != null && part.year > ownerYear -> RelationType.SEQUEL.name
                    else -> RelationType.COLLECTION.name
                }
                part.copy(relation = relation)
            }
    }

    override suspend fun collectionParts(collectionId: String): List<RemoteRelated> {
        val key = apiKeys.get("TMDB").ifBlank { null } ?: return emptyList()
        val collection = runCatching { tmdb.collection(collectionId, key) }.getOrNull()
        return collection?.parts.orEmpty().mapIndexed { index, movie ->
            RemoteRelated(
                remoteId = movie.id.toString(),
                mediaType = MediaType.MOVIE.name,
                title = movie.title.orEmpty(),
                year = yearFromDate(movie.release_date),
                posterUrl = tmdbPoster(movie.poster_path),
                relation = RelationType.COLLECTION.name,
                position = index,
                runtimeMinutes = 0,
                firstDate = movie.release_date
            )
        }
    }

    private suspend fun ensureMovieGenres(key: String) {
        if (movieGenreMap.isNotEmpty()) return
        movieGenreMap = runCatching { tmdb.movieGenres(key).genres.associate { it.id to it.name } }
            .getOrDefault(emptyMap())
    }

    private suspend fun requireTmdbKey(): String =
        apiKeys.require("TMDB", "TMDB")
}
