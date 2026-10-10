package com.grandsphere.fiche.data.backup

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.grandsphere.fiche.data.local.entity.BanEntity
import com.grandsphere.fiche.data.local.entity.DlcEntity
import com.grandsphere.fiche.data.local.entity.EpisodeEntity
import com.grandsphere.fiche.data.local.entity.RelatedTitleEntity
import com.grandsphere.fiche.data.local.entity.SeasonEntity
import com.grandsphere.fiche.data.local.entity.TitleEntity
import com.grandsphere.fiche.data.local.entity.WatchlistEntity
import com.grandsphere.fiche.data.prefs.SettingsSnapshot
import com.grandsphere.fiche.domain.model.MediaType
import com.grandsphere.fiche.util.remoteWebUrl

enum class BackupKind { FULL, BANLIST, WATCHLIST, SETTINGS, TRACKED, APPEND, SHARE }

/**
 * Rebuilds a Fiche JSON backup against the current schema: unknown keys are
 * ignored, missing fields get defaults, and identity does not depend on version.
 */
object ImportBackupReconstructor {
    fun parse(json: String): JsonObject {
        val element = runCatching { JsonParser.parseString(json) }.getOrNull()
        require(element != null && element.isJsonObject) { "Unrecognized Fiche backup file" }
        return element.asJsonObject
    }

    fun identify(root: JsonObject): BackupKind {
        val kind = root.get("kind")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.asString
        return when {
            kind == "share" -> BackupKind.SHARE
            kind == "tracked" -> BackupKind.TRACKED
            kind == "append" || kind == "recommendations" -> BackupKind.APPEND
            titlesLookLikeShare(root) -> BackupKind.SHARE
            titlesLookLikeAppend(root) -> BackupKind.APPEND
            root.has("titles") && (root.has("seasons") || root.has("episodes") || root.has("dlcs") || root.has("achievements") || root.has("version")) -> BackupKind.FULL
            root.has("titles") && root.has("kind") -> BackupKind.TRACKED
            root.has("titles") -> BackupKind.FULL
            root.has("banlist") -> BackupKind.BANLIST
            root.has("watchlist") -> BackupKind.WATCHLIST
            looksLikeSettings(root) -> BackupKind.SETTINGS
            else -> error("Unrecognized Fiche backup file")
        }
    }

    fun titles(root: JsonObject): List<TitleEntity> =
        objects(root, "titles").mapNotNull { reconstructTitle(it) }

    fun seasons(root: JsonObject): List<SeasonEntity> =
        objects(root, "seasons").mapNotNull { reconstructSeason(it) }

    fun episodes(root: JsonObject): List<EpisodeEntity> =
        objects(root, "episodes").mapNotNull { reconstructEpisode(it) }

    fun watchlist(root: JsonObject): List<WatchlistEntity> =
        objects(root, "watchlist")
            .mapNotNull { reconstructWatchlist(it) }
            .dedupeBy { it.mediaType to it.remoteId }

    fun banlist(root: JsonObject): List<BanEntity> =
        objects(root, "banlist")
            .mapNotNull { reconstructBan(it) }
            .dedupeBy { it.mediaType to it.url }

    fun related(root: JsonObject): List<RelatedTitleEntity> =
        objects(root, "related").mapNotNull { reconstructRelated(it) }

    fun dlcs(root: JsonObject): List<DlcEntity> =
        objects(root, "dlcs").mapNotNull { reconstructDlc(it) }

    fun settings(root: JsonObject): SettingsSnapshot? {
        val src = root.get("settings")?.takeIf { it.isJsonObject }?.asJsonObject ?: root
        if (!looksLikeSettings(src)) return null
        return SettingsSnapshot(
            theme = src.str("theme"),
            mediaType = src.str("mediaType"),
            tmdbApiKey = src.str("tmdbApiKey"),
            rawgApiKey = src.str("rawgApiKey"),
            tasteDiveApiKey = src.str("tasteDiveApiKey"),
            malApiKey = src.str("malApiKey"),
            sort = src.str("sort"),
            groupArgb = src.intOrNull("groupArgb"),
            backgroundArgb = src.intOrNull("backgroundArgb"),
            widgetArgb = src.intOrNull("widgetArgb"),
            actionArgb = src.intOrNull("actionArgb"),
            alternateArgb = src.intOrNull("alternateArgb"),
            fontScale = src.floatOrNull("fontScale"),
            compactMode = if (src.has("compactMode") && !src.get("compactMode").isJsonNull) {
                src.bool("compactMode", false)
            } else {
                null
            },
            showCompletedMark = if (src.has("showCompletedMark") && !src.get("showCompletedMark").isJsonNull) {
                src.bool("showCompletedMark", true)
            } else {
                null
            },
            enabledMediaTypes = src.str("enabledMediaTypes")
        )
    }

