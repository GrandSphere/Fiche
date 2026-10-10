package com.grandsphere.fiche.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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
import kotlinx.coroutines.flow.Flow

@Dao
interface TitleDao {
    @Query("SELECT * FROM titles WHERE mediaType = :mediaType ORDER BY addedAt DESC")
    fun observeByType(mediaType: String): Flow<List<TitleEntity>>

    @Query("SELECT * FROM titles ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<TitleEntity>>

    @Query("SELECT * FROM titles")
    suspend fun getAll(): List<TitleEntity>

    @Query("SELECT * FROM titles WHERE id = :id")
    fun observeById(id: Long): Flow<TitleEntity?>

    @Query("SELECT * FROM titles WHERE id = :id")
    suspend fun getById(id: Long): TitleEntity?

    @Query("SELECT * FROM titles WHERE mediaType = :mediaType AND remoteId = :remoteId LIMIT 1")
    suspend fun getByRemote(mediaType: String, remoteId: String): TitleEntity?

    @Query("SELECT remoteId FROM titles WHERE mediaType = :mediaType")
    suspend fun remoteIds(mediaType: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TitleEntity): Long

    @Update
    suspend fun update(entity: TitleEntity)

    @Query("DELETE FROM titles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT DISTINCT genres FROM titles WHERE mediaType = :mediaType AND genres != ''")
    suspend fun genreBlobs(mediaType: String): List<String>

    @Query("SELECT * FROM titles WHERE mediaType = :mediaType AND recommended = 1 ORDER BY title")
    suspend fun recommended(mediaType: String): List<TitleEntity>

    @Query("SELECT * FROM titles WHERE busyLoading = 1")
    suspend fun busyLoading(): List<TitleEntity>

    @Query("UPDATE titles SET busyLoading = :value WHERE id = :id")
    suspend fun setBusyLoading(id: Long, value: Int)
}

@Dao
interface SeasonDao {
    @Query("SELECT * FROM seasons WHERE titleId = :titleId ORDER BY seasonNumber")
    fun observeForTitle(titleId: Long): Flow<List<SeasonEntity>>

    @Query("SELECT * FROM seasons WHERE titleId = :titleId ORDER BY seasonNumber")
    suspend fun forTitle(titleId: Long): List<SeasonEntity>

    @Query("SELECT * FROM seasons")
    suspend fun getAll(): List<SeasonEntity>

    @Query("SELECT * FROM seasons")
    fun observeAll(): Flow<List<SeasonEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SeasonEntity>)

    @Update
    suspend fun update(entity: SeasonEntity)

    @Query("UPDATE seasons SET notInterested = :notInterested WHERE id = :id")
    suspend fun setNotInterested(id: Long, notInterested: Boolean)

    @Query("DELETE FROM seasons WHERE titleId = :titleId")
    suspend fun deleteForTitle(titleId: Long)
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE titleId = :titleId ORDER BY seasonNumber, episodeNumber")
    fun observeForTitle(titleId: Long): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE titleId = :titleId ORDER BY seasonNumber, episodeNumber")
    suspend fun forTitle(titleId: Long): List<EpisodeEntity>

