package de.meineserien.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.meineserien.app.ui.STREAMING_PROVIDERS
import de.meineserien.app.ui.SeriesViewModel
import de.meineserien.app.ui.formatGermanDate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    seriesId: Int,
    viewModel: SeriesViewModel,
    onBack: () -> Unit
) {
    val detailState by viewModel.observeDetail(seriesId).collectAsState(initial = null)
    var selectedSeason by remember { mutableIntStateOf(1) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(seriesId) {
        viewModel.clearNewBadge(seriesId)
    }

    val item = detailState ?: return
    val seasons = item.episodes.map { it.season }.distinct().sorted()
    if (seasons.isNotEmpty() && selectedSeason !in seasons) {
        selectedSeason = seasons.first()
    }

    val seasonEpisodes = item.episodes
        .filter { it.season == selectedSeason }
        .sortedBy { it.number }

    val seasonWatched = seasonEpisodes.count { it.isWatched }
    val seasonProgress = if (seasonEpisodes.isEmpty()) 0f else seasonWatched.toFloat() / seasonEpisodes.size
    val totalPercent = (item.progressPercent * 100).roundToInt()
    val currentProviders = item.series.streamingServices.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.series.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Serie löschen")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.Top) {
                    AsyncImage(
                        model = item.series.coverUrl,
                        contentDescription = item.series.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(110.dp)
                            .height(160.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${item.series.premiereYear} • ${item.series.network}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val isEnded = item.series.status.equals("Ended", ignoreCase = true)
                        val isCanceled = item.series.status.equals("Canceled", ignoreCase = true)
                        if (isEnded || isCanceled) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (isCanceled)
                                        "Serie abgesetzt – keine neuen Staffeln"
                                    else
                                        "Serie endgültig beendet – keine neuen Staffeln",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Gesamtfortschritt: $totalPercent %", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { item.progressPercent },
                            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Gesehen: ${item.watchedEpisodes} | Offen: ${item.remainingEpisodes} (Gesamt: ${item.totalEpisodes})",
                            style = MaterialTheme.typography.bodySmall
                        )
                        item.lastWatchedEpisode?.let { last ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Zuletzt gesehen: Staffel ${last.season}, Folge ${last.number}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            item {
                Text("Streamingdienste", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    STREAMING_PROVIDERS.forEach { provider ->
                        val active = provider in currentProviders
                        FilterChip(
                            selected = active,
                            onClick = {
                                val updated = if (active) currentProviders - provider else currentProviders + provider
                                viewModel.updateProviders(item.series, updated.toList())
                            },
                            label = { Text(provider) }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    seasons.forEach { seasonNum ->
                        FilterChip(
                            selected = selectedSeason == seasonNum,
                            onClick = { selectedSeason = seasonNum },
                            label = { Text("Staffel $seasonNum") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Staffel $selectedSeason: $seasonWatched von ${seasonEpisodes.size} Episoden gesehen",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { seasonProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                )
                if (seasonEpisodes.isNotEmpty()) {
                    val allSeasonWatched = seasonWatched == seasonEpisodes.size
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            if (allSeasonWatched) {
                                seasonEpisodes.forEach { viewModel.toggleEpisode(it) }
                            } else {
                                seasonEpisodes.filter { !it.isWatched }.forEach { viewModel.toggleEpisode(it) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (allSeasonWatched) Icons.Outlined.RadioButtonUnchecked else Icons.Filled.CheckCircle,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (allSeasonWatched) "Staffel $selectedSeason zurücksetzen" else "Alle Folgen dieser Staffel auswählen"
                        )
                    }
                }
            }

            items(seasonEpisodes, key = { it.id }) { ep ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (ep.isWatched)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        else
                            MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.toggleEpisode(ep) }) {
                            Icon(
                                imageVector = if (ep.isWatched) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = "Als gesehen markieren",
                                tint = if (ep.isWatched) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Folge ${ep.number}: ${ep.title}",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Ausstrahlung: ${formatGermanDate(ep.airDate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { viewModel.markUpToEpisode(item.series.id, ep.season, ep.number) }
                        ) {
                            Text("Bis hier", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Serie löschen?") },
                text = { Text("Soll „${item.series.title}“ wirklich aus eurer Liste entfernt werden?") },
                confirmButton = {
                    Button(onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteSeries(item.series.id)
                        onBack()
                    }) {
                        Text("Löschen")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Abbrechen")
                    }
                }
            )
        }
    }
}
