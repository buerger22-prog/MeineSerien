package de.meineserien.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import de.meineserien.app.ui.SeriesViewModel
import de.meineserien.app.ui.screens.DetailScreen
import de.meineserien.app.ui.screens.MainScreen
import de.meineserien.app.ui.theme.MeineSerienTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }

        setContent {
            MeineSerienTheme {
                val vm: SeriesViewModel = viewModel()
                val scope = rememberCoroutineScope()
                var openedSeriesId by remember { mutableStateOf<Int?>(null) }

                val exportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    uri?.let {
                        scope.launch {
                            val json = vm.getBackupJson()
                            contentResolver.openOutputStream(it)?.use { out ->
                                out.write(json.toByteArray())
                            }
                        }
                    }
                }

                val importLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        contentResolver.openInputStream(it)?.use { input ->
                            val json = input.bufferedReader().readText()
                            vm.restoreBackupJson(json)
                        }
                    }
                }

                if (openedSeriesId != null) {
                    BackHandler { openedSeriesId = null }
                    DetailScreen(
                        seriesId = openedSeriesId!!,
                        viewModel = vm,
                        onBack = { openedSeriesId = null }
                    )
                } else {
                    MainScreen(
                        viewModel = vm,
                        onOpenSeries = { openedSeriesId = it },
                        onExportBackup = { exportLauncher.launch("meine_serien_backup.json") },
                        onImportBackup = { importLauncher.launch(arrayOf("application/json")) }
                    )
                }
            }
        }
    }
}
