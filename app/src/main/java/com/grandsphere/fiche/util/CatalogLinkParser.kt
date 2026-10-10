package com.grandsphere.fiche.util

import android.net.Uri

enum class CatalogLinkKind {
    TVMAZE_SHOW,
    TMDB_MOVIE,
    TMDB_TV,
    OPEN_LIBRARY_WORK,
    OPEN_LIBRARY_EDITION,
    IMDB_TITLE,
    MAL_ANIME,
    MAL_MANGA,
    RAWG_GAME,
    STEAM_APP
}

data class CatalogLink(
    val kind: CatalogLinkKind,
    val id: String
)

object CatalogLinkParser {
    private val urlRegex = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val imdbIdRegex = Regex("""tt\d+""", RegexOption.IGNORE_CASE)

    fun parse(text: String?, uri: Uri? = null): CatalogLink? {
        val candidates = buildList {
            uri?.toString()?.let { add(it) }
            if (!text.isNullOrBlank()) {
                add(text)
                urlRegex.findAll(text).forEach { add(it.value) }
            }
        }
        candidates.forEach { candidate ->
            parseOne(candidate)?.let { return it }
        }
        val imdb = text?.let { imdbIdRegex.find(it)?.value }
        if (imdb != null) return CatalogLink(CatalogLinkKind.IMDB_TITLE, imdb.lowercase())
        return null
    }

    fun extractedName(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val withoutUrls = urlRegex.replace(text, " ")
        val line = withoutUrls.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        return stripYearSuffix(line)
            .replace(Regex("""\s*[|·].*"""), "")
            .replace("IMDb", "", ignoreCase = true)
            .replace("MyAnimeList", "", ignoreCase = true)
            .replace("Steam", "", ignoreCase = true)
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun stripYearSuffix(name: String): String =
        name.replace(Regex("""\s*\(\d{4}\)\s*"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun parseOne(raw: String): CatalogLink? {
        val uri = runCatching { Uri.parse(raw.trim().removeSuffix("/")) }.getOrNull() ?: return null
        val host = uri.host?.removePrefix("www.")?.removePrefix("m.")?.lowercase() ?: return null
        val path = uri.path.orEmpty()
        return when {
            host == "tvmaze.com" || host.endsWith(".tvmaze.com") -> {
                val id = Regex("""/shows/(\d+)""").find(path)?.groupValues?.get(1) ?: return null
                CatalogLink(CatalogLinkKind.TVMAZE_SHOW, id)
            }
            host == "themoviedb.org" || host.endsWith(".themoviedb.org") -> {
                Regex("""/movie/(\d+)""").find(path)?.groupValues?.get(1)?.let {
                    return CatalogLink(CatalogLinkKind.TMDB_MOVIE, it)
                }
                Regex("""/tv/(\d+)""").find(path)?.groupValues?.get(1)?.let {
                    return CatalogLink(CatalogLinkKind.TMDB_TV, it)
                }
                null
            }
            host == "openlibrary.org" -> {
                Regex("""/works/(OL\d+W)""", RegexOption.IGNORE_CASE).find(path)?.groupValues?.get(1)?.let {
                    return CatalogLink(CatalogLinkKind.OPEN_LIBRARY_WORK, it.uppercase())
                }
                Regex("""/books/(OL\d+M)""", RegexOption.IGNORE_CASE).find(path)?.groupValues?.get(1)?.let {
                    return CatalogLink(CatalogLinkKind.OPEN_LIBRARY_EDITION, it.uppercase())
                }
                null
            }
            host == "imdb.com" || host.endsWith(".imdb.com") -> {
                val id = Regex("""/title/(tt\d+)""", RegexOption.IGNORE_CASE).find(path)?.groupValues?.get(1)
                    ?: return null
                CatalogLink(CatalogLinkKind.IMDB_TITLE, id.lowercase())
            }
            host == "myanimelist.net" || host.endsWith(".myanimelist.net") -> {
                Regex("""/anime/(\d+)""").find(path)?.groupValues?.get(1)?.let {
                    return CatalogLink(CatalogLinkKind.MAL_ANIME, it)
                }
                Regex("""/manga/(\d+)""").find(path)?.groupValues?.get(1)?.let {
                    return CatalogLink(CatalogLinkKind.MAL_MANGA, it)
                }
                null
            }
            host == "rawg.io" || host.endsWith(".rawg.io") -> {
                val slug = Regex("""/games/([^/?#]+)""").find(path)?.groupValues?.get(1) ?: return null
                CatalogLink(CatalogLinkKind.RAWG_GAME, slug)
            }
            host == "store.steampowered.com" ||
                host == "steampowered.com" ||
                host.endsWith(".steampowered.com") ||
                host == "steamcommunity.com" ||
                host.endsWith(".steamcommunity.com") -> {
                val id = Regex("""/app/(\d+)""").find(path)?.groupValues?.get(1) ?: return null
                CatalogLink(CatalogLinkKind.STEAM_APP, id)
            }
            else -> null
        }
    }
}
