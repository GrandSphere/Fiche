package com.grandsphere.fiche.data.catalog.books

import com.grandsphere.fiche.data.catalog.CatalogProviderId
import com.grandsphere.fiche.data.catalog.MediaCatalogProvider
import com.grandsphere.fiche.data.catalog.inferBookSeries
import com.grandsphere.fiche.data.catalog.matchesDiscover
import com.grandsphere.fiche.data.catalog.toRemoteTitle
import com.grandsphere.fiche.data.remote.DiscoverPage
import com.grandsphere.fiche.data.remote.RemoteTitle
import com.grandsphere.fiche.data.remote.openlibrary.OpenLibraryApi
import com.grandsphere.fiche.data.remote.openlibrary.openLibraryCover
import com.grandsphere.fiche.data.remote.openlibrary.workIdFromKey
import com.grandsphere.fiche.domain.model.DiscoverFilter
import com.grandsphere.fiche.domain.model.MediaType

interface BooksCatalog : MediaCatalogProvider {
    suspend fun discover(
        filter: DiscoverFilter,
        page: Int = 0,
        pageSize: Int = 20
    ): DiscoverPage

    suspend fun workIdFromEdition(editionId: String): String?
}

class OpenLibraryBooksCatalog(
    private val openLibrary: OpenLibraryApi
) : BooksCatalog {
    override val id: CatalogProviderId = CatalogProviderId.OPEN_LIBRARY

    override suspend fun search(query: String): List<RemoteTitle> {
        if (query.isBlank()) return emptyList()
        return openLibrary.search(query).docs.map { it.toRemoteTitle() }
    }

    override suspend fun details(remoteId: String): RemoteTitle {
        val work = openLibrary.work(remoteId)
        val description = when (val d = work.description) {
            is String -> d
            is Map<*, *> -> d["value"]?.toString().orEmpty()
            else -> ""
        }
        val hit = runCatching { openLibrary.search("key:/works/$remoteId", 1) }
            .getOrNull()?.docs?.firstOrNull()
        return RemoteTitle(
            mediaType = MediaType.BOOK.name,
            remoteId = remoteId,
            title = work.title.orEmpty(),
            year = hit?.first_publish_year,
            posterUrl = openLibraryCover(hit?.cover_i),
            overview = description,
            author = hit?.author_name?.joinToString(", "),
            genres = (work.subjects ?: emptyList()).take(8),
            seriesName = inferBookSeries(work.title, work.subjects),
            pageCount = hit?.number_of_pages_median ?: 0
        )
    }

    override suspend fun discover(
        filter: DiscoverFilter,
        page: Int,
        pageSize: Int
    ): DiscoverPage {
        val q = when {
            filter.query.isNotBlank() -> filter.query
            filter.author.isNotBlank() -> filter.author
            filter.publisher.isNotBlank() -> filter.publisher
            filter.newReleases -> "first_publish_year:${java.time.Year.now().value}"
            filter.genres.isNotEmpty() -> filter.genres.joinToString(" ")
            else -> "fiction"
        }
        val docs = openLibrary.searchAdvanced(
            query = q,
            subject = filter.genres.singleOrNull(),
            author = filter.author.ifBlank { null },
            publisher = filter.publisher.ifBlank { null },
            year = filter.yearFrom?.toString(),
            limit = pageSize,
            page = page + 1,
            sort = if (filter.newReleases) "new" else null
        ).docs
        val items = docs.map { it.toRemoteTitle(genreLimit = 6) }.filter { it.matchesDiscover(filter) }
        return DiscoverPage(items = items, hasMore = docs.size >= pageSize)
    }

    override suspend fun workIdFromEdition(editionId: String): String? {
        val edition = runCatching { openLibrary.edition(editionId) }.getOrNull() ?: return null
        return edition.works.orEmpty().firstNotNullOfOrNull { workIdFromKey(it.key).ifBlank { null } }
    }
}
