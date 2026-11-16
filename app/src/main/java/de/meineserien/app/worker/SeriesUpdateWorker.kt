package de.meineserien.app.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.meineserien.app.data.local.AppDatabase
import de.meineserien.app.data.remote.TvMazeApi
import de.meineserien.app.data.repository.SeriesRepository

class SeriesUpdateWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(context)
        val repo = SeriesRepository(db.seriesDao(), TvMazeApi.create())
        val updates = repo.syncAllSeriesForUpdates()

        if (updates.isNotEmpty()) {
            sendNotification(updates)
        }
        return Result.success()
    }

    private fun sendNotification(messages: List<String>) {
        val channelId = "meine_serien_updates"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Neue Staffeln & Episoden",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Benachrichtigt über neue Staffeln oder Episoden deiner gespeicherten Serien"
            }
            manager.createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Neuigkeiten bei deinen Serien")
            .setContentText(messages.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(messages.joinToString("\n")))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(1001, notification)
    }
}
