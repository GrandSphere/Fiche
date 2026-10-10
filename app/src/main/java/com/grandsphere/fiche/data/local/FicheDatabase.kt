package com.grandsphere.fiche.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.grandsphere.fiche.data.local.dao.ApiKeyDao
import com.grandsphere.fiche.data.local.dao.BanDao
import com.grandsphere.fiche.data.local.dao.DislikeDao
import com.grandsphere.fiche.data.local.dao.DlcDao
import com.grandsphere.fiche.data.local.dao.EpisodeDao
import com.grandsphere.fiche.data.local.dao.InterestNotesDao
import com.grandsphere.fiche.data.local.dao.LibraryPrefsDao
import com.grandsphere.fiche.data.local.dao.OverviewStatsDao
import com.grandsphere.fiche.data.local.dao.RelatedDao
import com.grandsphere.fiche.data.local.dao.SeasonDao
import com.grandsphere.fiche.data.local.dao.TitleDao
import com.grandsphere.fiche.data.local.dao.WatchlistDao
import com.grandsphere.fiche.data.local.entity.ApiKeyEntity
import com.grandsphere.fiche.data.local.entity.BanEntity
import com.grandsphere.fiche.data.local.entity.DislikeEntity
import com.grandsphere.fiche.data.local.entity.DlcEntity
import com.grandsphere.fiche.data.local.entity.EpisodeEntity
import com.grandsphere.fiche.data.local.entity.InterestNotesEntity
import com.grandsphere.fiche.data.local.entity.LibraryPrefsEntity
import com.grandsphere.fiche.data.local.entity.OverviewStatsEntity
import com.grandsphere.fiche.data.local.entity.RelatedTitleEntity
import com.grandsphere.fiche.data.local.entity.SeasonEntity
import com.grandsphere.fiche.data.local.entity.TitleEntity
import com.grandsphere.fiche.data.local.entity.WatchlistEntity

@Database(
    entities = [
        TitleEntity::class,
        SeasonEntity::class,
        EpisodeEntity::class,
        WatchlistEntity::class,
        BanEntity::class,
        DislikeEntity::class,
        RelatedTitleEntity::class,
        OverviewStatsEntity::class,
        InterestNotesEntity::class,
        DlcEntity::class,
        ApiKeyEntity::class,
        LibraryPrefsEntity::class
    ],
    version = 16,
    exportSchema = false
)
abstract class FicheDatabase : RoomDatabase() {
    abstract fun titleDao(): TitleDao
    abstract fun seasonDao(): SeasonDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun banDao(): BanDao
    abstract fun dislikeDao(): DislikeDao
    abstract fun relatedDao(): RelatedDao
    abstract fun overviewStatsDao(): OverviewStatsDao
    abstract fun interestNotesDao(): InterestNotesDao
    abstract fun dlcDao(): DlcDao
    abstract fun apiKeyDao(): ApiKeyDao
    abstract fun libraryPrefsDao(): LibraryPrefsDao
}
