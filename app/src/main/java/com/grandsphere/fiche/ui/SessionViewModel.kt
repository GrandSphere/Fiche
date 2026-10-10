package com.grandsphere.fiche.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grandsphere.fiche.data.backup.BackupRepository
import com.grandsphere.fiche.data.backup.SharedTitle
import com.grandsphere.fiche.data.prefs.UserPreferencesRepository
import com.grandsphere.fiche.data.repository.AddSearchPrefillStore
import com.grandsphere.fiche.data.repository.LibraryRepository
import com.grandsphere.fiche.data.repository.ShareImportStore
import com.grandsphere.fiche.domain.model.MediaType
import com.grandsphere.fiche.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val backup: BackupRepository,
    private val library: LibraryRepository,
    val addPrefill: AddSearchPrefillStore,
    val shareImport: ShareImportStore
) : ViewModel() {
    val importMessage = MutableSharedFlow<String>()
    val mediaType = prefs.mediaType.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaType.SERIES)
    val enabledMediaTypes = prefs.enabledMediaTypes.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaType.entries
    )
    val themeMode = prefs.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    fun setMediaType(type: MediaType) = viewModelScope.launch { prefs.setMediaType(type) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { prefs.setTheme(mode) }

    fun confirmShareImport(selected: List<SharedTitle>) = viewModelScope.launch {
        if (selected.isEmpty()) {
            shareImport.clear()
            return@launch
        }
        runCatching { library.importSharedTitles(selected) }
            .onSuccess { result ->
                importMessage.emit("Added ${result.added}, skipped ${result.skipped}")
            }
            .onFailure { importMessage.emit(it.message ?: "Import failed") }
        shareImport.clear()
    }

    fun dismissShareImport() {
        shareImport.clear()
    }

    fun showMessage(text: String) = viewModelScope.launch {
        importMessage.emit(text)
    }

    private val _suggestionSeeds = MutableSharedFlow<Set<Long>>(extraBufferCapacity = 1)
    val suggestionSeeds = _suggestionSeeds

    fun offerSuggestionSeeds(ids: Set<Long>) {
        _suggestionSeeds.tryEmit(ids)
    }
}
