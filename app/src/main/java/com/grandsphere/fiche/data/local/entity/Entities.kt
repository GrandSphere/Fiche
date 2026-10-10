package com.grandsphere.fiche.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "titles",
    indices = [Index(value = ["mediaType", "remoteId"], unique = true)]
)
data class TitleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaType: String,
    val remoteId: String,
    val title: String,
    val year: Int? = null,
    val posterUrl: String? = null,
    val overview: String? = null,
    val author: String? = null,
    val status: String? = null,
    val cancelled: Boolean = false,
    val genres: String = "",
    val userRating: Float? = null,
    val moreOfThis: Boolean = false,
    val completed: Boolean = false,
    val collectionId: String? = null,
    val collectionName: String? = null,
    val seriesName: String? = null,
    val seriesPosition: Int? = null,
    val firstDate: String? = null,
    val lastDate: String? = null,
    val totalUnits: Int = 0,
    val watchedUnits: Int = 0,
    val watchedMinutes: Int = 0,
    val totalMinutes: Int = 0,
    val imdbId: String? = null,
    val runtimeMinutes: Int = 0,
    val pageCount: Int = 0,
    val recommended: Boolean = false,
    val localPosterPath: String? = null,
    val displayTitle: String? = null,
    val catalogUrl: String? = null,
    val hidden: Boolean = false,
    /** When false, title is excluded from overview totals (watch time, completed, pages). */
    val tracked: Boolean = true,
    /** When true, a collection movie is stored but ignored for franchise completion (like untracked seasons). */
    val notInterested: Boolean = false,
    val countSequelsInCompletion: Boolean = true,
    /** 1 while background enrich is in progress; 0 when fully loaded. */
    val busyLoading: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    @get:Ignore
    val displayName: String get() = displayTitle?.takeIf { it.isNotBlank() } ?: title
}

@Entity(
    tableName = "seasons",
    foreignKeys = [
        ForeignKey(
            entity = TitleEntity::class,
            parentColumns = ["id"],
            childColumns = ["titleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("titleId"), Index(value = ["titleId", "seasonNumber"], unique = true)]
)
data class SeasonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titleId: Long,
    val seasonNumber: Int,
    val episodeCount: Int,
    val startDate: String? = null,
    val endDate: String? = null,
    /** When true, season is ignored for completion % and required watch time. */
    val notInterested: Boolean = false
)

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = TitleEntity::class,
            parentColumns = ["id"],
            childColumns = ["titleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("titleId"),
        Index(value = ["titleId", "seasonNumber", "episodeNumber"], unique = true)
    ]
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titleId: Long,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val airDate: String? = null,
    val watched: Boolean = false,
    val runtimeMinutes: Int = 0
)

@Entity(
    tableName = "watchlist",
    indices = [Index(value = ["mediaType", "remoteId"], unique = true)]
)
data class WatchlistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaType: String,
    val remoteId: String,
    val title: String,
    val year: Int? = null,
    val posterUrl: String? = null,
    val overview: String? = null,
    val genres: String = "",
    val collectionId: String? = null,
    val collectionName: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "banlist",
    indices = [Index(value = ["mediaType", "url"], unique = true)]
)
data class BanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaType: String,
    val title: String,
    val year: Int? = null,
    val url: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "dislike_list",
    indices = [Index(value = ["mediaType", "url"], unique = true)]
)
data class DislikeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaType: String,
    val title: String,
    val year: Int? = null,
    val url: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "related_titles",
    indices = [
        Index(value = ["ownerMediaType", "ownerRemoteId", "relatedRemoteId", "relation"], unique = true)
    ]
)
data class RelatedTitleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerRemoteId: String,
    val ownerMediaType: String,
    val relatedRemoteId: String,
    val relatedMediaType: String,
    val relation: String,
    val relatedTitle: String,
    val year: Int? = null,
    val posterUrl: String? = null,
    val position: Int = 0,
    val watched: Boolean = false,
    val runtimeMinutes: Int = 0
)

@Entity(
    tableName = "dlcs",
    foreignKeys = [
        ForeignKey(
            entity = TitleEntity::class,
            parentColumns = ["id"],
            childColumns = ["titleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("titleId"),
        Index(value = ["titleId", "remoteId"], unique = true)
    ]
)
data class DlcEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titleId: Long,
    val remoteId: String,
    val name: String,
    val released: String? = null,
    val posterUrl: String? = null,
    val completed: Boolean = false,
    val position: Int = 0
)

@Entity(tableName = "overview_stats")
data class OverviewStatsEntity(
    @PrimaryKey val mediaType: String,
    val total: Int = 0,
    val completed: Int = 0,
    val inProgress: Int = 0,
    val notStarted: Int = 0,
    val hidden: Int = 0,
    val watchedMinutes: Int = 0,
    val pagesRead: Int = 0,
    val totalEpisodes: Int = 0,
    val watchedEpisodes: Int = 0,
    val averageRating: Float? = null,
    val favouriteGenres: String = "",
    val favouriteTitle: String = "",
    val genresByHours: String = "",
    val topCreators: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "interest_notes")
data class InterestNotesEntity(
    @PrimaryKey val mediaType: String,
    val body: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "api_keys")
data class ApiKeyEntity(
    @PrimaryKey val provider: String,
    val apiKey: String = ""
)

/** Persisted library filter/sort preferences (single row). */
@Entity(tableName = "library_prefs")
data class LibraryPrefsEntity(
    @PrimaryKey val id: Int = 1,
    val sortsCsv: String = "RATING",
    val showHidden: Boolean = false,
    val hideCompleted: Boolean = false,
    val hideIncomplete: Boolean = false,
    val storeImagesLocally: Boolean = true,
    val showCategoryTabs: Boolean = true,
    val seriesApi: String = "TVMAZE",
    val moviesApi: String = "TMDB",
    val animeApi: String = "TVMAZE",
    val gamesApi: String = "RAWG",
    val booksApi: String = "OPEN_LIBRARY"
)
