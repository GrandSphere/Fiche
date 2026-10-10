package com.grandsphere.fiche.data.remote.tvmaze

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TvMazeApi {
    @GET("search/shows")
    suspend fun search(@Query("q") query: String): List<TvMazeSearchItem>

    @GET("lookup/shows")
    suspend fun lookupByImdb(@Query("imdb") imdb: String): TvMazeShow

    @GET("shows/{id}")
    suspend fun show(@Path("id") id: String): TvMazeShow

    @GET("shows/{id}/seasons")
    suspend fun seasons(@Path("id") id: String): List<TvMazeSeason>

    @GET("shows/{id}/episodes")
    suspend fun episodes(@Path("id") id: String): List<TvMazeEpisode>

    @GET("shows/{id}/episodebynumber")
    suspend fun episodeByNumber(
        @Path("id") id: String,
        @Query("season") season: Int,
        @Query("number") number: Int
    ): TvMazeEpisode

    @GET("schedule")
    suspend fun schedule(@Query("country") country: String = "US"): List<TvMazeScheduleItem>

    @GET("shows")
    suspend fun showsPage(@Query("page") page: Int): List<TvMazeShow>

    @GET("search/people")
    suspend fun searchPeople(@Query("q") query: String): List<TvMazePeopleSearch>

    @GET("people/{id}/castcredits")
    suspend fun personCast(
        @Path("id") id: String,
        @Query("embed") embed: String = "show"
    ): List<TvMazeCastCredit>

    @GET("people/{id}/crewcredits")
    suspend fun personCrew(
        @Path("id") id: String,
        @Query("embed") embed: String = "show"
    ): List<TvMazeCastCredit>
}

data class TvMazeSearchItem(val score: Double? = null, val show: TvMazeShow)

data class TvMazeShow(
    val id: Long,
    val name: String,
    val genres: List<String>? = null,
    val status: String? = null,
    val premiered: String? = null,
    val ended: String? = null,
    val summary: String? = null,
    val image: TvMazeImage? = null,
    val externals: TvMazeExternals? = null,
    val runtime: Int? = null,
    val averageRuntime: Int? = null
)

data class TvMazeImage(val medium: String? = null, val original: String? = null)
data class TvMazeExternals(val imdb: String? = null)
data class TvMazeSeason(
    val id: Long,
    val number: Int? = null,
    val episodeOrder: Int? = null,
    val premiereDate: String? = null,
    val endDate: String? = null
)
data class TvMazeEpisode(
    val id: Long,
    val season: Int? = null,
    val number: Int? = null,
    val airdate: String? = null,
    val runtime: Int? = null,
    val url: String? = null
)
data class TvMazeScheduleItem(val show: TvMazeShow? = null)
data class TvMazePeopleSearch(val person: TvMazePerson)
data class TvMazePerson(val id: Long, val name: String? = null)
data class TvMazeCastCredit(val _embedded: TvMazeEmbeddedShow? = null)
data class TvMazeEmbeddedShow(val show: TvMazeShow? = null)
