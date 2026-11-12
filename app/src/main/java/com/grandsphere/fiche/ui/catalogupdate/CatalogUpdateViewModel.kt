package com.grandsphere.fiche.ui.catalogupdate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grandsphere.fiche.data.repository.CatalogUpdateHit
import com.grandsphere.fiche.data.repository.LibraryRepository
import com.grandsphere.fiche.data.repository.RefreshProgress
import com.grandsphere.fiche.domain.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class CatalogUpdatePhase { IDLE, CHECKING, RESULTS, APPLYING }

data class CatalogUpdateRow(
    val hit: CatalogUpdateHit,
    val selected: Boolean = true
)

@HiltViewModel
class CatalogUpdateViewModel @Inject constructor(
    private val library: LibraryRepository
) : ViewModel() {
    val phase = MutableStateFlow(CatalogUpdatePhase.IDLE)
    val progress = MutableStateFlow<RefreshProgress?>(null)
    val rows = MutableStateFlow<List<CatalogUpdateRow>>(emptyList())
    val mediaType = MutableStateFlow(MediaType.SERIES)
    val message = MutableSharedFlow<String>()

    fun start(type: MediaType) {
        if (phase.value == CatalogUpdatePhase.CHECKING ||
            phase.value == CatalogUpdatePhase.APPLYING
        ) {
            return
        }
        viewModelScope.launch {
            mediaType.value = type
            phase.value = CatalogUpdatePhase.CHECKING
            progress.value = RefreshProgress(0, 1, "Checking")
            val found = runCatching {
                withContext(Dispatchers.IO) {
                    library.probeCatalogUpdates(type) { progress.value = it }
                }
            }.getOrElse { err ->
                message.emit(err.message ?: "Check failed")
                phase.value = CatalogUpdatePhase.IDLE
                progress.value = null
                return@launch
            }
            rows.value = found.map { CatalogUpdateRow(it) }
            progress.value = null
            phase.value = CatalogUpdatePhase.RESULTS
        }
    }

    fun toggle(titleId: Long) {
        rows.value = rows.value.map { row ->
            if (row.hit.titleId == titleId) row.copy(selected = !row.selected) else row
        }
    }

    fun apply() {
        viewModelScope.launch {
            val selected = rows.value.filter { it.selected }.map { it.hit }
            if (selected.isEmpty()) {
                dismiss()
                return@launch
            }
            phase.value = CatalogUpdatePhase.APPLYING
            runCatching {
                withContext(Dispatchers.IO) {
                    library.applyCatalogUpdates(mediaType.value, selected) { progress.value = it }
                }
            }.onSuccess { count ->
                message.emit(
                    if (count == 0) "Nothing updated" else "Updated $count titles"
                )
            }.onFailure { err ->
                message.emit(err.message ?: "Update failed")
            }
            phase.value = CatalogUpdatePhase.IDLE
            rows.value = emptyList()
            progress.value = null
        }
    }

    fun dismiss() {
        if (phase.value == CatalogUpdatePhase.CHECKING ||
            phase.value == CatalogUpdatePhase.APPLYING
        ) {
            return
        }
        phase.value = CatalogUpdatePhase.IDLE
        rows.value = emptyList()
        progress.value = null
    }
}
