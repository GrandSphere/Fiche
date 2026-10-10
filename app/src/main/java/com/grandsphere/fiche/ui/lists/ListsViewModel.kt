package com.grandsphere.fiche.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grandsphere.fiche.data.local.entity.BanEntity
import com.grandsphere.fiche.data.local.entity.DislikeEntity
import com.grandsphere.fiche.data.local.entity.LibraryPrefsEntity
import com.grandsphere.fiche.data.local.entity.WatchlistEntity
import com.grandsphere.fiche.data.prefs.UserPreferencesRepository
import com.grandsphere.fiche.data.remote.RemoteTitle
import com.grandsphere.fiche.data.repository.LibraryRepository
import com.grandsphere.fiche.domain.model.MediaType
import com.grandsphere.fiche.ui.common.RemoteTitleActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ListsViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val prefs: UserPreferencesRepository
) : ViewModel() {
    val mediaType = prefs.mediaType.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaType.SERIES)
    val libraryPrefs = library.observeLibraryPrefs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryPrefsEntity())
    val enabledMediaTypes = prefs.enabledMediaTypes.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaType.entries
    )
    val message = MutableSharedFlow<String>()

    private val actions = RemoteTitleActions(
        library = library,
        scope = viewModelScope,
        typeProvider = { mediaType.value },
        message = message
    )
    val libraryIds = actions.libraryIds
    val completedIds = actions.completedIds
    val watchlistIds = actions.watchlistIds
    val banIds = actions.banIds
    val dislikeIds = actions.dislikeIds

    val watchlist = mediaType.flatMapLatest { library.observeWatchlist(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bans = mediaType.flatMapLatest { library.observeBans(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dislikes = mediaType.flatMapLatest { library.observeDislikes(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val notes = mediaType.flatMapLatest { library.observeNotes(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    init {
        viewModelScope.launch {
            mediaType.collect { actions.refreshIds(it) }
        }
    }

    fun add(item: RemoteTitle) = actions.add(item)
    fun watchlist(item: RemoteTitle) = actions.watchlist(item)
    fun dislike(item: RemoteTitle) = actions.dislike(item)
    fun ban(item: RemoteTitle) = actions.ban(item)
    fun markWatched(item: RemoteTitle) = actions.markWatched(item)

    fun unban(item: BanEntity) = viewModelScope.launch { library.unban(item.id) }

    fun undislike(item: DislikeEntity) = viewModelScope.launch { library.undislike(item.id) }

    fun saveNotes(body: String) = viewModelScope.launch {
        library.saveNotes(mediaType.value, body)
    }

    fun setMediaType(type: MediaType) = viewModelScope.launch { prefs.setMediaType(type) }
}

fun WatchlistEntity.toRemoteTitle(): RemoteTitle = RemoteTitle(
    mediaType = mediaType,
    remoteId = remoteId,
    title = title,
    year = year,
    posterUrl = posterUrl,
    overview = overview,
    genres = genres.split(',').map { it.trim() }.filter { it.isNotEmpty() },
    collectionId = collectionId,
    collectionName = collectionName
)
