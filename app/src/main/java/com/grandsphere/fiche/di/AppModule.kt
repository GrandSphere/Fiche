package com.grandsphere.fiche.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.grandsphere.fiche.data.catalog.CatalogRouter
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
import com.grandsphere.fiche.data.local.FicheDatabase
import com.grandsphere.fiche.data.local.dao.LibraryPrefsDao
import com.grandsphere.fiche.data.prefs.ApiKeyRepository
import com.grandsphere.fiche.data.prefs.UserPreferencesRepository
import com.grandsphere.fiche.data.remote.jikan.JikanApi
import com.grandsphere.fiche.data.remote.mal.MalApi
import com.grandsphere.fiche.data.remote.openlibrary.OpenLibraryApi
import com.grandsphere.fiche.data.remote.rawg.RawgApi
import com.grandsphere.fiche.data.remote.tastedive.TasteDiveApi
import com.grandsphere.fiche.data.remote.tmdb.TmdbApi
import com.grandsphere.fiche.data.remote.tvmaze.TvMazeApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TvMazeRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TmdbRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpenLibraryRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class JikanRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MalRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RawgRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TasteDiveRetrofit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().setPrettyPrinting().create()

    @Provides
    @Singleton
    fun provideOkHttp(@ApplicationContext context: Context): OkHttpClient {
        val cacheDir = File(context.cacheDir, "http_cache")
        val cache = Cache(cacheDir, 20L * 1024 * 1024) // 20 MB

        /** Force-cache interceptor: if server sends no cache headers and the
         *  response is a success, cache it for up to 5 minutes. */
        val cacheInterceptor = Interceptor { chain ->
            val response: Response = chain.proceed(chain.request())
            if (response.isSuccessful && response.cacheControl.noCache.not() &&
                response.header("Cache-Control") == null
            ) {
                response.newBuilder()
                    .header("Cache-Control", "public, max-age=300") // 5 min
                    .build()
            } else response
        }

        val builder = OkHttpClient.Builder()
            .cache(cache)
            .addNetworkInterceptor(cacheInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)

        if (com.grandsphere.fiche.BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(logging)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FicheDatabase {
        return Room.databaseBuilder(context, FicheDatabase::class.java, "fiche.db")
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16
            )
            .build()
    }

    @Provides fun provideTitleDao(db: FicheDatabase) = db.titleDao()
    @Provides fun provideSeasonDao(db: FicheDatabase) = db.seasonDao()
    @Provides fun provideEpisodeDao(db: FicheDatabase) = db.episodeDao()
    @Provides fun provideWatchlistDao(db: FicheDatabase) = db.watchlistDao()
    @Provides fun provideBanDao(db: FicheDatabase) = db.banDao()
    @Provides fun provideDislikeDao(db: FicheDatabase) = db.dislikeDao()
    @Provides fun provideRelatedDao(db: FicheDatabase) = db.relatedDao()
    @Provides fun provideOverviewStatsDao(db: FicheDatabase) = db.overviewStatsDao()
    @Provides fun provideInterestNotesDao(db: FicheDatabase) = db.interestNotesDao()
    @Provides fun provideDlcDao(db: FicheDatabase) = db.dlcDao()
    @Provides fun provideApiKeyDao(db: FicheDatabase) = db.apiKeyDao()
    @Provides fun provideLibraryPrefsDao(db: FicheDatabase) = db.libraryPrefsDao()

    @Provides
    @Singleton
    @TvMazeRetrofit
    fun provideTvMazeRetrofit(client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.tvmaze.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    @TmdbRetrofit
    fun provideTmdbRetrofit(client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    @OpenLibraryRetrofit
    fun provideOpenLibraryRetrofit(client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://openlibrary.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    @JikanRetrofit
    fun provideJikanRetrofit(client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.jikan.moe/v4/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    @MalRetrofit
    fun provideMalRetrofit(client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.myanimelist.net/v2/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    @RawgRetrofit
    fun provideRawgRetrofit(client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.rawg.io/api/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    @TasteDiveRetrofit
    fun provideTasteDiveRetrofit(gson: Gson): Retrofit {
        val tasteDiveClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Fiche/1.0 (Android)")
                        .build()
                )
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl("https://tastedive.com/")
            .client(tasteDiveClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE titles ADD COLUMN author TEXT")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE titles ADD COLUMN runtimeMinutes INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE episodes ADD COLUMN runtimeMinutes INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE titles ADD COLUMN pageCount INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE titles ADD COLUMN watchedMinutes INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE titles ADD COLUMN totalMinutes INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS achievements (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    titleId INTEGER NOT NULL,
                    remoteId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT,
                    unlocked INTEGER NOT NULL,
                    position INTEGER NOT NULL,
                    FOREIGN KEY(titleId) REFERENCES titles(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_achievements_titleId ON achievements(titleId)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_achievements_titleId_remoteId ON achievements(titleId, remoteId)")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE titles ADD COLUMN displayTitle TEXT")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS achievements")
            db.execSQL("ALTER TABLE related_titles ADD COLUMN watched INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS dlcs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    titleId INTEGER NOT NULL,
                    remoteId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    released TEXT,
                    completed INTEGER NOT NULL,
                    position INTEGER NOT NULL,
                    FOREIGN KEY(titleId) REFERENCES titles(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_dlcs_titleId ON dlcs(titleId)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_dlcs_titleId_remoteId ON dlcs(titleId, remoteId)")
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE titles ADD COLUMN catalogUrl TEXT")
            db.execSQL("ALTER TABLE related_titles ADD COLUMN runtimeMinutes INTEGER NOT NULL DEFAULT 0")
            db.execSQL("DELETE FROM related_titles WHERE ownerMediaType IN ('BOOK', 'GAME')")
        }
    }

    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE titles ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE titles ADD COLUMN countSequelsInCompletion INTEGER NOT NULL DEFAULT 1")
        }
    }

    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE titles ADD COLUMN tracked INTEGER NOT NULL DEFAULT 1")
            db.execSQL("UPDATE titles SET tracked = 0 WHERE hidden = 1")
        }
    }

    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE dlcs ADD COLUMN posterUrl TEXT")
            db.execSQL(
                """
                UPDATE titles SET
                    totalUnits = 1,
                    watchedUnits = CASE WHEN completed = 1 THEN 1 ELSE 0 END
                WHERE mediaType = 'GAME'
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE overview_stats ADD COLUMN inProgress INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE overview_stats ADD COLUMN notStarted INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE overview_stats ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE overview_stats ADD COLUMN totalEpisodes INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE overview_stats ADD COLUMN watchedEpisodes INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE overview_stats ADD COLUMN averageRating REAL")
            db.execSQL("ALTER TABLE overview_stats ADD COLUMN topCreators TEXT NOT NULL DEFAULT ''")
        }
    }

    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE titles ADD COLUMN busyLoading INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE seasons ADD COLUMN notInterested INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE overview_stats ADD COLUMN favouriteTitle TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE overview_stats ADD COLUMN genresByHours TEXT NOT NULL DEFAULT ''")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS banlist_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    mediaType TEXT NOT NULL,
                    title TEXT NOT NULL,
                    year INTEGER,
                    url TEXT NOT NULL,
                    addedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO banlist_new (id, mediaType, title, year, url, addedAt)
                SELECT id, mediaType, title, year,
                       CASE
                         WHEN remoteId IS NOT NULL AND remoteId != '' THEN 'remote:' || remoteId
                         ELSE coalesce(title, '') || '|' || coalesce(year, '')
                       END,
                       addedAt
                FROM banlist
                """.trimIndent()
            )
            db.execSQL("DROP TABLE banlist")
            db.execSQL("ALTER TABLE banlist_new RENAME TO banlist")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_banlist_mediaType_url ON banlist(mediaType, url)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS dislike_list (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    mediaType TEXT NOT NULL,
                    title TEXT NOT NULL,
                    year INTEGER,
                    url TEXT NOT NULL,
                    addedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_dislike_list_mediaType_url ON dislike_list(mediaType, url)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS api_keys (
                    provider TEXT NOT NULL PRIMARY KEY,
                    apiKey TEXT NOT NULL
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS library_prefs (
                    id INTEGER NOT NULL PRIMARY KEY,
                    sortsCsv TEXT NOT NULL,
                    showHidden INTEGER NOT NULL,
                    hideCompleted INTEGER NOT NULL,
                    hideIncomplete INTEGER NOT NULL,
                    storeImagesLocally INTEGER NOT NULL,
                    showCategoryTabs INTEGER NOT NULL,
                    seriesApi TEXT NOT NULL,
                    moviesApi TEXT NOT NULL,
                    animeApi TEXT NOT NULL,
                    gamesApi TEXT NOT NULL,
                    booksApi TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO library_prefs (
                    id, sortsCsv, showHidden, hideCompleted, hideIncomplete,
                    storeImagesLocally, showCategoryTabs,
                    seriesApi, moviesApi, animeApi, gamesApi, booksApi
                ) VALUES (
                    1, 'RATING', 0, 0, 0, 1, 1,
                    'TVMAZE', 'TMDB', 'TVMAZE', 'RAWG', 'OPEN_LIBRARY'
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE titles ADD COLUMN notInterested INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE titles ADD COLUMN recommended INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE titles ADD COLUMN localPosterPath TEXT")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS overview_stats (
                    mediaType TEXT NOT NULL PRIMARY KEY,
                    total INTEGER NOT NULL,
                    completed INTEGER NOT NULL,
                    watchedMinutes INTEGER NOT NULL,
                    pagesRead INTEGER NOT NULL,
                    favouriteGenres TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS interest_notes (
                    mediaType TEXT NOT NULL PRIMARY KEY,
                    body TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    @Provides
    @Singleton
    fun provideTvMazeApi(@TvMazeRetrofit retrofit: Retrofit): TvMazeApi =
        retrofit.create(TvMazeApi::class.java)

    @Provides
    @Singleton
    fun provideTmdbApi(@TmdbRetrofit retrofit: Retrofit): TmdbApi =
        retrofit.create(TmdbApi::class.java)

    @Provides
    @Singleton
    fun provideOpenLibraryApi(@OpenLibraryRetrofit retrofit: Retrofit): OpenLibraryApi =
        retrofit.create(OpenLibraryApi::class.java)

    @Provides
    @Singleton
    fun provideJikanApi(@JikanRetrofit retrofit: Retrofit): JikanApi =
        retrofit.create(JikanApi::class.java)

    @Provides
    @Singleton
    fun provideMalApi(@MalRetrofit retrofit: Retrofit): MalApi =
        retrofit.create(MalApi::class.java)

    @Provides
    @Singleton
    fun provideRawgApi(@RawgRetrofit retrofit: Retrofit): RawgApi =
        retrofit.create(RawgApi::class.java)

    @Provides
    @Singleton
    fun provideTasteDiveApi(@TasteDiveRetrofit retrofit: Retrofit): TasteDiveApi =
        retrofit.create(TasteDiveApi::class.java)

    // --- Catalog providers (selected per MediaType via CatalogRouter / library_prefs) ---

    @Provides
    @Singleton
    fun provideTvMazeSeriesCatalog(
        tvMaze: TvMazeApi,
        tmdb: TmdbApi,
        prefs: UserPreferencesRepository,
        apiKeys: ApiKeyRepository
    ): TvMazeSeriesCatalog = TvMazeSeriesCatalog(tvMaze, tmdb, prefs, apiKeys)

    @Provides
    @Singleton
    fun provideTmdbSeriesCatalog(
        tmdb: TmdbApi,
        apiKeys: ApiKeyRepository
    ): TmdbSeriesCatalog = TmdbSeriesCatalog(tmdb, apiKeys)

    @Provides
    @Singleton
    fun provideTmdbMoviesCatalog(
        tmdb: TmdbApi,
        apiKeys: ApiKeyRepository
    ): TmdbMoviesCatalog = TmdbMoviesCatalog(tmdb, apiKeys)

    @Provides
    @Singleton
    fun provideTvMazeAnimeCatalog(
        tvMaze: TvMazeApi,
        tmdb: TmdbApi,
        prefs: UserPreferencesRepository,
        apiKeys: ApiKeyRepository
    ): TvMazeAnimeCatalog = TvMazeAnimeCatalog(tvMaze, tmdb, prefs, apiKeys)

    @Provides
    @Singleton
    fun provideTmdbAnimeCatalog(
        tmdb: TmdbApi,
        apiKeys: ApiKeyRepository
    ): TmdbAnimeCatalog = TmdbAnimeCatalog(tmdb, apiKeys)

    @Provides
    @Singleton
    fun provideJikanAnimeCatalog(jikan: JikanApi): JikanAnimeCatalog = JikanAnimeCatalog(jikan)

    @Provides
    @Singleton
    fun provideMalAnimeCatalog(
        mal: MalApi,
        apiKeys: ApiKeyRepository
    ): MalAnimeCatalog = MalAnimeCatalog(mal, apiKeys)

    @Provides
    @Singleton
    fun provideRawgGamesCatalog(
        rawg: RawgApi,
        apiKeys: ApiKeyRepository
    ): RawgGamesCatalog = RawgGamesCatalog(rawg, apiKeys)

    @Provides
    @Singleton
    fun provideOpenLibraryBooksCatalog(openLibrary: OpenLibraryApi): OpenLibraryBooksCatalog =
        OpenLibraryBooksCatalog(openLibrary)

    @Provides
    @Singleton
    fun provideCatalogRouter(
        prefsDao: LibraryPrefsDao,
        tvMazeSeries: TvMazeSeriesCatalog,
        tmdbSeries: TmdbSeriesCatalog,
        tmdbMovies: TmdbMoviesCatalog,
        tvMazeAnime: TvMazeAnimeCatalog,
        tmdbAnime: TmdbAnimeCatalog,
        jikanAnime: JikanAnimeCatalog,
        malAnime: MalAnimeCatalog,
        rawgGames: RawgGamesCatalog,
        openLibraryBooks: OpenLibraryBooksCatalog
    ): CatalogRouter = CatalogRouter(
        prefsDao = prefsDao,
        tvMazeSeries = tvMazeSeries,
        tmdbSeries = tmdbSeries,
        tmdbMovies = tmdbMovies,
        tvMazeAnime = tvMazeAnime,
        tmdbAnime = tmdbAnime,
        jikanAnime = jikanAnime,
        malAnime = malAnime,
        rawgGames = rawgGames,
        openLibraryBooks = openLibraryBooks
    )

    @Provides fun provideDefaultSeriesCatalog(catalog: TvMazeSeriesCatalog): SeriesCatalog = catalog
    @Provides fun provideDefaultMoviesCatalog(catalog: TmdbMoviesCatalog): MoviesCatalog = catalog
    @Provides fun provideDefaultAnimeCatalog(catalog: TvMazeAnimeCatalog): AnimeCatalog = catalog
    @Provides fun provideDefaultGamesCatalog(catalog: RawgGamesCatalog): GamesCatalog = catalog
    @Provides fun provideDefaultBooksCatalog(catalog: OpenLibraryBooksCatalog): BooksCatalog = catalog
}
