package com.grandsphere.fiche.data.remote.openlibrary

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OpenLibraryApi {
    @GET("search.json")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): OpenLibrarySearchResponse

    @GET("search.json")
    suspend fun searchAdvanced(
        @Query("q") query: String,
        @Query("subject") subject: String? = null,
        @Query("author") author: String? = null,
        @Query("publisher") publisher: String? = null,
        @Query("first_publish_year") year: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("page") page: Int = 1,
        @Query("sort") sort: String? = null
    ): OpenLibrarySearchResponse

    @GET("works/{id}.json")
    suspend fun work(@Path("id") id: String): OpenLibraryWork

    @GET("books/{id}.json")
    suspend fun edition(@Path("id") id: String): OpenLibraryEdition
}

data class OpenLibrarySearchResponse(val docs: List<OpenLibraryDoc> = emptyList())
data class OpenLibraryDoc(
    val key: String? = null,
    val title: String? = null,
    val author_name: List<String>? = null,
    val first_publish_year: Int? = null,
    val cover_i: Long? = null,
    val subject: List<String>? = null,
    val number_of_pages_median: Int? = null
)
data class OpenLibraryWork(
    val title: String? = null,
    val description: Any? = null,
    val subjects: List<String>? = null
)
data class OpenLibraryEdition(val works: List<OpenLibraryWorkRef>? = null)
data class OpenLibraryWorkRef(val key: String? = null)

fun openLibraryCover(coverId: Long?): String? =
    coverId?.let { "https://covers.openlibrary.org/b/id/$it-L.jpg" }

fun workIdFromKey(key: String?): String = key?.substringAfterLast("/") ?: ""
