package de.meineserien.app.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.meineserien.app.data.remote.ShowPreviewDetails
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    var previewShow by remember { mutableStateOf<TvMazeShow?>(null) }
    var previewDetails by remember { mutableStateOf<ShowPreviewDetails?>(null) }
    var isLoadingPreview by remember { mutableStateOf(false) }
    var playTrailerInApp by remember { mutableStateOf(false) }
    val context = LocalContext.current
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

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    query = ""
                    viewModel.loadRecommendations()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Vorschläge basierend auf meinen Serien")
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                previewShow = show
                                previewDetails = null
                                playTrailerInApp = false
                                isLoadingPreview = true
                                scope.launch {
                                    previewDetails = viewModel.loadShowPreview(show)
                                    isLoadingPreview = false
                                }
                            },
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
                                    year,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                val desc = show.summary?.replace(Regex("<.*?>"), "")?.trim().orEmpty()
                                if (desc.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
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

    previewShow?.let { targetShow ->
        AlertDialog(
            onDismissRequest = { previewShow = null },
            title = { Text(text = targetShow.name, fontWeight = FontWeight.Bold) },
            text = {
                if (isLoadingPreview || previewDetails == null) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                } else {
                    val details = previewDetails!!
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            AsyncImage(
                                model = details.posterUrl,
                                contentDescription = details.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(96.dp)
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Start: ${details.year}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (details.seasonsCount > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("${details.seasonsCount} Staffel(n)", style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = details.statusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(details.overview, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(14.dp))
                        if (details.trailerYoutubeKey != null) {
                            Button(
                                onClick = { playTrailerInApp = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Trailer hier in der App abspielen")
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    val q = Uri.encode("${details.title} Trailer Deutsch")
                                    val url = "https://www.youtube.com/results?search_query=$q"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Trailer auf YouTube suchen")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val chosen = targetShow
                    previewShow = null
                    selectedProviders = emptySet()
                    isDetectingProviders = true
                    showPendingProviderSelection = chosen
                    scope.launch {
                        val detected = viewModel.detectProvidersForShow(chosen.id)
                        selectedProviders = detected.toSet()
                        isDetectingProviders = false
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Merken")
                }
            },
            dismissButton = {
                TextButton(onClick = { previewShow = null }) { Text("Schließen") }
            }
        )
    }

    if (playTrailerInApp && previewDetails?.trailerYoutubeKey != null) {
        Dialog(
            onDismissRequest = { playTrailerInApp = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = androidx.compose.ui.graphics.Color.Black
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EmbeddedYoutubePlayer(videoKey = previewDetails!!.trailerYoutubeKey!!)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { playTrailerInApp = false }) {
                        Text("Trailer schließen")
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmbeddedYoutubePlayer(videoKey: String) {
    val embedUrl = "https://www.youtube.com/embed/$videoKey?autoplay=1&playsinline=1&rel=0&modestbranding=1"
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                loadUrl(embedUrl)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    )
}
