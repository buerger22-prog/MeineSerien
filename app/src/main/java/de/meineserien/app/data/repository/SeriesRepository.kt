package de.meineserien.app.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.meineserien.app.data.local.*
import de.meineserien.app.data.remote.TvMazeApi
import de.meineserien.app.data.remote.TvMazeShow
import kotlinx.coroutines.flow.Flow

data class BackupPayload(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val seriesList: List<SeriesWithEpisodes>
)

class SeriesRepository(
    private val dao: SeriesDao,
    private val api: TvMazeApi
) {
    private val gson = Gson()

    fun observeAllSeries(): Flow<List<SeriesWithEpisodes>> = dao.observeAllSeriesWithEpisodes()
    fun observeSeriesDetail(id: Int): Flow<SeriesWithEpisodes?> = dao.observeSeriesDetail(id)

    suspend fun searchRemoteSeries(query: String): Result<List<TvMazeShow>> {
        return try {
            val results = api.searchShows(query).map { it.show }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addSeriesFromShow(show: TvMazeShow, streamingServices: List<String>): Result<Boolean> {
        return try {
            if (dao.exists(show.id)) {
                return Result.success(false)
            }
            val episodes = api.getEpisodes(show.id)
                .filter { it.number != null && it.season > 0 }
                .map { ep ->
                    EpisodeEntity(
                        id = ep.id,
                        seriesId = show.id,
                        season = ep.season,
                        number = ep.number ?: 0,
                        title = ep.name.ifBlank { "Episode ${ep.number}" },
                        airDate = ep.airdate
                    )
                }

            val networkName = show.network?.name ?: show.webChannel?.name ?: "Unbekannt"
            val year = show.premiered?.take(4) ?: "—"
            val cleanSummary = show.summary?.replace(Regex("<.*?>"), "")?.trim() ?: ""

            val entity = SeriesEntity(
                id = show.id,
                title = show.name,
                premiereYear = year,
                network = networkName,
                coverUrl = show.image?.original ?: show.image?.medium,
                summary = cleanSummary,
                status = api.getShowStatus(show.id) ?: show.status ?: "Unbekannt",
                streamingServices = streamingServices.joinToString(",")
            )

            dao.insertSeries(entity)
            dao.insertEpisodesIgnore(episodes)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateStreamingServices(series: SeriesEntity, services: List<String>) {
        dao.updateSeries(series.copy(streamingServices = services.joinToString(",")))
    }

    suspend fun toggleEpisodeWatched(episode: EpisodeEntity) {
        val nowWatched = !episode.isWatched
        dao.updateEpisode(
            episode.copy(
                isWatched = nowWatched,
                watchedTimestamp = if (nowWatched) System.currentTimeMillis() else null
            )
        )
    }

    suspend fun setProgressUpTo(seriesId: Int, season: Int, number: Int) {
        dao.markUpToEpisodeWatched(
            seriesId = seriesId,
            season = season,
            number = number,
            watched = true,
            timestamp = System.currentTimeMillis()
        )
    }

    suspend fun deleteSeries(seriesId: Int) {
        dao.deleteSeriesById(seriesId)
    }

    suspend fun clearNewBadge(seriesId: Int) {
        dao.clearNewBadge(seriesId)
    }

    suspend fun syncAllSeriesForUpdates(): List<String> {
        val notifications = mutableListOf<String>()
        val allLocal = dao.getAllSeriesOnce()

        for (item in allLocal) {
            try {
                val latestStatus = api.getShowStatus(item.series.id)
                if (!latestStatus.isNullOrBlank() && latestStatus != item.series.status) {
                    dao.updateSeries(item.series.copy(status = latestStatus))
                }
                val remoteEpisodes = api.getEpisodes(item.series.id)
                    .filter { it.number != null && it.season > 0 }

                val existingIds = item.episodes.map { it.id }.toSet()
                val existingSeasons = item.episodes.map { it.season }.toSet()

                val newOnes = remoteEpisodes.filter { it.id !in existingIds }
                if (newOnes.isNotEmpty()) {
                    val mapped = newOnes.map { ep ->
                        EpisodeEntity(
                            id = ep.id,
                            seriesId = item.series.id,
                            season = ep.season,
                            number = ep.number ?: 0,
                            title = ep.name.ifBlank { "Episode ${ep.number}" },
                            airDate = ep.airdate
                        )
                    }
                    dao.insertEpisodesIgnore(mapped)
                    dao.updateSeries(
                        item.series.copy(
                            hasNewContentBadge = true,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )

                    val newSeasons = mapped.map { it.season }.distinct().filter { it !in existingSeasons }
                    if (newSeasons.isNotEmpty()) {
                        notifications.add("${item.series.title}: Staffel ${newSeasons.joinToString(", ")} angekündigt!")
                    } else {
                        notifications.add("${item.series.title}: ${mapped.size} neue Episode(n) verfügbar.")
                    }
                }
            } catch (_: Exception) {
            }
        }
        return notifications
    }

    suspend fun migrateAllSeriesToGermanTmdb(): Int {
        val allLocal = dao.getAllSeriesOnce()
        var migratedCount = 0

        for (item in allLocal) {
            try {
                val results = api.searchShows(item.series.title).map { it.show }
                if (results.isEmpty()) continue

                val bestMatch = results.firstOrNull {
                    it.premiered?.take(4) == item.series.premiereYear
                } ?: results.first()

                val remoteEpisodes = api.getEpisodes(bestMatch.id)
                    .filter { it.number != null && it.season > 0 }
                if (remoteEpisodes.isEmpty()) continue

                val watchedMap = item.episodes
                    .filter { it.isWatched }
                    .associate { (it.season to it.number) to it.watchedTimestamp }

                val latestStatus = api.getShowStatus(bestMatch.id) ?: item.series.status
                val detectedProviders = if (item.series.streamingServices.isBlank() || item.series.streamingServices == "Andere Anbieter") {
                    api.getGermanProviders(bestMatch.id).ifEmpty { listOf("Andere Anbieter") }.joinToString(",")
                } else {
                    item.series.streamingServices
                }

                val newSeries = SeriesEntity(
                    id = bestMatch.id,
                    title = bestMatch.name,
                    premiereYear = bestMatch.premiered?.take(4) ?: item.series.premiereYear,
                    network = item.series.network,
                    coverUrl = bestMatch.image?.original ?: bestMatch.image?.medium ?: item.series.coverUrl,
                    summary = bestMatch.summary?.replace(Regex("<.*?>"), "")?.trim().orEmpty().ifBlank { item.series.summary },
                    status = latestStatus,
                    streamingServices = detectedProviders,
                    hasNewContentBadge = item.series.hasNewContentBadge,
                    lastUpdated = System.currentTimeMillis()
                )

                val newEpisodes = remoteEpisodes.map { ep ->
                    val epNum = ep.number ?: 0
                    val key = ep.season to epNum
                    val wasWatched = key in watchedMap
                    EpisodeEntity(
                        id = ep.id,
                        seriesId = bestMatch.id,
                        season = ep.season,
                        number = epNum,
                        title = ep.name.ifBlank { "Folge $epNum" },
                        airDate = ep.airdate,
                        isWatched = wasWatched,
                        watchedTimestamp = if (wasWatched) watchedMap[key] else null
                    )
                }

                if (bestMatch.id != item.series.id) {
                    dao.deleteSeriesById(item.series.id)
                }
                dao.insertSeries(newSeries)
                dao.insertEpisodesIgnore(newEpisodes)
                newEpisodes.filter { it.isWatched }.forEach { dao.updateEpisode(it) }
                migratedCount++
            } catch (_: Exception) {
            }
        }
        return migratedCount
    }

    suspend fun exportBackupJson(): String {
        val data = dao.getAllSeriesOnce()
        return gson.toJson(BackupPayload(seriesList = data))
    }

    suspend fun importBackupJson(json: String): Result<Int> {
        return try {
            val type = object : TypeToken<BackupPayload>() {}.type
            val payload: BackupPayload = gson.fromJson(json, type)
            var count = 0
            for (item in payload.seriesList) {
                dao.insertSeries(item.series)
                dao.insertEpisodesIgnore(item.episodes)
                item.episodes.filter { it.isWatched }.forEach { dao.updateEpisode(it) }
                count++
            }
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