    fun sharedTitles(root: JsonObject): List<SharedTitle> {
        val element = root.get("titles") ?: return emptyList()
        if (!element.isJsonArray) return emptyList()
        return element.asJsonArray.mapNotNull { item ->
            if (!item.isJsonObject) return@mapNotNull null
            val obj = item.asJsonObject
            val title = obj.str("title") ?: return@mapNotNull null
            val category = obj.str("category") ?: obj.str("mediaType") ?: return@mapNotNull null
            SharedTitle(
                title = title,
                category = category,
                author = obj.str("author"),
                url = obj.str("url") ?: obj.str("catalogUrl"),
                firstDate = obj.str("firstDate"),
                lastDate = obj.str("lastDate"),
                genres = obj.str("genres"),
                overview = obj.str("overview")
            )
        }
    }

    fun parseShareJson(text: String): List<SharedTitle>? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{")) return null
        val root = runCatching { parse(trimmed) }.getOrNull() ?: return null
        if (identify(root) != BackupKind.SHARE) return null
        val titles = sharedTitles(root)
        return titles.takeIf { it.isNotEmpty() }
    }

    fun appendSpecs(root: JsonObject): Pair<String?, List<AppendTitleSpec>> {
        val rootType = root.str("mediaType")
        val element = root.get("titles") ?: return rootType to emptyList()
        if (!element.isJsonArray) return rootType to emptyList()
        val specs = element.asJsonArray.mapNotNull { item ->
            when {
                item.isJsonPrimitive && item.asJsonPrimitive.isString ->
                    AppendTitleSpec(title = item.asJsonPrimitive.asString)
                item.isJsonObject -> {
                    val obj = item.asJsonObject
                    val title = obj.str("title") ?: return@mapNotNull null
                    AppendTitleSpec(
                        title = title,
                        year = obj.intOrNull("year"),
                        mediaType = obj.str("mediaType"),
                        remoteId = obj.str("remoteId"),
                        url = obj.str("url") ?: obj.str("catalogUrl")
                    )
                }
                else -> null
            }
        }
        return rootType to specs
    }

