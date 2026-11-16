package de.meineserien.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class TvMazeSearchResult(
    val score: Double = 1.0,
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

// Interne TMDB-Modelle (de-DE)
private data class TmdbSearchResponse(
    val results: List<TmdbShowItem> = emptyList()
)

private data class TmdbShowItem(
    val id: Int,
    val name: String?,
    @SerializedName("original_name") val originalName: String?,
    @SerializedName("first_air_date") val firstAirDate: String?,
    val overview: String?,
    @SerializedName("poster_path") val posterPath: String?
)

private data class TmdbShowDetail(
    val id: Int,
    val name: String?,
    @SerializedName("first_air_date") val firstAirDate: String?,
    val status: String?,
    val overview: String?,
    val networks: List<TmdbNetworkItem> = emptyList(),
    val seasons: List<TmdbSeasonSummary> = emptyList()
)

private data class TmdbNetworkItem(
    val name: String
)

private data class TmdbSeasonSummary(
    @SerializedName("season_number") val seasonNumber: Int
)

private data class TmdbSeasonDetail(
    val episodes: List<TmdbEpisodeItem> = emptyList()
)

private data class TmdbEpisodeItem(
    val id: Int,
    val name: String?,
    @SerializedName("season_number") val seasonNumber: Int,
    @SerializedName("episode_number") val episodeNumber: Int?,
    @SerializedName("air_date") val airDate: String?
)

private interface TmdbRetrofitService {
    @GET("search/tv")
    suspend fun searchTv(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "de-DE"
    ): TmdbSearchResponse

    @GET("tv/{tv_id}")
    suspend fun getTvDetail(
        @Path("tv_id") tvId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "de-DE"
    ): TmdbShowDetail

    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getSeasonDetail(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "de-DE"
    ): TmdbSeasonDetail
}

class TvMazeApi private constructor(private val service: TmdbRetrofitService) {
    private val apiKey = "140ea5aae3b88271f7fb2e3815d4afae"

    suspend fun searchShows(query: String): List<TvMazeSearchResult> {
        val response = service.searchTv(apiKey = apiKey, query = query)
        return response.results.map { item ->
            val posterMedium = item.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }
            val posterOriginal = item.posterPath?.let { "https://image.tmdb.org/t/p/w780$it" }
            TvMazeSearchResult(
                score = 1.0,
                show = TvMazeShow(
                    id = item.id,
                    name = item.name?.takeIf { it.isNotBlank() } ?: item.originalName ?: "Unbekannt",
                    premiered = item.firstAirDate,
                    status = null,
                    summary = item.overview,
                    network = TvMazeNetwork("TMDB"),
                    webChannel = null,
                    image = TvMazeImage(medium = posterMedium, original = posterOriginal)
                )
            )
        }
    }

    suspend fun getEpisodes(showId: Int): List<TvMazeEpisode> {
        val detail = service.getTvDetail(tvId = showId, apiKey = apiKey)
        val allEpisodes = mutableListOf<TvMazeEpisode>()

        for (season in detail.seasons.filter { it.seasonNumber > 0 }.sortedBy { it.seasonNumber }) {
            try {
                val seasonData = service.getSeasonDetail(
                    tvId = showId,
                    seasonNumber = season.seasonNumber,
                    apiKey = apiKey
                )
                seasonData.episodes.forEach { ep ->
                    allEpisodes.add(
                        TvMazeEpisode(
                            id = ep.id,
                            name = ep.name?.takeIf { it.isNotBlank() } ?: "Folge ${ep.episodeNumber ?: 0}",
                            season = ep.seasonNumber,
                            number = ep.episodeNumber,
                            airdate = ep.airDate
                        )
                    )
                }
            } catch (_: Exception) {
                // Einzelne noch leere/angekündigte Staffeln überspringen
            }
        }
        return allEpisodes
    }

    companion object {
        fun create(): TvMazeApi {
            val service = Retrofit.Builder()
                .baseUrl("https://api.themoviedb.org/3/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(TmdbRetrofitService::class.java)
            return TvMazeApi(service)
        }
    }
}
