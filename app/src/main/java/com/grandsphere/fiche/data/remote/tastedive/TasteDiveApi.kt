package com.grandsphere.fiche.data.remote.tastedive

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface TasteDiveApi {
    @GET("api/similar")
    suspend fun similar(
        @Query("q") query: String,
        @Query("type") type: String,
        @Query("k") key: String,
        @Query("limit") limit: Int = 20,
        @Query("info") info: Int = 0
    ): TasteDiveResponse
}

data class TasteDiveResponse(
    @SerializedName("similar") val similar: TasteDiveSimilar? = null,
    @SerializedName("error") val error: String? = null
)

data class TasteDiveSimilar(
    @SerializedName("info") val info: List<TasteDiveItem> = emptyList(),
    @SerializedName("results") val results: List<TasteDiveItem> = emptyList()
)

data class TasteDiveItem(
    @SerializedName("name") val name: String? = null,
    @SerializedName("type") val type: String? = null
)
