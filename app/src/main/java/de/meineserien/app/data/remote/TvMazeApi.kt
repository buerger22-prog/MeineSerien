package de.meineserien.app.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class TvMazeSearchResult(
    val score: Double,
    val show: TvMazeShow
)

data class TvMazeShow(
    val id: Int,
    val name: String,
    val premiered: String?,
    val status: String?,
    val summary: String?,
    val network: TvMazeNetwork?,
    val webChannel: TvMazeNetwork?,
    val image: TvMazeImage?
)

data class TvMazeNetwork(
    val name: String
)

data class TvMazeImage(
    val medium: String?,
    val original: String?
)

data class TvMazeEpisode(
    val id: Int,
    val name: String,
    val season: Int,
    val number: Int?,
    val airdate: String?
)

interface TvMazeApi {
    @GET("search/shows")
    suspend fun searchShows(@Query("q") query: String): List<TvMazeSearchResult>

    @GET("shows/{id}/episodes")
    suspend fun getEpisodes(@Path("id") showId: Int): List<TvMazeEpisode>

    companion object {
        fun create(): TvMazeApi {
            return Retrofit.Builder()
                .baseUrl("https://api.tvmaze.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(TvMazeApi::class.java)
        }
    }
}
