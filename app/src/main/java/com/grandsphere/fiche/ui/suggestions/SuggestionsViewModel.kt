package com.grandsphere.fiche.ui.suggestions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grandsphere.fiche.data.local.entity.LibraryPrefsEntity
import com.grandsphere.fiche.data.local.entity.TitleEntity
import com.grandsphere.fiche.data.prefs.ApiKeyRepository
import com.grandsphere.fiche.data.prefs.UserPreferencesRepository
import com.grandsphere.fiche.data.remote.RemoteTitle
import com.grandsphere.fiche.data.remote.tastedive.TasteDiveApi
import com.grandsphere.fiche.data.repository.CatalogRepository
import com.grandsphere.fiche.data.repository.LibraryRepository
import com.grandsphere.fiche.domain.model.MediaType
import com.grandsphere.fiche.ui.common.RemoteTitleActions
import com.grandsphere.fiche.util.splitCsv
import com.grandsphere.fiche.util.titleSearchVariants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

data class SuggestionSeedFilter(
    val completed: Boolean = false,
    val moreOfThis: Boolean = false,
    val recommended: Boolean = false,
    val allLibrary: Boolean = false,
    val useRating: Boolean = false,
    val useGenre: Boolean = false,
    val genres: Set<String> = emptySet(),
    val disallowGenres: Set<String> = emptySet(),
    val minRating: Int? = null,
    /** Empty = seed from current result media type only. */
    val seedCategories: Set<MediaType> = emptySet(),
    val allCategories: Boolean = false,
    /** Optional explicit seed title ids from home multi-select. */
    val seedTitleIds: Set<Long> = emptySet()
)

