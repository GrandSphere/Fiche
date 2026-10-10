package com.grandsphere.fiche.data.prefs

import com.grandsphere.fiche.data.local.dao.ApiKeyDao
import com.grandsphere.fiche.data.local.entity.ApiKeyEntity
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API keys live in Room. DataStore is used only as a one-time migration source
 * and kept in sync on save so older code paths still work during transition.
 */
@Singleton
class ApiKeyRepository @Inject constructor(
    private val apiKeys: ApiKeyDao,
    private val prefs: UserPreferencesRepository
) {
    @Volatile
    private var migrated = false

    suspend fun ensureMigrated() {
        if (migrated) return
        suspend fun copyIfMissing(provider: String, fromPrefs: String) {
            val existing = apiKeys.getKey(provider).orEmpty()
            if (existing.isBlank() && fromPrefs.isNotBlank()) {
                apiKeys.upsert(ApiKeyEntity(provider, fromPrefs.trim()))
            }
        }
        copyIfMissing("TMDB", prefs.tmdbApiKey.first())
        copyIfMissing("RAWG", prefs.rawgApiKey.first())
        copyIfMissing("TASTEDIVE", prefs.tasteDiveApiKey.first())
        copyIfMissing("MAL", prefs.malApiKey.first())
        migrated = true
    }

    suspend fun get(provider: String): String {
        ensureMigrated()
        return apiKeys.getKey(provider).orEmpty().trim()
    }

    suspend fun require(provider: String, label: String = provider): String {
        val key = get(provider)
        if (key.isBlank()) error("$label API key missing — set it in Settings → Catalog")
        return key
    }

    suspend fun set(provider: String, value: String) {
        apiKeys.upsert(ApiKeyEntity(provider, value.trim()))
        prefs.setStoredApiKey(provider, value)
    }
}
