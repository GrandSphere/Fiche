package com.grandsphere.fiche.data.remote

data class DiscoverPage(
    val items: List<RemoteTitle>,
    val hasMore: Boolean,
    /** Next TVMaze shows page index when scanning series/anime catalogs. */
    val tvScanCursor: Int = 0
)

data class RemoteTitle(
    val mediaType: String,
    val remoteId: String,
    val title: String,
    val year: Int? = null,
    val posterUrl: String? = null,
    val overview: String? = null,
    val author: String? = null,
    val status: String? = null,
    val cancelled: Boolean = false,
    val genres: List<String> = emptyList(),
    val collectionId: String? = null,
    val collectionName: String? = null,
    val seriesName: String? = null,
    val seriesPosition: Int? = null,
    val firstDate: String? = null,
    val lastDate: String? = null,
    val imdbId: String? = null,
    val runtimeMinutes: Int = 0,
    val pageCount: Int = 0,
    val unitHint: Int = 0
)

data class RemoteSeason(
    val seasonNumber: Int,
    val episodeCount: Int,
    val startDate: String?,
    val endDate: String?
)

data class RemoteEpisode(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val airDate: String?,
    val runtimeMinutes: Int = 0
)

data class RemoteRelated(
    val remoteId: String,
    val mediaType: String,
    val title: String,
    val year: Int?,
    val posterUrl: String?,
    val relation: String,
    val position: Int = 0,
    val runtimeMinutes: Int = 0,
    val firstDate: String? = null
)

data class RemoteDlc(
    val remoteId: String,
    val name: String,
    val released: String? = null,
    val posterUrl: String? = null,
    val position: Int = 0
)
