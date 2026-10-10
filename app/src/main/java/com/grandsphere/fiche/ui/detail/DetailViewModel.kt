package com.grandsphere.fiche.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grandsphere.fiche.data.backup.BackupRepository
import com.grandsphere.fiche.data.backup.formatSharedNames
import com.grandsphere.fiche.data.local.entity.DlcEntity
import com.grandsphere.fiche.data.local.entity.EpisodeEntity
import com.grandsphere.fiche.data.local.entity.RelatedTitleEntity
import com.grandsphere.fiche.data.local.entity.SeasonEntity
import com.grandsphere.fiche.data.local.entity.TitleEntity
import com.grandsphere.fiche.data.remote.RemoteTitle
import com.grandsphere.fiche.data.repository.CatalogRepository
import com.grandsphere.fiche.data.repository.LibraryRepository
import com.grandsphere.fiche.data.repository.MovieSequelEntry
import com.grandsphere.fiche.domain.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val library: LibraryRepository,
    private val catalog: CatalogRepository,
    private val backup: BackupRepository
) : ViewModel() {
    private val titleId: Long = checkNotNull(savedStateHandle["titleId"])

    val title = library.observeTitle(titleId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val seasons = library.observeSeasons(titleId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val episodes = library.observeEpisodes(titleId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val dlcs = library.observeDlcs(titleId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val related = library.observeRelatedForTitle(titleId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sequels = library.observeMovieSequels(titleId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val message = MutableSharedFlow<String>()
    val openUrl = MutableSharedFlow<String>()
    val pendingShare = MutableSharedFlow<Intent>()
    private val _catalogHits = MutableStateFlow<List<RemoteTitle>>(emptyList())
    val catalogHits = _catalogHits.asStateFlow()
    val catalogSearchLoading = MutableStateFlow(false)

    fun toggleEpisode(episode: EpisodeEntity) {
        viewModelScope.launch { library.setEpisodeWatched(episode.id, titleId, !episode.watched) }
    }

    fun openEpisode(episode: EpisodeEntity) {
        viewModelScope.launch {
            val item = title.value ?: return@launch
            val url = catalog.episodeWebUrl(
                catalogUrl = item.catalogUrl,
                mediaType = item.mediaType,
                remoteId = item.remoteId,
                season = episode.seasonNumber,
                episode = episode.episodeNumber
            )
            if (url.isNullOrBlank()) {
                message.emit("Couldn't open that episode")
            } else {
                openUrl.emit(url)
            }
        }
    }

    fun toggleSeason(season: SeasonEntity, watched: Boolean) {
        viewModelScope.launch { library.setSeasonWatched(titleId, season.seasonNumber, watched) }
    }

    fun toggleDlc(dlc: DlcEntity) {
        viewModelScope.launch {
            library.setDlcCompleted(dlc.id, titleId, !dlc.completed)
        }
    }

    fun toggleRelated(related: RelatedTitleEntity) {
        viewModelScope.launch {
            library.setRelatedWatched(related.id, !related.watched)
        }
    }

    fun toggleSequel(entry: MovieSequelEntry) {
        viewModelScope.launch {
            library.setSequelWatched(entry.link.relatedRemoteId, !entry.isWatched)
        }
    }

    fun openSequel(entry: MovieSequelEntry, onNavigate: (Long) -> Unit) {
        viewModelScope.launch {
            val id = library.openSequelTitle(entry.link)
            onNavigate(id)
        }
    }

    fun refreshTitle() {
        viewModelScope.launch {
            runCatching { library.refreshTitle(titleId) }
                .onSuccess { ok ->
                    message.emit(if (ok) "Refreshed" else "Could not refresh")
                }
                .onFailure { message.emit(it.message ?: "Could not refresh") }
        }
    }

    fun searchCurrentCatalog(query: String) {
        viewModelScope.launch {
            val item = title.value ?: return@launch
            val type = runCatching { MediaType.valueOf(item.mediaType) }.getOrNull() ?: return@launch
            catalogSearchLoading.value = true
            _catalogHits.value = runCatching { catalog.search(type, query.trim()) }
                .getOrElse { emptyList() }
            catalogSearchLoading.value = false
        }
    }

    fun retargetTo(remote: RemoteTitle) {
        viewModelScope.launch {
            library.retargetTitle(titleId, remote)
                .onSuccess { message.emit("Updated listing") }
                .onFailure { message.emit(it.message ?: "Could not update listing") }
            _catalogHits.value = emptyList()
        }
    }

    fun clearCatalogHits() {
        _catalogHits.value = emptyList()
    }

    fun toggleWatched(title: TitleEntity) {
        viewModelScope.launch { library.setUnitWatched(titleId, !title.completed) }
    }

    fun setBookPage(page: Int) {
        viewModelScope.launch { library.setBookPage(titleId, page) }
    }

    fun moveToInterests() {
        viewModelScope.launch { library.moveToInterests(titleId) }
    }

    fun setRating(value: Float?) {
        viewModelScope.launch { library.setRating(titleId, value) }
    }

    fun banTitle() {
        viewModelScope.launch { library.banLibraryTitle(titleId) }
    }

    fun dislikeTitle() {
        viewModelScope.launch { library.dislikeLibraryTitle(titleId) }
    }

    fun setMoreOfThis(value: Boolean) {
        viewModelScope.launch { library.setMoreOfThis(titleId, value) }
    }

    fun setRecommended(value: Boolean) {
        viewModelScope.launch { library.setRecommended(titleId, value) }
    }

    fun setPoster(uri: Uri) {
        viewModelScope.launch { library.setCustomPoster(titleId, uri) }
    }

    fun clearPoster() {
        viewModelScope.launch { library.clearPoster(titleId) }
    }

    fun resetPoster() {
        viewModelScope.launch { library.resetPoster(titleId) }
    }

    fun setOverview(text: String) {
        viewModelScope.launch { library.setOverview(titleId, text) }
    }

    fun resetOverview() {
        viewModelScope.launch { library.resetOverview(titleId) }
    }

    fun setDisplayTitle(value: String?) {
        viewModelScope.launch { library.setDisplayTitle(titleId, value) }
    }

    fun setSeasonNotInterested(season: SeasonEntity, notInterested: Boolean) {
        viewModelScope.launch {
            library.setSeasonNotInterested(season.id, titleId, notInterested)
        }
    }

    fun removeCollection() {
        viewModelScope.launch {
            library.removeMovieCollection(titleId)
            message.emit("Removed collection")
        }
    }

    fun delete() {
        viewModelScope.launch { library.deleteTitle(titleId) }
    }

    fun setHidden(hidden: Boolean) {
        viewModelScope.launch { library.setHidden(titleId, hidden) }
    }

    fun setCountSequelsInCompletion(enabled: Boolean) {
        viewModelScope.launch { library.setCountSequelsInCompletion(titleId, enabled) }
    }

    fun setSequelNotInterested(entry: MovieSequelEntry, notInterested: Boolean) {
        viewModelScope.launch {
            library.setSequelNotInterested(entry.link.relatedRemoteId, notInterested)
        }
    }

    fun shareName() {
        val current = title.value ?: return
        viewModelScope.launch {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, formatSharedNames(listOf(current)))
            }
            pendingShare.emit(intent)
        }
    }

    fun shareRecommendation() {
        val current = title.value ?: return
        viewModelScope.launch {
            runCatching { backup.shareFile(backup.exportRecommendation(current)) }
                .onSuccess { pendingShare.emit(it) }
                .onFailure { message.emit(it.message ?: "Share failed") }
        }
    }

    fun shareCollection() {
        viewModelScope.launch {
            runCatching { backup.shareFile(backup.exportSharedCollection(titleId)) }
                .onSuccess { pendingShare.emit(it) }
                .onFailure { message.emit(it.message ?: "Share failed") }
        }
    }
}
