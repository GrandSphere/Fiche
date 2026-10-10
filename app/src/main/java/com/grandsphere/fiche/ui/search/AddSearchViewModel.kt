package com.grandsphere.fiche.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grandsphere.fiche.data.local.entity.LibraryPrefsEntity
import com.grandsphere.fiche.data.prefs.UserPreferencesRepository
import com.grandsphere.fiche.data.remote.RemoteTitle
import com.grandsphere.fiche.data.repository.AddSearchPrefill
import com.grandsphere.fiche.data.repository.AddSearchPrefillStore
import com.grandsphere.fiche.data.repository.CatalogRepository
import com.grandsphere.fiche.data.repository.LibraryRepository
import com.grandsphere.fiche.domain.model.MediaType
import com.grandsphere.fiche.ui.common.RemoteTitleActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddSearchViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    library: LibraryRepository,
    prefs: UserPreferencesRepository,
    private val prefillStore: AddSearchPrefillStore
) : ViewModel() {
    private val typeOverride = MutableStateFlow<MediaType?>(null)
    val mediaType = combine(prefs.mediaType, typeOverride) { pref, override -> override ?: pref }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaType.SERIES)
    val enabledMediaTypes = prefs.enabledMediaTypes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaType.entries)
    val libraryPrefs = library.observeLibraryPrefs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryPrefsEntity())
    val query = MutableStateFlow("")
    val results = MutableStateFlow<List<RemoteTitle>>(emptyList())
    val loading = MutableStateFlow(false)
    val hasMore = MutableStateFlow(false)
    val statusMessage = MutableStateFlow<String?>(null)
    val fromShare = MutableStateFlow(false)
    val message = MutableSharedFlow<String>()

    private val actions = RemoteTitleActions(
        library = library,
        scope = viewModelScope,
        typeProvider = { typeOverride.value ?: mediaType.value },
        message = message
    )
    val libraryIds = actions.libraryIds
    val watchlistIds = actions.watchlistIds
    val banIds = actions.banIds
    val dislikeIds = actions.dislikeIds
    val completedIds = actions.completedIds

    init {
        prefillStore.pending.value?.let { prefill ->
            prefillStore.clear()
            applyPrefill(prefill)
        }
        viewModelScope.launch { mediaType.collect { actions.refreshIds(it) } }
        viewModelScope.launch {
            prefillStore.pending.filterNotNull().collect { prefill ->
                prefillStore.clear()
                applyPrefill(prefill)
            }
        }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setMediaType(type: MediaType) {
        typeOverride.value = type
        results.value = emptyList()
    }

    fun search() {
        search(typeOverride.value ?: mediaType.value)
    }

    fun loadMore() {
        search(typeOverride.value ?: mediaType.value, append = true)
    }

    fun add(item: RemoteTitle) = actions.add(item)
    fun watchlist(item: RemoteTitle) = actions.watchlist(item)
    fun dislike(item: RemoteTitle) = actions.dislike(item)
    fun ban(item: RemoteTitle) = actions.ban(item)
    fun markWatched(item: RemoteTitle) = actions.markWatched(item)

    private fun applyPrefill(prefill: AddSearchPrefill) {
        typeOverride.value = prefill.type
        query.value = prefill.query
        fromShare.value = prefill.fromShare
        results.value = listOfNotNull(prefill.seed)
        if (prefill.resolving) {
            loading.value = true
            return
        }
        viewModelScope.launch {
            actions.refreshIds(prefill.type)
            if (prefill.query.isNotBlank() || prefill.seed != null) {
                search(prefill.type, pinFirst = prefill.seed)
            } else {
                loading.value = false
                fromShare.value = false
            }
        }
    }

    private fun search(type: MediaType, pinFirst: RemoteTitle? = null, append: Boolean = false) {
        viewModelScope.launch {
            loading.value = true
            statusMessage.value = null
            if (!append) fromShare.value = fromShare.value
            runCatching {
                actions.refreshIds(type)
                val excluded = actions.banIds.value + actions.dislikeIds.value
                val already = if (append) results.value.map { it.remoteId }.toSet() else emptySet()
                catalog.fillSearch(type, query.value, excluded, alreadyShown = already)
            }
                .onSuccess { page ->
                    val next = if (pinFirst == null) {
                        page.items
                    } else {
                        listOf(pinFirst) + page.items.filter { it.remoteId != pinFirst.remoteId }
                    }
                    results.value = if (append) results.value + next else next
                    hasMore.value = page.hasMore
                    if (page.noNewResults) {
                        statusMessage.value = "No new results could be found"
                        hasMore.value = false
                    }
                }
                .onFailure { message.emit(it.message ?: "Search failed") }
            loading.value = false
            fromShare.value = false
        }
    }
}
