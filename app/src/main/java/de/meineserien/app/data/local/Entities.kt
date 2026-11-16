package de.meineserien.app.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "series")
data class SeriesEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val premiereYear: String,
    val network: String,
    val coverUrl: String?,
    val summary: String,
    val status: String,
    val streamingServices: String,
    val hasNewContentBadge: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("seriesId")]
)
data class EpisodeEntity(
    @PrimaryKey val id: Int,
    val seriesId: Int,
    val season: Int,
    val number: Int,
    val title: String,
    val airDate: String?,
    val isWatched: Boolean = false,
    val watchedTimestamp: Long? = null
)

data class SeriesWithEpisodes(
    @Embedded val series: SeriesEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "seriesId"
    )
    val episodes: List<EpisodeEntity>
) {
    val totalEpisodes: Int get() = episodes.size
    val watchedEpisodes: Int get() = episodes.count { it.isWatched }
    val remainingEpisodes: Int get() = totalEpisodes - watchedEpisodes
    val progressPercent: Float
        get() = if (totalEpisodes == 0) 0f else watchedEpisodes.toFloat() / totalEpisodes.toFloat()

    val lastWatchedEpisode: EpisodeEntity?
        get() = episodes
            .filter { it.isWatched }
            .maxWithOrNull(compareBy({ it.watchedTimestamp ?: 0L }, { it.season }, { it.number }))
}
