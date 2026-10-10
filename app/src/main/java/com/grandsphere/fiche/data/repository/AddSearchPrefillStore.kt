package com.grandsphere.fiche.data.repository

import com.grandsphere.fiche.data.remote.RemoteTitle
import com.grandsphere.fiche.domain.model.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AddSearchPrefill(
    val query: String,
    val type: MediaType,
    val seed: RemoteTitle? = null,
    val fromShare: Boolean = true,
    val resolving: Boolean = false
)

@Singleton
class AddSearchPrefillStore @Inject constructor() {
    private val _pending = MutableStateFlow<AddSearchPrefill?>(null)
    val pending = _pending.asStateFlow()

    fun offer(value: AddSearchPrefill) {
        _pending.value = value
    }

    fun clear() {
        _pending.value = null
    }
}
