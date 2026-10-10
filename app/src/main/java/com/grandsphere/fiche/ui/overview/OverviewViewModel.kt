package com.grandsphere.fiche.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grandsphere.fiche.data.prefs.UserPreferencesRepository
import com.grandsphere.fiche.data.repository.LibraryRepository
import com.grandsphere.fiche.data.repository.OverviewStats
import com.grandsphere.fiche.domain.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val library: LibraryRepository,
    prefs: UserPreferencesRepository
) : ViewModel() {
    private val _tab = MutableStateFlow(MediaType.SERIES)
    val tab = _tab.asStateFlow()
    val enabledMediaTypes = prefs.enabledMediaTypes.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaType.entries
    )

    val stats = tab.flatMapLatest { library.observeOverview(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewStats())

    init {
        viewModelScope.launch {
            val current = prefs.mediaType.first()
            _tab.value = current
        }
        viewModelScope.launch {
            MediaType.entries.forEach { library.persistOverview(it) }
        }
        viewModelScope.launch {
            prefs.enabledMediaTypes.collect { enabled ->
                if (_tab.value !in enabled && enabled.isNotEmpty()) {
                    _tab.value = enabled.first()
                }
            }
        }
    }

    fun select(type: MediaType) {
        _tab.value = type
    }
}
