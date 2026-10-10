package com.grandsphere.fiche.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.grandsphere.fiche.data.local.FicheDatabase
import com.grandsphere.fiche.data.local.dao.BanDao
import com.grandsphere.fiche.data.local.dao.DlcDao
import com.grandsphere.fiche.data.local.dao.EpisodeDao
import com.grandsphere.fiche.data.local.dao.RelatedDao
import com.grandsphere.fiche.data.local.dao.SeasonDao
import com.grandsphere.fiche.data.local.dao.TitleDao
import com.grandsphere.fiche.data.local.dao.WatchlistDao
import com.grandsphere.fiche.data.local.entity.BanEntity
import com.grandsphere.fiche.data.local.entity.DlcEntity
import com.grandsphere.fiche.data.local.entity.EpisodeEntity
import com.grandsphere.fiche.data.local.entity.RelatedTitleEntity
import com.grandsphere.fiche.data.local.entity.SeasonEntity
import com.grandsphere.fiche.data.local.entity.TitleEntity
import com.grandsphere.fiche.data.local.entity.WatchlistEntity
import com.grandsphere.fiche.data.prefs.ApiKeyRepository
import com.grandsphere.fiche.data.prefs.SettingsSnapshot
import com.grandsphere.fiche.data.repository.LibraryRepository
import com.grandsphere.fiche.domain.model.MediaType
import com.grandsphere.fiche.data.prefs.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val titles: TitleDao,
    private val seasons: SeasonDao,
    private val episodes: EpisodeDao,
    private val watchlist: WatchlistDao,
    private val bans: BanDao,
    private val related: RelatedDao,
    private val dlcs: DlcDao,
    private val prefs: UserPreferencesRepository,
    private val apiKeys: ApiKeyRepository,
    private val library: LibraryRepository,
    private val db: FicheDatabase
) {
    suspend fun exportFull(): File {
        val payload = FullBackup(
            version = 2,
            titles = titles.getAll().map { it.copy(localPosterPath = null) },
            seasons = seasons.getAll(),
            episodes = episodes.getAll(),
            watchlist = watchlist.getAll(),
            banlist = bans.getAll(),
            related = related.getAll(),
            dlcs = dlcs.getAll(),
            settings = settingsForExport()
        )
        return writeJson("fiche-full-backup.json", payload)
    }

    suspend fun exportBanlist(): File = writeJson("fiche-banlist.json", BanBackup(bans.getAll()))
    suspend fun exportWatchlist(): File = writeJson("fiche-interests.json", WatchlistBackup(watchlist.getAll()))
    suspend fun exportSettings(): File = writeJson("fiche-settings.json", settingsForExport())

    suspend fun exportTracked(): File = writeJson(
        "fiche-tracked.json",
        TrackedBackup(titles = titles.getAll().map { it.copy(localPosterPath = null) })
    )

    suspend fun exportTrackedTitles(titleIds: List<Long>): File {
        require(titleIds.isNotEmpty()) { "Nothing selected" }
        val selected = titleIds.mapNotNull { titles.getById(it) }
        require(selected.isNotEmpty()) { "Nothing selected" }
        return writeJson(
            "fiche-tracked.json",
            TrackedBackup(titles = selected.map { it.copy(localPosterPath = null) })
        )
    }

    suspend fun exportSharedTitles(titleIds: List<Long>): File {
        require(titleIds.isNotEmpty()) { "Nothing selected" }
        val selected = titleIds.mapNotNull { titles.getById(it) }
        require(selected.isNotEmpty()) { "Nothing selected" }
        return writeJson(
            "fiche-share.json",
            SharePayload(titles = selected.map { it.toSharedTitle() })
        )
    }

    fun parseSharePayload(text: String): List<SharedTitle>? =
        ImportBackupReconstructor.parseShareJson(text)

    suspend fun readSharePayload(uri: Uri): List<SharedTitle>? {
        val text = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charset.forName("UTF-8"))
        } ?: return null
        return parseSharePayload(text)
    }

    suspend fun importShared(titles: List<SharedTitle>): String {
        val result = library.importSharedTitles(titles)
        persistOverviewCaches()
        return when {
            result.added == 0 && result.skipped > 0 -> "All ${result.skipped} already in your library"
            result.skipped > 0 -> "Added ${result.added}, skipped ${result.skipped} duplicates"
            else -> "Added ${result.added} to your library"
        }
    }

    suspend fun exportRecommendations(type: MediaType): File {
        val items = titles.recommended(type.name)
        require(items.isNotEmpty()) { "No recommended ${type.label.lowercase()} yet" }
        return writeJson(
            "fiche-recommendations.json",
            AppendBackup(
                source = "recommendations",
                mediaType = type.name,
                titles = items.map { it.toAppendTitle() }
            )
        )
    }

    suspend fun exportRecommendations(titleIds: List<Long>): File {
        require(titleIds.isNotEmpty()) { "Nothing selected" }
        val items = titleIds.mapNotNull { titles.getById(it) }
        require(items.isNotEmpty()) { "Nothing selected" }
        return writeJson(
            "fiche-recommendations.json",
            AppendBackup(
                source = "recommendations",
                mediaType = items.map { it.mediaType }.distinct().singleOrNull(),
                titles = items.map { it.toAppendTitle() }
            )
        )
    }

    suspend fun exportRecommendation(title: TitleEntity): File {
        return writeJson(
            "fiche-recommendations.json",
            AppendBackup(
                source = "recommendations",
                mediaType = title.mediaType,
                titles = listOf(title.toAppendTitle())
            )
        )
    }

    suspend fun exportSharedCollection(titleId: Long): File {
        val title = titles.getById(titleId) ?: error("Missing title")
        val collectionId = title.collectionId?.takeIf { it.isNotBlank() }
            ?: error("Not part of a collection")
        val members = titles.getAll().filter {
            it.mediaType == MediaType.MOVIE.name && it.collectionId == collectionId
        }
        require(members.isNotEmpty()) { "Collection is empty" }
        return writeJson(
            "fiche-share.json",
            SharePayload(titles = members.map { it.toSharedTitle() })
        )
    }

    fun shareFile(file: File): Intent {
        val mime = if (file.name.endsWith(".txt")) "text/plain" else "application/json"
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun importFrom(uri: Uri): String {
        val text = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charset.forName("UTF-8"))
        } ?: error("Could not read file")
        return importFromText(text)
    }

    suspend fun importFromFile(
        file: File,
        onProgress: suspend (Int, Int) -> Unit = { _, _ -> }
    ): String = importFromText(file.readText(Charset.forName("UTF-8")), onProgress)

    private suspend fun importFromText(
        text: String,
        onProgress: suspend (Int, Int) -> Unit = { _, _ -> }
    ): String {
        val trimmed = text.trimStart()
        if (!trimmed.startsWith("{")) {
            return importAppendText(text)
        }
        val root = ImportBackupReconstructor.parse(text)
        val kind = ImportBackupReconstructor.identify(root)
        val message = when (kind) {
            BackupKind.FULL -> {
                importFull(root, onProgress)
                "Import complete"
            }
            BackupKind.TRACKED -> {
                importTracked(root, onProgress)
                "Import complete"
            }
            BackupKind.BANLIST -> {
                val items = ImportBackupReconstructor.banlist(root)
                if (items.isNotEmpty()) bans.insertAll(items.map { it.copy(id = 0) })
                "Import complete"
            }
            BackupKind.WATCHLIST -> {
                val items = ImportBackupReconstructor.watchlist(root)
                if (items.isNotEmpty()) watchlist.insertAll(items.map { it.copy(id = 0) })
                "Import complete"
            }
            BackupKind.SETTINGS -> {
                val snapshot = ImportBackupReconstructor.settings(root)
                    ?: error("Unrecognized Fiche backup file")
                prefs.importSnapshot(snapshot)
                applyApiKeys(snapshot)
                "Import complete"
            }
            BackupKind.APPEND -> importAppendJson(root)
            BackupKind.SHARE -> importShared(ImportBackupReconstructor.sharedTitles(root))
        }
        if (kind != BackupKind.APPEND && kind != BackupKind.SETTINGS && kind != BackupKind.SHARE) {
            persistOverviewCaches()
        }
        return message
    }

    private suspend fun importAppendJson(root: JsonObject): String {
        val (mediaTypeName, specs) = ImportBackupReconstructor.appendSpecs(root)
        val type = mediaTypeName?.let { runCatching { MediaType.valueOf(it) }.getOrNull() }
            ?: prefs.mediaType.first()
        val result = library.appendSpecs(type, specs)
        return "Added ${result.added}, skipped ${result.skipped}"
    }

    private suspend fun importAppendText(text: String): String {
        val (headerType, specs) = ImportBackupReconstructor.parseAppendText(text)
        require(specs.isNotEmpty()) { "No titles to import" }
        val result = library.appendSpecs(headerType ?: prefs.mediaType.first(), specs)
        return "Added ${result.added}, skipped ${result.skipped}"
    }

    private suspend fun importFull(
        root: JsonObject,
        onProgress: suspend (Int, Int) -> Unit
    ) {
        val importedTitles = ImportBackupReconstructor.titles(root)
        val total = importedTitles.size.coerceAtLeast(1)
        onProgress(0, total)
        val settings = ImportBackupReconstructor.settings(root)
        db.withTransaction {
            val existingByRemote = titles.getAll().associateBy { it.mediaType to it.remoteId }
            val idMap = mutableMapOf<Long, Long>()
            importedTitles.forEachIndexed { index, old ->
                val existing = existingByRemote[old.mediaType to old.remoteId]
                val newId = if (existing != null) {
                    titles.update(old.copy(id = existing.id, localPosterPath = existing.localPosterPath))
                    existing.id
                } else {
                    titles.insert(old.copy(id = 0, localPosterPath = null))
                }
                if (old.id != 0L) idMap[old.id] = newId
                onProgress(index + 1, total)
            }
            idMap.values.distinct().forEach { titleId ->
                seasons.deleteForTitle(titleId)
                episodes.deleteForTitle(titleId)
                dlcs.deleteForTitle(titleId)
            }
            val seasonRows = ImportBackupReconstructor.seasons(root).mapNotNull { season ->
                val newTitleId = idMap[season.titleId] ?: return@mapNotNull null
                season.copy(id = 0, titleId = newTitleId)
            }
            if (seasonRows.isNotEmpty()) seasons.insertAll(seasonRows)
            val episodeRows = ImportBackupReconstructor.episodes(root).mapNotNull { episode ->
                val newTitleId = idMap[episode.titleId] ?: return@mapNotNull null
                episode.copy(id = 0, titleId = newTitleId)
            }
            if (episodeRows.isNotEmpty()) episodes.insertAll(episodeRows)
            val dlcRows = ImportBackupReconstructor.dlcs(root).mapNotNull { dlc ->
                val newTitleId = idMap[dlc.titleId] ?: return@mapNotNull null
                dlc.copy(id = 0, titleId = newTitleId)
            }
            if (dlcRows.isNotEmpty()) dlcs.insertAll(dlcRows)
            val watchRows = ImportBackupReconstructor.watchlist(root).map { it.copy(id = 0) }
            if (watchRows.isNotEmpty()) watchlist.insertAll(watchRows)
            val banRows = ImportBackupReconstructor.banlist(root).map { it.copy(id = 0) }
            if (banRows.isNotEmpty()) bans.insertAll(banRows)
            val relatedRows = ImportBackupReconstructor.related(root).map { it.copy(id = 0) }
            if (relatedRows.isNotEmpty()) related.insertAll(relatedRows)
        }
        settings?.let {
            prefs.importSnapshot(it)
            applyApiKeys(it)
        }
    }

    private suspend fun importTracked(
        root: JsonObject,
        onProgress: suspend (Int, Int) -> Unit
    ) {
        val importedTitles = ImportBackupReconstructor.titles(root)
        val total = importedTitles.size.coerceAtLeast(1)
        onProgress(0, total)
        db.withTransaction {
            val existingByRemote = titles.getAll().associateBy { it.mediaType to it.remoteId }
            importedTitles.forEachIndexed { index, old ->
                val existing = existingByRemote[old.mediaType to old.remoteId]
                if (existing != null) {
                    titles.update(old.copy(id = existing.id, localPosterPath = existing.localPosterPath))
                } else {
                    titles.insert(old.copy(id = 0, localPosterPath = null))
                }
                onProgress(index + 1, total)
            }
        }
    }

    private suspend fun persistOverviewCaches() {
        MediaType.entries.forEach { library.persistOverview(it) }
    }

    private suspend fun settingsForExport(): SettingsSnapshot {
        return prefs.snapshot().copy(
            tmdbApiKey = apiKeys.get("TMDB"),
            rawgApiKey = apiKeys.get("RAWG"),
            tasteDiveApiKey = apiKeys.get("TASTEDIVE"),
            malApiKey = apiKeys.get("MAL")
        )
    }

    private suspend fun applyApiKeys(snapshot: SettingsSnapshot) {
        snapshot.tmdbApiKey?.let { apiKeys.set("TMDB", it) }
        snapshot.rawgApiKey?.let { apiKeys.set("RAWG", it) }
        snapshot.tasteDiveApiKey?.let { apiKeys.set("TASTEDIVE", it) }
        snapshot.malApiKey?.let { apiKeys.set("MAL", it) }
    }

    private fun writeJson(name: String, payload: Any): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, name)
        file.writeText(gson.toJson(payload), Charset.forName("UTF-8"))
        return file
    }
}

data class FullBackup(
    val version: Int,
    val titles: List<TitleEntity>,
    val seasons: List<SeasonEntity>,
    val episodes: List<EpisodeEntity>,
    val watchlist: List<WatchlistEntity>,
    val banlist: List<BanEntity>,
    val related: List<RelatedTitleEntity>,
    val dlcs: List<DlcEntity> = emptyList(),
    val settings: SettingsSnapshot?
)

data class BanBackup(val banlist: List<BanEntity>)
data class WatchlistBackup(val watchlist: List<WatchlistEntity>)
data class TrackedBackup(val kind: String = "tracked", val titles: List<TitleEntity>)
data class AppendBackup(
    val kind: String = "append",
    val source: String = "recommendations",
    val mediaType: String? = null,
    val titles: List<AppendTitle>
)
data class AppendTitle(
    val title: String,
    val year: Int? = null,
    val mediaType: String? = null,
    val remoteId: String? = null,
    val url: String? = null
)