    @Query("SELECT * FROM episodes")
    suspend fun getAll(): List<EpisodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<EpisodeEntity>)

    @Update
    suspend fun update(entity: EpisodeEntity)

    @Query("UPDATE episodes SET watched = :watched WHERE titleId = :titleId AND seasonNumber = :seasonNumber")
    suspend fun setSeasonWatched(titleId: Long, seasonNumber: Int, watched: Boolean)

    @Query("UPDATE episodes SET watched = :watched WHERE id = :id")
    suspend fun setWatched(id: Long, watched: Boolean)

    @Query(
        """
        UPDATE episodes SET watched = 1
        WHERE titleId = :titleId
          AND airDate IS NOT NULL AND airDate != ''
          AND substr(airDate, 1, 10) <= :today
        """
    )
    suspend fun markAiredWatched(titleId: Long, today: String)

    @Query("SELECT COUNT(*) FROM episodes WHERE titleId = :titleId")
    suspend fun count(titleId: Long): Int

    @Query("SELECT COUNT(*) FROM episodes WHERE titleId = :titleId AND watched = 1")
    suspend fun watchedCount(titleId: Long): Int

    @Query("DELETE FROM episodes WHERE titleId = :titleId")
    suspend fun deleteForTitle(titleId: Long)

    @Transaction
    suspend fun replaceEpisodes(titleId: Long, episodes: List<EpisodeEntity>) {
        deleteForTitle(titleId)
        if (episodes.isNotEmpty()) insertAll(episodes)
    }
}

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist WHERE mediaType = :mediaType ORDER BY addedAt DESC")
    fun observeByType(mediaType: String): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist")
    suspend fun getAll(): List<WatchlistEntity>

    @Query("SELECT * FROM watchlist WHERE mediaType = :mediaType AND remoteId = :remoteId LIMIT 1")
    suspend fun getByRemote(mediaType: String, remoteId: String): WatchlistEntity?

    @Query("SELECT remoteId FROM watchlist WHERE mediaType = :mediaType")
    suspend fun remoteIds(mediaType: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WatchlistEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<WatchlistEntity>)

    @Query("DELETE FROM watchlist WHERE mediaType = :mediaType AND remoteId = :remoteId")
    suspend fun delete(mediaType: String, remoteId: String)

    @Query("DELETE FROM watchlist WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface BanDao {
    @Query("SELECT * FROM banlist WHERE mediaType = :mediaType ORDER BY addedAt DESC")
    fun observeByType(mediaType: String): Flow<List<BanEntity>>

    @Query("SELECT * FROM banlist ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<BanEntity>>

    @Query("SELECT * FROM banlist")
    suspend fun getAll(): List<BanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BanEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<BanEntity>)

    @Query("DELETE FROM banlist WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM banlist WHERE mediaType = :mediaType AND url = :url LIMIT 1")
    suspend fun getByUrl(mediaType: String, url: String): BanEntity?

    @Query(
        """
        SELECT * FROM banlist
        WHERE mediaType = :mediaType AND title = :title
          AND ((:year IS NULL AND year IS NULL) OR year = :year)
        LIMIT 1
        """
    )
    suspend fun getByTitleYear(mediaType: String, title: String, year: Int?): BanEntity?
}

@Dao
interface DislikeDao {
    @Query("SELECT * FROM dislike_list WHERE mediaType = :mediaType ORDER BY addedAt DESC")
    fun observeByType(mediaType: String): Flow<List<DislikeEntity>>

    @Query("SELECT * FROM dislike_list ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<DislikeEntity>>

    @Query("SELECT * FROM dislike_list")
    suspend fun getAll(): List<DislikeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DislikeEntity): Long

    @Query("DELETE FROM dislike_list WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM dislike_list WHERE mediaType = :mediaType AND url = :url LIMIT 1")
    suspend fun getByUrl(mediaType: String, url: String): DislikeEntity?

    @Query(
        """
        SELECT * FROM dislike_list
        WHERE mediaType = :mediaType AND title = :title
          AND ((:year IS NULL AND year IS NULL) OR year = :year)
        LIMIT 1
        """
    )
    suspend fun getByTitleYear(mediaType: String, title: String, year: Int?): DislikeEntity?
}

@Dao
interface ApiKeyDao {
    @Query("SELECT * FROM api_keys")
    fun observeAll(): Flow<List<ApiKeyEntity>>

    @Query("SELECT * FROM api_keys WHERE provider = :provider LIMIT 1")
    suspend fun get(provider: String): ApiKeyEntity?

    @Query("SELECT apiKey FROM api_keys WHERE provider = :provider LIMIT 1")
    suspend fun getKey(provider: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ApiKeyEntity)
}

