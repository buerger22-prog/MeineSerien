package de.meineserien.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.meineserien.app.data.local.SeriesWithEpisodes
import de.meineserien.app.ui.STREAMING_PROVIDERS
import de.meineserien.app.ui.SeriesViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: SeriesViewModel,
    onOpenSeries: (Int) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    val seriesList by viewModel.filteredSeries.collectAsState()
    val localSearch by viewModel.localSearchQuery.collectAsState()
    val selectedFilter by viewModel.selectedProviderFilter.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showBackupMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Meine Serien", fontWeight = FontWeight.Bold)
                        Text(
                            "Gemeinsame Merkliste",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshAllNow() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Nach neuen Folgen suchen")
                    }
                    Box {
                        IconButton(onClick = { showBackupMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Datensicherung")
                        }
                        DropdownMenu(
                            expanded = showBackupMenu,
                            onDismissRequest = { showBackupMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sicherung exportieren") },
                                leadingIcon = { Icon(Icons.Default.Upload, null) },
                                onClick = {
                                    showBackupMenu = false
                                    onExportBackup()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Alte Serien auf Deutsch umstellen") },
                                leadingIcon = { Icon(Icons.Default.Translate, null) },
                                onClick = {
                                    showBackupMenu = false
                                    viewModel.migrateOldSeriesToGerman()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sicherung wiederherstellen") },
                                leadingIcon = { Icon(Icons.Default.Download, null) },
                                onClick = {
                                    showBackupMenu = false
                                    onImportBackup()
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Serie hinzufügen") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = localSearch,
                onValueChange = { viewModel.setLocalSearch(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Gespeicherte Serien durchsuchen...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (localSearch.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setLocalSearch("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Löschen")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                STREAMING_PROVIDERS.forEach { provider ->
                    FilterChip(
                        selected = selectedFilter == provider,
                        onClick = { viewModel.setProviderFilter(provider) },
                        label = { Text(provider) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (seriesList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (localSearch.isBlank() && selectedFilter == null)
                            "Noch keine Serien gespeichert.\nTippe unten auf „Serie hinzufügen“."
                        else
                            "Keine passenden Serien gefunden.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(seriesList, key = { it.series.id }) { item ->
                        SeriesCard(item = item, onClick = { onOpenSeries(item.series.id) })
                    }
                }
            }
        }

        if (showAddDialog) {
            SearchSheet(
                viewModel = viewModel,
                onDismiss = { showAddDialog = false }
            )
        }
    }
}

@Composable
fun SeriesCard(item: SeriesWithEpisodes, onClick: () -> Unit) {
    val percent = (item.progressPercent * 100).roundToInt()
    val lastEp = item.lastWatchedEpisode

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.series.coverUrl,
                contentDescription = item.series.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(92.dp)
                    .height(132.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.series.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    val isEnded = item.series.status.equals("Ended", ignoreCase = true) ||
                        item.series.status.equals("Canceled", ignoreCase = true)
                    if (isEnded) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "BEENDET",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (item.series.hasNewContentBadge) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "NEU",
                                color = Color.Black,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "${item.series.premiereYear} • ${item.series.streamingServices}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (lastEp != null)
                        "Zuletzt: Staffel ${lastEp.season}, Folge ${lastEp.number}"
                    else
                        "Noch nicht gestartet",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { item.progressPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${item.watchedEpisodes} / ${item.totalEpisodes} Folgen (${item.remainingEpisodes} offen)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$percent %",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
