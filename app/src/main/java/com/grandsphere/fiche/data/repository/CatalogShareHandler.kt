package com.grandsphere.fiche.data.repository

import android.net.Uri
import com.grandsphere.fiche.data.prefs.UserPreferencesRepository
import com.grandsphere.fiche.data.remote.RemoteTitle
import com.grandsphere.fiche.domain.model.MediaType
import com.grandsphere.fiche.util.CatalogLinkKind
import com.grandsphere.fiche.util.CatalogLinkParser
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

sealed class ShareOutcome {
    data class AlreadyTracked(val title: String) : ShareOutcome()
    data class OpenSearch(val prefill: AddSearchPrefill) : ShareOutcome()
    data class Message(val text: String) : ShareOutcome()
}

@Singleton
class CatalogShareHandler @Inject constructor(
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    private val prefs: UserPreferencesRepository,
    private val prefillStore: AddSearchPrefillStore
) {
    fun beginLoading(text: String?, uri: Uri?) {
        val link = CatalogLinkParser.parse(text, uri) ?: return
        val type = typeFor(link.kind)
        val name = CatalogLinkParser.extractedName(text)
        prefillStore.offer(
            AddSearchPrefill(
                query = name,
                type = type,
                seed = null,
                fromShare = true,
                resolving = true
            )
        )
    }

    suspend fun handle(text: String?, uri: Uri?): ShareOutcome {
        val link = CatalogLinkParser.parse(text, uri)
            ?: return ShareOutcome.Message("No catalog link found")
        val extracted = CatalogLinkParser.extractedName(text)
        val type = typeFor(link.kind)
        return runCatching {
            when (link.kind) {
                CatalogLinkKind.TVMAZE_SHOW -> offerFromRemote(catalog.remoteFromTvMazeId(link.id))
                CatalogLinkKind.TMDB_MOVIE -> offerFromRemote(catalog.details(MediaType.MOVIE, link.id))
                CatalogLinkKind.TMDB_TV -> offerFromRemote(catalog.details(MediaType.SERIES, link.id))
                CatalogLinkKind.OPEN_LIBRARY_WORK -> offerFromRemote(catalog.details(MediaType.BOOK, link.id))
                CatalogLinkKind.OPEN_LIBRARY_EDITION -> {
                    val workId = catalog.workIdFromEdition(link.id)
                        ?: return@runCatching failLoading(extracted, type, "Couldn't resolve that Open Library edition")
                    offerFromRemote(catalog.details(MediaType.BOOK, workId))
                }
                CatalogLinkKind.IMDB_TITLE -> openImdb(link.id, extracted)
                CatalogLinkKind.MAL_ANIME -> openMal(link.id, extracted)
                CatalogLinkKind.MAL_MANGA -> failLoading(extracted, type, "Manga pages aren't Open Library books")
                CatalogLinkKind.RAWG_GAME -> offerFromRemote(catalog.details(MediaType.GAME, link.id))
                CatalogLinkKind.STEAM_APP -> openSteam(link.id, extracted)
            }
        }.getOrElse { failLoading(extracted, type, it.message ?: "Couldn't open that link") }
    }

    private suspend fun openSteam(appId: String, extracted: String): ShareOutcome {
        val hit = catalog.resolveSteamApp(appId)
        val name = hit?.title?.ifBlank { null }
            ?: catalog.steamAppName(appId)?.ifBlank { null }
            ?: extracted.trim().takeIf { it.isNotBlank() }
        if (name.isNullOrBlank() && hit == null) {
            return failLoading(extracted, MediaType.GAME, "Couldn't resolve that Steam link")
        }
        return offerSearch(name ?: hit!!.title, MediaType.GAME, hit)
    }

    private suspend fun offerFromRemote(remote: RemoteTitle): ShareOutcome {
        val type = runCatching { MediaType.valueOf(remote.mediaType) }.getOrNull()
            ?: return failLoading(remote.title, MediaType.MOVIE, "Unknown media type")
        if (library.alreadyTracked(type, remote.remoteId)) {
            offerSearch(remote.title, type, remote)
            return ShareOutcome.AlreadyTracked(remote.title)
        }
        return offerSearch(remote.title, type, remote)
    }

    private suspend fun openImdb(imdb: String, extracted: String): ShareOutcome {
        val hit = catalog.lookupImdbTitle(imdb)
        return if (hit != null) {
            offerSearch(hit.title, MediaType.valueOf(hit.mediaType), hit)
        } else {
            val type = prefs.mediaType.first()
            offerSearch(extracted, type, null)
        }
    }

    private suspend fun openMal(malId: String, extracted: String): ShareOutcome {
        val mal = catalog.malAnimeLookup(malId)
        val imdbHit = mal?.imdbId?.let { catalog.lookupImdbTitle(it) }
        return if (imdbHit != null) {
            offerSearch(imdbHit.title, MediaType.valueOf(imdbHit.mediaType), imdbHit)
        } else {
            offerSearch(mal?.title?.ifBlank { extracted } ?: extracted, MediaType.ANIME, null)
        }
    }

    private suspend fun offerSearch(query: String, type: MediaType, seed: RemoteTitle?): ShareOutcome {
        prefs.setMediaType(type)
        val prefill = AddSearchPrefill(
            query = query,
            type = type,
            seed = seed,
            fromShare = true,
            resolving = false
        )
        prefillStore.offer(prefill)
        return ShareOutcome.OpenSearch(prefill)
    }

    private fun failLoading(query: String, type: MediaType, message: String): ShareOutcome.Message {
        prefillStore.offer(
            AddSearchPrefill(
                query = query,
                type = type,
                seed = null,
                fromShare = false,
                resolving = false
            )
        )
        return ShareOutcome.Message(message)
    }

    private fun typeFor(kind: CatalogLinkKind): MediaType = when (kind) {
        CatalogLinkKind.TVMAZE_SHOW, CatalogLinkKind.TMDB_TV -> MediaType.SERIES
        CatalogLinkKind.TMDB_MOVIE -> MediaType.MOVIE
        CatalogLinkKind.OPEN_LIBRARY_WORK, CatalogLinkKind.OPEN_LIBRARY_EDITION -> MediaType.BOOK
        CatalogLinkKind.MAL_ANIME -> MediaType.ANIME
        CatalogLinkKind.RAWG_GAME, CatalogLinkKind.STEAM_APP -> MediaType.GAME
        CatalogLinkKind.IMDB_TITLE -> MediaType.MOVIE
        CatalogLinkKind.MAL_MANGA -> MediaType.BOOK
    }
}