@Dao
interface LibraryPrefsDao {
    @Query("SELECT * FROM library_prefs WHERE id = 1 LIMIT 1")
    fun observe(): Flow<LibraryPrefsEntity?>

    @Query("SELECT * FROM library_prefs WHERE id = 1 LIMIT 1")
    suspend fun get(): LibraryPrefsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LibraryPrefsEntity)
}

@Dao
interface RelatedDao {
    @Query("SELECT * FROM related_titles WHERE ownerMediaType = :mediaType")
    fun observeByOwnerType(mediaType: String): Flow<List<RelatedTitleEntity>>

    @Query("SELECT * FROM related_titles WHERE relation = :relation")
    fun observeByRelation(relation: String): Flow<List<RelatedTitleEntity>>

    @Query("SELECT * FROM related_titles WHERE ownerRemoteId = :remoteId AND ownerMediaType = :mediaType ORDER BY position, year")
    fun observeForOwner(mediaType: String, remoteId: String): Flow<List<RelatedTitleEntity>>

    @Query("SELECT * FROM related_titles WHERE ownerRemoteId = :remoteId AND ownerMediaType = :mediaType")
    suspend fun forOwner(mediaType: String, remoteId: String): List<RelatedTitleEntity>

    @Query("SELECT * FROM related_titles")
    suspend fun getAll(): List<RelatedTitleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RelatedTitleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RelatedTitleEntity): Long

    @Query("UPDATE related_titles SET watched = :watched WHERE id = :id")
    suspend fun setWatched(id: Long, watched: Boolean)

    @Query("SELECT * FROM related_titles WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RelatedTitleEntity?

    @Query("DELETE FROM related_titles WHERE ownerMediaType = :mediaType AND ownerRemoteId = :remoteId")
    suspend fun deleteForOwner(mediaType: String, remoteId: String)

    @Query("DELETE FROM related_titles WHERE ownerMediaType = :mediaType")
    suspend fun deleteForOwnerMediaType(mediaType: String)

    @Query(
        "SELECT * FROM related_titles WHERE ownerMediaType = :mediaType AND relatedRemoteId = :relatedRemoteId"
    )
    suspend fun forRelated(mediaType: String, relatedRemoteId: String): List<RelatedTitleEntity>
}

@Dao
interface OverviewStatsDao {
    @Query("SELECT * FROM overview_stats WHERE mediaType = :mediaType LIMIT 1")
    fun observe(mediaType: String): Flow<OverviewStatsEntity?>

    @Query("SELECT * FROM overview_stats WHERE mediaType = :mediaType LIMIT 1")
    suspend fun get(mediaType: String): OverviewStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OverviewStatsEntity)
}

@Dao
interface DlcDao {
    @Query("SELECT * FROM dlcs WHERE titleId = :titleId ORDER BY position")
    fun observeForTitle(titleId: Long): Flow<List<DlcEntity>>

    @Query("SELECT * FROM dlcs WHERE titleId = :titleId ORDER BY position")
    suspend fun forTitle(titleId: Long): List<DlcEntity>

    @Query("SELECT * FROM dlcs")
    suspend fun getAll(): List<DlcEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DlcEntity>)

    @Query("DELETE FROM dlcs WHERE titleId = :titleId")
    suspend fun deleteForTitle(titleId: Long)

    @Query("UPDATE dlcs SET completed = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)

    @Transaction
    suspend fun replaceForTitle(titleId: Long, items: List<DlcEntity>) {
        deleteForTitle(titleId)
        if (items.isNotEmpty()) insertAll(items)
    }
}

@Dao
interface InterestNotesDao {
    @Query("SELECT * FROM interest_notes WHERE mediaType = :mediaType LIMIT 1")
    fun observe(mediaType: String): Flow<InterestNotesEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: InterestNotesEntity)
}
