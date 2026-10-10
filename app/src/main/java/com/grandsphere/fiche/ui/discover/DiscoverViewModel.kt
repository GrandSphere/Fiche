package com.grandsphere.fiche.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grandsphere.fiche.data.local.entity.LibraryPrefsEntity
import com.grandsphere.fiche.data.prefs.UserPreferencesRepository
import com.grandsphere.fiche.data.remote.RemoteTitle
import com.grandsphere.fiche.data.repository.CatalogRepository
import com.grandsphere.fiche.data.repository.LibraryRepository
import com.grandsphere.fiche.domain.model.DiscoverCriterion
import com.grandsphere.fiche.domain.model.DiscoverFilter
import com.grandsphere.fiche.domain.model.GenreCatalog
import com.grandsphere.fiche.domain.model.MediaType
import com.grandsphere.fiche.ui.common.RemoteTitleActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    prefs: UserPreferencesRepository
) : ViewModel() {
    val enabledMediaTypes = prefs.enabledMediaTypes.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaType.entries
    )
    val findType = MutableStateFlow(MediaType.SERIES)
    val libraryPrefs = library.observeLibraryPrefs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryPrefsEntity())
    val filter = MutableStateFlow(DiscoverFilter())
    val criteria = MutableStateFlow<Set<DiscoverCriterion>>(emptySet())
    val results = MutableStateFlow<List<RemoteTitle>>(emptyList())
    val loading = MutableStateFlow(false)
    val hasMore = MutableStateFlow(false)
    val statusMessage = MutableStateFlow<String?>(null)
    private var currentPage = 0
    private var tvScanCursor = 0
    val genres = MutableStateFlow<List<String>>(emptyList())
    val message = MutableSharedFlow<String>()
    val selectionMode = MutableStateFlow(false)
    val selectedRemoteIds = MutableStateFlow<Set<String>>(emptySet())

    private val actions = RemoteTitleActions(
        library = library,
        scope = viewModelScope,
        typeProvider = { findType.value },
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
            combine(actions.banIds, actions.dislikeIds) { ban, dislike -> ban + dislike }.collect { excluded ->
                results.value = results.value.filter { it.remoteId !in excluded }
            }
        }
        viewModelScope.launch {
            findType.value = prefs.mediaType.first()
            findType.collect { type ->
                actions.refreshIds(type)
                genres.value = (library.allGenreOptions(type) + GenreCatalog.forType(type)).distinct().sorted()
            }
        }
    }

    fun setFindType(type: MediaType) {
        findType.value = type
        // Only reset criteria that are type-specific (actor/author/producer/developer/publisher/ended)
        // Keep genre and year selections so switching tabs to peek doesn't destroy filters.
        val keep = setOf(DiscoverCriterion.GENRE, DiscoverCriterion.YEAR, DiscoverCriterion.NEW_RELEASES)
        criteria.value = criteria.value.intersect(keep)
        filter.value = filter.value.copy(
            actor = "", author = "", producer = "", developer = "", publisher = "", endedOnly = false
        )
        // Do NOT auto-load — match Suggestions and wait for an explicit user trigger.
    }

    fun setQuery(value: String) {
        filter.value = filter.value.copy(query = value)
    }

    fun addCriterion(criterion: DiscoverCriterion) {
        criteria.value = criteria.value + criterion
        if (criterion == DiscoverCriterion.NEW_RELEASES) {
            filter.value = filter.value.copy(newReleases = true)
        }
        if (criterion == DiscoverCriterion.ENDED) {
            filter.value = filter.value.copy(endedOnly = true)
        }
    }

    fun removeCriterion(criterion: DiscoverCriterion) {
        criteria.value = criteria.value - criterion
        filter.value = when (criterion) {
            DiscoverCriterion.GENRE -> filter.value.copy(genres = emptySet())
            DiscoverCriterion.DISALLOW_GENRE -> filter.value.copy(disallowGenres = emptySet())
            DiscoverCriterion.YEAR -> filter.value.copy(yearFrom = null, yearTo = null, yearFromText = "", yearToText = "")
            DiscoverCriterion.ACTOR -> filter.value.copy(actor = "")
            DiscoverCriterion.AUTHOR -> filter.value.copy(author = "")
            DiscoverCriterion.PRODUCER -> filter.value.copy(producer = "")
            DiscoverCriterion.DEVELOPER -> filter.value.copy(developer = "")
            DiscoverCriterion.PUBLISHER -> filter.value.copy(publisher = "")
            DiscoverCriterion.NEW_RELEASES -> filter.value.copy(newReleases = false)
            DiscoverCriterion.ENDED -> filter.value.copy(endedOnly = false)
        }
    }

    fun addGenre(genre: String) {
        filter.value = filter.value.copy(genres = filter.value.genres + genre)
    }

    fun removeGenre(genre: String) {
        filter.value = filter.value.copy(genres = filter.value.genres - genre)
    }

    fun addDisallowGenre(genre: String) {
        filter.value = filter.value.copy(disallowGenres = filter.value.disallowGenres + genre)
    }

    fun removeDisallowGenre(genre: String) {
        filter.value = filter.value.copy(disallowGenres = filter.value.disallowGenres - genre)
    }

    fun setYear(from: Int?, to: Int?) {
        filter.value = filter.value.copy(yearFrom = from, yearTo = to)
    }

    fun setYearText(fromText: String, toText: String) {
        filter.value = filter.value.copy(
            yearFromText = fromText,
            yearToText = toText,
            yearFrom = fromText.toIntOrNull(),
            yearTo = toText.toIntOrNull()
        )
    }

    fun setActor(value: String) {
        filter.value = filter.value.copy(actor = value)
    }

    fun setAuthor(value: String) {
        filter.value = filter.value.copy(author = value)
    }

    fun setProducer(value: String) {
        filter.value = filter.value.copy(producer = value)
    }

    fun setDeveloper(value: String) {
        filter.value = filter.value.copy(developer = value)
    }

    fun setPublisher(value: String) {
        filter.value = filter.value.copy(publisher = value)
    }

    fun availableCriteria(): List<DiscoverCriterion> {
        val type = findType.value
        val all = when (type) {
            MediaType.BOOK -> listOf(
                DiscoverCriterion.GENRE,
                DiscoverCriterion.DISALLOW_GENRE,
                DiscoverCriterion.YEAR,
                DiscoverCriterion.AUTHOR,
                DiscoverCriterion.PUBLISHER,
                DiscoverCriterion.NEW_RELEASES
            )
            MediaType.GAME -> listOf(
                DiscoverCriterion.GENRE,
                DiscoverCriterion.DISALLOW_GENRE,
                DiscoverCriterion.YEAR,
                DiscoverCriterion.DEVELOPER,
                DiscoverCriterion.NEW_RELEASES
            )
            else -> listOf(
                DiscoverCriterion.GENRE,
                DiscoverCriterion.DISALLOW_GENRE,
                DiscoverCriterion.YEAR,
                DiscoverCriterion.ACTOR,
                DiscoverCriterion.PRODUCER,
                DiscoverCriterion.NEW_RELEASES,
                DiscoverCriterion.ENDED
            )
        }
        return all.filter { it !in criteria.value }
    }

    fun load() {
        viewModelScope.launch {
            loading.value = true
            currentPage = 0
            tvScanCursor = 0
            val type = findType.value
            actions.refreshIds(type)
            val exclude = libraryIds.value + banIds.value + dislikeIds.value
            runCatching {
                catalog.fillDiscover(type, filter.value, exclude, startPage = 0, tvScanCursor = 0)
            }
                .onSuccess { page ->
                    results.value = page.items
                    hasMore.value = page.hasMore
                    currentPage = page.nextPage
                    tvScanCursor = page.tvScanCursor
                    statusMessage.value = if (page.noNewResults) "No new results could be found" else null
                }
                .onFailure {
                    results.value = emptyList()
                    hasMore.value = false
                    message.emit(it.message ?: "Could not load titles")
                }
            loading.value = false
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            if (loading.value || !hasMore.value) return@launch
            loading.value = true
            val type = findType.value
            val exclude = libraryIds.value + banIds.value + dislikeIds.value + results.value.map { it.remoteId }.toSet()
            statusMessage.value = null
            runCatching {
                catalog.fillDiscover(
                    type,
                    filter.value,
                    exclude,
                    startPage = currentPage,
                    tvScanCursor = tvScanCursor
                )
            }
                .onSuccess { page ->
                    results.value = results.value + page.items
                    hasMore.value = page.hasMore
                    currentPage = page.nextPage
                    tvScanCursor = page.tvScanCursor
                    if (page.noNewResults) {
                        statusMessage.value = "No new results could be found"
                        hasMore.value = false
                    }
                }
                .onFailure { message.emit(it.message ?: "Could not load more titles") }
            loading.value = false
        }
    }

    fun add(item: RemoteTitle) = actions.add(item)
    fun watchlist(item: RemoteTitle) = actions.watchlist(item)
    fun dislike(item: RemoteTitle) = actions.dislike(item)
    fun ban(item: RemoteTitle) = actions.ban(item)
    fun markWatched(item: RemoteTitle) = actions.markWatched(item)

    fun onResultClick(remoteId: String) {
        if (selectionMode.value) {
            selectedRemoteIds.value = if (remoteId in selectedRemoteIds.value)
                selectedRemoteIds.value - remoteId
            else
                selectedRemoteIds.value + remoteId
            if (selectedRemoteIds.value.isEmpty()) selectionMode.value = false
        }
    }

    fun onLongPressResult(remoteId: String) {
        selectionMode.value = true
        selectedRemoteIds.value = selectedRemoteIds.value + remoteId
    }

    fun clearSelection() {
        selectionMode.value = false
        selectedRemoteIds.value = emptySet()
    }

    fun selectAllResults() {
        selectedRemoteIds.value = results.value.map { it.remoteId }.toSet()
    }

    fun markWatchedSelected() = viewModelScope.launch {
        val ids = selectedRemoteIds.value.toSet()
        val toMark = results.value.filter { it.remoteId in ids }
        actions.markWatchedMany(toMark)
        clearSelection()
    }
}
