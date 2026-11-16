package de.meineserien.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.meineserien.app.data.local.*
import de.meineserien.app.data.remote.TvMazeApi
import de.meineserien.app.data.remote.TvMazeShow
import de.meineserien.app.data.repository.SeriesRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val STREAMING_PROVIDERS = listOf(
    "Netflix",
    "Amazon Prime Video",
    "Disney+",
    "WOW",
    "Apple TV+",
    "Paramount+",
    "RTL+",
    "Andere Anbieter"
)

fun formatGermanDate(isoDate: String?): String {
    if (isoDate.isNullOrBlank()) return "Termin unbekannt"
    return try {
        val parsed = LocalDate.parse(isoDate)
        parsed.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    } catch (_: Exception) {
        isoDate
    }
}

class SeriesViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = SeriesRepository(
        AppDatabase.getInstance(application).seriesDao(),
        TvMazeApi.create()
    )

    private val _localSearchQuery = MutableStateFlow("")
    val localSearchQuery: StateFlow<String> = _localSearchQuery.asStateFlow()

    private val _selectedProviderFilter = MutableStateFlow<String?>(null)
    val selectedProviderFilter: StateFlow<String?> = _selectedProviderFilter.asStateFlow()

    private val _remoteSearchResults = MutableStateFlow<List<TvMazeShow>>(emptyList())
    val remoteSearchResults: StateFlow<List<TvMazeShow>> = _remoteSearchResults.asStateFlow()

    private val _isSearchingRemote = MutableStateFlow(false)
    val isSearchingRemote: StateFlow<Boolean> = _isSearchingRemote.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val filteredSeries: StateFlow<List<SeriesWithEpisodes>> = combine(
        repo.observeAllSeries(),
        _localSearchQuery,
        _selectedProviderFilter
    ) { allSeries, query, provider ->
        allSeries.filter { item ->
            val matchesQuery = query.isBlank() ||
                item.series.title.contains(query, ignoreCase = true)
            val providers = item.series.streamingServices.split(",").map { it.trim() }
            val matchesProvider = provider == null || provider in providers
            matchesQuery && matchesProvider
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun observeDetail(seriesId: Int): Flow<SeriesWithEpisodes?> = repo.observeSeriesDetail(seriesId)

    fun setLocalSearch(query: String) {
        _localSearchQuery.value = query
    }

    fun setProviderFilter(provider: String?) {
        _selectedProviderFilter.value = if (_selectedProviderFilter.value == provider) null else provider
    }

    suspend fun detectProvidersForShow(showId: Int): List<String> {
        return de.meineserien.app.data.remote.TvMazeApi.create().getGermanProviders(showId)
    }

    fun searchOnline(query: String) {
        if (query.isBlank()) {
            _remoteSearchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearchingRemote.value = true
            val res = repo.searchRemoteSeries(query)
            _isSearchingRemote.value = false
            res.onSuccess { _remoteSearchResults.value = it }
                .onFailure { _statusMessage.value = "Keine Internetverbindung – Online-Suche nicht möglich." }
        }
    }

    fun addSeries(show: TvMazeShow, selectedProviders: List<String>) {
        viewModelScope.launch {
            val providers = selectedProviders.ifEmpty { listOf("Andere Anbieter") }
            val res = repo.addSeriesFromShow(show, providers)
            res.onSuccess { added ->
                _statusMessage.value = if (added) {
                    "„${show.name}“ wurde hinzugefügt."
                } else {
                    "„${show.name}“ ist bereits in deiner Liste."
                }
            }.onFailure {
                _statusMessage.value = "Fehler beim Laden der Episoden. Bitte Verbindung prüfen."
            }
        }
    }

    fun updateProviders(series: SeriesEntity, providers: List<String>) {
        viewModelScope.launch {
            repo.updateStreamingServices(series, providers.ifEmpty { listOf("Andere Anbieter") })
        }
    }

    fun toggleEpisode(episode: EpisodeEntity) {
        viewModelScope.launch {
            repo.toggleEpisodeWatched(episode)
        }
    }

    fun markUpToEpisode(seriesId: Int, season: Int, number: Int) {
        viewModelScope.launch {
            repo.setProgressUpTo(seriesId, season, number)
            _statusMessage.value = "Fortschritt bis S${season}E${number} gespeichert."
        }
    }

    fun deleteSeries(seriesId: Int) {
        viewModelScope.launch {
            repo.deleteSeries(seriesId)
            _statusMessage.value = "Serie entfernt."
        }
    }

    fun clearNewBadge(seriesId: Int) {
        viewModelScope.launch {
            repo.clearNewBadge(seriesId)
        }
    }

    fun refreshAllNow() {
        viewModelScope.launch {
            _statusMessage.value = "Suche nach neuen Staffeln & Episoden..."
            val updates = repo.syncAllSeriesForUpdates()
            _statusMessage.value = if (updates.isEmpty()) {
                "Alle Serien sind auf dem neuesten Stand."
            } else {
                updates.joinToString(" | ")
            }
        }
    }

    suspend fun getBackupJson(): String = repo.exportBackupJson()

    fun restoreBackupJson(json: String) {
        viewModelScope.launch {
            repo.importBackupJson(json)
                .onSuccess { count -> _statusMessage.value = "$count Serien erfolgreich wiederhergestellt." }
                .onFailure { _statusMessage.value = "Sicherung konnte nicht gelesen werden." }
        }
    }

    fun clearMessage() {
        _statusMessage.value = null
    }
}