    fun parseAppendText(text: String): Pair<MediaType?, List<AppendTitleSpec>> {
        val header = Regex("^Fiche recommendations\\s*[—\\-]\\s*(.+)$", RegexOption.IGNORE_CASE)
        val withYear = Regex("^(.*?)\\s*\\((\\d{4})\\)\\s*$")
        var type: MediaType? = null
        val specs = mutableListOf<AppendTitleSpec>()
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val headerMatch = header.matchEntire(line)
            if (headerMatch != null) {
                type = mediaTypeFromLabel(headerMatch.groupValues[1])
                return@forEach
            }
            val yearMatch = withYear.matchEntire(line)
            if (yearMatch != null) {
                specs += AppendTitleSpec(
                    title = yearMatch.groupValues[1].trim(),
                    year = yearMatch.groupValues[2].toInt()
                )
            } else {
                specs += AppendTitleSpec(title = line)
            }
        }
        return type to specs
    }

    private fun mediaTypeFromLabel(label: String): MediaType? {
        val value = label.trim()
        return MediaType.entries.firstOrNull {
            it.name.equals(value, true) || it.label.equals(value, true) || it.libraryLabel.equals(value, true)
        }
    }

    private fun titlesLookLikeShare(root: JsonObject): Boolean {
        val element = root.get("titles") ?: return false
        if (!element.isJsonArray || element.asJsonArray.isEmpty) return false
        val first = element.asJsonArray.first()
        if (!first.isJsonObject) return false
        val obj = first.asJsonObject
        return obj.has("title") &&
            (obj.has("category") || obj.has("mediaType")) &&
            (obj.has("url") || obj.has("catalogUrl") || obj.has("overview") || obj.has("genres")) &&
            !obj.has("remoteId") &&
            !obj.has("completed")
    }

    private fun titlesLookLikeAppend(root: JsonObject): Boolean {
        val element = root.get("titles") ?: return false
        if (!element.isJsonArray || element.asJsonArray.size() == 0) return false
        val first = element.asJsonArray.first()
        if (first.isJsonPrimitive) return true
        if (!first.isJsonObject) return false
        val obj = first.asJsonObject
        return obj.has("title") && !obj.has("remoteId") && !obj.has("seasons")
    }

    private fun looksLikeSettings(obj: JsonObject): Boolean {
        return obj.has("theme") || obj.has("tmdbApiKey") || obj.has("groupArgb") ||
            obj.has("widgetArgb") || obj.has("actionArgb") || obj.has("fontScale") ||
            obj.has("compactMode") || obj.has("showCompletedMark") || obj.has("backgroundArgb") ||
            obj.has("rawgApiKey") || obj.has("tasteDiveApiKey") || obj.has("malApiKey") || obj.has("alternateArgb") ||
            obj.has("enabledMediaTypes") ||
            (obj.has("mediaType") && obj.has("sort"))
    }

    private fun reconstructTitle(obj: JsonObject): TitleEntity? {
        val mediaType = obj.str("mediaType") ?: return null
        val remoteId = obj.str("remoteId") ?: return null
        val now = System.currentTimeMillis()
        return TitleEntity(
            id = obj.long("id", 0L),
            mediaType = mediaType,
            remoteId = remoteId,
            title = obj.str("title") ?: "Untitled",
            year = obj.intOrNull("year"),
            posterUrl = obj.str("posterUrl"),
            overview = obj.str("overview"),
            author = obj.str("author"),
            status = obj.str("status"),
            cancelled = obj.bool("cancelled", false),
            genres = obj.str("genres") ?: "",
            userRating = obj.floatOrNull("userRating"),
            moreOfThis = obj.bool("moreOfThis", false),
            completed = obj.bool("completed", false),
            collectionId = obj.str("collectionId"),
            collectionName = obj.str("collectionName"),
            seriesName = obj.str("seriesName"),
            seriesPosition = obj.intOrNull("seriesPosition"),
            firstDate = obj.str("firstDate"),
            lastDate = obj.str("lastDate"),
            totalUnits = obj.int("totalUnits", 0),
            watchedUnits = obj.int("watchedUnits", 0),
            watchedMinutes = obj.int("watchedMinutes", 0),
            totalMinutes = obj.int("totalMinutes", 0),
            imdbId = obj.str("imdbId"),
            runtimeMinutes = obj.int("runtimeMinutes", 0),
            pageCount = obj.int("pageCount", 0),
            recommended = obj.bool("recommended", false),
            localPosterPath = null,
            displayTitle = obj.str("displayTitle"),
            catalogUrl = obj.str("catalogUrl") ?: remoteWebUrl(mediaType, remoteId),
            hidden = obj.bool("hidden", false),
            tracked = obj.bool("tracked", !obj.bool("hidden", false)),
            notInterested = obj.bool("notInterested", false),
            countSequelsInCompletion = obj.bool("countSequelsInCompletion", true),
            addedAt = obj.long("addedAt", now),
            updatedAt = obj.long("updatedAt", now)
        )
    }

    private fun reconstructSeason(obj: JsonObject): SeasonEntity? {
        val titleId = obj.longOrNull("titleId") ?: return null
        val seasonNumber = obj.intOrNull("seasonNumber") ?: return null
        return SeasonEntity(
            id = obj.long("id", 0L),
            titleId = titleId,
            seasonNumber = seasonNumber,
            episodeCount = obj.int("episodeCount", 0),
            startDate = obj.str("startDate"),
            endDate = obj.str("endDate"),
            notInterested = obj.bool("notInterested", false)
        )
    }

    private fun reconstructEpisode(obj: JsonObject): EpisodeEntity? {
        val titleId = obj.longOrNull("titleId") ?: return null
        val seasonNumber = obj.intOrNull("seasonNumber") ?: return null
        val episodeNumber = obj.intOrNull("episodeNumber") ?: return null
        return EpisodeEntity(
            id = obj.long("id", 0L),
            titleId = titleId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            airDate = obj.str("airDate"),
            watched = obj.bool("watched", false),
            runtimeMinutes = obj.int("runtimeMinutes", 0)
        )
    }

    private fun reconstructWatchlist(obj: JsonObject): WatchlistEntity? {
        val mediaType = obj.str("mediaType") ?: return null
        val remoteId = obj.str("remoteId") ?: return null
        return WatchlistEntity(
            id = obj.long("id", 0L),
            mediaType = mediaType,
            remoteId = remoteId,
            title = obj.str("title") ?: "Untitled",
            year = obj.intOrNull("year"),
            posterUrl = obj.str("posterUrl"),
            overview = obj.str("overview"),
            genres = obj.str("genres") ?: "",
            collectionId = obj.str("collectionId"),
            collectionName = obj.str("collectionName"),
            addedAt = obj.long("addedAt", System.currentTimeMillis())
        )
    }

    private fun reconstructBan(obj: JsonObject): BanEntity? {
        val mediaType = obj.str("mediaType") ?: return null
        val title = obj.str("title") ?: "Untitled"
        val year = obj.intOrNull("year")
        val url = obj.str("url")
            ?: obj.str("remoteId")?.let { "remote:$it" }
            ?: "$title|${year ?: ""}"
        return BanEntity(
            id = obj.long("id", 0L),
            mediaType = mediaType,
            title = title,
            year = year,
            url = url,
            addedAt = obj.long("addedAt", System.currentTimeMillis())
        )
    }

    private fun reconstructRelated(obj: JsonObject): RelatedTitleEntity? {
        val ownerRemoteId = obj.str("ownerRemoteId") ?: return null
        val ownerMediaType = obj.str("ownerMediaType") ?: return null
        val relatedRemoteId = obj.str("relatedRemoteId") ?: return null
        val relatedMediaType = obj.str("relatedMediaType") ?: return null
        val relation = obj.str("relation") ?: return null
        return RelatedTitleEntity(
            id = obj.long("id", 0L),
            ownerRemoteId = ownerRemoteId,
            ownerMediaType = ownerMediaType,
            relatedRemoteId = relatedRemoteId,
            relatedMediaType = relatedMediaType,
            relation = relation,
            relatedTitle = obj.str("relatedTitle") ?: "Untitled",
            year = obj.intOrNull("year"),
            posterUrl = obj.str("posterUrl"),
            position = obj.int("position", 0),
            watched = obj.bool("watched", false),
            runtimeMinutes = obj.int("runtimeMinutes", 0)
        )
    }

    private fun reconstructDlc(obj: JsonObject): DlcEntity? {
        val titleId = obj.longOrNull("titleId") ?: return null
        val remoteId = obj.str("remoteId") ?: return null
        val name = obj.str("name") ?: return null
        return DlcEntity(
            id = obj.long("id", 0L),
            titleId = titleId,
            remoteId = remoteId,
            name = name,
            released = obj.str("released"),
            posterUrl = obj.str("posterUrl"),
            completed = obj.bool("completed", false),
            position = obj.int("position", 0)
        )
    }

    private fun objects(root: JsonObject, key: String): List<JsonObject> {
        val element = root.get(key) ?: return emptyList()
        if (!element.isJsonArray) return emptyList()
        return element.asJsonArray.mapNotNull { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject
        }
    }

    private fun <T> List<T>.dedupeBy(key: (T) -> Pair<String, String>): List<T> {
        val map = LinkedHashMap<Pair<String, String>, T>()
        forEach { map[key(it)] = it }
        return map.values.toList()
    }

    private fun JsonObject.str(key: String): String? {
        val element = get(key) ?: return null
        if (element.isJsonNull || !element.isJsonPrimitive) return null
        return element.asJsonPrimitive.asString.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.bool(key: String, default: Boolean): Boolean {
        val element = get(key) ?: return default
        if (element.isJsonNull || !element.isJsonPrimitive) return default
        val primitive = element.asJsonPrimitive
        return when {
            primitive.isBoolean -> primitive.asBoolean
            primitive.isNumber -> primitive.asInt != 0
            primitive.isString -> primitive.asString.equals("true", true) || primitive.asString == "1"
            else -> default
        }
    }

    private fun JsonObject.int(key: String, default: Int): Int = intOrNull(key) ?: default

    private fun JsonObject.intOrNull(key: String): Int? = numberOrNull(key)?.toInt()

    private fun JsonObject.long(key: String, default: Long): Long = longOrNull(key) ?: default

    private fun JsonObject.longOrNull(key: String): Long? = numberOrNull(key)?.toLong()

    private fun JsonObject.floatOrNull(key: String): Float? = numberOrNull(key)?.toFloat()

    private fun JsonObject.numberOrNull(key: String): Double? {
        val element = get(key) ?: return null
        if (element.isJsonNull || !element.isJsonPrimitive) return null
        val primitive = element.asJsonPrimitive
        return when {
            primitive.isNumber -> primitive.asDouble
            primitive.isString -> primitive.asString.toDoubleOrNull()
            else -> null
        }
    }
}

data class AppendTitleSpec(
    val title: String,
    val year: Int? = null,
    val mediaType: String? = null,
    val remoteId: String? = null,
    val url: String? = null
)
