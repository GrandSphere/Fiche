package com.grandsphere.fiche.ui.home

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grandsphere.fiche.data.backup.BackupRepository
import com.grandsphere.fiche.data.backup.formatSharedNames
import com.grandsphere.fiche.data.local.entity.LibraryPrefsEntity
import com.grandsphere.fiche.data.local.entity.TitleEntity
import com.grandsphere.fiche.data.prefs.UserPreferencesRepository
import com.grandsphere.fiche.data.repository.LibraryRepository
import com.grandsphere.fiche.data.repository.RefreshProgress
import com.grandsphere.fiche.domain.model.LibraryFilter
import com.grandsphere.fiche.domain.model.MediaType
import com.grandsphere.fiche.domain.model.SortOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val backup: BackupRepository,
    private val prefs: UserPreferencesRepository
) : ViewModel() {
    val mediaType = prefs.mediaType.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaType.SERIES
    )
    val enabledMediaTypes = prefs.enabledMediaTypes.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaType.entries
    )
    val compactMode = prefs.compactMode.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), false
    )
    val showCompletedMark = prefs.appearance.map { it.showCompletedMark }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), true
    )
    val libraryPrefs = library.observeLibraryPrefs().stateIn(
        viewModelScope, SharingStarted.Eagerly, LibraryPrefsEntity()
    )
    val showCategoryTabs = libraryPrefs.map { it.showCategoryTabs }.stateIn(
        viewModelScope, SharingStarted.Eagerly, true
    )
    val query = MutableStateFlow("")
    val filter = MutableStateFlow(LibraryFilter(sort = SortOption.RATING, sorts = listOf(SortOption.RATING)))
    val refreshing = MutableStateFlow(false)
    val refreshProgress = MutableStateFlow<RefreshProgress?>(null)
    val message = MutableSharedFlow<String>()
    val pendingShare = MutableSharedFlow<Intent>()
    val navigateSuggestions = MutableSharedFlow<Set<Long>>()
    val genres = MutableStateFlow<List<String>>(emptyList())
    val selectionMode = MutableStateFlow(false)
    val selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    val combineSequels = prefs.combineSequels.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), false
    )

    val titles = combine(mediaType, query, filter, combineSequels) { type, q, f, combine ->
        val active = f.copy(query = q, showHidden = if (q.isNotBlank()) true else f.showHidden)
        Triple(type, active, combine)
    }.flatMapLatest { (type, activeFilter, combine) ->
        library.observeFiltered(type, flowOf(activeFilter), flowOf(combine))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            mediaType.collect { genres.value = library.allGenreOptions(it) }
        }
        viewModelScope.launch { library.backfillWatchTimeIfNeeded() }
        viewModelScope.launch {
            library.observeLibraryPrefs().collect { prefsEntity ->
                val sorts = prefsEntity.sortsCsv.split(',')
                    .mapNotNull { raw -> SortOption.entries.firstOrNull { it.name == raw.trim() } }
                    .ifEmpty { listOf(SortOption.RATING) }
                filter.value = filter.value.copy(
                    sorts = sorts,
                    sort = sorts.first(),
                    showHidden = prefsEntity.showHidden,
                    hideCompleted = prefsEntity.hideCompleted,
                    hideIncomplete = prefsEntity.hideIncomplete
                )
            }
        }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun clearQuery() {
        query.value = ""
    }

    fun applyFilter(value: LibraryFilter) {
        filter.value = value
        viewModelScope.launch {
            library.updateLibraryPrefs { prefs ->
                prefs.copy(
                    sortsCsv = value.sortChain().joinToString(",") { it.name },
                    showHidden = value.showHidden,
                    hideCompleted = value.hideCompleted,
                    hideIncomplete = value.hideIncomplete
                )
            }
        }
    }

    fun setMediaType(type: MediaType) {
        viewModelScope.launch { prefs.setMediaType(type) }
    }

    fun refreshRemote() {
        viewModelScope.launch {
            refreshing.value = true
            refreshProgress.value = null
            runCatching {
                library.refreshLibrary(mediaType.value) { refreshProgress.value = it }
            }
                .onSuccess { result ->
                    val extra = if (result.reopened > 0) " · ${result.reopened} marked incomplete" else ""
                    message.emit(
                        if (result.showsUpdated == 0 && result.reopened == 0) "Library is up to date"
                        else "Updated ${result.showsUpdated} titles$extra"
                    )
                }
                .onFailure { message.emit(it.message ?: "Refresh failed") }
            refreshProgress.value = null
            refreshing.value = false
        }
    }

    fun grouped(items: List<TitleEntity>): List<Pair<String?, List<TitleEntity>>> {
        return when (mediaType.value) {
            MediaType.MOVIE, MediaType.SERIES, MediaType.ANIME -> listOf(null to items)
            MediaType.BOOK, MediaType.GAME -> items.groupBy { it.seriesName }
                .toList()
                .sortedBy { it.first ?: "zzz" }
                .map { it.first to it.second.sortedBy { book -> book.year ?: 0 } }
        }
    }

    fun onLongPressTitle(id: Long) {
        selectionMode.value = true
        selectedIds.value = selectedIds.value + id
    }

    fun selectAll(ids: List<Long>) {
        if (ids.isEmpty()) return
        selectionMode.value = true
        selectedIds.value = ids.toSet()
    }

    fun onTitleClick(id: Long, openDetail: () -> Unit) {
        if (selectionMode.value) {
            toggleSelection(id)
        } else {
            openDetail()
        }
    }

    fun toggleSelection(id: Long) {
        val next = selectedIds.value.toMutableSet()
        if (id in next) next.remove(id) else next.add(id)
        selectedIds.value = next
        if (next.isEmpty()) selectionMode.value = false
    }

    fun clearSelection() {
        selectionMode.value = false
        selectedIds.value = emptySet()
    }

    fun shareSelected() {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { backup.shareFile(backup.exportSharedTitles(ids)) }
                .onSuccess { pendingShare.emit(it) }
                .onFailure { message.emit(it.message ?: "Share failed") }
        }
    }

    fun shareSelectedNames() {
        val ids = selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val selected = library.allTitles().filter { it.id in ids }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, formatSharedNames(selected))
            }
            pendingShare.emit(intent)
        }
    }

    fun shareSelectedRecommendations() {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { backup.shareFile(backup.exportRecommendations(ids)) }
                .onSuccess { pendingShare.emit(it) }
                .onFailure { message.emit(it.message ?: "Share failed") }
        }
    }

    fun shareSelectedCollection(items: List<TitleEntity>) {
        val id = items.firstOrNull { it.id in selectedIds.value && !it.collectionId.isNullOrBlank() }?.id
            ?: return
        viewModelScope.launch {
            runCatching { backup.shareFile(backup.exportSharedCollection(id)) }
                .onSuccess { pendingShare.emit(it) }
                .onFailure { message.emit(it.message ?: "Share failed") }
        }
    }

    fun dislikeSelected() {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { library.bulkDislike(ids) }
                .onSuccess {
                    clearSelection()
                    message.emit("Added ${ids.size} to dislike list")
                }
                .onFailure { message.emit(it.message ?: "Could not dislike") }
        }
    }

    fun banSelected() {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { library.bulkBan(ids) }
                .onSuccess {
                    clearSelection()
                    message.emit("Moved ${ids.size} to ban list")
                }
                .onFailure { message.emit(it.message ?: "Could not ban") }
        }
    }

    fun getSuggestionsForSelected() {
        val ids = selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            navigateSuggestions.emit(ids)
            clearSelection()
        }
    }

    fun moveSelectedToInterests() {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { library.bulkMoveToInterests(ids) }
                .onSuccess {
                    clearSelection()
                    message.emit("Moved ${ids.size} to interests")
                }
                .onFailure { message.emit(it.message ?: "Could not move") }
        }
    }

    fun deleteSelected() {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { library.bulkDelete(ids) }
                .onSuccess {
                    clearSelection()
                    message.emit("Removed ${ids.size}")
                }
                .onFailure { message.emit(it.message ?: "Could not remove") }
        }
    }

    fun markSelectedWatched() {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { library.bulkMarkWatched(ids) }
                .onSuccess {
                    clearSelection()
                    message.emit("Marked ${ids.size} watched")
                }
                .onFailure { message.emit(it.message ?: "Could not mark watched") }
        }
    }

    fun hideSelected() {
        applyHideToSelected(hidden = true)
    }

    fun unhideSelected() {
        applyHideToSelected(hidden = false)
    }

    private fun applyHideToSelected(hidden: Boolean) {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { library.bulkSetHidden(ids, hidden) }
                .onSuccess {
                    clearSelection()
                    message.emit(if (hidden) "Hidden ${ids.size}" else "Unhid ${ids.size}")
                }
                .onFailure { message.emit(it.message ?: "Could not update") }
        }
    }

    fun canHideSelected(items: List<TitleEntity>): Boolean =
        items.any { it.id in selectedIds.value && !it.hidden }

    fun canUnhideSelected(items: List<TitleEntity>): Boolean =
        items.any { it.id in selectedIds.value && it.hidden }

    fun canRemoveCollection(items: List<TitleEntity>): Boolean {
        val selected = items.filter { it.id in selectedIds.value && it.mediaType == MediaType.MOVIE.name }
        val collectionIds = selected.mapNotNull { it.collectionId?.takeIf { id -> id.isNotBlank() } }.distinct()
        return collectionIds.size == 1
    }

    fun banTitle(id: Long) {
        viewModelScope.launch {
            runCatching { library.banLibraryTitle(id) }
                .onSuccess { message.emit("Moved to ban list") }
                .onFailure { message.emit(it.message ?: "Could not ban") }
        }
    }

    fun dislikeTitle(id: Long) {
        viewModelScope.launch {
            runCatching { library.dislikeLibraryTitle(id) }
                .onSuccess { message.emit("Added to dislike list") }
                .onFailure { message.emit(it.message ?: "Could not dislike") }
        }
    }

    fun removeTitle(id: Long) {
        viewModelScope.launch {
            runCatching { library.deleteTitle(id) }
                .onSuccess { message.emit("Removed") }
                .onFailure { message.emit(it.message ?: "Could not remove") }
        }
    }

    fun removeCollection(id: Long) {
        viewModelScope.launch {
            runCatching { library.removeMovieCollection(id) }
                .onSuccess { message.emit("Removed collection") }
                .onFailure { message.emit(it.message ?: "Could not remove collection") }
        }
    }

    fun removeSelectedCollection(items: List<TitleEntity>) {
        val id = items.firstOrNull { it.id in selectedIds.value && !it.collectionId.isNullOrBlank() }?.id
            ?: return
        clearSelection()
        removeCollection(id)
    }
}
