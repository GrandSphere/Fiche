package com.grandsphere.fiche.data.catalog

import com.grandsphere.fiche.data.catalog.anime.AnimeCatalog
import com.grandsphere.fiche.data.catalog.anime.JikanAnimeCatalog
import com.grandsphere.fiche.data.catalog.anime.MalAnimeCatalog
import com.grandsphere.fiche.data.catalog.anime.TmdbAnimeCatalog
import com.grandsphere.fiche.data.catalog.anime.TvMazeAnimeCatalog
import com.grandsphere.fiche.data.catalog.books.BooksCatalog
import com.grandsphere.fiche.data.catalog.books.OpenLibraryBooksCatalog
import com.grandsphere.fiche.data.catalog.games.GamesCatalog
import com.grandsphere.fiche.data.catalog.games.RawgGamesCatalog
import com.grandsphere.fiche.data.catalog.movies.MoviesCatalog
import com.grandsphere.fiche.data.catalog.movies.TmdbMoviesCatalog
import com.grandsphere.fiche.data.catalog.series.SeriesCatalog
import com.grandsphere.fiche.data.catalog.series.TmdbSeriesCatalog
import com.grandsphere.fiche.data.catalog.series.TvMazeSeriesCatalog
import com.grandsphere.fiche.data.local.dao.LibraryPrefsDao
import com.grandsphere.fiche.data.local.entity.LibraryPrefsEntity
import com.grandsphere.fiche.domain.model.MediaType

/**
 * Resolves the active [MediaCatalogProvider] for a [MediaType] from library_prefs.
 * Defaults: Series=TVMAZE, Movies=TMDB, Anime=TVMAZE, Games=RAWG, Books=OPEN_LIBRARY.
 */
class CatalogRouter(
    private val prefsDao: LibraryPrefsDao,
    private val tvMazeSeries: TvMazeSeriesCatalog,
    private val tmdbSeries: TmdbSeriesCatalog,
    private val tmdbMovies: TmdbMoviesCatalog,
    private val tvMazeAnime: TvMazeAnimeCatalog,
    private val tmdbAnime: TmdbAnimeCatalog,
    private val jikanAnime: JikanAnimeCatalog,
    private val malAnime: MalAnimeCatalog,
    private val rawgGames: RawgGamesCatalog,
    private val openLibraryBooks: OpenLibraryBooksCatalog
) {
    suspend fun prefs(): LibraryPrefsEntity =
        prefsDao.get() ?: LibraryPrefsEntity()

    suspend fun providerIdFor(type: MediaType): CatalogProviderId {
        val prefs = prefs()
        return when (type) {
            MediaType.SERIES -> CatalogProviderId.fromRaw(prefs.seriesApi, CatalogProviderId.TVMAZE)
            MediaType.MOVIE -> CatalogProviderId.fromRaw(prefs.moviesApi, CatalogProviderId.TMDB)
            MediaType.ANIME -> CatalogProviderId.fromRaw(prefs.animeApi, CatalogProviderId.TVMAZE)
            MediaType.GAME -> CatalogProviderId.fromRaw(prefs.gamesApi, CatalogProviderId.RAWG)
            MediaType.BOOK -> CatalogProviderId.fromRaw(prefs.booksApi, CatalogProviderId.OPEN_LIBRARY)
        }
    }

    suspend fun series(): SeriesCatalog = when (providerIdFor(MediaType.SERIES)) {
        CatalogProviderId.TMDB -> tmdbSeries
        else -> tvMazeSeries
    }

    suspend fun movies(): MoviesCatalog = tmdbMovies

    suspend fun anime(): AnimeCatalog = when (providerIdFor(MediaType.ANIME)) {
        CatalogProviderId.TMDB -> tmdbAnime
        CatalogProviderId.MAL -> malAnime
        CatalogProviderId.JIKAN -> jikanAnime
        else -> tvMazeAnime
    }

    suspend fun games(): GamesCatalog = rawgGames

    suspend fun books(): BooksCatalog = openLibraryBooks

    suspend fun forType(type: MediaType): MediaCatalogProvider = when (type) {
        MediaType.SERIES -> series()
        MediaType.MOVIE -> movies()
        MediaType.ANIME -> anime()
        MediaType.GAME -> games()
        MediaType.BOOK -> books()
    }
}
