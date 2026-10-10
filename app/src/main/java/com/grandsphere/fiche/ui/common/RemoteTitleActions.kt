package com.grandsphere.fiche.ui.common

import com.grandsphere.fiche.data.remote.RemoteTitle
import com.grandsphere.fiche.data.repository.LibraryRepository
import com.grandsphere.fiche.domain.model.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Shared add / watchlist / ban / dislike / mark-watched actions used by Add Search,
 * Find New, and Suggestions.
 */
class RemoteTitleActions(
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
    private val typeProvider: () -> MediaType,
    private val message: MutableSharedFlow<String>,
    private val onExcluded: (RemoteTitle) -> Unit = {}
) {
    val libraryIds = MutableStateFlow<Set<String>>(emptySet())
    val completedIds = MutableStateFlow<Set<String>>(emptySet())
    val watchlistIds = MutableStateFlow<Set<String>>(emptySet())
    val banIds = MutableStateFlow<Set<String>>(emptySet())
    val dislikeIds = MutableStateFlow<Set<String>>(emptySet())

    suspend fun refreshIds(type: MediaType = typeProvider()) {
        libraryIds.value = library.libraryIds(type)
        completedIds.value = library.completedIds(type)
        watchlistIds.value = library.watchlistIds(type)
        banIds.value = library.banIds(type)
        dislikeIds.value = library.dislikeIds(type)
    }

    fun add(item: RemoteTitle) = scope.launch {
        val type = typeProvider()
        message.emit("Adding ${item.title}…")
        runCatching { library.addToLibrary(type, item) }
            .onSuccess {
                refreshIds(type)
                message.emit("Added ${item.title}")
            }
            .onFailure { message.emit(it.message ?: "Could not add") }
    }

    fun watchlist(item: RemoteTitle) = scope.launch {
        val type = typeProvider()
        if (item.remoteId in watchlistIds.value) {
            library.removeWatchlist(type, item.remoteId)
            refreshIds(type)
            message.emit("Removed from interests")
        } else {
            library.addToWatchlist(type, item)
            refreshIds(type)
            message.emit("Saved to interests")
        }
    }

    fun dislike(item: RemoteTitle) = scope.launch {
        val type = typeProvider()
        library.dislike(type, item)
        refreshIds(type)
        onExcluded(item)
        message.emit("Disliked ${item.title}")
    }

    fun ban(item: RemoteTitle) = scope.launch {
        val type = typeProvider()
        library.ban(type, item)
        refreshIds(type)
        onExcluded(item)
        message.emit("Banned ${item.title}")
    }

    fun markWatched(item: RemoteTitle) = scope.launch {
        val type = typeProvider()
        message.emit("Adding ${item.title}…")
        runCatching { library.addAsWatched(type, item) }
            .onSuccess {
                refreshIds(type)
                completedIds.value = completedIds.value + item.remoteId
                message.emit("Added ${item.title}")
            }
            .onFailure { message.emit(it.message ?: "Could not mark watched") }
    }

    suspend fun markWatchedMany(items: List<RemoteTitle>) {
        val type = typeProvider()
        items.forEach { item ->
            runCatching { library.addAsWatched(type, item) }
        }
        refreshIds(type)
        completedIds.value = completedIds.value + items.map { it.remoteId }.toSet()
        message.emit("Marked ${items.size} as watched")
    }
}
