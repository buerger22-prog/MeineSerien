package de.meineserien.app

import android.app.Application
import androidx.work.*
import de.meineserien.app.worker.SeriesUpdateWorker
import java.util.concurrent.TimeUnit

class MeineSerienApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<SeriesUpdateWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "series_auto_update",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
