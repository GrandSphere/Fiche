package com.grandsphere.fiche.data.remote.jikan

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface JikanApi {
    @GET("anime")
    suspend fun searchAnime(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20,
        @Query("page") page: Int = 1,
        @Query("sfw") sfw: Boolean = true
    ): JikanSearchResponse

    @GET("anime/{id}/full")
    suspend fun animeFull(@Path("id") id: String): JikanFullResponse

    @GET("anime/{id}/episodes")
    suspend fun animeEpisodes(
        @Path("id") id: String,
        @Query("page") page: Int = 1
    ): JikanEpisodesResponse

    @GET("top/anime")
    suspend fun topAnime(
        @Query("page") page: Int = 1,
        @Query("filter") filter: String? = null,
        @Query("limit") limit: Int = 20
    ): JikanSearchResponse

    @GET("seasons/now")
    suspend fun seasonNow(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): JikanSearchResponse
}

data class JikanSearchResponse(
    val data: List<JikanAnime> = emptyList(),
    val pagination: JikanPagination? = null
)

data class JikanFullResponse(val data: JikanAnime? = null)

data class JikanEpisodesResponse(
    val data: List<JikanEpisode> = emptyList(),
    val pagination: JikanPagination? = null
)

data class JikanPagination(
    val last_visible_page: Int? = null,
    val has_next_page: Boolean? = null
)

data class JikanAnime(
    val mal_id: Long? = null,
    val title: String? = null,
    val title_english: String? = null,
    val year: Int? = null,
    val type: String? = null,
    val synopsis: String? = null,
    val status: String? = null,
    val episodes: Int? = null,
    val duration: String? = null,
    val images: JikanImages? = null,
    val genres: List<JikanNamed>? = null,
    val aired: JikanAired? = null,
    val external: List<JikanExternal>? = null,
    val relations: List<JikanRelation>? = null
)

data class JikanImages(val jpg: JikanImageUrls? = null, val webp: JikanImageUrls? = null)
data class JikanImageUrls(
    val image_url: String? = null,
    val large_image_url: String? = null
)

data class JikanNamed(val mal_id: Long? = null, val name: String? = null)
data class JikanAired(val from: String? = null, val to: String? = null)
data class JikanExternal(val name: String? = null, val url: String? = null)
data class JikanRelation(
    val relation: String? = null,
    val entry: List<JikanRelationEntry>? = null
)
data class JikanRelationEntry(
    val mal_id: Long? = null,
    val type: String? = null,
    val name: String? = null
)

data class JikanEpisode(
    val mal_id: Long? = null,
    val title: String? = null,
    val aired: String? = null,
    val filler: Boolean? = null,
    val recap: Boolean? = null
)