@HiltViewModel
class SuggestionsViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val apiKeys: ApiKeyRepository,
    private val library: LibraryRepository,
    private val catalog: CatalogRepository,
    private val tasteDive: TasteDiveApi
) : ViewModel() {
    val mediaType = prefs.mediaType.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaType.SERIES)
    val libraryPrefs = library.observeLibraryPrefs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryPrefsEntity())
    val enabledMediaTypes = prefs.enabledMediaTypes.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MediaType.entries
    )
    val seedFilter = MutableStateFlow(SuggestionSeedFilter())
    val genres = MutableStateFlow<List<String>>(emptyList())
    val results = MutableStateFlow<List<RemoteTitle>>(emptyList())
    val loading = MutableStateFlow(false)
    val hasMore = MutableStateFlow(false)
    private val cachedTasteDiveNames = mutableListOf<String>()
    private var tasteDiveNameIndex = 0
    private var seedPool = listOf<TitleEntity>()
    private var seedIndex = 0
    private val seenRemoteIds = mutableSetOf<String>()
    val message = MutableSharedFlow<String>()
    val selectionMode = MutableStateFlow(false)
    val selectedRemoteIds = MutableStateFlow<Set<String>>(emptySet())

    private val actions = RemoteTitleActions(
        library = library,
        scope = viewModelScope,
        typeProvider = { mediaType.value },
        message = message,
        onExcluded = { item -> results.value = results.value.filter { it.remoteId != item.remoteId } }
    )
    val libraryIds = actions.libraryIds
    val watchlistIds = actions.watchlistIds
    val banIds = actions.banIds
    val dislikeIds = actions.dislikeIds
    val completedIds = actions.completedIds

    init {
        viewModelScope.launch {
            mediaType.collect { type ->
                refreshGenreOptions()
                actions.refreshIds(type)
            }
        }
        viewModelScope.launch {
            seedFilter.collect { refreshGenreOptions() }
        }
    }

    fun setMediaType(type: MediaType) = viewModelScope.launch { prefs.setMediaType(type) }

    fun updateFilter(block: (SuggestionSeedFilter) -> SuggestionSeedFilter) {
        seedFilter.value = block(seedFilter.value)
    }

    fun clearSelection() {
        selectionMode.value = false
        selectedRemoteIds.value = emptySet()
    }

    fun onLongPressResult(remoteId: String) {
        selectionMode.value = true
        selectedRemoteIds.value = selectedRemoteIds.value + remoteId
    }

    fun onResultClick(remoteId: String, defaultAction: () -> Unit) {
        if (selectionMode.value) {
            toggleSelection(remoteId)
        } else {
            defaultAction()
        }
    }

    fun toggleSelection(remoteId: String) {
        val next = selectedRemoteIds.value.toMutableSet()
        if (remoteId in next) next.remove(remoteId) else next.add(remoteId)
        selectedRemoteIds.value = next
        if (next.isEmpty()) selectionMode.value = false
    }

    fun selectAllResults() {
        val ids = results.value.map { it.remoteId }.toSet()
        if (ids.isEmpty()) return
        selectionMode.value = true
        selectedRemoteIds.value = ids
    }

    fun markWatchedSelected() {
        val selected = results.value.filter { it.remoteId in selectedRemoteIds.value }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            actions.markWatchedMany(selected)
            clearSelection()
        }
    }

    fun load() = viewModelScope.launch {
        val type = mediaType.value
        val filter = seedFilter.value
        val key = apiKeys.get("TASTEDIVE")
        if (key.isBlank()) {
            message.emit("Add a TasteDive API key in Settings → Catalog")
            return@launch
        }
        loading.value = true
        clearSelection()
        cachedTasteDiveNames.clear()
        tasteDiveNameIndex = 0
        seenRemoteIds.clear()
        seedIndex = 0
        runCatching {
            seedPool = buildSeeds(type, filter)
            if (seedPool.isEmpty()) {
                message.emit("No library titles match your seed filters")
                results.value = emptyList()
                hasMore.value = false
                return@runCatching
            }
            val names = fetchTasteDiveNames(listOf(seedPool.first()), type, key)
            if (names.isEmpty()) {
                results.value = emptyList()
                hasMore.value = false
                message.emit("TasteDive returned no suggestions")
                return@runCatching
            }
            cachedTasteDiveNames.addAll(names)
            val exclude = libraryIds.value + banIds.value + dislikeIds.value
            val mapped = mapNextBatch(type, exclude, batchSize = 10)
            results.value = mapped
            hasMore.value = hasMoreSuggestions(type, key, exclude)
            if (mapped.isEmpty()) {
                message.emit("TasteDive returned ${names.size} names but none matched the catalog")
            }
        }.onFailure { err ->
            val detail = (err as? HttpException)?.let { http ->
                runCatching { http.response()?.errorBody()?.string() }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.take(200)
            }
            message.emit(detail ?: err.message ?: "Suggestions failed")
            results.value = emptyList()
            hasMore.value = false
        }
        loading.value = false
        actions.refreshIds(type)
    }

    fun loadMore() = viewModelScope.launch {
        if (loading.value || !hasMore.value) return@launch
        val type = mediaType.value
        val key = apiKeys.get("TASTEDIVE")
        if (key.isBlank()) return@launch
        loading.value = true
        runCatching {
            val exclude = libraryIds.value + banIds.value + dislikeIds.value + seenRemoteIds
            var mapped = mapNextBatch(type, exclude, batchSize = 10)
            if (mapped.isEmpty()) {
                fetchMoreTasteDiveNames(type, key)
                mapped = mapNextBatch(type, exclude, batchSize = 10)
            }
            if (mapped.isNotEmpty()) {
                results.value = results.value + mapped
            }
            hasMore.value = hasMoreSuggestions(type, key, exclude)
        }.onFailure { err ->
            message.emit(err.message ?: "Could not load more suggestions")
        }
        loading.value = false
    }

    private suspend fun fetchMoreTasteDiveNames(type: MediaType, key: String) {
        while (seedIndex + 1 < seedPool.size) {
            seedIndex++
            val names = fetchTasteDiveNames(listOf(seedPool[seedIndex]), type, key)
            val fresh = names.filter { it !in cachedTasteDiveNames }
            if (fresh.isNotEmpty()) {
                cachedTasteDiveNames.addAll(fresh)
                return
            }
        }
    }

    private suspend fun hasMoreSuggestions(type: MediaType, key: String, exclude: Set<String>): Boolean {
        if (tasteDiveNameIndex < cachedTasteDiveNames.size) return true
        if (seedIndex + 1 < seedPool.size) return true
        return false
    }

    private suspend fun fetchTasteDiveNames(
        seeds: List<TitleEntity>,
        type: MediaType,
        key: String
    ): List<String> {
        if (seeds.isEmpty()) return emptyList()
        val seed = seeds.first()
        val resultType = tasteDiveResultType(type)
        for (q in buildTasteDiveQueries(seed, type)) {
            val response = tasteDive.similar(
                query = q,
                type = resultType,
                key = key,
                limit = 20
            )
            if (!response.error.isNullOrBlank()) continue
            val names = response.similar?.results.orEmpty()
                .mapNotNull { it.name?.takeIf { name -> name.isNotBlank() } }
                .distinct()
            if (names.isNotEmpty()) return names
        }
        return emptyList()
    }

    private suspend fun mapNextBatch(
        type: MediaType,
        exclude: Set<String>,
        batchSize: Int
    ): List<RemoteTitle> {
        val mapped = mutableListOf<RemoteTitle>()
        val allExclude = exclude + seenRemoteIds
        while (mapped.size < batchSize && tasteDiveNameIndex < cachedTasteDiveNames.size) {
            val name = cachedTasteDiveNames[tasteDiveNameIndex++]
            val hit = findBestCatalogMatch(type, name, allExclude)
            if (hit != null && hit.remoteId !in seenRemoteIds) {
                mapped += hit
                seenRemoteIds += hit.remoteId
            }
        }
        return mapped
    }

    private suspend fun findBestCatalogMatch(
        type: MediaType,
        name: String,
        exclude: Set<String>
    ): RemoteTitle? {
        var best: RemoteTitle? = null
        var bestScore = 0
        for (variant in titleSearchVariants(name)) {
            val hits = runCatching { catalog.search(type, variant) }.getOrDefault(emptyList())
            for (hit in hits) {
                if (hit.remoteId in exclude) continue
                val score = titleSimilarity(name, hit.title)
                if (score > bestScore) {
                    bestScore = score
                    best = hit
                }
            }
            if (bestScore >= 100) break
        }
        return if (bestScore >= 50) best else null
    }

    private fun titleSimilarity(query: String, candidate: String): Int {
        val normalizedQuery = query.lowercase().replace(Regex("""[^a-z0-9]"""), "")
        val normalizedCandidate = candidate.lowercase().replace(Regex("""[^a-z0-9]"""), "")
        return when {
            normalizedQuery == normalizedCandidate -> 100
            normalizedCandidate.contains(normalizedQuery) || normalizedQuery.contains(normalizedCandidate) -> 60
            else -> 10
        }
    }

    fun track(item: RemoteTitle) = actions.add(item)
    fun watchlist(item: RemoteTitle) = actions.watchlist(item)
    fun dislike(item: RemoteTitle) = actions.dislike(item)
    fun ban(item: RemoteTitle) = actions.ban(item)
    fun markWatched(item: RemoteTitle) = actions.markWatched(item)

    fun setSeedTitles(ids: Set<Long>) {
        seedFilter.value = seedFilter.value.copy(seedTitleIds = ids)
    }

    private suspend fun buildSeeds(type: MediaType, filter: SuggestionSeedFilter): List<TitleEntity> {
        if (filter.seedTitleIds.isNotEmpty()) {
            return library.allTitles()
                .filter { it.id in filter.seedTitleIds }
                .let { rankSeeds(it, cap = 30) }
        }
        val enabled = enabledMediaTypes.value.ifEmpty { MediaType.entries }
        val types = when {
            filter.allCategories -> enabled
            filter.seedCategories.isNotEmpty() -> filter.seedCategories.filter { it in enabled }
            else -> listOf(type)
        }
        val typeNames = types.map { it.name }.toSet()
        val all = library.allTitles().filter { it.mediaType in typeNames }
        val statusSelected = filter.completed || filter.moreOfThis || filter.recommended
        var pool = all.filter { title ->
            when {
                filter.allLibrary -> true
                !statusSelected -> true
                else -> (filter.completed && title.completed) ||
                    (filter.moreOfThis && title.moreOfThis) ||
                    (filter.recommended && title.recommended)
            }
        }
        if (pool.size <= 20) return rankSeeds(pool)

        if (filter.useGenre && filter.genres.isNotEmpty()) {
            val genreMatches = pool.filter { title ->
                filter.genres.all { wanted ->
                    splitCsv(title.genres).any { it.equals(wanted, true) }
                }
            }
            if (genreMatches.isNotEmpty()) pool = genreMatches
            if (pool.size <= 20) return rankSeeds(pool)
        }

        if (filter.disallowGenres.isNotEmpty()) {
            pool = pool.filter { title ->
                filter.disallowGenres.none { unwanted ->
                    splitCsv(title.genres).any { it.equals(unwanted, true) }
                }
            }
        }

        val recommended = pool.filter { it.recommended }
        if (recommended.isNotEmpty()) pool = recommended
        if (pool.size <= 20) return rankSeeds(pool)

        val moreOfThis = pool.filter { it.moreOfThis }
        if (moreOfThis.isNotEmpty()) pool = moreOfThis
        if (pool.size <= 20) return rankSeeds(pool)

        if (filter.useRating && filter.minRating != null) {
            pool = pool.filter { title ->
                val rating = title.userRating
                rating == null || rating >= filter.minRating
            }
        }
        return rankSeeds(pool)
    }

    private fun rankSeeds(pool: List<TitleEntity>, cap: Int = 20): List<TitleEntity> =
        pool.sortedWith(
            compareByDescending<TitleEntity> { it.userRating ?: -1f }
                .thenByDescending { it.updatedAt }
        ).take(cap)

    /** Probe-tested formats: typed prefix is most reliable; colons in titles must be sanitized. */
    private fun buildTasteDiveQueries(seed: TitleEntity, resultType: MediaType): List<String> {
        val clean = sanitizeTitle(seed.title)
        if (clean.isBlank()) return emptyList()
        val seedPrefix = tasteDivePrefix(seed.mediaType)
        val outputPrefix = tasteDivePrefix(resultType.name)
        return listOf(
            "$seedPrefix:$clean",
            clean,
            "$outputPrefix:$clean"
        ).distinct()
    }

    private fun sanitizeTitle(raw: String): String =
        raw.replace(":", " - ")
            .replace(",", " ")
            .replace(Regex("""[\r\n\t]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(80)

    private fun tasteDivePrefix(mediaType: String): String = when (mediaType) {
        MediaType.MOVIE.name -> "movie"
        MediaType.SERIES.name, MediaType.ANIME.name -> "show"
        MediaType.BOOK.name -> "book"
        MediaType.GAME.name -> "game"
        else -> "movie"
    }

    private suspend fun refreshGenreOptions() {
        val filter = seedFilter.value
        val type = mediaType.value
        val enabled = enabledMediaTypes.value.ifEmpty { MediaType.entries }
        val types = when {
            filter.allCategories -> enabled
            filter.seedCategories.isNotEmpty() -> filter.seedCategories.filter { it in enabled }
            else -> listOf(type)
        }
        genres.value = types.flatMap { library.allGenreOptions(it) }.distinct().sorted()
    }

    private fun tasteDiveResultType(type: MediaType): String = when (type) {
        MediaType.MOVIE -> "movie"
        MediaType.SERIES, MediaType.ANIME -> "show"
        MediaType.BOOK -> "book"
        MediaType.GAME -> "game"
    }
}
