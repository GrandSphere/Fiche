package com.grandsphere.fiche.data.remote.tmdb

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("search/movie")
    suspend fun searchMovies(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1
    ): TmdbPaged<TmdbMovie>

    @GET("search/tv")
    suspend fun searchTv(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1
    ): TmdbPaged<TmdbTvShow>

    @GET("movie/{id}")
    suspend fun movie(
        @Path("id") id: String,
        @Query("api_key") apiKey: String
    ): TmdbMovieDetail

    @GET("collection/{id}")
    suspend fun collection(
        @Path("id") id: String,
        @Query("api_key") apiKey: String
    ): TmdbCollection

    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("api_key") apiKey: String,
        @Query("with_genres") genres: String? = null,
        @Query("primary_release_date.gte") from: String? = null,
        @Query("primary_release_date.lte") to: String? = null,
        @Query("with_people") people: String? = null,
        @Query("with_crew") crew: String? = null,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1
    ): TmdbPaged<TmdbMovie>

    @GET("discover/tv")
    suspend fun discoverTv(
        @Query("api_key") apiKey: String,
        @Query("with_genres") genres: String? = null,
        @Query("first_air_date.gte") from: String? = null,
        @Query("first_air_date.lte") to: String? = null,
        @Query("with_origin_country") originCountry: String? = null,
        @Query("with_status") status: String? = null,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1
    ): TmdbPaged<TmdbTvShow>

    @GET("movie/now_playing")
    suspend fun nowPlaying(@Query("api_key") apiKey: String): TmdbPaged<TmdbMovie>

    @GET("movie/upcoming")
    suspend fun upcoming(@Query("api_key") apiKey: String): TmdbPaged<TmdbMovie>

    @GET("genre/movie/list")
    suspend fun movieGenres(@Query("api_key") apiKey: String): TmdbGenreList

    @GET("genre/tv/list")
    suspend fun tvGenres(@Query("api_key") apiKey: String): TmdbGenreList

    @GET("find/{id}")
    suspend fun find(
        @Path("id") id: String,
        @Query("api_key") apiKey: String,
        @Query("external_source") source: String = "imdb_id"
    ): TmdbFindResult

    @GET("tv/{id}")
    suspend fun tv(
        @Path("id") id: String,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") append: String? = "external_ids"
    ): TmdbTvDetail

    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun tvSeason(
        @Path("tv_id") tvId: String,
        @Path("season_number") seasonNumber: Int,
        @Query("api_key") apiKey: String
    ): TmdbSeasonDetail

    @GET("search/person")
    suspend fun searchPerson(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1
    ): TmdbPaged<TmdbPerson>
}

data class TmdbPaged<T>(
    val page: Int = 1,
    val total_pages: Int = 1,
    val results: List<T> = emptyList()
)
data class TmdbMovie(
    val id: Long,
    val title: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val release_date: String? = null,
    val genre_ids: List<Int>? = null
)
data class TmdbMovieDetail(
    val id: Long,
    val title: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val release_date: String? = null,
    val status: String? = null,
    val genres: List<TmdbGenre>? = null,
    val belongs_to_collection: TmdbCollectionRef? = null,
    val imdb_id: String? = null,
    val runtime: Int? = null
)
data class TmdbCollectionRef(val id: Long, val name: String? = null)
data class TmdbCollection(val id: Long, val name: String? = null, val parts: List<TmdbMovie>? = null)
data class TmdbGenre(val id: Int, val name: String)
data class TmdbGenreList(val genres: List<TmdbGenre> = emptyList())
data class TmdbFindResult(
    val movie_results: List<TmdbMovie>? = null,
    val tv_results: List<TmdbTvLite>? = null
)
data class TmdbTvLite(val id: Long)
data class TmdbTvShow(
    val id: Long,
    val name: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val first_air_date: String? = null,
    val genre_ids: List<Int>? = null,
    val origin_country: List<String>? = null
)
data class TmdbTvDetail(
    val id: Long,
    val name: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val first_air_date: String? = null,
    val last_air_date: String? = null,
    val status: String? = null,
    val genres: List<TmdbGenre>? = null,
    val episode_run_time: List<Int>? = null,
    val seasons: List<TmdbSeasonSummary>? = null,
    val external_ids: TmdbExternalIds? = null
)
data class TmdbExternalIds(val imdb_id: String? = null)
data class TmdbSeasonSummary(
    val season_number: Int? = null,
    val episode_count: Int? = null,
    val air_date: String? = null
)
data class TmdbSeasonDetail(
    val season_number: Int? = null,
    val episodes: List<TmdbEpisode>? = null
)
data class TmdbEpisode(
    val episode_number: Int? = null,
    val air_date: String? = null,
    val runtime: Int? = null
)
data class TmdbPerson(val id: Long, val name: String? = null)

fun tmdbPoster(path: String?): String? = path?.let { "https://image.tmdb.org/t/p/w500$it" }
