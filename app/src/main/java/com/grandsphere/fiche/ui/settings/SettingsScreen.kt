package com.grandsphere.fiche.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.grandsphere.fiche.data.backup.BackupRepository
import com.grandsphere.fiche.data.catalog.CatalogProviderId
import com.grandsphere.fiche.data.local.entity.LibraryPrefsEntity
import com.grandsphere.fiche.data.prefs.ApiKeyRepository
import com.grandsphere.fiche.data.prefs.AppearancePrefs
import com.grandsphere.fiche.data.prefs.UserPreferencesRepository
import com.grandsphere.fiche.data.repository.LibraryRepository
import com.grandsphere.fiche.data.repository.RefreshProgress
import com.grandsphere.fiche.data.work.ImportBackupWorker
import com.grandsphere.fiche.data.work.RecalcProgressWorker
import com.grandsphere.fiche.data.work.ReloadCatalogWorker
import com.grandsphere.fiche.domain.model.AppearanceDefaults
import com.grandsphere.fiche.domain.model.MediaType
import com.grandsphere.fiche.domain.model.ThemeMode
import com.grandsphere.fiche.ui.chrome.CollapsibleSection
import com.grandsphere.fiche.ui.components.FicheSnackbarHost
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class StorageJob { NONE, IMAGES, RELOAD_ALL, RELOAD_INCOMPLETE }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: UserPreferencesRepository,
    private val backup: BackupRepository,
    private val library: LibraryRepository,
    private val apiKeys: ApiKeyRepository
) : ViewModel() {
    val appearance = prefs.appearance
    private val _tmdbKey = MutableStateFlow<String?>(null)
    private val _rawgKey = MutableStateFlow<String?>(null)
    private val _tasteDiveKey = MutableStateFlow<String?>(null)
    private val _malKey = MutableStateFlow<String?>(null)
    val tmdbKey: StateFlow<String?> = _tmdbKey.asStateFlow()
    val rawgKey: StateFlow<String?> = _rawgKey.asStateFlow()
    val tasteDiveKey: StateFlow<String?> = _tasteDiveKey.asStateFlow()
    val malKey: StateFlow<String?> = _malKey.asStateFlow()
    val enabledTypes = prefs.enabledMediaTypes
    val autoStoreSequels = prefs.autoStoreSequels
    val combineSequels = prefs.combineSequels
    val libraryPrefs = library.observeLibraryPrefs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryPrefsEntity())
    val message = MutableSharedFlow<String>()
    val pendingShare = MutableSharedFlow<Intent>()
    val refreshProgress = MutableStateFlow<RefreshProgress?>(null)
    val storageJob = MutableStateFlow(StorageJob.NONE)
    val importing = MutableStateFlow(false)
    val importProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val recalculating = MutableStateFlow(false)
    val recalcProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val updatingCatalog = MutableStateFlow(false)
    val catalogProgress = MutableStateFlow<Pair<Int, Int>?>(null)

    init {
        viewModelScope.launch {
            apiKeys.ensureMigrated()
            _tmdbKey.value = apiKeys.get("TMDB")
            _rawgKey.value = apiKeys.get("RAWG")
            _tasteDiveKey.value = apiKeys.get("TASTEDIVE")
            _malKey.value = apiKeys.get("MAL")
        }
        viewModelScope.launch {
            var lastState: WorkInfo.State? = null
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(ImportBackupWorker.UNIQUE_NAME)
                .collect { infos ->
                    val info = infos.firstOrNull()
                    val state = info?.state
                    importing.value = state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED
                    val current = info?.progress?.getInt(ImportBackupWorker.KEY_CURRENT, 0) ?: 0
                    val total = info?.progress?.getInt(ImportBackupWorker.KEY_TOTAL, 0) ?: 0
                    importProgress.value = if (
                        (state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED) && total > 0
                    ) {
                        current to total
                    } else {
                        null
                    }
                    val messageText = info?.outputData?.getString(ImportBackupWorker.KEY_MESSAGE)
                    if (lastState != null && lastState != state && !messageText.isNullOrBlank()) {
                        when (state) {
                            WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED -> message.emit(messageText)
                            else -> { }
                        }
                    }
                    lastState = state
                }
        }
        viewModelScope.launch {
            var lastState: WorkInfo.State? = null
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(RecalcProgressWorker.UNIQUE_NAME)
                .collect { infos ->
                    val info = infos.firstOrNull()
                    val state = info?.state
                    recalculating.value = state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED
                    val current = info?.progress?.getInt(RecalcProgressWorker.KEY_CURRENT, 0) ?: 0
                    val total = info?.progress?.getInt(RecalcProgressWorker.KEY_TOTAL, 0) ?: 0
                    recalcProgress.value = if (
                        (state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED) && total > 0
                    ) {
                        current to total
                    } else {
                        null
                    }
                    val messageText = info?.outputData?.getString(RecalcProgressWorker.KEY_MESSAGE)
                    if (lastState != null && lastState != state && !messageText.isNullOrBlank()) {
                        when (state) {
                            WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED -> message.emit(messageText)
                            else -> { }
                        }
                    }
                    lastState = state
                }
        }
        viewModelScope.launch {
            var lastState: WorkInfo.State? = null
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(ReloadCatalogWorker.UNIQUE_NAME)
                .collect { infos ->
                    val info = infos.firstOrNull()
                    val state = info?.state
                    updatingCatalog.value = state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED
                    val current = info?.progress?.getInt(ReloadCatalogWorker.KEY_CURRENT, 0) ?: 0
                    val total = info?.progress?.getInt(ReloadCatalogWorker.KEY_TOTAL, 0) ?: 0
                    catalogProgress.value = if (
                        (state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED) && total > 0
                    ) {
                        current to total
                    } else {
                        null
                    }
                    val messageText = info?.outputData?.getString(ReloadCatalogWorker.KEY_MESSAGE)
                    if (lastState != null && lastState != state && !messageText.isNullOrBlank()) {
                        when (state) {
                            WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED -> message.emit(messageText)
                            else -> { }
                        }
                    }
                    lastState = state
                }
        }
    }

    fun save(
        appearance: AppearancePrefs,
        tmdbKey: String,
        rawgKey: String,
        tasteDiveKey: String,
        malKey: String
    ) =
        viewModelScope.launch {
            apiKeys.set("TMDB", tmdbKey)
            apiKeys.set("RAWG", rawgKey)
            apiKeys.set("TASTEDIVE", tasteDiveKey)
            apiKeys.set("MAL", malKey)
            prefs.saveAppearance(appearance, tmdbKey, rawgKey, tasteDiveKey)
            _tmdbKey.value = tmdbKey.trim()
            _rawgKey.value = rawgKey.trim()
            _tasteDiveKey.value = tasteDiveKey.trim()
            _malKey.value = malKey.trim()
            message.emit("Saved")
        }

    fun setCategoryEnabled(type: MediaType, enabled: Boolean) = viewModelScope.launch {
        prefs.setMediaTypeEnabled(type, enabled)
    }

    fun setShowCategoryTabs(enabled: Boolean) = viewModelScope.launch {
        library.updateLibraryPrefs { it.copy(showCategoryTabs = enabled) }
    }

    fun setCatalogApi(type: MediaType, provider: String) = viewModelScope.launch {
        library.updateLibraryPrefs { prefs ->
            when (type) {
                MediaType.SERIES -> prefs.copy(seriesApi = provider)
                MediaType.MOVIE -> prefs.copy(moviesApi = provider)
                MediaType.ANIME -> prefs.copy(animeApi = provider)
                MediaType.GAME -> prefs.copy(gamesApi = provider)
                MediaType.BOOK -> prefs.copy(booksApi = provider)
            }
        }
    }

    fun setStoreImagesLocally(enabled: Boolean) = viewModelScope.launch {
        library.setStoreImagesLocally(enabled)
        message.emit(if (enabled) "Storing images locally" else "Using online images")
    }

    fun clearImages() = viewModelScope.launch {
        library.clearDownloadedPosters()
        message.emit("Cleared downloaded images")
    }

    fun redownloadImages() = viewModelScope.launch {
        if (storageBusy()) return@launch
        storageJob.value = StorageJob.IMAGES
        refreshProgress.value = null
        runCatching {
            library.redownloadMissingPosters { refreshProgress.value = it }
            message.emit("Redownloaded missing images")
        }.onFailure { message.emit(it.message ?: "Could not redownload") }
        storageJob.value = StorageJob.NONE
        refreshProgress.value = null
    }

    fun reloadAll() = viewModelScope.launch {
        if (storageBusy()) return@launch
        storageJob.value = StorageJob.RELOAD_ALL
        refreshProgress.value = null
        MediaType.entries.forEach { type ->
            runCatching {
                library.refreshLibrary(type) { refreshProgress.value = it }
            }
        }
        storageJob.value = StorageJob.NONE
        refreshProgress.value = null
        message.emit("Reloaded all titles")
    }

    fun reloadIncomplete() = viewModelScope.launch {
        if (storageBusy()) return@launch
        storageJob.value = StorageJob.RELOAD_INCOMPLETE
        refreshProgress.value = null
        val n = runCatching {
            library.reloadIncomplete { refreshProgress.value = it }
        }.getOrDefault(0)
        storageJob.value = StorageJob.NONE
        refreshProgress.value = null
        message.emit(if (n == 0) "Nothing incomplete" else "Reloaded $n incomplete")
    }

    fun reloadFromSelectedCatalogues() {
        if (storageBusy() || importing.value || recalculating.value) return
        val request = OneTimeWorkRequestBuilder<ReloadCatalogWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ReloadCatalogWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelCatalogUpdate() {
        WorkManager.getInstance(context).cancelUniqueWork(ReloadCatalogWorker.UNIQUE_NAME)
    }

    private fun storageBusy(): Boolean =
        storageJob.value != StorageJob.NONE || updatingCatalog.value

    fun recalculateProgress() {
        if (recalculating.value || importing.value) return
        val request = OneTimeWorkRequestBuilder<RecalcProgressWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            RecalcProgressWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun setAutoStoreSequels(enabled: Boolean) = viewModelScope.launch {
        prefs.setAutoStoreSequels(enabled)
    }

    fun setCombineSequels(enabled: Boolean) = viewModelScope.launch {
        prefs.setCombineSequels(enabled)
    }

    fun exportFull() = share { backup.exportFull() }
    fun exportBan() = share { backup.exportBanlist() }
    fun exportWatchlist() = share { backup.exportWatchlist() }
    fun exportTracked() = share { backup.exportTracked() }
    fun exportSettings() = share { backup.exportSettings() }
    fun exportRecommendations() = share {
        backup.exportRecommendations(prefs.mediaType.first())
    }

    fun importUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val dest = File(context.cacheDir, "import-pending.json")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Could not read file")
                val request = OneTimeWorkRequestBuilder<ImportBackupWorker>()
                    .setInputData(workDataOf(ImportBackupWorker.KEY_PATH to dest.absolutePath))
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    ImportBackupWorker.UNIQUE_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            }.onFailure { message.emit(it.message ?: "Import failed") }
        }
    }

    private fun share(block: suspend () -> java.io.File) {
        viewModelScope.launch {
            runCatching { backup.shareFile(block()) }
                .onSuccess { pendingShare.emit(it) }
                .onFailure { message.emit(it.message ?: "Export failed") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenDrawer: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val storedAppearance by viewModel.appearance.collectAsStateWithLifecycle(null)
    val storedKey by viewModel.tmdbKey.collectAsStateWithLifecycle(null)
    val storedRawg by viewModel.rawgKey.collectAsStateWithLifecycle(null)
    val storedTasteDive by viewModel.tasteDiveKey.collectAsStateWithLifecycle(null)
    val storedMal by viewModel.malKey.collectAsStateWithLifecycle(null)
    val enabledTypes by viewModel.enabledTypes.collectAsStateWithLifecycle(MediaType.entries)
    val autoStoreSequels by viewModel.autoStoreSequels.collectAsStateWithLifecycle(true)
    val combineSequels by viewModel.combineSequels.collectAsStateWithLifecycle(false)
    val libraryPrefs by viewModel.libraryPrefs.collectAsStateWithLifecycle()
    val refreshProgress by viewModel.refreshProgress.collectAsStateWithLifecycle()
    val storageJob by viewModel.storageJob.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val recalculating by viewModel.recalculating.collectAsStateWithLifecycle()
    val recalcProgress by viewModel.recalcProgress.collectAsStateWithLifecycle()
    val updatingCatalog by viewModel.updatingCatalog.collectAsStateWithLifecycle()
    val catalogProgress by viewModel.catalogProgress.collectAsStateWithLifecycle()
    val storageBusy = storageJob != StorageJob.NONE || updatingCatalog
    var draft by remember { mutableStateOf<AppearancePrefs?>(null) }
    var key by remember { mutableStateOf<String?>(null) }
    var rawg by remember { mutableStateOf<String?>(null) }
    var tasteDive by remember { mutableStateOf<String?>(null) }
    var mal by remember { mutableStateOf<String?>(null) }
    val shown = draft
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importUri(it) }
    }
    var picker by remember { mutableStateOf<String?>(null) }
    var confirmCatalogUpdate by remember { mutableStateOf(false) }

    LaunchedEffect(storedAppearance) {
        if (draft == null && storedAppearance != null) draft = storedAppearance
    }
    LaunchedEffect(storedKey) {
        if (key == null && storedKey != null) key = storedKey
    }
    LaunchedEffect(storedRawg) {
        if (rawg == null && storedRawg != null) rawg = storedRawg
    }
    LaunchedEffect(storedTasteDive) {
        if (tasteDive == null && storedTasteDive != null) tasteDive = storedTasteDive
    }
    LaunchedEffect(storedMal) {
        if (mal == null && storedMal != null) mal = storedMal
    }

    LaunchedEffect(Unit) { viewModel.message.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) {
        viewModel.pendingShare.collect { intent ->
            context.startActivity(Intent.createChooser(intent, "Share Fiche file"))
        }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, "Menu") }
                }
            )
        },
        snackbarHost = {}
    ) { padding ->
        val current = shown
        val currentKey = key.orEmpty()
        val currentRawg = rawg.orEmpty()
        val currentTasteDive = tasteDive.orEmpty()
        val currentMal = mal.orEmpty()
        if (current == null) {
            Box(Modifier.fillMaxWidth().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Loading settings…")
            }
            return@Scaffold
        }
        val light = AppearanceDefaults.isLight(current.themeMode)
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CollapsibleSection(
                title = "Appearance",
                hint = "Theme, colours, font size, and compact library cards.",
                startExpanded = false
            ) {
                Text("Theme")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = current.themeMode == mode,
                            onClick = {
                                if (mode == current.themeMode) return@FilterChip
                                val nextLight = AppearanceDefaults.isLight(mode)
                                draft = current.copy(
                                    themeMode = mode,
                                    backgroundArgb = AppearanceDefaults.background(nextLight),
                                    groupArgb = AppearanceDefaults.group(nextLight),
                                    actionArgb = AppearanceDefaults.action(nextLight),
                                    alternateArgb = AppearanceDefaults.alternate(nextLight)
                                )
                            },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.titlecase() }) }
                        )
                    }
                }
                ColorRow("Background", current.backgroundArgb, { picker = "background" }) {
                    draft = current.copy(backgroundArgb = AppearanceDefaults.background(light))
                }
                ColorRow("Group colour", current.groupArgb, { picker = "group" }) {
                    draft = current.copy(groupArgb = AppearanceDefaults.group(light))
                }
                ColorRow("Action colour", current.actionArgb, { picker = "action" }) {
                    draft = current.copy(actionArgb = AppearanceDefaults.action(light))
                }
                ColorRow("Alternate colour", current.alternateArgb, { picker = "alternate" }) {
                    draft = current.copy(alternateArgb = AppearanceDefaults.alternate(light))
                }
                HorizontalDivider()
                Text("Font size")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Smaller" to 0.85f, "Default" to 1f, "Larger" to 1.15f).forEach { (label, scale) ->
                        FilterChip(
                            selected = kotlin.math.abs(current.fontScale - scale) < 0.01f,
                            onClick = { draft = current.copy(fontScale = scale) },
                            label = { Text(label) }
                        )
                    }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Compact library", modifier = Modifier.weight(1f))
                    Switch(
                        checked = current.compactMode,
                        onCheckedChange = { draft = current.copy(compactMode = it) }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Show completed mark", modifier = Modifier.weight(1f))
                    Switch(
                        checked = current.showCompletedMark,
                        onCheckedChange = { draft = current.copy(showCompletedMark = it) }
                    )
                }
            }
            CollapsibleSection(
                title = "Categories",
                hint = "Turn off a category to hide it from the sidebar, overview, and library. At least one must stay on."
            ) {
                MediaType.entries.forEach { type ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = type in enabledTypes,
                            onCheckedChange = { checked -> viewModel.setCategoryEnabled(type, checked) }
                        )
                        Text(type.label)
                    }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Show category tabs on homescreen", modifier = Modifier.weight(1f))
                    Switch(
                        checked = libraryPrefs.showCategoryTabs,
                        onCheckedChange = viewModel::setShowCategoryTabs
                    )
                }
            }
            CollapsibleSection(
                title = "Storage",
                hint = "Local posters use disk space. Online mode loads poster URLs with Coil instead."
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Store images locally", modifier = Modifier.weight(1f))
                    Switch(
                        checked = libraryPrefs.storeImagesLocally,
                        onCheckedChange = viewModel::setStoreImagesLocally
                    )
                }
                TextButton(onClick = viewModel::clearImages, enabled = !storageBusy) {
                    Text("Clear downloaded images")
                }
                TextButton(onClick = viewModel::redownloadImages, enabled = !storageBusy) {
                    Text(
                        run {
                            val progress = refreshProgress
                            when {
                                storageJob != StorageJob.IMAGES -> "Redownload images"
                                progress != null -> "Reloading... (${progress.current}/${progress.total})"
                                else -> "Reloading..."
                            }
                        }
                    )
                }
                HorizontalDivider()
                TextButton(onClick = viewModel::reloadAll, enabled = !storageBusy) {
                    Text(
                        run {
                            val progress = refreshProgress
                            when {
                                storageJob != StorageJob.RELOAD_ALL -> "Reload all information"
                                progress != null -> "Reloading... (${progress.current}/${progress.total})"
                                else -> "Reloading..."
                            }
                        }
                    )
                }
                TextButton(onClick = viewModel::reloadIncomplete, enabled = !storageBusy) {
                    Text(
                        run {
                            val progress = refreshProgress
                            when {
                                storageJob != StorageJob.RELOAD_INCOMPLETE -> "Reload incomplete information"
                                progress != null -> "Reloading... (${progress.current}/${progress.total})"
                                else -> "Reloading..."
                            }
                        }
                    )
                }
                TextButton(
                    onClick = {
                        if (updatingCatalog) {
                            viewModel.cancelCatalogUpdate()
                        } else {
                            confirmCatalogUpdate = true
                        }
                    },
                    enabled = updatingCatalog || !storageBusy
                ) {
                    Text(
                        run {
                            val progress = catalogProgress
                            when {
                                !updatingCatalog -> "Update from selected catalogues"
                                progress != null -> "Updating catalogue... (${progress.first}/${progress.second})"
                                else -> "Updating catalogue..."
                            }
                        }
                    )
                }
            }
            CollapsibleSection(
                title = "Movies",
                hint = "Franchise linking uses TMDB collections. Combine Sequels shows one row per franchise on the home list."
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Auto-store movie sequels", modifier = Modifier.weight(1f))
                    Switch(
                        checked = autoStoreSequels,
                        onCheckedChange = viewModel::setAutoStoreSequels
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Combine sequels", modifier = Modifier.weight(1f))
                    Switch(
                        checked = combineSequels,
                        onCheckedChange = viewModel::setCombineSequels
                    )
                }
            }
            CollapsibleSection(
                title = "Catalog",
                hint = "Pick an API per category. Keys are stored in the database and synced to preferences for providers that need them."
            ) {
                CatalogApiRow(
                    label = "Series API",
                    current = libraryPrefs.seriesApi,
                    options = listOf(CatalogProviderId.TVMAZE.name, CatalogProviderId.TMDB.name),
                    onSelect = { viewModel.setCatalogApi(MediaType.SERIES, it) }
                )
                if (libraryPrefs.seriesApi.equals(CatalogProviderId.TMDB.name, ignoreCase = true)) {
                    ApiKeyField(
                        value = currentKey,
                        onValueChange = { key = it },
                        label = "TMDB API key",
                        helpTitle = "TMDB API key",
                        helpText = "Create a free TMDB account, open Settings → API, and copy the API Key (v3). Do not use the Read Access Token.",
                        helpUrl = "https://www.themoviedb.org/settings/api"
                    )
                }
                HorizontalDivider()
                CatalogApiRow(
                    label = "Movies API",
                    current = libraryPrefs.moviesApi,
                    options = listOf(CatalogProviderId.TMDB.name),
                    onSelect = { viewModel.setCatalogApi(MediaType.MOVIE, it) }
                )
                if (libraryPrefs.moviesApi.equals(CatalogProviderId.TMDB.name, ignoreCase = true)) {
                    ApiKeyField(
                        value = currentKey,
                        onValueChange = { key = it },
                        label = "TMDB API key",
                        helpTitle = "TMDB API key",
                        helpText = "Create a free TMDB account, open Settings → API, and copy the API Key (v3). Do not use the Read Access Token.",
                        helpUrl = "https://www.themoviedb.org/settings/api"
                    )
                }
                HorizontalDivider()
                CatalogApiRow(
                    label = "Anime API",
                    current = libraryPrefs.animeApi,
                    options = listOf(
                        CatalogProviderId.TVMAZE.name,
                        CatalogProviderId.TMDB.name,
                        CatalogProviderId.JIKAN.name,
                        CatalogProviderId.MAL.name
                    ),
                    onSelect = { viewModel.setCatalogApi(MediaType.ANIME, it) }
                )
                if (libraryPrefs.animeApi.equals(CatalogProviderId.TMDB.name, ignoreCase = true)) {
                    ApiKeyField(
                        value = currentKey,
                        onValueChange = { key = it },
                        label = "TMDB API key",
                        helpTitle = "TMDB API key",
                        helpText = "Create a free TMDB account, open Settings → API, and copy the API Key (v3). Do not use the Read Access Token.",
                        helpUrl = "https://www.themoviedb.org/settings/api"
                    )
                }
                if (libraryPrefs.animeApi.equals(CatalogProviderId.MAL.name, ignoreCase = true)) {
                    ApiKeyField(
                        value = currentMal,
                        onValueChange = { mal = it },
                        label = "MAL Client ID",
                        helpTitle = "MAL Client ID",
                        helpText = "Create a MAL API client and paste only the Client ID (not the Client Secret). Fiche only reads public anime data. It never signs in or updates your MyAnimeList.",
                        helpUrl = "https://myanimelist.net/apiconfig/v2/api_info"
                    )
                    Text(
                        "Required for the MAL catalog. Client ID only.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                HorizontalDivider()
                CatalogApiRow(
                    label = "Games API",
                    current = libraryPrefs.gamesApi,
                    options = listOf(CatalogProviderId.RAWG.name),
                    onSelect = { viewModel.setCatalogApi(MediaType.GAME, it) }
                )
                if (libraryPrefs.gamesApi.equals(CatalogProviderId.RAWG.name, ignoreCase = true)) {
                    ApiKeyField(
                        value = currentRawg,
                        onValueChange = { rawg = it },
                        label = "RAWG API key",
                        helpTitle = "RAWG API key",
                        helpText = "Create a free RAWG account, open the API docs page, and copy your API key.",
                        helpUrl = "https://rawg.io/apidocs"
                    )
                }
                HorizontalDivider()
                CatalogApiRow(
                    label = "Books API",
                    current = libraryPrefs.booksApi,
                    options = listOf(CatalogProviderId.OPEN_LIBRARY.name),
                    onSelect = { viewModel.setCatalogApi(MediaType.BOOK, it) }
                )
                HorizontalDivider()
                ApiKeyField(
                    value = currentTasteDive,
                    onValueChange = { tasteDive = it },
                    label = "TasteDive API key",
                    helpTitle = "TasteDive API key",
                    helpText = "Request a free TasteDive API key, then paste it here for Suggestions.",
                    helpUrl = "https://tastedive.com/read/api"
                )
            }
            CollapsibleSection(
                title = "Backup",
                hint = "Export or import your library, interests, ban list, and settings. Images are not in the file; they download again after import. Recalculate progress rebuilds watch stats from episodes without contacting the catalog."
            ) {
                TextButton(onClick = viewModel::exportFull) { Text("Export & share full database") }
                TextButton(
                    onClick = { importer.launch(arrayOf("application/json", "*/*")) },
                    enabled = !importing
                ) {
                    Text(
                        run {
                            val progress = importProgress
                            when {
                                !importing -> "Import database"
                                progress != null -> "Importing... (${progress.first}/${progress.second})"
                                else -> "Importing..."
                            }
                        }
                    )
                }
                HorizontalDivider()
                TextButton(onClick = viewModel::exportTracked) { Text("Share tracked list") }
                TextButton(onClick = viewModel::exportWatchlist) { Text("Share interests") }
                TextButton(onClick = viewModel::exportBan) { Text("Share ban list") }
                TextButton(onClick = viewModel::exportRecommendations) { Text("Share recommendations") }
                TextButton(onClick = viewModel::exportSettings) { Text("Share settings") }
                TextButton(
                    onClick = viewModel::recalculateProgress,
                    enabled = !importing && !recalculating
                ) {
                    Text(
                        run {
                            val progress = recalcProgress
                            when {
                                !recalculating -> "Recalculate progress"
                                progress != null -> "Recalculating... (${progress.first}/${progress.second})"
                                else -> "Recalculating..."
                            }
                        }
                    )
                }
            }
            Button(
                onClick = { viewModel.save(current, currentKey, currentRawg, currentTasteDive, currentMal) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }
        }
    }
        FicheSnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    if (confirmCatalogUpdate) {
        AlertDialog(
            onDismissRequest = { confirmCatalogUpdate = false },
            text = {
                Text("This is not a good idea. It takes a long time and correct shows are chosen automatically which can lead to bad results.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCatalogUpdate = false
                        viewModel.reloadFromSelectedCatalogues()
                    }
                ) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCatalogUpdate = false }) { Text("Cancel") }
            }
        )
    }

    val pickerDraft = shown
    if (pickerDraft != null) {
        when (picker) {
            "background" -> ColorPickerDialog("Background", pickerDraft.backgroundArgb, { draft = pickerDraft.copy(backgroundArgb = it); picker = null }) { picker = null }
            "group" -> ColorPickerDialog("Group colour", pickerDraft.groupArgb, { draft = pickerDraft.copy(groupArgb = it); picker = null }) { picker = null }
            "action" -> ColorPickerDialog("Action colour", pickerDraft.actionArgb, { draft = pickerDraft.copy(actionArgb = it); picker = null }) { picker = null }
            "alternate" -> ColorPickerDialog("Alternate colour", pickerDraft.alternateArgb, { draft = pickerDraft.copy(alternateArgb = it); picker = null }) { picker = null }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogApiRow(
    label: String,
    current: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.equals(current, ignoreCase = true) } ?: options.first()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColorRow(label: String, argb: Int, onEdit: () -> Unit, onReset: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onEdit, onLongClick = onReset)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(argb))
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
        Text(label)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ApiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    helpTitle: String,
    helpText: String,
    helpUrl: String
) {
    val context = LocalContext.current
    var showHelp by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = label,
                modifier = Modifier.pointerInput(label) {
                    detectTapGestures(onLongPress = { showHelp = true })
                }
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(helpTitle) },
            text = { Text(helpText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(helpUrl)))
                        }
                        showHelp = false
                    }
                ) { Text("Open site") }
            },
            dismissButton = {
                TextButton(onClick = { showHelp = false }) { Text("OK") }
            }
        )
    }
}
