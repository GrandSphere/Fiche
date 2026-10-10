package com.grandsphere.fiche.data.repository

import android.content.Context
import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.grandsphere.fiche.data.backup.AppendTitleSpec
import com.grandsphere.fiche.data.backup.SharedTitle
import com.grandsphere.fiche.util.CatalogLinkKind
import com.grandsphere.fiche.util.CatalogLinkParser
import com.grandsphere.fiche.util.yearFromDate
import com.grandsphere.fiche.data.local.PosterStore
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
import com.grandsphere.fiche.data.prefs.UserPreferencesRepository
import com.grandsphere.fiche.data.remote.RemoteRelated
import com.grandsphere.fiche.data.remote.RemoteTitle
import com.grandsphere.fiche.data.work.EnrichTitleWorker
import com.grandsphere.fiche.domain.model.LibraryFilter
import com.grandsphere.fiche.domain.model.MediaType
import com.grandsphere.fiche.domain.model.RelationType
import com.grandsphere.fiche.domain.model.SortOption
import com.grandsphere.fiche.domain.model.displayCompleted
import com.grandsphere.fiche.domain.model.displayWatching
import com.grandsphere.fiche.util.catalogMatchScore
import com.grandsphere.fiche.util.cleanCatalogOverview
import com.grandsphere.fiche.util.csv
import com.grandsphere.fiche.util.defaultCatalogProvider
import com.grandsphere.fiche.util.displayCatalogStatus
import com.grandsphere.fiche.util.isStrongCatalogMatch
import com.grandsphere.fiche.util.listingBelongsToProvider
import com.grandsphere.fiche.util.movieHasReleased
import com.grandsphere.fiche.util.remoteWebUrl
import com.grandsphere.fiche.util.seasonHasStarted
import com.grandsphere.fiche.util.splitCsv
import com.grandsphere.fiche.util.titleSearchVariants
import com.grandsphere.fiche.util.todayIsoDate
import com.grandsphere.fiche.util.unitsMismatch
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val titles: TitleDao,
    private val seasons: SeasonDao,
    private val episodes: EpisodeDao,
    private val watchlist: WatchlistDao,
    private val bans: BanDao,
    private val dislikes: DislikeDao,
    private val related: RelatedDao,
    private val overviewStats: OverviewStatsDao,
    private val interestNotes: InterestNotesDao,
    private val dlcs: DlcDao,
    private val libraryPrefs: LibraryPrefsDao,
    private val posters: PosterStore,
    private val catalog: CatalogRepository,
    private val prefs: UserPreferencesRepository
) {
    private val pendingMarkWatchedIds = ConcurrentHashMap.newKeySet<Long>()
    fun observeLibrary(type: MediaType): Flow<List<TitleEntity>> = titles.observeByType(type.name)
    fun observeTitle(id: Long) = titles.observeById(id)
    fun observeSeasons(id: Long) = seasons.observeForTitle(id)
    fun observeEpisodes(id: Long) = episodes.observeForTitle(id)
    fun observeDlcs(id: Long) = dlcs.observeForTitle(id)
    fun observeWatchlist(type: MediaType) = watchlist.observeByType(type.name)
    fun observeBans(type: MediaType) = bans.observeByType(type.name)
    fun observeDislikes(type: MediaType) = dislikes.observeByType(type.name)
    fun observeLibraryPrefs(): Flow<LibraryPrefsEntity> = flow {
        ensureJikanMalSplit()
        emitAll(libraryPrefs.observe().map { it ?: LibraryPrefsEntity() })
    }

    suspend fun libraryPrefs(): LibraryPrefsEntity {
        ensureJikanMalSplit()
        return libraryPrefs.get() ?: LibraryPrefsEntity()
    }

    private suspend fun ensureJikanMalSplit() {
        if (prefs.isJikanMalSplitDone()) return
        val current = libraryPrefs.get() ?: LibraryPrefsEntity()
        if (current.animeApi.equals("MAL", ignoreCase = true)) {
            libraryPrefs.upsert(current.copy(animeApi = "JIKAN"))
        }
        prefs.setJikanMalSplitDone()
    }

    suspend fun updateLibraryPrefs(block: (LibraryPrefsEntity) -> LibraryPrefsEntity) {
        val current = libraryPrefs()
        libraryPrefs.upsert(block(current))
    }
    fun observeRelated(type: MediaType) = related.observeByOwnerType(type.name)
    fun observeRelatedForTitle(titleId: Long): Flow<List<RelatedTitleEntity>> =
        observeTitle(titleId).flatMapLatest { title ->
            if (title == null) flowOf(emptyList())
            else related.observeForOwner(title.mediaType, title.remoteId)
        }

    fun observeMovieSequels(titleId: Long): Flow<List<MovieSequelEntry>> =
        combine(
            titles.observeById(titleId),
            related.observeByOwnerType(MediaType.MOVIE.name),
            titles.observeByType(MediaType.MOVIE.name),
            bans.observeByType(MediaType.MOVIE.name)
        ) { title, allRelated, movies, banList ->
            if (title == null || title.mediaType != MediaType.MOVIE.name) {
                emptyList()
            } else {
                val banKeys = banList.mapNotNull { it.remoteKey() }.toSet()
                val banUrls = banList.map { it.url }.toSet()
                val links = allRelated.filter {
                    it.ownerMediaType == title.mediaType && it.ownerRemoteId == title.remoteId
                }
                val byRemote = movies.associateBy { it.remoteId }
                links.map { link ->
                    val sibling = byRemote[link.relatedRemoteId]
                    val catalogUrl = sibling?.catalogUrl
                        ?: remoteWebUrl(MediaType.MOVIE.name, link.relatedRemoteId)
                    val names = listOfNotNull(
                        link.relatedTitle,
                        sibling?.title,
                        sibling?.displayName
                    ).map { it.lowercase() }.toSet()
                    val years = setOfNotNull(link.year, sibling?.year)
                    val banned = link.relatedRemoteId in banKeys ||
                        (sibling?.remoteId != null && sibling.remoteId in banKeys) ||
                        banUrlForRemote(link.relatedRemoteId) in banUrls ||
                        (catalogUrl != null && catalogUrl in banUrls) ||
                        (sibling?.catalogUrl != null && sibling.catalogUrl in banUrls) ||
                        banList.any { ban ->
                            ban.title.lowercase() in names &&
                                (ban.year == null || years.isEmpty() || ban.year in years)
                        }
                    MovieSequelEntry(link, sibling, banned)
                }.sortedWith(sequelReleaseOrder())
            }
        }

    private fun sequelReleaseOrder(): Comparator<MovieSequelEntry> =
        compareBy<MovieSequelEntry>(
            { entry -> entry.title?.firstDate?.take(10).orEmpty().ifBlank { "9999-12-31" } },
            { entry -> entry.link.year ?: Int.MAX_VALUE },
            { entry -> entry.link.position }
        )

    fun observeBanIds(type: MediaType): Flow<Set<String>> =
        bans.observeByType(type.name).map { list -> list.mapNotNull { it.remoteKey() }.toSet() }

    suspend fun titleIdByRemote(type: MediaType, remoteId: String): Long? =
        titles.getByRemote(type.name, remoteId)?.id
    fun observeOverview(type: MediaType): Flow<OverviewStats> =
        combine(
            overviewStats.observe(type.name),
            watchlist.observeByType(type.name),
            bans.observeByType(type.name),
            titles.observeByType(type.name)
        ) { entity, interests, banned, library ->
            val remaining = library
                .filter { it.tracked && !it.displayCompleted() }
                .sumOf { (it.totalMinutes - it.watchedMinutes).coerceAtLeast(0) }
            entity.toStats().copy(
                interestsCount = interests.size,
                bannedCount = banned.size,
                remainingMinutes = remaining
            )
        }
    fun observeNotes(type: MediaType): Flow<String> =
        interestNotes.observe(type.name).map { it?.body.orEmpty() }

    fun observeFiltered(
        type: MediaType,
        filterFlow: Flow<LibraryFilter>,
        combineSequelsFlow: Flow<Boolean> = flowOf(false)
    ): Flow<List<TitleEntity>> {
        return combine(observeLibrary(type), filterFlow, combineSequelsFlow) { items, filter, combineSequels ->
            val filtered = applyFilter(items, filter)
            if (type == MediaType.MOVIE && combineSequels) applyCombineSequels(filtered, items) else filtered
        }
    }

    fun observeStats(type: MediaType): Flow<LibraryStats> =
        combine(observeLibrary(type), seasons.observeAll()) { list, allSeasons ->
            val seasonsByTitle = allSeasons.groupBy { it.titleId }
            val tracked = list.filter { it.countsInOverview(seasonsByTitle[it.id].orEmpty()) }
            LibraryStats(
                total = tracked.size,
                completed = tracked.count { it.displayCompleted() },
                watching = tracked.count { it.displayWatching() },
                notStarted = tracked.count { !it.displayCompleted() && !it.displayWatching() }
            )
        }

    suspend fun persistOverview(type: MediaType) {
        overviewStats.upsert(computeOverview(type).toEntity(type))
    }

    suspend fun persistAllOverviews(onProgress: (suspend (RefreshProgress) -> Unit)? = null) {
        val all = titles.getAll()
        onProgress?.invoke(RefreshProgress(0, all.size.coerceAtLeast(1), "Recalculating"))
        all.forEachIndexed { index, title ->
            onProgress?.invoke(RefreshProgress(index + 1, all.size.coerceAtLeast(1), "Recalculating"))
            val type = runCatching { MediaType.valueOf(title.mediaType) }.getOrNull() ?: return@forEachIndexed
            when {
                type.tracksEpisodes -> recalcProgress(title.id)
                type == MediaType.MOVIE -> recalcMovieFranchiseProgress(title.id)
                type == MediaType.BOOK -> persistBookProgress(title.id)
                type == MediaType.GAME -> persistDlcProgress(title.id)
            }
        }
        MediaType.entries.forEach { persistOverview(it) }
    }

    suspend fun backfillWatchTimeIfNeeded() {
        if (!prefs.isSequelMigrationDone()) {
            migrateSequelTitlesFromRelated()
            prefs.setSequelMigrationDone()
        }
        if (!prefs.isTrackedStatsMigrationDone()) {
            persistAllOverviews()
            prefs.setTrackedStatsMigrationDone()
        }
        val needs = titles.getAll().any { title ->
            val type = runCatching { MediaType.valueOf(title.mediaType) }.getOrNull() ?: return@any false
            when {
                type.tracksEpisodes -> title.totalMinutes == 0 && title.totalUnits > 0
                type == MediaType.MOVIE -> title.runtimeMinutes > 0 && title.totalUnits == 0
                else -> false
            }
        }
        if (needs) persistAllOverviews()
    }

    private suspend fun currentListingUrl(type: MediaType, remoteId: String): String? =
        remoteWebUrl(type.name, remoteId, catalog.providerIdFor(type))

    private fun keepListingUrl(type: MediaType, remoteId: String, existing: String?): String? {
        existing?.takeIf { it.isNotBlank() }?.let { return it }
        return remoteWebUrl(type.name, remoteId, defaultCatalogProvider(type.name))
    }

    suspend fun redownloadMissingPosters(
        onProgress: ((RefreshProgress) -> Unit)? = null
    ) {
        if (!libraryPrefs().storeImagesLocally) return
        val list = titles.getAll()
        list.forEachIndexed { index, title ->
            onProgress?.invoke(RefreshProgress(index + 1, list.size, "Reloading"))
            if (title.localPosterPath.isNullOrBlank() && !title.posterUrl.isNullOrBlank()) {
                ensurePoster(title.id, title.posterUrl)
            }
        }
    }

    suspend fun clearDownloadedPosters() {
        titles.getAll().forEach { title ->
            posters.delete(title.id)
            if (!title.localPosterPath.isNullOrBlank()) {
                titles.update(title.copy(localPosterPath = null, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    suspend fun setStoreImagesLocally(enabled: Boolean) {
        updateLibraryPrefs { it.copy(storeImagesLocally = enabled) }
        if (!enabled) clearDownloadedPosters()
    }

    suspend fun reloadIncomplete(
        onProgress: ((RefreshProgress) -> Unit)? = null
    ): Int {
        val pending = titles.busyLoading()
        pending.forEachIndexed { index, title ->
            onProgress?.invoke(RefreshProgress(index + 1, pending.size, "Reloading"))
            runCatching { enrichTitle(title.id) }
        }
        return pending.size
    }

    suspend fun addToLibrary(type: MediaType, remote: RemoteTitle): Long {
        watchlist.delete(type.name, remote.remoteId)
        val existing = titles.getByRemote(type.name, remote.remoteId)
        if (existing != null) {
            if (existing.busyLoading == 1) {
                enqueueEnrich(existing.id)
                return existing.id
            }
            titles.update(
                existing.copy(
                    hidden = false,
                    tracked = true,
                    busyLoading = 1,
                    title = remote.title.ifBlank { existing.title },
                    year = remote.year ?: existing.year,
                    catalogUrl = existing.catalogUrl ?: currentListingUrl(type, remote.remoteId),
                    posterUrl = remote.posterUrl ?: existing.posterUrl,
                    updatedAt = System.currentTimeMillis()
                )
            )
            enqueueEnrich(existing.id)
            return existing.id
        }
        val stub = TitleEntity(
            mediaType = type.name,
            remoteId = remote.remoteId,
            title = remote.title,
            year = remote.year,
            posterUrl = remote.posterUrl,
            overview = remote.overview,
            author = remote.author,
            genres = csv(remote.genres),
            collectionId = remote.collectionId,
            collectionName = remote.collectionName,
            catalogUrl = currentListingUrl(type, remote.remoteId),
            busyLoading = 1
        )
        val id = titles.insert(stub)
        enqueueEnrich(id)
        return id
    }

    /** Called by [EnrichTitleWorker] — loads full details and clears busyLoading. */
    suspend fun enrichTitle(titleId: Long) {
        val title = titles.getById(titleId) ?: return
        val type = runCatching { MediaType.valueOf(title.mediaType) }.getOrNull() ?: return
        val remote = title.toRemote()
        val detailed = resolveDetailed(type, remote, title)
        val merged = mergeDetailedIntoExisting(title, detailed, remote, type, unhide = true)
            .copy(busyLoading = 0, updatedAt = System.currentTimeMillis())
        titles.update(merged)
        when {
            type.tracksEpisodes -> {
                syncEpisodes(titleId, detailed.remoteId, preserveWatched = true)
                if (pendingMarkWatchedIds.remove(titleId)) {
                    markReleasedToDateWatched(titleId, type)
                }
            }
            type == MediaType.GAME -> syncDlcs(titleId, detailed.remoteId, preserveCompleted = true)
            type == MediaType.MOVIE -> {
                val totalMins = merged.runtimeMinutes.coerceAtLeast(0)
                titles.update(
                    merged.copy(
                        totalUnits = 1,
                        watchedUnits = if (merged.completed) 1 else 0,
                        totalMinutes = totalMins,
                        watchedMinutes = if (merged.completed) totalMins else 0,
                        busyLoading = 0
                    )
                )
                persistRelated(type, detailed)
                recalcMovieFranchiseProgress(titleId)
            }
            type == MediaType.BOOK -> {
                val total = merged.pageCount.takeIf { it > 0 } ?: 1
                titles.update(
                    merged.copy(
                        totalUnits = total,
                        watchedUnits = if (merged.completed) total else merged.watchedUnits,
                        completed = merged.completed,
                        busyLoading = 0
                    )
                )
            }
            else -> { }
        }
        if (type.tracksEpisodes || type == MediaType.GAME) {
            persistRelated(type, detailed)
        }
        ensurePoster(titleId, detailed.posterUrl ?: merged.posterUrl)
        persistOverview(type)
    }

    private fun enqueueEnrich(titleId: Long) {
        val request = OneTimeWorkRequestBuilder<EnrichTitleWorker>()
            .setInputData(workDataOf(EnrichTitleWorker.KEY_TITLE_ID to titleId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            EnrichTitleWorker.uniqueName(titleId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    suspend fun alreadyTracked(type: MediaType, remoteId: String): Boolean =
        titles.getByRemote(type.name, remoteId) != null

    suspend fun appendSpecs(defaultType: MediaType, specs: List<AppendTitleSpec>): AppendResult {
        var added = 0
        var skipped = 0
        val existing = titles.getAll()
        specs.forEach { spec ->
            val url = spec.url?.trim()?.takeIf { it.isNotBlank() }
            if (spec.title.isBlank() && spec.remoteId.isNullOrBlank() && url == null) {
                skipped++
                return@forEach
            }
            val type = spec.mediaType?.let { runCatching { MediaType.valueOf(it) }.getOrNull() } ?: defaultType
            if (url != null && existing.any {
                    it.mediaType == type.name && it.catalogUrl?.equals(url, ignoreCase = true) == true
                }
            ) {
                skipped++
                return@forEach
            }
            if (!spec.remoteId.isNullOrBlank() && titles.getByRemote(type.name, spec.remoteId) != null) {
                skipped++
                return@forEach
            }
            val alreadyTrackedByName = url == null && spec.remoteId.isNullOrBlank() && existing.any { item ->
                item.mediaType == type.name &&
                    item.title.equals(spec.title, ignoreCase = true) &&
                    (spec.year == null || item.year == spec.year)
            }
            if (alreadyTrackedByName) {
                skipped++
                return@forEach
            }
            val remote = resolveAppendRemote(spec, type)
            if (remote == null) {
                skipped++
                return@forEach
            }
            if (titles.getByRemote(type.name, remote.remoteId) != null) {
                skipped++
                return@forEach
            }
            addToLibrary(type, remote)
            added++
        }
        return AppendResult(added, skipped)
    }

    private suspend fun resolveAppendRemote(spec: AppendTitleSpec, type: MediaType): RemoteTitle? {
        val url = spec.url?.trim()?.takeIf { it.isNotBlank() }
        if (url != null) {
            val fromUrl = resolveSharedRemote(
                SharedTitle(title = spec.title, category = type.name, url = url),
                type
            )
            if (fromUrl != null) return fromUrl
        }
        if (!spec.remoteId.isNullOrBlank()) {
            return runCatching { catalog.details(type, spec.remoteId) }.getOrNull()
                ?: RemoteTitle(
                    mediaType = type.name,
                    remoteId = spec.remoteId,
                    title = spec.title.ifBlank { spec.remoteId },
                    year = spec.year
                )
        }
        val hits = runCatching { catalog.search(type, spec.title) }.getOrDefault(emptyList())
        return hits.firstOrNull { spec.year == null || it.year == spec.year } ?: hits.firstOrNull()
    }

    suspend fun importSharedTitles(specs: List<SharedTitle>): AppendResult {
        var added = 0
        var skipped = 0
        val existing = titles.getAll()
        specs.forEach { spec ->
            if (spec.title.isBlank()) {
                skipped++
                return@forEach
            }
            if (sharedTitleExists(spec, existing)) {
                skipped++
                return@forEach
            }
            val type = runCatching { MediaType.valueOf(spec.category) }.getOrNull()
            if (type == null) {
                skipped++
                return@forEach
            }
            val remote = resolveSharedRemote(spec, type)
            if (remote == null) {
                skipped++
                return@forEach
            }
            if (titles.getByRemote(type.name, remote.remoteId) != null) {
                skipped++
                return@forEach
            }
            addSharedToLibrary(type, remote, spec)
            added++
        }
        return AppendResult(added, skipped)
    }

    suspend fun compareSharedTitles(specs: List<SharedTitle>): List<ShareImportComparison> {
        val existing = titles.getAll()
        return specs.map { spec ->
            val inLibrary = sharedTitleExists(spec, existing)
            val match = existing.firstOrNull { item ->
                item.mediaType == spec.category && (
                    item.title.equals(spec.title, ignoreCase = true) ||
                        item.displayName.equals(spec.title, ignoreCase = true) ||
                        (!spec.url.isNullOrBlank() &&
                            item.catalogUrl?.equals(spec.url, ignoreCase = true) == true)
                    )
            }
            val watched = match?.displayCompleted() == true
            ShareImportComparison(
                shared = spec,
                alreadyInLibrary = inLibrary,
                alreadyWatched = watched,
                selected = !inLibrary
            )
        }
    }

    private fun sharedTitleExists(spec: SharedTitle, existing: List<TitleEntity>): Boolean {
        val typeName = spec.category
        val library = existing.filter { it.mediaType == typeName }
        val url = spec.url?.trim()?.takeIf { it.isNotBlank() }
        if (url != null && library.any { it.catalogUrl?.equals(url, ignoreCase = true) == true }) {
            return true
        }
        return library.any {
            it.title.equals(spec.title, ignoreCase = true) ||
                it.displayName.equals(spec.title, ignoreCase = true)
        }
    }

    private suspend fun resolveSharedRemote(spec: SharedTitle, type: MediaType): RemoteTitle? {
        val link = spec.url?.let { url ->
            runCatching { CatalogLinkParser.parse(url, Uri.parse(url)) }.getOrNull()
        }
        val fromCatalog = when (link?.kind) {
            CatalogLinkKind.TMDB_MOVIE -> runCatching { catalog.details(MediaType.MOVIE, link.id) }.getOrNull()
            CatalogLinkKind.TVMAZE_SHOW -> {
                val showType = if (type == MediaType.ANIME) MediaType.ANIME else MediaType.SERIES
                runCatching { catalog.details(showType, link.id) }.getOrNull()
            }
            CatalogLinkKind.OPEN_LIBRARY_WORK -> runCatching { catalog.details(MediaType.BOOK, link.id) }.getOrNull()
            CatalogLinkKind.RAWG_GAME -> runCatching { catalog.details(MediaType.GAME, link.id) }.getOrNull()
            else -> null
        }
        val genres = splitCsv(spec.genres.orEmpty())
        return fromCatalog?.let { hit ->
            hit.copy(
                title = spec.title.ifBlank { hit.title },
                overview = spec.overview ?: hit.overview,
                author = spec.author ?: hit.author,
                firstDate = spec.firstDate ?: hit.firstDate,
                lastDate = if (type.tracksEpisodes) spec.lastDate ?: hit.lastDate else hit.lastDate,
                genres = genres.ifEmpty { hit.genres },
                year = yearFromDate(spec.firstDate) ?: hit.year
            )
        } ?: RemoteTitle(
            mediaType = type.name,
            remoteId = link?.id ?: sharedFallbackRemoteId(spec),
            title = spec.title,
            year = yearFromDate(spec.firstDate),
            overview = spec.overview,
            author = spec.author,
            firstDate = spec.firstDate,
            lastDate = if (type.tracksEpisodes) spec.lastDate else null,
            genres = genres
        )
    }

    private fun sharedFallbackRemoteId(spec: SharedTitle): String {
        val url = spec.url?.trim()?.takeIf { it.isNotBlank() }
        if (url != null) return "url:${url.hashCode()}"
        return "shared:${spec.category}:${spec.title.lowercase().hashCode()}"
    }

    private suspend fun addSharedToLibrary(type: MediaType, remote: RemoteTitle, spec: SharedTitle) {
        val detailed = resolveDetailed(type, remote, null)
        val entity = detailed.toEntity(type).copy(
            overview = spec.overview ?: detailed.overview,
            author = spec.author ?: detailed.author,
            firstDate = spec.firstDate ?: detailed.firstDate,
            lastDate = if (type.tracksEpisodes) spec.lastDate ?: detailed.lastDate else null,
            genres = spec.genres?.takeIf { it.isNotBlank() } ?: csv(detailed.genres),
            catalogUrl = spec.url ?: currentListingUrl(type, remote.remoteId),
            displayTitle = spec.title.takeIf { it.isNotBlank() }
        )
        val id = titles.insert(entity)
        when {
            type.tracksEpisodes -> syncEpisodes(id, detailed.remoteId, preserveWatched = false)
            type == MediaType.GAME -> syncDlcs(id, detailed.remoteId, preserveCompleted = false)
            else -> {
                val totalMins = if (type == MediaType.MOVIE) entity.runtimeMinutes.coerceAtLeast(0) else 0
                val total = if (type == MediaType.BOOK) entity.pageCount.takeIf { it > 0 } ?: 1 else 1
                titles.update(
                    entity.copy(
                        id = id,
                        totalUnits = total,
                        watchedUnits = 0,
                        completed = false,
                        totalMinutes = totalMins,
                        watchedMinutes = 0
                    )
                )
            }
        }
        persistRelated(type, detailed)
        ensurePoster(id, entity.posterUrl)
        persistOverview(type)
    }

    suspend fun refreshTitle(titleId: Long): Boolean {
        val title = titles.getById(titleId) ?: return false
        val type = runCatching { MediaType.valueOf(title.mediaType) }.getOrNull() ?: return false
        return runCatching {
            val detailed = resolveDetailed(type, title.toRemote(), title)
            val keepPosterCleared = title.posterUrl == ""
            when {
                type == MediaType.MOVIE -> {
                    titles.update(
                        mergeDetailedIntoExisting(title, detailed, title.toRemote(), type).copy(
                            posterUrl = if (keepPosterCleared) "" else (detailed.posterUrl ?: title.posterUrl),
                            catalogUrl = keepListingUrl(type, title.remoteId, title.catalogUrl)
                        )
                    )
                    persistRelated(type, detailed)
                    recalcMovieFranchiseProgress(titleId)
                }
                type == MediaType.GAME -> {
                    titles.update(
                        mergeDetailedIntoExisting(title, detailed, title.toRemote(), type).copy(
                            posterUrl = if (keepPosterCleared) "" else (detailed.posterUrl ?: title.posterUrl)
                        )
                    )
                    syncDlcs(titleId, title.remoteId, preserveCompleted = true)
                    persistRelated(type, detailed)
                }
                type == MediaType.BOOK -> {
                    titles.update(
                        mergeDetailedIntoExisting(title, detailed, title.toRemote(), type).copy(
                            posterUrl = if (keepPosterCleared) "" else (detailed.posterUrl ?: title.posterUrl)
                        )
                    )
                }
                type.tracksEpisodes -> {
                    titles.update(
                        title.copy(
                            status = displayCatalogStatus(detailed.status) ?: title.status,
                            cancelled = detailed.cancelled,
                            firstDate = detailed.firstDate ?: title.firstDate,
                            lastDate = detailed.lastDate ?: title.lastDate,
                            posterUrl = if (keepPosterCleared) "" else (detailed.posterUrl ?: title.posterUrl),
                            runtimeMinutes = detailed.runtimeMinutes.takeIf { it > 0 } ?: title.runtimeMinutes,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    syncEpisodes(titleId, title.remoteId, preserveWatched = true)
                    persistRelated(type, detailed)
                }
            }
            if (!keepPosterCleared && title.localPosterPath.isNullOrBlank()) {
                ensurePoster(titleId, detailed.posterUrl ?: title.posterUrl)
            }
            if (type == MediaType.MOVIE) reconcileMovieFranchises()
            persistOverview(type)
            true
        }.getOrDefault(false)
    }

    /** Bulk catalogue update: listing metadata and units only (no related, overview, or franchise work). */
    private suspend fun refreshListing(titleId: Long): Boolean {
        val title = titles.getById(titleId) ?: return false
        val type = runCatching { MediaType.valueOf(title.mediaType) }.getOrNull() ?: return false
        return runCatching {
            val detailed = resolveDetailed(type, title.toRemote(), title)
            val keepPosterCleared = title.posterUrl == ""
            when {
                type == MediaType.MOVIE -> {
                    titles.update(
                        mergeDetailedIntoExisting(title, detailed, title.toRemote(), type).copy(
                            posterUrl = if (keepPosterCleared) "" else (detailed.posterUrl ?: title.posterUrl),
                            catalogUrl = keepListingUrl(type, title.remoteId, title.catalogUrl)
                        )
                    )
                }
                type == MediaType.GAME -> {
                    titles.update(
                        mergeDetailedIntoExisting(title, detailed, title.toRemote(), type).copy(
                            posterUrl = if (keepPosterCleared) "" else (detailed.posterUrl ?: title.posterUrl)
                        )
                    )
                    syncDlcs(titleId, title.remoteId, preserveCompleted = true)
                }
                type == MediaType.BOOK -> {
                    titles.update(
                        mergeDetailedIntoExisting(title, detailed, title.toRemote(), type).copy(
                            posterUrl = if (keepPosterCleared) "" else (detailed.posterUrl ?: title.posterUrl)
                        )
                    )
                }
                type.tracksEpisodes -> {
                    titles.update(
                        title.copy(
                            status = displayCatalogStatus(detailed.status) ?: title.status,
                            cancelled = detailed.cancelled,
                            firstDate = detailed.firstDate ?: title.firstDate,
                            lastDate = detailed.lastDate ?: title.lastDate,
                            posterUrl = if (keepPosterCleared) "" else (detailed.posterUrl ?: title.posterUrl),
                            runtimeMinutes = detailed.runtimeMinutes.takeIf { it > 0 } ?: title.runtimeMinutes,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    syncEpisodes(titleId, title.remoteId, preserveWatched = true)
                }
            }
            if (!keepPosterCleared && title.localPosterPath.isNullOrBlank()) {
                ensurePoster(titleId, detailed.posterUrl ?: title.posterUrl)
            }
            true
        }.getOrDefault(false)
    }

    private suspend fun retargetListing(
        titleId: Long,
        remote: RemoteTitle,
        fetchDetails: Boolean = true
    ): Result<Unit> {
        val title = titles.getById(titleId)
            ?: return Result.failure(IllegalStateException("Title not found"))
        val type = runCatching { MediaType.valueOf(title.mediaType) }.getOrNull()
            ?: return Result.failure(IllegalStateException("Unknown type"))
        val newId = remote.remoteId.trim()
        if (newId.isBlank()) {
            return Result.failure(IllegalStateException("Missing catalog id"))
        }
        val other = titles.getByRemote(type.name, newId)
        if (other != null && other.id != titleId) {
            return Result.failure(IllegalStateException("That listing is already in your library"))
        }
        if (newId == title.remoteId) {
            val ok = refreshListing(titleId)
            val latest = titles.getById(titleId)
            if (latest != null) {
                titles.update(latest.copy(catalogUrl = currentListingUrl(type, newId)))
            }
            return if (ok) Result.success(Unit) else Result.failure(IllegalStateException("Could not refresh"))
        }
        return runCatching {
            val detailed = if (fetchDetails) {
                runCatching { catalog.details(type, newId) }.getOrElse { remote }
            } else {
                remote
            }
            val url = currentListingUrl(type, newId)
            val merged = mergeDetailedIntoExisting(title, detailed, remote, type).copy(
                remoteId = newId,
                catalogUrl = url,
                posterUrl = detailed.posterUrl ?: remote.posterUrl ?: title.posterUrl,
                localPosterPath = null,
                updatedAt = System.currentTimeMillis()
            )
            titles.update(merged)
            when {
                type.tracksEpisodes -> syncEpisodes(titleId, newId, preserveWatched = true)
                type == MediaType.GAME -> syncDlcs(titleId, newId, preserveCompleted = true)
                type == MediaType.MOVIE -> {
                    val totalMins = merged.runtimeMinutes.coerceAtLeast(0)
                    val current = titles.getById(titleId) ?: merged
                    titles.update(
                        current.copy(
                            totalUnits = 1,
                            watchedUnits = if (current.completed) 1 else current.watchedUnits,
                            totalMinutes = totalMins,
                            watchedMinutes = if (current.completed) totalMins else current.watchedMinutes
                        )
                    )
                }
                type == MediaType.BOOK -> {
                    val current = titles.getById(titleId) ?: merged
                    val total = current.pageCount.takeIf { it > 0 } ?: 1
                    titles.update(
                        current.copy(
                            totalUnits = total,
                            watchedUnits = if (current.completed) total else current.watchedUnits.coerceAtMost(total)
                        )
                    )
                }
            }
            ensurePoster(titleId, detailed.posterUrl ?: remote.posterUrl, replace = true)
        }
    }

    suspend fun retargetTitle(titleId: Long, remote: RemoteTitle): Result<Unit> {
        val title = titles.getById(titleId)
            ?: return Result.failure(IllegalStateException("Title not found"))
        val type = runCatching { MediaType.valueOf(title.mediaType) }.getOrNull()
            ?: return Result.failure(IllegalStateException("Unknown type"))
        val newId = remote.remoteId.trim()
        if (newId.isBlank()) {
            return Result.failure(IllegalStateException("Missing catalog id"))
        }
        val other = titles.getByRemote(type.name, newId)
        if (other != null && other.id != titleId) {
            return Result.failure(IllegalStateException("That listing is already in your library"))
        }
        if (newId == title.remoteId) {
            val ok = refreshTitle(titleId)
            val latest = titles.getById(titleId)
            if (latest != null) {
                titles.update(
                    latest.copy(catalogUrl = currentListingUrl(type, newId))
                )
            }
            return if (ok) Result.success(Unit) else Result.failure(IllegalStateException("Could not refresh"))
        }
        return runCatching {
            val detailed = runCatching { catalog.details(type, newId) }.getOrElse { remote }
            val url = currentListingUrl(type, newId)
            val merged = mergeDetailedIntoExisting(title, detailed, remote, type).copy(
                remoteId = newId,
                catalogUrl = url,
                posterUrl = detailed.posterUrl ?: remote.posterUrl ?: title.posterUrl,
                localPosterPath = null,
                updatedAt = System.currentTimeMillis()
            )
            titles.update(merged)
            when {
                type.tracksEpisodes -> {
                    syncEpisodes(titleId, newId, preserveWatched = true)
                    persistRelated(type, detailed)
                }
                type == MediaType.GAME -> {
                    syncDlcs(titleId, newId, preserveCompleted = true)
                    persistRelated(type, detailed)
                }
                type == MediaType.MOVIE -> {
                    val totalMins = merged.runtimeMinutes.coerceAtLeast(0)
                    val current = titles.getById(titleId) ?: merged
                    titles.update(
                        current.copy(
                            totalUnits = 1,
                            watchedUnits = if (current.completed) 1 else current.watchedUnits,
                            totalMinutes = totalMins,
                            watchedMinutes = if (current.completed) totalMins else current.watchedMinutes
                        )
                    )
                    persistRelated(type, detailed)
                    recalcMovieFranchiseProgress(titleId)
                    reconcileMovieFranchises()
                }
                type == MediaType.BOOK -> {
                    val current = titles.getById(titleId) ?: merged
                    val total = current.pageCount.takeIf { it > 0 } ?: 1
                    titles.update(
                        current.copy(
                            totalUnits = total,
                            watchedUnits = if (current.completed) total else current.watchedUnits.coerceAtMost(total)
                        )
                    )
                }
            }
            ensurePoster(titleId, detailed.posterUrl ?: remote.posterUrl, replace = true)
            persistOverview(type)
        }
    }

    suspend fun setOverview(titleId: Long, text: String) {
        val title = titles.getById(titleId) ?: return
        titles.update(
            title.copy(
                overview = text.trim().ifBlank { null },
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun addAsWatched(type: MediaType, remote: RemoteTitle): Long {
        val id = addToLibrary(type, remote)
        pendingMarkWatchedIds.add(id)
        markReleasedToDateWatched(id, type)
        return id
    }

    private suspend fun markReleasedToDateWatched(titleId: Long, type: MediaType) {
        if (!type.tracksEpisodes) {
            setUnitWatched(titleId, true)
            return
        }
        episodes.markAiredWatched(titleId, todayIsoDate())
        recalcProgressThenOverview(titleId)
    }

    suspend fun addToWatchlist(type: MediaType, remote: RemoteTitle) {
        if (titles.getByRemote(type.name, remote.remoteId) != null) return
        if (bans.getByUrl(type.name, banUrlForRemote(remote.remoteId)) != null) return
        watchlist.insert(
            WatchlistEntity(
                mediaType = type.name,
                remoteId = remote.remoteId,
                title = remote.title,
                year = remote.year,
                posterUrl = remote.posterUrl,
                overview = remote.overview,
                genres = csv(remote.genres),
                collectionId = remote.collectionId,
                collectionName = remote.collectionName
            )
        )
    }

    suspend fun moveWatchlistToLibrary(item: WatchlistEntity) {
        val remote = RemoteTitle(
            mediaType = item.mediaType,
            remoteId = item.remoteId,
            title = item.title,
            year = item.year,
            posterUrl = item.posterUrl,
            overview = item.overview,
            genres = splitCsv(item.genres),
            collectionId = item.collectionId,
            collectionName = item.collectionName
        )
        addToLibrary(MediaType.valueOf(item.mediaType), remote)
    }

    suspend fun removeWatchlist(item: WatchlistEntity) {
        watchlist.deleteById(item.id)
    }

    suspend fun removeWatchlist(type: MediaType, remoteId: String) {
        watchlist.delete(type.name, remoteId)
    }

    suspend fun ban(type: MediaType, remote: RemoteTitle) {
        watchlist.delete(type.name, remote.remoteId)
        bans.insert(
            BanEntity(
                mediaType = type.name,
                title = remote.title,
                year = remote.year,
                url = banUrlForRemote(remote.remoteId)
            )
        )
    }

    suspend fun dislike(type: MediaType, remote: RemoteTitle) {
        watchlist.delete(type.name, remote.remoteId)
        dislikes.insert(
            DislikeEntity(
                mediaType = type.name,
                title = remote.title,
                year = remote.year,
                url = banUrlForRemote(remote.remoteId)
            )
        )
    }

    suspend fun undislike(id: Long) = dislikes.delete(id)

    suspend fun unban(id: Long) = bans.delete(id)

    suspend fun banLibraryTitle(titleId: Long) {
        val title = titles.getById(titleId) ?: return
        val type = runCatching { MediaType.valueOf(title.mediaType) }.getOrNull() ?: return
        val collectionId = title.collectionId
        val remoteId = title.remoteId
        ban(type, title.toRemote())
        deleteTitle(titleId)
        if (type == MediaType.MOVIE) {
            recalcOwnersLinkingSequel(remoteId)
            if (!collectionId.isNullOrBlank()) {
                titles.getAll()
                    .filter { it.mediaType == MediaType.MOVIE.name && it.collectionId == collectionId }
                    .forEach { recalcMovieFranchiseProgress(it.id) }
            }
        }
    }

    suspend fun dislikeLibraryTitle(titleId: Long) {
        val title = titles.getById(titleId) ?: return
        val type = runCatching { MediaType.valueOf(title.mediaType) }.getOrNull() ?: return
        dislike(type, title.toRemote())
    }

    suspend fun bulkDislike(ids: List<Long>) {
        ids.forEach { dislikeLibraryTitle(it) }
    }

    suspend fun bulkBan(ids: List<Long>) {
        ids.forEach { banLibraryTitle(it) }
    }

    suspend fun isBanned(type: MediaType, remote: RemoteTitle): Boolean =
        matchBanOrDislike(
            bans.getByUrl(type.name, banUrlForRemote(remote.remoteId)),
            bans.getByTitleYear(type.name, remote.title, remote.year)
        ) || matchBanOrDislike(
            bans.getByUrl(type.name, currentListingUrl(type, remote.remoteId).orEmpty()),
            null
        )

    suspend fun isDisliked(type: MediaType, remote: RemoteTitle): Boolean =
        matchBanOrDislike(
            dislikes.getByUrl(type.name, banUrlForRemote(remote.remoteId)),
            dislikes.getByTitleYear(type.name, remote.title, remote.year)
        )

    private fun matchBanOrDislike(byUrl: Any?, byTitle: Any?): Boolean =
        byUrl != null || byTitle != null

    suspend fun isRemoteBanned(type: MediaType, remoteId: String, title: String? = null, year: Int? = null): Boolean {
        if (bans.getByUrl(type.name, banUrlForRemote(remoteId)) != null) return true
        currentListingUrl(type, remoteId)?.let { url ->
            if (bans.getByUrl(type.name, url) != null) return true
        }
        if (!title.isNullOrBlank()) {
            if (bans.getByTitleYear(type.name, title, year) != null) return true
            if (year != null && bans.getByTitleYear(type.name, title, null) != null) return true
        }
        return false
    }

    suspend fun setSeasonNotInterested(seasonId: Long, titleId: Long, notInterested: Boolean) {
        seasons.setNotInterested(seasonId, notInterested)
        recalcProgressThenOverview(titleId)
    }

    suspend fun removeMovieCollection(titleId: Long) {
        val title = titles.getById(titleId) ?: return
        if (title.mediaType != MediaType.MOVIE.name) {
            deleteTitle(titleId)
            return
        }
        val collectionId = title.collectionId
        if (collectionId.isNullOrBlank()) {
            deleteTitle(titleId)
            return
        }
        val members = titles.getAll().filter {
            it.mediaType == MediaType.MOVIE.name && it.collectionId == collectionId
        }
        members.forEach { deleteTitle(it.id) }
    }

    suspend fun deleteTitle(id: Long) {
        val title = titles.getById(id) ?: return
        titles.delete(id)
        posters.delete(id)
        runCatching { persistOverview(MediaType.valueOf(title.mediaType)) }
    }

    suspend fun setEpisodeWatched(episodeId: Long, titleId: Long, watched: Boolean) {
        episodes.setWatched(episodeId, watched)
        recalcProgressThenOverview(titleId)
    }

    suspend fun setSeasonWatched(titleId: Long, seasonNumber: Int, watched: Boolean) {
        episodes.setSeasonWatched(titleId, seasonNumber, watched)
        recalcProgressThenOverview(titleId)
    }

    suspend fun setUnitWatched(titleId: Long, watched: Boolean) {
        val title = titles.getById(titleId) ?: return
        val type = runCatching { MediaType.valueOf(title.mediaType) }.getOrNull()
        when (type) {
            MediaType.BOOK -> {
                val total = title.pageCount.takeIf { it > 0 } ?: 1
                titles.update(
                    title.copy(
                        watchedUnits = if (watched) total else 0,
                        totalUnits = total,
                        completed = watched,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            MediaType.GAME -> {
                titles.update(
                    title.copy(
                        completed = watched,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                persistDlcProgress(titleId)
            }
            MediaType.MOVIE -> {
                val totalMins = title.runtimeMinutes.coerceAtLeast(0)
                titles.update(
                    title.copy(
                        completed = watched,
                        watchedMinutes = if (watched) totalMins else 0,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                recalcMovieFranchiseProgress(titleId)
                recalcOwnersLinkingSequel(title.remoteId)
            }
            else -> {
                val totalMins = title.runtimeMinutes.coerceAtLeast(0)
                titles.update(
                    title.copy(
                        watchedUnits = if (watched) 1 else 0,
                        totalUnits = 1,
                        completed = watched,
                        totalMinutes = totalMins,
                        watchedMinutes = if (watched) totalMins else 0,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        runCatching { persistOverview(MediaType.valueOf(title.mediaType)) }
    }

    suspend fun setDlcCompleted(dlcId: Long, titleId: Long, completed: Boolean) {
        dlcs.setCompleted(dlcId, completed)
        persistDlcProgress(titleId)
        val title = titles.getById(titleId) ?: return
        runCatching { persistOverview(MediaType.valueOf(title.mediaType)) }
    }

    suspend fun setRelatedWatched(relatedId: Long, watched: Boolean) {
        val row = related.getById(relatedId) ?: return
        if (row.ownerMediaType != MediaType.MOVIE.name) {
            related.setWatched(relatedId, watched)
            return
        }
        setSequelWatched(row.relatedRemoteId, watched)
    }

    suspend fun openSequelTitle(link: RelatedTitleEntity): Long {
        var sibling = titles.getByRemote(MediaType.MOVIE.name, link.relatedRemoteId)
        if (sibling == null) {
            sibling = ensureSequelTitleFromRelated(link, false)
        }
        if (sibling.hidden || !sibling.tracked) {
            titles.update(
                sibling.copy(
                    hidden = false,
                    tracked = true,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        return sibling.id
    }

    suspend fun setSequelWatched(relatedRemoteId: String, watched: Boolean) {
        var sibling = titles.getByRemote(MediaType.MOVIE.name, relatedRemoteId)
        if (sibling == null) {
            val bootstrap = related.forRelated(MediaType.MOVIE.name, relatedRemoteId).firstOrNull()
                ?: return
            sibling = ensureSequelTitleFromRelated(bootstrap, watched)
        }
        if (!movieHasReleased(sibling.firstDate, sibling.year)) return
        val runtime = sibling.runtimeMinutes.coerceAtLeast(0)
        val standalone = !sibling.countSequelsInCompletion || sibling.totalUnits <= 1
        titles.update(
            sibling.copy(
                completed = watched,
                watchedUnits = if (standalone) (if (watched) 1 else 0) else sibling.watchedUnits,
                watchedMinutes = if (watched) runtime else 0,
                updatedAt = System.currentTimeMillis()
            )
        )
        syncRelatedWatchedFlags(relatedRemoteId, watched)
        recalcOwnersLinkingSequel(relatedRemoteId)
        recalcMovieFranchiseProgress(sibling.id)
        persistOverview(MediaType.MOVIE)
    }

    suspend fun setSequelNotInterested(relatedRemoteId: String, notInterested: Boolean) {
        var sibling = titles.getByRemote(MediaType.MOVIE.name, relatedRemoteId)
        if (sibling == null) {
            val bootstrap = related.forRelated(MediaType.MOVIE.name, relatedRemoteId).firstOrNull()
                ?: return
            sibling = ensureSequelTitleFromRelated(bootstrap, false)
        }
        titles.update(
            sibling.copy(
                notInterested = notInterested,
                updatedAt = System.currentTimeMillis()
            )
        )
        recalcOwnersLinkingSequel(relatedRemoteId)
        recalcMovieFranchiseProgress(sibling.id)
        persistOverview(MediaType.MOVIE)
    }

    private suspend fun syncRelatedWatchedFlags(relatedRemoteId: String, watched: Boolean) {
        related.forRelated(MediaType.MOVIE.name, relatedRemoteId).forEach { link ->
            related.setWatched(link.id, watched)
        }
    }

    suspend fun setHidden(titleId: Long, hidden: Boolean) {
        val title = titles.getById(titleId) ?: return
        titles.update(
            title.copy(
                hidden = hidden,
                tracked = !hidden,
                updatedAt = System.currentTimeMillis()
            )
        )
        val type = runCatching { MediaType.valueOf(title.mediaType) }.getOrNull()
        if (type == MediaType.MOVIE) {
            recalcOwnersLinkingSequel(title.remoteId)
            recalcMovieFranchiseProgress(titleId)
        }
        type?.let { persistOverview(it) }
    }

    suspend fun bulkSetHidden(ids: List<Long>, hidden: Boolean) {
        ids.forEach { setHidden(it, hidden) }
    }

    suspend fun bulkDelete(ids: List<Long>) {
        val types = mutableSetOf<MediaType>()
        ids.forEach { id ->
            val title = titles.getById(id) ?: return@forEach
            titles.delete(id)
            posters.delete(id)
            runCatching { MediaType.valueOf(title.mediaType) }.getOrNull()?.let { types += it }
        }
        types.forEach { persistOverview(it) }
    }

    suspend fun bulkMoveToInterests(ids: List<Long>) {
        ids.forEach { moveToInterests(it) }
    }

    suspend fun bulkMarkWatched(ids: List<Long>) {
        ids.forEach { setUnitWatched(it, true) }
    }

    suspend fun setCountSequelsInCompletion(titleId: Long, enabled: Boolean) {
        val title = titles.getById(titleId) ?: return
        titles.update(title.copy(countSequelsInCompletion = enabled, updatedAt = System.currentTimeMillis()))
        recalcMovieFranchiseProgress(titleId)
        persistOverview(MediaType.MOVIE)
    }

    suspend fun setBookPage(titleId: Long, page: Int) {
        val title = titles.getById(titleId) ?: return
        if (title.mediaType != MediaType.BOOK.name) return
        val total = title.pageCount.coerceAtLeast(0)
        if (total <= 0) return
        val current = page.coerceIn(0, total)
        titles.update(
            title.copy(
                watchedUnits = current,
                totalUnits = total,
                completed = current >= total,
                updatedAt = System.currentTimeMillis()
            )
        )
        persistOverview(MediaType.BOOK)
    }

    suspend fun moveToInterests(titleId: Long) {
        val title = titles.getById(titleId) ?: return
        if (watchlist.getByRemote(title.mediaType, title.remoteId) == null) {
            watchlist.insert(
                WatchlistEntity(
                    mediaType = title.mediaType,
                    remoteId = title.remoteId,
                    title = title.displayName,
                    year = title.year,
                    posterUrl = title.posterUrl,
                    overview = title.overview,
                    genres = title.genres,
                    collectionId = title.collectionId,
                    collectionName = title.collectionName
                )
            )
        }
        deleteTitle(titleId)
    }

    suspend fun setDisplayTitle(titleId: Long, value: String?) {
        val title = titles.getById(titleId) ?: return
        val cleaned = value?.trim()?.takeIf { it.isNotBlank() && !it.equals(title.title, ignoreCase = false) }
        titles.update(title.copy(displayTitle = cleaned, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setRating(titleId: Long, rating: Float?) {
        val title = titles.getById(titleId) ?: return
        titles.update(title.copy(userRating = rating, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setMoreOfThis(titleId: Long, value: Boolean) {
        val title = titles.getById(titleId) ?: return
        titles.update(title.copy(moreOfThis = value, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setRecommended(titleId: Long, value: Boolean) {
        val title = titles.getById(titleId) ?: return
        titles.update(title.copy(recommended = value, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setCustomPoster(titleId: Long, uri: android.net.Uri) {
        val title = titles.getById(titleId) ?: return
        val path = posters.importFromUri(titleId, uri) ?: return
        titles.update(title.copy(localPosterPath = path, updatedAt = System.currentTimeMillis()))
    }

    suspend fun clearPoster(titleId: Long) {
        val title = titles.getById(titleId) ?: return
        posters.delete(titleId)
        titles.update(title.copy(posterUrl = "", localPosterPath = null, updatedAt = System.currentTimeMillis()))
    }

    suspend fun resetPoster(titleId: Long) {
        val title = titles.getById(titleId) ?: return
        val type = runCatching { MediaType.valueOf(title.mediaType) }.getOrNull() ?: return
        val detailed = runCatching { catalog.details(type, title.remoteId) }.getOrNull() ?: return
        posters.delete(titleId)
        val path = ensurePoster(titleId, detailed.posterUrl, replace = true)
        titles.update(
            title.copy(
                posterUrl = detailed.posterUrl,
                localPosterPath = path,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun resetOverview(titleId: Long) {
        val title = titles.getById(titleId) ?: return
        val type = runCatching { MediaType.valueOf(title.mediaType) }.getOrNull() ?: return
        val detailed = runCatching { catalog.details(type, title.remoteId) }.getOrNull() ?: return
        titles.update(title.copy(overview = detailed.overview, updatedAt = System.currentTimeMillis()))
    }

    suspend fun saveNotes(type: MediaType, body: String) {
        interestNotes.upsert(
            InterestNotesEntity(
                mediaType = type.name,
                body = body,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun recommended(type: MediaType): List<TitleEntity> = titles.recommended(type.name)

    suspend fun allTitles(): List<TitleEntity> = titles.getAll()

    suspend fun refreshLibrary(
        type: MediaType,
        onProgress: ((RefreshProgress) -> Unit)? = null
    ): RefreshResult {
        if (type == MediaType.BOOK) {
            val list = titles.getAll().filter { it.mediaType == type.name }
            var updated = 0
            list.forEachIndexed { index, title ->
                onProgress?.invoke(RefreshProgress(index + 1, list.size))
                if (refreshTitle(title.id)) updated++
                delay(250)
            }
            persistOverview(type)
            return RefreshResult(updated, 0)
        }
        if (type == MediaType.GAME) {
            val list = titles.getAll().filter { it.mediaType == type.name }
            var updated = 0
            list.forEachIndexed { index, title ->
                onProgress?.invoke(RefreshProgress(index + 1, list.size))
                runCatching {
                    val detailed = catalog.details(type, title.remoteId)
                    val keepPosterCleared = title.posterUrl == ""
                    titles.update(
                        title.copy(
                            overview = detailed.overview ?: title.overview,
                            author = detailed.author ?: title.author,
                            year = detailed.year ?: title.year,
                            genres = if (detailed.genres.isNotEmpty()) csv(detailed.genres) else title.genres,
                            posterUrl = if (keepPosterCleared) "" else (detailed.posterUrl ?: title.posterUrl),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    syncDlcs(title.id, title.remoteId, preserveCompleted = true)
                    persistRelated(type, detailed)
                    if (!keepPosterCleared && title.localPosterPath.isNullOrBlank()) {
                        ensurePoster(title.id, detailed.posterUrl ?: title.posterUrl)
                    }
                    updated++
                }
                delay(250)
            }
            persistOverview(type)
            return RefreshResult(updated, 0)
        }
        if (type == MediaType.MOVIE) {
            val list = titles.getAll().filter { it.mediaType == type.name }
            var updated = 0
            list.forEachIndexed { index, title ->
                onProgress?.invoke(RefreshProgress(index + 1, list.size))
                runCatching {
                    val detailed = catalog.details(type, title.remoteId)
                    val keepPosterCleared = title.posterUrl == ""
                    if (detailed.runtimeMinutes > 0) {
                        val runtime = detailed.runtimeMinutes
                        titles.update(
                            title.copy(
                                runtimeMinutes = runtime,
                                watchedMinutes = if (title.completed) runtime else 0,
                                year = detailed.year ?: title.year,
                                firstDate = detailed.firstDate ?: title.firstDate,
                                overview = detailed.overview ?: title.overview,
                                genres = if (detailed.genres.isNotEmpty()) csv(detailed.genres) else title.genres,
                                collectionId = detailed.collectionId ?: title.collectionId,
                                collectionName = detailed.collectionName ?: title.collectionName,
                                posterUrl = if (keepPosterCleared) "" else (detailed.posterUrl ?: title.posterUrl),
                                catalogUrl = keepListingUrl(type, title.remoteId, title.catalogUrl),
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    persistRelated(type, detailed)
                    recalcMovieFranchiseProgress(title.id)
                    if (!keepPosterCleared && title.localPosterPath.isNullOrBlank()) {
                        ensurePoster(title.id, detailed.posterUrl ?: title.posterUrl)
                    }
                    updated++
                }
                delay(250)
            }
            reconcileMovieFranchises()
            persistOverview(type)
            return RefreshResult(updated, 0)
        }
        val list = titles.getAll().filter { it.mediaType == type.name }
        var updated = 0
        var reopened = 0
        list.forEachIndexed { index, title ->
            onProgress?.invoke(RefreshProgress(index + 1, list.size))
            val before = episodes.count(title.id)
            runCatching {
                val detailed = catalog.details(type, title.remoteId)
                val keepPosterCleared = title.posterUrl == ""
                titles.update(
                    title.copy(
                        status = displayCatalogStatus(detailed.status) ?: title.status,
                        cancelled = detailed.cancelled,
                        firstDate = detailed.firstDate ?: title.firstDate,
                        lastDate = detailed.lastDate ?: title.lastDate,
                        posterUrl = if (keepPosterCleared) "" else (detailed.posterUrl ?: title.posterUrl),
                        runtimeMinutes = detailed.runtimeMinutes.takeIf { it > 0 } ?: title.runtimeMinutes,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                syncEpisodes(title.id, title.remoteId, preserveWatched = true)
                persistRelated(type, detailed)
                if (!keepPosterCleared && title.localPosterPath.isNullOrBlank()) {
                    ensurePoster(title.id, detailed.posterUrl ?: title.posterUrl)
                }
            }
            val after = episodes.count(title.id)
            if (after != before) updated++
            val latest = titles.getById(title.id)
            if (latest != null && title.completed && !latest.completed) reopened++
            delay(350)
        }
        persistOverview(type)
        return RefreshResult(updated, reopened)
    }

    suspend fun reloadFromSelectedCatalogs(
        onProgress: (suspend (RefreshProgress) -> Unit)? = null
    ): CatalogReloadSummary {
        val all = titles.getAll().filterNot {
            it.hidden && it.mediaType == MediaType.MOVIE.name
        }
        val total = all.size.coerceAtLeast(1)
        val log = StringBuilder()
        var replaced = 0
        var refreshed = 0
        var skipped = 0
        try {
            all.forEachIndexed { index, title ->
                coroutineContext.ensureActive()
                onProgress?.invoke(RefreshProgress(index + 1, total, "Updating catalogue"))
                val type = runCatching { MediaType.valueOf(title.mediaType) }.getOrNull()
                if (type == null) {
                    skipped++
                    appendReloadLog(
                        log, title, 0, emptyList(),
                        "not replaced", "unknown media type"
                    )
                    return@forEachIndexed
                }
                val provider = catalog.providerIdFor(type)
                if (listingBelongsToProvider(title.catalogUrl, title.mediaType, provider)) {
                    skipped++
                    appendReloadLog(
                        log, title, 0, emptyList(),
                        "not replaced",
                        "already on ${provider.name}"
                    )
                    return@forEachIndexed
                }
                val outcome = rematchTitle(title, type)
                when (outcome.kind) {
                    "replaced" -> replaced++
                    "refreshed" -> refreshed++
                    else -> skipped++
                }
                appendReloadLog(
                    log, title, outcome.candidateCount, outcome.candidates,
                    outcome.kind, outcome.reason, outcome.chosenUrl
                )
                delay(250)
            }
        } finally {
            withContext(NonCancellable) {
                MediaType.entries.forEach { persistOverview(it) }
            }
        }
        return CatalogReloadSummary(replaced, refreshed, skipped, log.toString())
    }

    private data class RematchOutcome(
        val kind: String,
        val reason: String,
        val candidateCount: Int,
        val candidates: List<RemoteTitle>,
        val chosenUrl: String? = null
    )

    private suspend fun rematchTitle(
        title: TitleEntity,
        type: MediaType
    ): RematchOutcome {
        val primary = title.displayName.trim()
        val hits = linkedMapOf<String, RemoteTitle>()
        runCatching { catalog.search(type, primary) }.getOrDefault(emptyList()).forEach { hit ->
            hits.putIfAbsent(hit.remoteId, hit)
        }
        if (hits.isEmpty()) {
            titleSearchVariants(title.displayName)
                .plus(titleSearchVariants(title.title))
                .distinct()
                .filter { it.isNotBlank() && !it.equals(primary, ignoreCase = true) }
                .take(2)
                .forEach { query ->
                    runCatching { catalog.search(type, query) }.getOrDefault(emptyList()).forEach { hit ->
                        hits.putIfAbsent(hit.remoteId, hit)
                    }
                }
        }
        val candidates = hits.values.toList()
        if (candidates.isEmpty()) {
            return RematchOutcome("not replaced", "no catalog hits", 0, emptyList())
        }
        val scored = candidates
            .map { it to catalogMatchScore(title.displayName, title.year, it) }
            .sortedByDescending { it.second }
        val best = scored.first()
        val second = scored.getOrNull(1)
        if (!isStrongCatalogMatch(best.second, second?.second, title.year != null)) {
            val reason = if (second != null && best.second - second.second < 16) {
                "ambiguous match"
            } else {
                "weak match (best ${best.second})"
            }
            return RematchOutcome("not replaced", reason, candidates.size, candidates)
        }
        val winner = best.first
        val other = titles.getByRemote(type.name, winner.remoteId)
        if (other != null && other.id != title.id) {
            return RematchOutcome(
                "not replaced",
                "remoteId already used by ${other.displayName}",
                candidates.size,
                candidates
            )
        }
        val detailed = runCatching { catalog.details(type, winner.remoteId) }.getOrElse { winner }
        val catalogUnits = when {
            detailed.unitHint > 0 -> detailed.unitHint
            detailed.pageCount > 0 -> detailed.pageCount
            else -> 0
        }
        if (unitsMismatch(title.totalUnits, catalogUnits)) {
            return RematchOutcome(
                "not replaced",
                "episode/unit count mismatch (library ${title.totalUnits} vs catalog $catalogUnits)",
                candidates.size,
                candidates
            )
        }
        val result = retargetListing(title.id, detailed, fetchDetails = false)
        return if (result.isSuccess) {
            RematchOutcome(
                "replaced",
                "matched ${winner.title}",
                candidates.size,
                candidates,
                currentListingUrl(type, winner.remoteId)
            )
        } else {
            RematchOutcome(
                "not replaced",
                result.exceptionOrNull()?.message ?: "retarget failed",
                candidates.size,
                candidates
            )
        }
    }

    private fun appendReloadLog(
        log: StringBuilder,
        title: TitleEntity,
        candidateCount: Int,
        candidates: List<RemoteTitle>,
        outcome: String,
        reason: String,
        chosenUrl: String? = null
    ) {
        log.appendLine("=== ${title.displayName} (${title.mediaType}, ${title.year ?: "year unknown"}) ===")
        log.appendLine("Candidates: $candidateCount")
        if (candidates.size > 1) {
            candidates.take(8).forEach { hit ->
                val score = catalogMatchScore(title.displayName, title.year, hit)
                log.appendLine("- ${hit.title} (${hit.year ?: "?"}) score=$score id=${hit.remoteId}")
            }
        }
        log.appendLine("Outcome: $outcome ($reason)")
        if (!chosenUrl.isNullOrBlank()) log.appendLine("URL: $chosenUrl")
        log.appendLine()
    }

    suspend fun libraryIds(type: MediaType): Set<String> = titles.remoteIds(type.name).toSet()

    suspend fun completedIds(type: MediaType): Set<String> =
        titles.getAll()
            .filter { it.mediaType == type.name && it.displayCompleted() }
            .map { it.remoteId }
            .toSet()
    suspend fun watchlistIds(type: MediaType): Set<String> = watchlist.remoteIds(type.name).toSet()
    suspend fun banIds(type: MediaType): Set<String> =
        bans.getAll()
            .filter { it.mediaType == type.name }
            .mapNotNull { it.remoteKey() }
            .toSet()

    suspend fun dislikeIds(type: MediaType): Set<String> =
        dislikes.getAll()
            .filter { it.mediaType == type.name }
            .mapNotNull { it.remoteKey() }
            .toSet()

    /** Remote ids that should be hidden from search / discover / suggestions. */
    suspend fun excludedRemoteIds(type: MediaType): Set<String> =
        banIds(type) + dislikeIds(type)

    suspend fun allGenreOptions(type: MediaType): List<String> {
        return titles.genreBlobs(type.name)
            .flatMap { splitCsv(it) }
            .distinct()
            .sorted()
    }

    private suspend fun syncEpisodes(titleId: Long, remoteId: String, preserveWatched: Boolean) {
        val title = titles.getById(titleId)
        val fallback = title?.runtimeMinutes?.takeIf { it > 0 } ?: 45
        val mediaType = title?.mediaType?.let { runCatching { MediaType.valueOf(it) }.getOrNull() }
            ?: MediaType.SERIES
        val (remoteSeasons, remoteEpisodes) = catalog.seasonsAndEpisodes(mediaType, remoteId, fallback)
        val previous = if (preserveWatched) {
            episodes.forTitle(titleId).associate { (it.seasonNumber to it.episodeNumber) to it.watched }
        } else emptyMap()
        val previousNotInterested = seasons.forTitle(titleId)
            .associate { it.seasonNumber to it.notInterested }
        seasons.deleteForTitle(titleId)
        seasons.insertAll(
            remoteSeasons.map {
                SeasonEntity(
                    titleId = titleId,
                    seasonNumber = it.seasonNumber,
                    episodeCount = it.episodeCount,
                    startDate = it.startDate,
                    endDate = it.endDate,
                    notInterested = previousNotInterested[it.seasonNumber] == true
                )
            }
        )
        val mapped = remoteEpisodes.map {
            EpisodeEntity(
                titleId = titleId,
                seasonNumber = it.seasonNumber,
                episodeNumber = it.episodeNumber,
                airDate = it.airDate,
                watched = previous[it.seasonNumber to it.episodeNumber] == true,
                runtimeMinutes = it.runtimeMinutes
            )
        }
        episodes.replaceEpisodes(titleId, mapped)
        recalcProgressThenOverview(titleId)
    }

    private suspend fun recalcProgressThenOverview(titleId: Long) {
        recalcProgress(titleId)
        val title = titles.getById(titleId) ?: return
        runCatching { persistOverview(MediaType.valueOf(title.mediaType)) }
    }

    private suspend fun recalcProgress(titleId: Long) {
        val title = titles.getById(titleId) ?: return
        val countable = countableEpisodes(titleId)
        val total = countable.size
        val watched = countable.count { it.watched }
        val completed = total > 0 && watched >= total
        val watchedMins = countable.filter { it.watched }.sumOf { it.effectiveRuntime() }
        val totalMins = countable.sumOf { it.effectiveRuntime() }
        titles.update(
            title.copy(
                totalUnits = total,
                watchedUnits = watched,
                completed = completed,
                watchedMinutes = watchedMins,
                totalMinutes = totalMins,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun persistMovieMinutes(titleId: Long) {
        val title = titles.getById(titleId) ?: return
        val totalMins = title.runtimeMinutes.coerceAtLeast(0)
        titles.update(
            title.copy(
                totalUnits = 1,
                watchedUnits = if (title.completed) 1 else 0,
                totalMinutes = totalMins,
                watchedMinutes = if (title.completed) totalMins else 0,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun syncDlcs(titleId: Long, remoteId: String, preserveCompleted: Boolean) {
        val remote = runCatching { catalog.dlcs(remoteId) }.getOrDefault(emptyList())
        if (remote.isEmpty()) {
            dlcs.deleteForTitle(titleId)
            persistDlcProgress(titleId)
            return
        }
        val previous = if (preserveCompleted) {
            dlcs.forTitle(titleId).associate { it.remoteId to it.completed }
        } else {
            emptyMap()
        }
        dlcs.replaceForTitle(
            titleId,
            remote.map {
                DlcEntity(
                    titleId = titleId,
                    remoteId = it.remoteId,
                    name = it.name,
                    released = it.released,
                    posterUrl = it.posterUrl,
                    completed = previous[it.remoteId] == true,
                    position = it.position
                )
            }
        )
        persistDlcProgress(titleId)
    }

    private suspend fun persistDlcProgress(titleId: Long) {
        // DLC tracked in dlcs table only; does not affect game completion or title units.
    }

    private suspend fun persistBookProgress(titleId: Long) {
        val title = titles.getById(titleId) ?: return
        val total = title.pageCount.takeIf { it > 0 } ?: title.totalUnits.coerceAtLeast(1)
        val current = when {
            title.completed && title.pageCount > 0 -> title.pageCount
            title.pageCount > 0 -> title.watchedUnits.coerceIn(0, title.pageCount)
            else -> title.watchedUnits.coerceAtLeast(0)
        }
        titles.update(
            title.copy(
                totalUnits = total,
                watchedUnits = current,
                completed = total > 0 && current >= total,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun EpisodeEntity.effectiveRuntime(): Int = runtimeMinutes.takeIf { it > 0 } ?: 45

    private suspend fun countableEpisodes(titleId: Long): List<EpisodeEntity> {
        val eps = episodes.forTitle(titleId)
        val seasonRows = seasons.forTitle(titleId)
        if (seasonRows.isEmpty()) return eps
        val countableSeasons = seasonRows
            .filter { seasonHasStarted(it.startDate) && !it.notInterested }
            .map { it.seasonNumber }
            .toSet()
        return eps.filter { it.seasonNumber in countableSeasons }
    }

    private suspend fun persistRelated(type: MediaType, title: RemoteTitle) {
        if (type == MediaType.BOOK || type == MediaType.GAME) {
            related.deleteForOwner(type.name, title.remoteId)
            return
        }
        if (type == MediaType.MOVIE) {
            persistMovieFranchise(title)
            return
        }
        val owner = titles.getByRemote(type.name, title.remoteId)
        val resolved = withCollectionFallback(title, title, owner)
        val items = runCatching { catalog.related(type, resolved) }.getOrDefault(emptyList())
        val previousWatched = related.forOwner(type.name, title.remoteId)
            .associate { it.relatedRemoteId to it.watched }
        related.deleteForOwner(type.name, title.remoteId)
        if (items.isEmpty()) return
        related.insertAll(
            items.map { item ->
                RelatedTitleEntity(
                    ownerRemoteId = title.remoteId,
                    ownerMediaType = type.name,
                    relatedRemoteId = item.remoteId,
                    relatedMediaType = item.mediaType,
                    relation = item.relation,
                    relatedTitle = item.title,
                    year = item.year,
                    posterUrl = item.posterUrl,
                    position = item.position,
                    watched = previousWatched[item.remoteId] == true,
                    runtimeMinutes = item.runtimeMinutes.coerceAtLeast(0)
                )
            }
        )
    }

    private suspend fun persistMovieFranchise(title: RemoteTitle) {
        val owner = titles.getByRemote(MediaType.MOVIE.name, title.remoteId)
        val resolved = withCollectionFallback(title, title, owner)
        if (!prefs.autoStoreSequels.first()) {
            related.deleteForOwner(MediaType.MOVIE.name, title.remoteId)
            owner?.let { recalcMovieFranchiseProgress(it.id) }
            return
        }
        val collectionId = resolved.collectionId
        if (collectionId.isNullOrBlank()) {
            related.deleteForOwner(MediaType.MOVIE.name, title.remoteId)
            owner?.let { recalcMovieFranchiseProgress(it.id) }
            return
        }
        val parts = runCatching { catalog.movieCollectionParts(collectionId) }.getOrDefault(emptyList())
        if (parts.isEmpty()) {
            related.deleteForOwner(MediaType.MOVIE.name, title.remoteId)
            owner?.let { recalcMovieFranchiseProgress(it.id) }
            return
        }
        val allParts = (
            if (parts.any { it.remoteId == resolved.remoteId }) {
                parts
            } else {
                parts + RemoteRelated(
                    remoteId = resolved.remoteId,
                    mediaType = MediaType.MOVIE.name,
                    title = resolved.title,
                    year = resolved.year,
                    posterUrl = resolved.posterUrl,
                    relation = RelationType.COLLECTION.name,
                    firstDate = resolved.firstDate
                )
            }
        ).sortedWith(collectionPartOrder())
        val detailsById = fetchFranchiseMovieDetails(allParts, resolved)
        val enrichedParts = allParts.map { part -> enrichCollectionPart(part, detailsById[part.remoteId]) }
        val previousWatchedByOwner = enrichedParts.associate { part ->
            part.remoteId to related.forOwner(MediaType.MOVIE.name, part.remoteId)
                .associate { it.relatedRemoteId to it.watched }
        }
        enrichedParts.forEach { part ->
            upsertHiddenSequelFromCollectionPart(
                part,
                detailsById[part.remoteId],
                resolved.collectionId,
                resolved.collectionName,
                watchedHint = false
            )
        }
        val ownersToRecalc = mutableSetOf<Long>()
        enrichedParts.forEach { ownerPart ->
            val siblings = enrichedParts.filter { it.remoteId != ownerPart.remoteId }
            val previousWatched = previousWatchedByOwner[ownerPart.remoteId].orEmpty()
            related.deleteForOwner(MediaType.MOVIE.name, ownerPart.remoteId)
            if (siblings.isEmpty()) return@forEach
            related.insertAll(
                siblings.mapIndexed { index, sibling ->
                    val siblingWatched = titles.getByRemote(MediaType.MOVIE.name, sibling.remoteId)?.completed
                        ?: previousWatched[sibling.remoteId] == true
                    RelatedTitleEntity(
                        ownerRemoteId = ownerPart.remoteId,
                        ownerMediaType = MediaType.MOVIE.name,
                        relatedRemoteId = sibling.remoteId,
                        relatedMediaType = MediaType.MOVIE.name,
                        relation = movieRelation(ownerPart.year, sibling.year),
                        relatedTitle = sibling.title,
                        year = sibling.year,
                        posterUrl = sibling.posterUrl,
                        position = index,
                        watched = siblingWatched,
                        runtimeMinutes = sibling.runtimeMinutes.coerceAtLeast(0)
                    )
                }
            )
            titles.getByRemote(MediaType.MOVIE.name, ownerPart.remoteId)?.let { ownersToRecalc += it.id }
        }
        ownersToRecalc.forEach { recalcMovieFranchiseProgress(it) }
    }

    private fun movieRelation(ownerYear: Int?, siblingYear: Int?): String = when {
        ownerYear != null && siblingYear != null && siblingYear < ownerYear -> RelationType.PREQUEL.name
        ownerYear != null && siblingYear != null && siblingYear > ownerYear -> RelationType.SEQUEL.name
        else -> RelationType.COLLECTION.name
    }

    private suspend fun migrateSequelTitlesFromRelated() {
        related.getAll()
            .filter { it.ownerMediaType == MediaType.MOVIE.name }
            .forEach { row ->
                ensureSequelTitleFromRelated(row, row.watched)
            }
        titles.getAll()
            .filter { it.mediaType == MediaType.MOVIE.name }
            .forEach { movie ->
                val ownMins = movie.runtimeMinutes.coerceAtLeast(0)
                titles.update(
                    movie.copy(
                        watchedMinutes = if (movie.completed) ownMins else 0,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                recalcMovieFranchiseProgress(movie.id)
            }
        MediaType.entries.forEach { persistOverview(it) }
    }

    /** Session cache so refreshing the same franchise does not re-hit TMDB for every part. */
    private val franchiseDetailCache = mutableMapOf<String, RemoteTitle>()

    private suspend fun fetchFranchiseMovieDetails(
        parts: List<RemoteRelated>,
        resolved: RemoteTitle
    ): Map<String, RemoteTitle> = coroutineScope {
        parts.map { it.remoteId }.distinct().map { remoteId ->
            async(Dispatchers.IO) {
                val cached = franchiseDetailCache[remoteId]
                val detail = when {
                    remoteId == resolved.remoteId -> resolved
                    cached != null -> cached
                    else -> runCatching { catalog.details(MediaType.MOVIE, remoteId) }.getOrNull()
                        ?.also { franchiseDetailCache[remoteId] = it }
                }
                remoteId to detail
            }
        }.awaitAll().filter { it.second != null }.associate { it.first to it.second!! }
    }

    private fun enrichCollectionPart(part: RemoteRelated, detail: RemoteTitle?): RemoteRelated {
        if (detail == null) return part
        return part.copy(
            title = detail.title.ifBlank { part.title },
            year = detail.year ?: part.year,
            posterUrl = detail.posterUrl ?: part.posterUrl,
            firstDate = detail.firstDate ?: part.firstDate,
            runtimeMinutes = detail.runtimeMinutes.takeIf { it > 0 } ?: part.runtimeMinutes
        )
    }

    private suspend fun upsertHiddenSequelFromCollectionPart(
        part: RemoteRelated,
        detail: RemoteTitle?,
        collectionId: String?,
        collectionName: String?,
        watchedHint: Boolean
    ): TitleEntity {
        val remote = (detail ?: RemoteTitle(
            mediaType = MediaType.MOVIE.name,
            remoteId = part.remoteId,
            title = part.title,
            year = part.year,
            posterUrl = part.posterUrl,
            firstDate = part.firstDate,
            lastDate = part.firstDate,
            collectionId = collectionId,
            collectionName = collectionName,
            runtimeMinutes = part.runtimeMinutes
        )).copy(
            collectionId = collectionId ?: detail?.collectionId,
            collectionName = collectionName ?: detail?.collectionName
        )
        val runtime = remote.runtimeMinutes.coerceAtLeast(0)
        val existing = titles.getByRemote(MediaType.MOVIE.name, part.remoteId)
        if (existing != null) {
            val merged = mergeDetailedIntoExisting(existing, remote, remote, MediaType.MOVIE).copy(
                collectionId = existing.collectionId ?: collectionId,
                collectionName = existing.collectionName ?: collectionName,
                firstDate = existing.firstDate ?: remote.firstDate,
                lastDate = existing.lastDate ?: remote.lastDate,
                year = existing.year ?: remote.year,
                runtimeMinutes = runtime.takeIf { it > 0 } ?: existing.runtimeMinutes,
                watchedMinutes = if (existing.completed) {
                    (runtime.takeIf { it > 0 } ?: existing.runtimeMinutes).coerceAtLeast(0)
                } else {
                    existing.watchedMinutes
                }
            )
            if (merged != existing) {
                titles.update(merged)
            }
            return merged
        }
        val entity = remote.toEntity(MediaType.MOVIE).copy(
            hidden = true,
            tracked = false,
            completed = watchedHint,
            watchedMinutes = if (watchedHint) runtime else 0,
            runtimeMinutes = runtime,
            totalUnits = 1,
            watchedUnits = if (watchedHint) 1 else 0,
            totalMinutes = runtime
        )
        val id = titles.insert(entity)
        return entity.copy(id = id)
    }

    private suspend fun upsertHiddenSequelFromRemote(item: RemoteRelated, watchedHint: Boolean): TitleEntity {
        val existing = titles.getByRemote(MediaType.MOVIE.name, item.remoteId)
        if (existing != null) return existing
        val detailed = runCatching { catalog.details(MediaType.MOVIE, item.remoteId) }.getOrNull()
        val runtime = (detailed?.runtimeMinutes ?: item.runtimeMinutes).coerceAtLeast(0)
        val remote = detailed ?: RemoteTitle(
            mediaType = MediaType.MOVIE.name,
            remoteId = item.remoteId,
            title = item.title,
            year = item.year,
            posterUrl = item.posterUrl,
            firstDate = item.firstDate,
            lastDate = item.firstDate,
            runtimeMinutes = runtime
        )
        val entity = remote.toEntity(MediaType.MOVIE).copy(
            hidden = true,
            tracked = false,
            completed = watchedHint,
            watchedMinutes = if (watchedHint) runtime else 0,
            runtimeMinutes = runtime,
            totalUnits = 1,
            watchedUnits = if (watchedHint) 1 else 0
        )
        val id = titles.insert(entity)
        return entity.copy(id = id)
    }

    private suspend fun ensureSequelTitleFromRelated(row: RelatedTitleEntity, watchedHint: Boolean): TitleEntity {
        val existing = titles.getByRemote(MediaType.MOVIE.name, row.relatedRemoteId)
        if (existing != null) return existing
        return upsertHiddenSequelFromRemote(
            RemoteRelated(
                remoteId = row.relatedRemoteId,
                mediaType = row.relatedMediaType,
                title = row.relatedTitle,
                year = row.year,
                posterUrl = row.posterUrl,
                relation = row.relation,
                position = row.position,
                runtimeMinutes = row.runtimeMinutes
            ),
            watchedHint
        )
    }

    private suspend fun reconcileMovieFranchises() {
        related.getAll()
            .filter { it.ownerMediaType == MediaType.MOVIE.name }
            .forEach { row -> ensureSequelTitleFromRelated(row, row.watched) }
        related.getAll()
            .filter { it.ownerMediaType == MediaType.MOVIE.name }
            .map { it.relatedRemoteId }
            .distinct()
            .forEach { remoteId ->
                val sibling = titles.getByRemote(MediaType.MOVIE.name, remoteId)
                if (sibling != null) syncRelatedWatchedFlags(remoteId, sibling.completed)
            }
        titles.getAll()
            .filter { it.mediaType == MediaType.MOVIE.name && !it.hidden && it.countSequelsInCompletion }
            .forEach { owner -> recalcMovieFranchiseProgress(owner.id) }
    }

    private suspend fun recalcOwnersLinkingSequel(relatedRemoteId: String) {
        related.forRelated(MediaType.MOVIE.name, relatedRemoteId).forEach { link ->
            titles.getByRemote(link.ownerMediaType, link.ownerRemoteId)?.let { owner ->
                recalcMovieFranchiseProgress(owner.id)
            }
        }
    }

    private suspend fun recalcMovieFranchiseProgress(titleId: Long) {
        val title = titles.getById(titleId) ?: return
        if (title.mediaType != MediaType.MOVIE.name) return
        val ownMins = title.runtimeMinutes.coerceAtLeast(0)
        val ownWatchedMins = if (title.completed) ownMins else 0
        val links = related.forOwner(title.mediaType, title.remoteId)
        if (!title.countSequelsInCompletion || links.isEmpty()) {
            titles.update(
                title.copy(
                    totalUnits = 1,
                    watchedUnits = if (title.completed) 1 else 0,
                    totalMinutes = ownMins,
                    watchedMinutes = ownWatchedMins,
                    completed = title.completed,
                    updatedAt = System.currentTimeMillis()
                )
            )
            return
        }
        val releasedLinks = links.filter { link ->
            if (isRemoteBanned(MediaType.MOVIE, link.relatedRemoteId, link.relatedTitle, link.year)) {
                return@filter false
            }
            val sibling = titles.getByRemote(MediaType.MOVIE.name, link.relatedRemoteId)
            if (sibling?.notInterested == true) return@filter false
            movieHasReleased(
                sibling?.firstDate,
                sibling?.year ?: link.year
            )
        }
        val ownerReleased = movieHasReleased(title.firstDate, title.year)
        var totalUnits = 0
        var watchedUnits = 0
        if (ownerReleased) {
            totalUnits++
            if (title.completed) watchedUnits++
        }
        releasedLinks.forEach { link ->
            val sibling = titles.getByRemote(MediaType.MOVIE.name, link.relatedRemoteId)
            val watched = sibling?.completed == true
            if (sibling != null && link.watched != sibling.completed) {
                related.setWatched(link.id, sibling.completed)
            }
            totalUnits++
            if (watched) watchedUnits++
        }
        val franchiseTotalMins = (if (ownerReleased) ownMins else 0) +
            releasedLinks.sumOf { link -> sequelRuntimeMinutes(link) }
        val franchiseWatchedMins = (if (title.completed && ownerReleased) ownMins else 0) +
            releasedLinks.filter { link ->
                titles.getByRemote(MediaType.MOVIE.name, link.relatedRemoteId)?.completed == true
            }.sumOf { link -> sequelRuntimeMinutes(link) }
        titles.update(
            title.copy(
                totalUnits = totalUnits,
                watchedUnits = watchedUnits,
                totalMinutes = franchiseTotalMins,
                watchedMinutes = franchiseWatchedMins,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun sequelRuntimeMinutes(link: RelatedTitleEntity): Int {
        val sibling = titles.getByRemote(MediaType.MOVIE.name, link.relatedRemoteId)
        return (sibling?.runtimeMinutes ?: link.runtimeMinutes).coerceAtLeast(0)
    }

    private fun collectionPartOrder(): Comparator<RemoteRelated> =
        compareBy(
            { part -> part.firstDate?.take(10).orEmpty().ifBlank { "9999-12-31" } },
            { part -> part.year ?: Int.MAX_VALUE }
        )

    private fun applyCombineSequels(
        visible: List<TitleEntity>,
        allInLibrary: List<TitleEntity>
    ): List<TitleEntity> {
        val collectionIds = visible.mapNotNull { it.collectionId?.takeIf { it.isNotBlank() } }.toSet()
        if (collectionIds.isEmpty()) return visible
        val representativeByCollection = collectionIds.associateWith { cid ->
            pickFranchiseRepresentative(allInLibrary.filter { it.collectionId == cid })
        }
        val consumed = mutableSetOf<String>()
        val visibleById = visible.associateBy { it.id }
        return buildList {
            visible.forEach { title ->
                val collectionId = title.collectionId
                if (collectionId.isNullOrBlank()) {
                    add(title)
                } else if (collectionId !in consumed) {
                    consumed += collectionId
                    val rep = representativeByCollection[collectionId] ?: title
                    add(visibleById[rep.id] ?: rep)
                }
            }
        }
    }

    private fun pickFranchiseRepresentative(group: List<TitleEntity>): TitleEntity {
        val sorted = group.sortedWith(
            compareBy<TitleEntity>(
                { it.firstDate?.take(10).orEmpty().ifBlank { "9999-12-31" } },
                { it.year ?: Int.MAX_VALUE }
            )
        )
        return sorted.firstOrNull { it.completed } ?: sorted.first()
    }

    /**
     * Single poster download helper — skips if the local file already exists
     * and `replace` is false.  Updates the title row on success.
     */
    private suspend fun ensurePoster(titleId: Long, url: String?, replace: Boolean = false): String? {
        if (!libraryPrefs().storeImagesLocally) return null
        val path = posters.download(titleId, url, replace) ?: return null
        val current = titles.getById(titleId) ?: return path
        if (current.localPosterPath != path) {
            titles.update(current.copy(localPosterPath = path, updatedAt = System.currentTimeMillis()))
        }
        return path
    }

    private suspend fun computeOverview(type: MediaType): OverviewStats {
        val list = titles.getAll().filter { it.mediaType == type.name }
        val allSeasons = seasons.getAll()
        val seasonsByTitle = allSeasons.groupBy { it.titleId }
        val tracked = list.filter { it.tracked }
        val overviewTitles = tracked.filter { it.countsInOverview(seasonsByTitle[it.id].orEmpty()) }
        val completed = overviewTitles.count { it.displayCompleted() }
        val inProgress = overviewTitles.count { it.displayWatching() }
        val notStarted = overviewTitles.count { !it.displayCompleted() && !it.displayWatching() }
        val hidden = overviewTitles.count { it.hidden }
        val watchedMinutes = overviewTitles.sumOf { it.watchedMinutes.coerceAtLeast(0) }
        val pagesRead = if (type == MediaType.BOOK) overviewTitles.sumOf { it.watchedUnits.coerceAtLeast(0) } else 0

        val totalEpisodes: Int
        val watchedEpisodes: Int
        if (type == MediaType.SERIES || type == MediaType.ANIME) {
            val overviewIds = overviewTitles.map { it.id }.toSet()
            val allEps = episodes.getAll()
            val relevant = overviewIds.flatMap { titleId ->
                val seasonRows = seasonsByTitle[titleId].orEmpty()
                val countableSeasons = seasonRows
                    .filter { seasonHasStarted(it.startDate) && !it.notInterested }
                    .map { it.seasonNumber }
                    .toSet()
                val eps = allEps.filter { it.titleId == titleId }
                if (seasonRows.isEmpty()) eps else eps.filter { it.seasonNumber in countableSeasons }
            }
            totalEpisodes = relevant.size
            watchedEpisodes = relevant.count { it.watched }
        } else {
            totalEpisodes = 0
            watchedEpisodes = 0
        }

        val ratings = tracked.mapNotNull { it.userRating }
        val averageRating: Float? = if (ratings.isNotEmpty()) ratings.average().toFloat() else null

        val favouriteTitle = tracked
            .filter { it.userRating != null }
            .sortedWith(
                compareByDescending<TitleEntity> { it.userRating ?: -1f }
                    .thenBy { it.displayName.lowercase() }
            )
            .take(3)
            .joinToString("\n") { title ->
                val rating = (title.userRating ?: 0f).toInt()
                "${title.displayName} $rating/10"
            }

        val genreScores = mutableMapOf<String, Float>()
        tracked.filter { it.userRating != null }.forEach { title ->
            val rating = title.userRating ?: return@forEach
            splitCsv(title.genres).forEach { genre ->
                genreScores[genre] = (genreScores[genre] ?: 0f) + rating
            }
        }
        val totalGenreScore = genreScores.values.sum()
        val favourite = genreScores.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { (genre, score) ->
                val percent = if (totalGenreScore <= 0f) 0 else (score * 100f / totalGenreScore).toInt()
                "$genre ($percent%)"
            }

        val genreHours = mutableMapOf<String, Int>()
        if (type == MediaType.BOOK || type == MediaType.GAME) {
            tracked.filter { it.displayCompleted() }.forEach { title ->
                splitCsv(title.genres).forEach { genre ->
                    genreHours[genre] = (genreHours[genre] ?: 0) + 1
                }
            }
        } else {
            tracked.forEach { title ->
                val mins = title.watchedMinutes.coerceAtLeast(0)
                if (mins <= 0) return@forEach
                splitCsv(title.genres).forEach { genre ->
                    genreHours[genre] = (genreHours[genre] ?: 0) + mins
                }
            }
        }
        val totalHoursMins = genreHours.values.sum().coerceAtLeast(0)
        val genresByHours = genreHours.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { (genre, mins) ->
                val percent = if (totalHoursMins == 0) 0 else (mins * 100f / totalHoursMins).toInt()
                "$genre ($percent%)"
            }

        val creatorScores = mutableMapOf<String, Int>()
        tracked.filter { it.completed }.forEach { title ->
            val creator = title.author?.takeIf { it.isNotBlank() }
            creator?.let { creatorScores[it] = (creatorScores[it] ?: 0) + 1 }
        }
        val topCreators = creatorScores.entries.sortedByDescending { it.value }.take(3).map { it.key }

        return OverviewStats(
            total = overviewTitles.size,
            completed = completed,
            inProgress = inProgress,
            notStarted = notStarted,
            hidden = hidden,
            watchedMinutes = watchedMinutes,
            pagesRead = pagesRead,
            totalEpisodes = totalEpisodes,
            watchedEpisodes = watchedEpisodes,
            averageRating = averageRating,
            favouriteGenres = favourite,
            genresByHours = genresByHours,
            favouriteTitle = favouriteTitle,
            topCreators = topCreators
        )
    }

    private fun TitleEntity.countsInOverview(seasonRows: List<SeasonEntity>): Boolean {
        if (!tracked) return false
        val episodeType = mediaType == MediaType.SERIES.name || mediaType == MediaType.ANIME.name
        if (!episodeType) return true
        if (seasonRows.isEmpty()) return true
        return seasonRows.any { !it.notInterested }
    }

    private suspend fun resolveDetailed(
        type: MediaType,
        remote: RemoteTitle,
        existing: TitleEntity?
    ): RemoteTitle {
        val detailed = runCatching { catalog.details(type, remote.remoteId) }.getOrDefault(remote)
        return withCollectionFallback(detailed, remote, existing)
    }

    private fun withCollectionFallback(
        detailed: RemoteTitle,
        remote: RemoteTitle,
        existing: TitleEntity? = null
    ): RemoteTitle {
        if (!detailed.collectionId.isNullOrBlank()) return detailed
        val collectionId = remote.collectionId ?: existing?.collectionId
        if (collectionId.isNullOrBlank()) return detailed
        return detailed.copy(
            collectionId = collectionId,
            collectionName = detailed.collectionName ?: remote.collectionName ?: existing?.collectionName
        )
    }

    private fun mergeDetailedIntoExisting(
        existing: TitleEntity,
        detailed: RemoteTitle,
        remote: RemoteTitle,
        type: MediaType,
        unhide: Boolean = false
    ): TitleEntity {
        val runtime = detailed.runtimeMinutes.takeIf { it > 0 } ?: existing.runtimeMinutes
        return existing.copy(
            title = detailed.title.ifBlank { existing.title },
            year = detailed.year ?: existing.year,
            posterUrl = detailed.posterUrl ?: existing.posterUrl ?: remote.posterUrl,
            overview = cleanCatalogOverview(detailed.overview) ?: existing.overview,
            author = detailed.author ?: existing.author,
            status = displayCatalogStatus(detailed.status) ?: displayCatalogStatus(existing.status) ?: existing.status,
            cancelled = detailed.cancelled,
            genres = if (detailed.genres.isNotEmpty()) csv(detailed.genres) else existing.genres,
            collectionId = detailed.collectionId ?: existing.collectionId,
            collectionName = detailed.collectionName ?: existing.collectionName,
            seriesName = detailed.seriesName ?: existing.seriesName,
            seriesPosition = detailed.seriesPosition ?: existing.seriesPosition,
            firstDate = detailed.firstDate ?: existing.firstDate,
            lastDate = detailed.lastDate ?: existing.lastDate,
            imdbId = detailed.imdbId ?: existing.imdbId,
            runtimeMinutes = runtime,
            pageCount = detailed.pageCount.takeIf { it > 0 } ?: existing.pageCount,
            catalogUrl = keepListingUrl(type, existing.remoteId, existing.catalogUrl),
            hidden = if (unhide) false else existing.hidden,
            tracked = if (unhide) true else existing.tracked,
            watchedMinutes = if (type == MediaType.MOVIE && existing.completed) runtime else existing.watchedMinutes,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun RemoteTitle.toEntity(type: MediaType) = TitleEntity(
        mediaType = type.name,
        remoteId = remoteId,
        title = title,
        year = year,
        posterUrl = posterUrl,
        overview = overview,
        author = author,
        status = status,
        cancelled = cancelled,
        genres = csv(genres),
        collectionId = collectionId,
        collectionName = collectionName,
        seriesName = seriesName,
        seriesPosition = seriesPosition,
        firstDate = firstDate,
        lastDate = lastDate,
        imdbId = imdbId,
        runtimeMinutes = runtimeMinutes,
        pageCount = pageCount,
        catalogUrl = remoteWebUrl(type.name, remoteId, defaultCatalogProvider(type.name))
    )

    private fun TitleEntity.toRemote() = RemoteTitle(
        mediaType = mediaType,
        remoteId = remoteId,
        title = title,
        year = year,
        posterUrl = posterUrl,
        overview = overview,
        author = author,
        status = status,
        cancelled = cancelled,
        genres = splitCsv(genres),
        collectionId = collectionId,
        collectionName = collectionName,
        seriesName = seriesName,
        seriesPosition = seriesPosition,
        firstDate = firstDate,
        lastDate = lastDate,
        imdbId = imdbId,
        runtimeMinutes = runtimeMinutes,
        pageCount = pageCount
    )

    private fun applyFilter(items: List<TitleEntity>, filter: LibraryFilter): List<TitleEntity> {
        val filtered = items.filter { item ->
            val queryOk = filter.query.isBlank() ||
                item.displayName.contains(filter.query, true) ||
                item.title.contains(filter.query, true) ||
                item.genres.contains(filter.query, true) ||
                item.author.orEmpty().contains(filter.query, true)
            val yearOk = item.year?.let { y ->
                (filter.yearFrom == null || y >= filter.yearFrom) &&
                    (filter.yearTo == null || y <= filter.yearTo)
            } ?: (filter.yearFrom == null && filter.yearTo == null)
            val completedOk = filter.completed == null || item.completed == filter.completed
            val cancelledOk = filter.cancelled == null || item.cancelled == filter.cancelled
            val moreOk = filter.moreOfThis == null || item.moreOfThis == filter.moreOfThis
            val ratedOk = filter.ratedOnly != true || item.userRating != null
            val recommendedOk = filter.recommended == null || item.recommended == filter.recommended
            val genreOk = filter.genres.isEmpty() || filter.genres.all { g ->
                splitCsv(item.genres).any { it.equals(g, true) }
            }
            val hiddenOk = filter.showHidden || !item.hidden
            val hideCompletedOk = !filter.hideCompleted || !item.displayCompleted()
            val hideIncompleteOk = !filter.hideIncomplete || item.displayCompleted()
            queryOk && yearOk && completedOk && cancelledOk && moreOk && ratedOk &&
                recommendedOk && genreOk && hiddenOk && hideCompletedOk && hideIncompleteOk
        }
        val chain = filter.sortChain()
        return filtered.sortedWith { a, b ->
            for (option in chain) {
                val cmp = compareTitles(a, b, option)
                if (cmp != 0) return@sortedWith cmp
            }
            0
        }
    }

    private fun compareTitles(a: TitleEntity, b: TitleEntity, option: SortOption): Int {
        return when (option) {
            SortOption.TITLE -> a.displayName.lowercase().compareTo(b.displayName.lowercase())
            SortOption.YEAR -> (b.year ?: 0).compareTo(a.year ?: 0)
            SortOption.RATING -> {
                val aRated = a.userRating != null
                val bRated = b.userRating != null
                when {
                    aRated && !bRated -> -1
                    !aRated && bRated -> 1
                    !aRated && !bRated -> 0
                    else -> (b.userRating ?: -1f).compareTo(a.userRating ?: -1f)
                }
            }
            SortOption.PROGRESS -> {
                val pa = if (a.totalUnits == 0) 0f else a.watchedUnits.toFloat() / a.totalUnits
                val pb = if (b.totalUnits == 0) 0f else b.watchedUnits.toFloat() / b.totalUnits
                pb.compareTo(pa)
            }
            SortOption.DATE_ADDED -> b.addedAt.compareTo(a.addedAt)
            SortOption.COMPLETED_FIRST -> b.completed.compareTo(a.completed)
            SortOption.INCOMPLETE_FIRST -> a.completed.compareTo(b.completed)
        }
    }
}

data class MovieSequelEntry(
    val link: RelatedTitleEntity,
    val title: TitleEntity?,
    val banned: Boolean = false
) {
    val isWatched: Boolean
        get() = title?.completed == true
}

data class LibraryStats(val total: Int, val completed: Int, val watching: Int, val notStarted: Int)

data class OverviewStats(
    val total: Int = 0,
    val completed: Int = 0,
    val inProgress: Int = 0,
    val notStarted: Int = 0,
    val hidden: Int = 0,
    val watchedMinutes: Int = 0,
    val remainingMinutes: Int = 0,
    val pagesRead: Int = 0,
    val totalEpisodes: Int = 0,
    val watchedEpisodes: Int = 0,
    val averageRating: Float? = null,
    val interestsCount: Int = 0,
    val bannedCount: Int = 0,
    val favouriteGenres: List<String> = emptyList(),
    val genresByHours: List<String> = emptyList(),
    val favouriteTitle: String = "",
    val topCreators: List<String> = emptyList()
)

data class RefreshProgress(val current: Int, val total: Int, val label: String = "Refreshing")

data class RefreshResult(val showsUpdated: Int, val reopened: Int)

data class CatalogReloadSummary(
    val replaced: Int,
    val refreshed: Int,
    val skipped: Int,
    val logText: String
)

data class AppendResult(val added: Int, val skipped: Int)

data class ShareImportComparison(
    val shared: SharedTitle,
    val alreadyInLibrary: Boolean,
    val alreadyWatched: Boolean,
    val selected: Boolean
)

private fun OverviewStatsEntity?.toStats() = OverviewStats(
    total = this?.total ?: 0,
    completed = this?.completed ?: 0,
    inProgress = this?.inProgress ?: 0,
    notStarted = this?.notStarted ?: 0,
    hidden = this?.hidden ?: 0,
    watchedMinutes = this?.watchedMinutes ?: 0,
    pagesRead = this?.pagesRead ?: 0,
    totalEpisodes = this?.totalEpisodes ?: 0,
    watchedEpisodes = this?.watchedEpisodes ?: 0,
    averageRating = this?.averageRating,
    favouriteGenres = this?.favouriteGenres?.let { splitCsv(it) }.orEmpty(),
    genresByHours = this?.genresByHours?.let { splitCsv(it) }.orEmpty(),
    favouriteTitle = this?.favouriteTitle.orEmpty(),
    topCreators = this?.topCreators?.let { splitCsv(it) }.orEmpty()
)

private fun OverviewStats.toEntity(type: MediaType) = OverviewStatsEntity(
    mediaType = type.name,
    total = total,
    completed = completed,
    inProgress = inProgress,
    notStarted = notStarted,
    hidden = hidden,
    watchedMinutes = watchedMinutes,
    pagesRead = pagesRead,
    totalEpisodes = totalEpisodes,
    watchedEpisodes = watchedEpisodes,
    averageRating = averageRating,
    favouriteGenres = csv(favouriteGenres),
    genresByHours = csv(genresByHours),
    favouriteTitle = favouriteTitle,
    topCreators = csv(topCreators),
    updatedAt = System.currentTimeMillis()
)

private fun banUrlForRemote(remoteId: String): String = "remote:$remoteId"

private fun BanEntity.remoteKey(): String? =
    url.takeIf { it.startsWith("remote:") }?.removePrefix("remote:")?.ifBlank { null }

private fun DislikeEntity.remoteKey(): String? =
    url.takeIf { it.startsWith("remote:") }?.removePrefix("remote:")?.ifBlank { null }
