package com.grandsphere.fiche.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.grandsphere.fiche.domain.model.AppearanceDefaults
import com.grandsphere.fiche.domain.model.MediaType
import com.grandsphere.fiche.domain.model.SortOption
import com.grandsphere.fiche.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("fiche_settings")

data class AppearancePrefs(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val backgroundArgb: Int = AppearanceDefaults.DARK_BACKGROUND,
    val groupArgb: Int = AppearanceDefaults.DARK_GROUP,
    val actionArgb: Int = AppearanceDefaults.DARK_ACTION,
    val alternateArgb: Int = AppearanceDefaults.DARK_ALTERNATE,
    val fontScale: Float = 1f,
    val compactMode: Boolean = false,
    val showCompletedMark: Boolean = true
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val themeKey = stringPreferencesKey("theme")
    private val mediaTypeKey = stringPreferencesKey("media_type")
    private val tmdbKey = stringPreferencesKey("tmdb_api_key")
    private val rawgKey = stringPreferencesKey("rawg_api_key")
    private val tasteDiveKey = stringPreferencesKey("tastedive_api_key")
    private val malKey = stringPreferencesKey("mal_api_key")
    private val sortKey = stringPreferencesKey("sort")
    private val groupKey = intPreferencesKey("group_argb")
    private val backgroundKey = intPreferencesKey("background_argb")
    private val widgetKey = intPreferencesKey("widget_argb")
    private val actionKey = intPreferencesKey("action_argb")
    private val alternateKey = intPreferencesKey("alternate_argb")
    private val fontKey = floatPreferencesKey("font_scale")
    private val compactKey = booleanPreferencesKey("compact_mode")
    private val showCompletedMarkKey = booleanPreferencesKey("show_completed_mark")
    private val enabledTypesKey = stringPreferencesKey("enabled_media_types")
    private val autoStoreSequelsKey = booleanPreferencesKey("auto_store_sequels")
    private val combineSequelsKey = booleanPreferencesKey("combine_sequels")
    private val useTmdbDiscoverTvKey = booleanPreferencesKey("use_tmdb_discover_tv")
    private val sequelMigrationDoneKey = booleanPreferencesKey("sequel_hidden_migration_done")
    private val trackedStatsMigrationDoneKey = booleanPreferencesKey("tracked_stats_migration_done")
    private val jikanMalSplitDoneKey = booleanPreferencesKey("jikan_mal_split_done")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[themeKey] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    val mediaType: Flow<MediaType> = context.dataStore.data.map { prefs ->
        runCatching { MediaType.valueOf(prefs[mediaTypeKey] ?: MediaType.SERIES.name) }
            .getOrDefault(MediaType.SERIES)
    }

    val tmdbApiKey: Flow<String> = context.dataStore.data.map { it[tmdbKey].orEmpty() }
    val rawgApiKey: Flow<String> = context.dataStore.data.map { it[rawgKey].orEmpty() }
    val tasteDiveApiKey: Flow<String> = context.dataStore.data.map { it[tasteDiveKey].orEmpty() }
    val malApiKey: Flow<String> = context.dataStore.data.map { it[malKey].orEmpty() }

    val sortOption: Flow<SortOption> = context.dataStore.data.map { prefs ->
        runCatching { SortOption.valueOf(prefs[sortKey] ?: SortOption.DATE_ADDED.name) }
            .getOrDefault(SortOption.DATE_ADDED)
    }

    val appearance: Flow<AppearancePrefs> = context.dataStore.data.map { prefs ->
        val mode = runCatching { ThemeMode.valueOf(prefs[themeKey] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
        val light = AppearanceDefaults.isLight(mode)
        AppearancePrefs(
            themeMode = mode,
            backgroundArgb = prefs[backgroundKey] ?: AppearanceDefaults.background(light),
            groupArgb = prefs[groupKey] ?: AppearanceDefaults.group(light),
            actionArgb = prefs[actionKey] ?: AppearanceDefaults.action(light),
            alternateArgb = prefs[alternateKey] ?: AppearanceDefaults.alternate(light),
            fontScale = prefs[fontKey] ?: 1f,
            compactMode = prefs[compactKey] ?: false,
            showCompletedMark = prefs[showCompletedMarkKey] ?: true
        )
    }

    val compactMode: Flow<Boolean> = context.dataStore.data.map { it[compactKey] ?: false }

    val enabledMediaTypes: Flow<List<MediaType>> = context.dataStore.data.map { prefs ->
        MediaType.parseEnabled(prefs[enabledTypesKey])
    }

    val autoStoreSequels: Flow<Boolean> = context.dataStore.data.map { it[autoStoreSequelsKey] ?: true }
    val combineSequels: Flow<Boolean> = context.dataStore.data.map { it[combineSequelsKey] ?: false }
    /** When true and a TMDB key is set, Find New Series/Anime uses TMDB Discover instead of scanning TvMaze. */
    val useTmdbDiscoverForTv: Flow<Boolean> =
        context.dataStore.data.map { it[useTmdbDiscoverTvKey] ?: false }

    suspend fun setAutoStoreSequels(value: Boolean) {
        context.dataStore.edit { it[autoStoreSequelsKey] = value }
    }

    suspend fun setCombineSequels(value: Boolean) {
        context.dataStore.edit { it[combineSequelsKey] = value }
    }

    suspend fun setUseTmdbDiscoverForTv(value: Boolean) {
        context.dataStore.edit { it[useTmdbDiscoverTvKey] = value }
    }

    suspend fun isSequelMigrationDone(): Boolean =
        context.dataStore.data.map { it[sequelMigrationDoneKey] ?: false }.first()

    suspend fun setSequelMigrationDone() {
        context.dataStore.edit { it[sequelMigrationDoneKey] = true }
    }

    suspend fun isTrackedStatsMigrationDone(): Boolean =
        context.dataStore.data.map { it[trackedStatsMigrationDoneKey] ?: false }.first()

    suspend fun setTrackedStatsMigrationDone() {
        context.dataStore.edit { it[trackedStatsMigrationDoneKey] = true }
    }

    suspend fun isJikanMalSplitDone(): Boolean =
        context.dataStore.data.map { it[jikanMalSplitDoneKey] ?: false }.first()

    suspend fun setJikanMalSplitDone() {
        context.dataStore.edit { it[jikanMalSplitDoneKey] = true }
    }

    suspend fun setMediaTypeEnabled(type: MediaType, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = MediaType.parseEnabled(prefs[enabledTypesKey]).toMutableList()
            if (enabled) {
                if (type !in current) current += type
            } else if (current.size > 1) {
                current.remove(type)
            }
            val ordered = MediaType.encodeEnabled(current)
            prefs[enabledTypesKey] = ordered
            val selected = runCatching {
                MediaType.valueOf(prefs[mediaTypeKey] ?: MediaType.SERIES.name)
            }.getOrDefault(MediaType.SERIES)
            val enabledList = MediaType.parseEnabled(ordered)
            if (selected !in enabledList) {
                prefs[mediaTypeKey] = enabledList.first().name
            }
        }
    }

    suspend fun setTheme(mode: ThemeMode) {
        val light = mode == ThemeMode.LIGHT
        context.dataStore.edit {
            it[themeKey] = mode.name
            it[backgroundKey] = AppearanceDefaults.background(light)
            it[groupKey] = AppearanceDefaults.group(light)
            it[actionKey] = AppearanceDefaults.action(light)
            it[alternateKey] = AppearanceDefaults.alternate(light)
        }
    }

    suspend fun setMediaType(type: MediaType) {
        context.dataStore.edit { it[mediaTypeKey] = type.name }
    }

    suspend fun setTmdbApiKey(key: String) {
        context.dataStore.edit { it[tmdbKey] = key.trim() }
    }

    suspend fun setSort(option: SortOption) {
        context.dataStore.edit { it[sortKey] = option.name }
    }

    suspend fun setBackgroundArgb(value: Int) {
        context.dataStore.edit { it[backgroundKey] = value }
    }

    suspend fun setGroupArgb(value: Int) {
        context.dataStore.edit { it[groupKey] = value }
    }

    suspend fun setWidgetArgb(value: Int) {
        context.dataStore.edit { it[widgetKey] = value }
    }

    suspend fun setActionArgb(value: Int) {
        context.dataStore.edit { it[actionKey] = value }
    }

    suspend fun setFontScale(value: Float) {
        context.dataStore.edit { it[fontKey] = value }
    }

    suspend fun setCompactMode(value: Boolean) {
        context.dataStore.edit { it[compactKey] = value }
    }

    suspend fun setShowCompletedMark(value: Boolean) {
        context.dataStore.edit { it[showCompletedMarkKey] = value }
    }

    suspend fun saveAppearance(
        appearance: AppearancePrefs,
        tmdbApiKey: String,
        rawgApiKey: String,
        tasteDiveApiKey: String
    ) {
        context.dataStore.edit {
            it[themeKey] = appearance.themeMode.name
            it[backgroundKey] = appearance.backgroundArgb
            it[groupKey] = appearance.groupArgb
            it[actionKey] = appearance.actionArgb
            it[alternateKey] = appearance.alternateArgb
            it[fontKey] = appearance.fontScale
            it[compactKey] = appearance.compactMode
            it[showCompletedMarkKey] = appearance.showCompletedMark
            it[tmdbKey] = tmdbApiKey.trim()
            it[rawgKey] = rawgApiKey.trim()
            it[tasteDiveKey] = tasteDiveApiKey.trim()
        }
    }

    suspend fun setStoredApiKey(provider: String, value: String) {
        context.dataStore.edit {
            when (provider) {
                "TMDB" -> it[tmdbKey] = value.trim()
                "RAWG" -> it[rawgKey] = value.trim()
                "TASTEDIVE" -> it[tasteDiveKey] = value.trim()
                "MAL" -> it[malKey] = value.trim()
            }
        }
    }

    suspend fun snapshot(): SettingsSnapshot {
        var theme = ThemeMode.SYSTEM.name
        var type = MediaType.SERIES.name
        var key = ""
        var rawg = ""
        var tasteDive = ""
        var mal = ""
        var sort = SortOption.DATE_ADDED.name
        var group = AppearanceDefaults.DARK_GROUP
        var background = AppearanceDefaults.DARK_BACKGROUND
        var action = AppearanceDefaults.DARK_ACTION
        var alternate = AppearanceDefaults.DARK_ALTERNATE
        var font = 1f
        var compact = false
        var showCompletedMark = true
        var enabledTypes = MediaType.encodeEnabled(MediaType.entries)
        context.dataStore.edit { current ->
            theme = current[themeKey] ?: ThemeMode.SYSTEM.name
            type = current[mediaTypeKey] ?: MediaType.SERIES.name
            key = current[tmdbKey].orEmpty()
            rawg = current[rawgKey].orEmpty()
            tasteDive = current[tasteDiveKey].orEmpty()
            mal = current[malKey].orEmpty()
            sort = current[sortKey] ?: SortOption.DATE_ADDED.name
            group = current[groupKey] ?: AppearanceDefaults.DARK_GROUP
            background = current[backgroundKey] ?: AppearanceDefaults.DARK_BACKGROUND
            action = current[actionKey] ?: AppearanceDefaults.DARK_ACTION
            alternate = current[alternateKey] ?: AppearanceDefaults.DARK_ALTERNATE
            font = current[fontKey] ?: 1f
            compact = current[compactKey] ?: false
            showCompletedMark = current[showCompletedMarkKey] ?: true
            enabledTypes = current[enabledTypesKey] ?: MediaType.encodeEnabled(MediaType.entries)
        }
        return SettingsSnapshot(
            theme = theme,
            mediaType = type,
            tmdbApiKey = key,
            rawgApiKey = rawg,
            tasteDiveApiKey = tasteDive,
            malApiKey = mal,
            sort = sort,
            groupArgb = group,
            backgroundArgb = background,
            actionArgb = action,
            alternateArgb = alternate,
            fontScale = font,
            compactMode = compact,
            showCompletedMark = showCompletedMark,
            enabledMediaTypes = enabledTypes
        )
    }

    suspend fun importSnapshot(snapshot: SettingsSnapshot) {
        context.dataStore.edit {
            snapshot.theme?.let { value -> it[themeKey] = value }
            snapshot.mediaType?.let { value -> it[mediaTypeKey] = value }
            snapshot.tmdbApiKey?.let { value -> it[tmdbKey] = value }
            snapshot.rawgApiKey?.let { value -> it[rawgKey] = value }
            snapshot.tasteDiveApiKey?.let { value -> it[tasteDiveKey] = value }
            snapshot.malApiKey?.let { value -> it[malKey] = value }
            snapshot.sort?.let { value -> it[sortKey] = value }
            snapshot.groupArgb?.let { value -> it[groupKey] = value }
            snapshot.backgroundArgb?.let { value -> it[backgroundKey] = value }
            snapshot.actionArgb?.let { value -> it[actionKey] = value }
            snapshot.alternateArgb?.let { value -> it[alternateKey] = value }
            snapshot.fontScale?.let { value -> it[fontKey] = value }
            snapshot.compactMode?.let { value -> it[compactKey] = value }
            snapshot.showCompletedMark?.let { value -> it[showCompletedMarkKey] = value }
            snapshot.enabledMediaTypes?.let { value -> it[enabledTypesKey] = value }
        }
    }
}

data class SettingsSnapshot(
    val theme: String? = null,
    val mediaType: String? = null,
    val tmdbApiKey: String? = null,
    val rawgApiKey: String? = null,
    val tasteDiveApiKey: String? = null,
    val malApiKey: String? = null,
    val sort: String? = null,
    val groupArgb: Int? = null,
    val backgroundArgb: Int? = null,
    val widgetArgb: Int? = null,
    val actionArgb: Int? = null,
    val alternateArgb: Int? = null,
    val fontScale: Float? = null,
    val compactMode: Boolean? = null,
    val showCompletedMark: Boolean? = null,
    val enabledMediaTypes: String? = null
)
