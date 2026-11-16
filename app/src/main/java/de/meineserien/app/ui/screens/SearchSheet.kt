package de.meineserien.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.meineserien.app.data.remote.TvMazeShow
import de.meineserien.app.ui.STREAMING_PROVIDERS
import de.meineserien.app.ui.SeriesViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchSheet(
    viewModel: SeriesViewModel,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results by viewModel.remoteSearchResults.collectAsState()
    val isLoading by viewModel.isSearchingRemote.collectAsState()
    var showPendingProviderSelection by remember { mutableStateOf<TvMazeShow?>(null) }
    var selectedProviders by remember { mutableStateOf(emptySet<String>()) }
    var isDetectingProviders by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Neue Serie suchen",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        if (it.length >= 2) viewModel.searchOnline(it)
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Serientitel eingeben...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(onClick = { viewModel.searchOnline(query) }) {
                    Icon(Icons.Default.Search, contentDescription = "Suchen")
                }
            }

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(results, key = { it.id }) { show ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = show.image?.medium,
                                contentDescription = show.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(64.dp)
                                    .height(90.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(show.name, fontWeight = FontWeight.Bold)
                                val year = show.premiered?.take(4) ?: "—"
                                val sender = show.network?.name ?: show.webChannel?.name ?: "Unbekannt"
                                Text(
                                    "$year • Sender: $sender",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(onClick = {
                                selectedProviders = emptySet()
                                isDetectingProviders = true
                                showPendingProviderSelection = show
                                scope.launch {
                                    val detected = viewModel.detectProvidersForShow(show.id)
                                    selectedProviders = detected.toSet()
                                    isDetectingProviders = false
                                }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Merken")
                            }
                        }
                    }
                }
            }
        }
    }

    showPendingProviderSelection?.let { chosenShow ->
        AlertDialog(
            onDismissRequest = { showPendingProviderSelection = null },
            title = { Text("Streamingdienste für „${chosenShow.name}“") },
            text = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    STREAMING_PROVIDERS.forEach { provider ->
                        val active = provider in selectedProviders
                        FilterChip(
                            selected = active,
                            onClick = {
                                selectedProviders = if (active) selectedProviders - provider else selectedProviders + provider
                            },
                            label = { Text(provider) }
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addSeries(chosenShow, selectedProviders.toList())
                    showPendingProviderSelection = null
                    onDismiss()
                }) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPendingProviderSelection = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}
