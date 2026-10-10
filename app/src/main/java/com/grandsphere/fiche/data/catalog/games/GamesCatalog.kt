package com.grandsphere.fiche.data.catalog.games

import com.grandsphere.fiche.data.catalog.CatalogProviderId
import com.grandsphere.fiche.data.catalog.MediaCatalogProvider
import com.grandsphere.fiche.data.catalog.matchesDiscover
import com.grandsphere.fiche.data.catalog.paginateClient
import com.grandsphere.fiche.data.catalog.toRemoteTitle
import com.grandsphere.fiche.data.prefs.ApiKeyRepository
import com.grandsphere.fiche.data.remote.DiscoverPage
import com.grandsphere.fiche.data.remote.RemoteDlc
import com.grandsphere.fiche.data.remote.RemoteTitle
import com.grandsphere.fiche.data.remote.rawg.RawgApi
import com.grandsphere.fiche.domain.model.DiscoverFilter

interface GamesCatalog : MediaCatalogProvider {
    suspend fun discover(
        filter: DiscoverFilter,
        page: Int = 0,
        pageSize: Int = 20
    ): DiscoverPage

    suspend fun dlcs(remoteId: String): List<RemoteDlc>
}

class RawgGamesCatalog(
    private val rawg: RawgApi,
    private val apiKeys: ApiKeyRepository
) : GamesCatalog {
    override val id: CatalogProviderId = CatalogProviderId.RAWG
    private var gameGenreMap: Map<String, String> = emptyMap()

    override suspend fun search(query: String): List<RemoteTitle> {
        if (query.isBlank()) return emptyList()
        val key = requireRawgKey()
        return rawg.search(key, query).results.map { it.toRemoteTitle() }
    }

    override suspend fun details(remoteId: String): RemoteTitle {
        val key = requireRawgKey()
        return rawg.game(remoteId, key).toRemoteTitle()
    }

    override suspend fun discover(
        filter: DiscoverFilter,
        page: Int,
        pageSize: Int
    ): DiscoverPage {
        val key = requireRawgKey()
        ensureGameGenres(key)
        if (filter.query.isNotBlank()) {
            val all = search(filter.query).filter { it.matchesDiscover(filter) }
            return paginateClient(all, page, pageSize)
        }
        val genreSlugs = filter.genres.mapNotNull { name ->
            gameGenreMap.entries.firstOrNull { it.value.equals(name, true) }?.key
                ?: name.lowercase().replace(' ', '-')
        }
        val dates = when {
            filter.newReleases -> {
                val end = java.time.LocalDate.now()
                val start = end.minusDays(90)
                "$start,$end"
            }
            filter.yearFrom != null || filter.yearTo != null -> {
                val from = filter.yearFrom ?: 1970
                val to = filter.yearTo ?: java.time.Year.now().value
                "$from-01-01,$to-12-31"
            }
            else -> null
        }
        val developerId = if (filter.developer.isNotBlank()) {
            runCatching {
                rawg.searchDevelopers(key, filter.developer).results.firstOrNull()?.id?.toString()
            }.getOrNull()
        } else {
            null
        }
        val response = rawg.discover(
            key = key,
            genres = genreSlugs.takeIf { it.isNotEmpty() }?.joinToString(","),
            dates = dates,
            developers = developerId,
            ordering = if (filter.newReleases) "-released" else "-added",
            page = page + 1,
            pageSize = pageSize
        )
        val items = response.results.map { it.toRemoteTitle() }.filter { it.matchesDiscover(filter) }
        return DiscoverPage(items = items, hasMore = !response.next.isNullOrBlank())
    }

    override suspend fun dlcs(remoteId: String): List<RemoteDlc> {
        val key = requireRawgKey()
        val all = mutableListOf<RemoteDlc>()
        var page = 1
        while (page <= 5) {
            val batch = runCatching { rawg.additions(remoteId, key, page) }
                .getOrNull()?.results.orEmpty()
            if (batch.isEmpty()) break
            batch.forEach { item ->
                all += RemoteDlc(
                    remoteId = item.slug?.ifBlank { null } ?: item.id.toString(),
                    name = item.name.orEmpty().ifBlank { "DLC ${item.id}" },
                    released = item.released,
                    posterUrl = item.background_image,
                    position = all.size
                )
            }
            if (batch.size < 40) break
            page++
        }
        return all
    }

    suspend fun searchRaw(query: String, pageSize: Int = 20) =
        rawg.search(requireRawgKey(), query, pageSize = pageSize)

    suspend fun stores(remoteId: String) =
        rawg.stores(remoteId, requireRawgKey())

    suspend fun keyOrNull(): String? =
        apiKeys.get("RAWG").ifBlank { null }

    private suspend fun ensureGameGenres(key: String) {
        if (gameGenreMap.isNotEmpty()) return
        gameGenreMap = runCatching {
            rawg.genres(key).results.associate { named ->
                val slug = named.slug.orEmpty()
                val name = named.name.orEmpty()
                slug to name
            }.filterKeys { it.isNotBlank() }
        }.getOrDefault(emptyMap())
    }

    private suspend fun requireRawgKey(): String =
        apiKeys.require("RAWG", "RAWG")
}
