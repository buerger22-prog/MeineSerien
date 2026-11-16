package de.meineserien.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesDao {
    @Transaction
    @Query("SELECT * FROM series ORDER BY title COLLATE NOCASE ASC")
    fun observeAllSeriesWithEpisodes(): Flow<List<SeriesWithEpisodes>>

    @Transaction
    @Query("SELECT * FROM series WHERE id = :seriesId")
    fun observeSeriesDetail(seriesId: Int): Flow<SeriesWithEpisodes?>

    @Transaction
    @Query("SELECT * FROM series")
    suspend fun getAllSeriesOnce(): List<SeriesWithEpisodes>

    @Query("SELECT EXISTS(SELECT 1 FROM series WHERE id = :seriesId)")
    suspend fun exists(seriesId: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeries(series: SeriesEntity)

    @Update
    suspend fun updateSeries(series: SeriesEntity)

    @Query("DELETE FROM series WHERE id = :seriesId")
    suspend fun deleteSeriesById(seriesId: Int)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEpisodesIgnore(episodes: List<EpisodeEntity>)

    @Update
    suspend fun updateEpisode(episode: EpisodeEntity)

    @Query("UPDATE episodes SET isWatched = :watched, watchedTimestamp = :timestamp WHERE seriesId = :seriesId AND (season < :season OR (season = :season AND number <= :number))")
    suspend fun markUpToEpisodeWatched(seriesId: Int, season: Int, number: Int, watched: Boolean, timestamp: Long?)

    @Query("UPDATE series SET hasNewContentBadge = 0 WHERE id = :seriesId")
    suspend fun clearNewBadge(seriesId: Int)
}
