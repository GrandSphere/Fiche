package com.grandsphere.fiche.data.repository

import com.grandsphere.fiche.data.backup.SharedTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShareImportStore @Inject constructor() {
    private val _pending = MutableStateFlow<List<SharedTitle>?>(null)
    val pending = _pending.asStateFlow()

    fun offer(titles: List<SharedTitle>) {
        _pending.value = titles
    }

    fun clear() {
        _pending.value = null
    }
}
