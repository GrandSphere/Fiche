package com.grandsphere.fiche.data.remote.rawg

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface RawgApi {
    @GET("games")
    suspend fun search(
        @Query("key") key: String,
        @Query("search") search: String,
        @Query("page_size") pageSize: Int = 20
    ): RawgPaged<RawgGame>

    @GET("games/{id}")
    suspend fun game(
        @Path("id") id: String,
        @Query("key") key: String
    ): RawgGameDetail

    @GET("games/{id}/additions")
    suspend fun additions(
        @Path("id") id: String,
        @Query("key") key: String,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 40
    ): RawgPaged<RawgGame>

    @GET("games/{id}/stores")
    suspend fun stores(
        @Path("id") id: String,
        @Query("key") key: String,
        @Query("page_size") pageSize: Int = 20
    ): RawgPaged<RawgStoreLink>

    @GET("games")
    suspend fun discover(
        @Query("key") key: String,
        @Query("genres") genres: String? = null,
        @Query("dates") dates: String? = null,
        @Query("developers") developers: String? = null,
        @Query("ordering") ordering: String = "-released",
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20
    ): RawgPaged<RawgGame>

    @GET("developers")
    suspend fun searchDevelopers(
        @Query("key") key: String,
        @Query("search") search: String,
        @Query("page_size") pageSize: Int = 10
    ): RawgPaged<RawgNamed>

    @GET("genres")
    suspend fun genres(@Query("key") key: String): RawgPaged<RawgNamed>
}

data class RawgPaged<T>(
    val count: Int = 0,
    val next: String? = null,
    val results: List<T> = emptyList()
)

data class RawgGame(
    val id: Long,
    val slug: String? = null,
    val name: String? = null,
    val released: String? = null,
    val background_image: String? = null,
    val genres: List<RawgNamed>? = null
)

data class RawgGameDetail(
    val id: Long,
    val slug: String? = null,
    val name: String? = null,
    val released: String? = null,
    val background_image: String? = null,
    val description_raw: String? = null,
    val genres: List<RawgNamed>? = null,
    val developers: List<RawgNamed>? = null,
    val playtime: Int? = null
)

data class RawgStoreLink(
    val id: Long? = null,
    val game_id: Long? = null,
    val store_id: Int? = null,
    val url: String? = null
)

data class RawgNamed(
    val id: Long? = null,
    val name: String? = null,
    val slug: String? = null
)
