package com.grandsphere.fiche.data.remote.mal

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Official MyAnimeList API v2, **read-only**.
 * Client ID in `X-MAL-CLIENT-ID` is a public app identifier, not a user login.
 * Do not add OAuth, `/users/`, `my_list_status`, or any POST/PUT/PATCH/DELETE.
 */

private const val LIST_FIELDS =
    "id,title,alternative_titles,start_date,synopsis,genres,main_picture,status,num_episodes,average_episode_duration,media_type"
private const val DETAIL_FIELDS =
    "$LIST_FIELDS,end_date,related_anime"

interface MalApi {
    @GET("anime")
    suspend fun search(
        @Header("X-MAL-CLIENT-ID") clientId: String,
        @Query("q") query: String,
        @Query("limit") limit: Int = 10,
        @Query("offset") offset: Int = 0,
        @Query("fields") fields: String = LIST_FIELDS
    ): MalListResponse

    @GET("anime/{id}")
    suspend fun anime(
        @Header("X-MAL-CLIENT-ID") clientId: String,
        @Path("id") id: String,
        @Query("fields") fields: String = DETAIL_FIELDS
    ): MalAnime

    @GET("anime/ranking")
    suspend fun ranking(
        @Header("X-MAL-CLIENT-ID") clientId: String,
        @Query("ranking_type") rankingType: String = "all",
        @Query("limit") limit: Int = 10,
        @Query("offset") offset: Int = 0,
        @Query("fields") fields: String = LIST_FIELDS
    ): MalListResponse
}

data class MalListResponse(
    val data: List<MalNodeWrap> = emptyList(),
    val paging: MalPaging? = null
)

data class MalNodeWrap(val node: MalAnime? = null)

data class MalPaging(val next: String? = null)

data class MalAnime(
    val id: Long? = null,
    val title: String? = null,
    val alternative_titles: MalAltTitles? = null,
    val start_date: String? = null,
    val end_date: String? = null,
    val synopsis: String? = null,
    val genres: List<MalNamed>? = null,
    val main_picture: MalPicture? = null,
    val status: String? = null,
    val num_episodes: Int? = null,
    val average_episode_duration: Int? = null,
    val media_type: String? = null,
    val related_anime: List<MalRelated>? = null
)

data class MalAltTitles(val en: String? = null)
data class MalNamed(val name: String? = null)
data class MalPicture(val large: String? = null, val medium: String? = null)
data class MalRelated(
    val node: MalAnime? = null,
    val relation_type: String? = null
)
