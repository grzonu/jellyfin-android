package org.jellyfin.mobile.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.media3.exoplayer.offline.DownloadService
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.utils.AndroidVersion
import org.jellyfin.mobile.utils.Constants
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

class DownloadExpirationWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val downloadDao: DownloadDao by inject()

    override suspend fun doWork(): Result {
        Timber.d("Running download expiration check")

        createExpirationNotificationChannel()

        val now = System.currentTimeMillis()
        val expiredDownloads = downloadDao.getExpired(now)

        for (download in expiredDownloads) {
            deleteExpiredDownload(download)
        }

        if (expiredDownloads.isNotEmpty()) {
            Timber.i("Deleted ${expiredDownloads.size} expired download(s)")
        }

        checkExpiringSoon(now)

        return Result.success()
    }

    private suspend fun deleteExpiredDownload(download: DownloadEntity) {
        Timber.d("Deleting expired download: ${download.itemId}")

        val contentId = download.mediaSource.itemId.toString()
        DownloadService.sendRemoveDownload(
            context,
            JellyfinDownloadService::class.java,
            contentId,
            false,
        )

        download.mediaSource.externalSubtitleStreams.forEach {
            DownloadService.sendRemoveDownload(
                context,
                JellyfinDownloadService::class.java,
                "$contentId:${it.index}",
                false,
            )
        }

        val downloadFolder = File(context.filesDir, "/Downloads/${download.itemId}/")
        downloadFolder.deleteRecursively()

        downloadDao.delete(download.itemId)
    }

    private suspend fun checkExpiringSoon(now: Long) {
        val tomorrow = now + TimeUnit.HOURS.toMillis(EXPIRATION_WARNING_HOURS)
        val expiringSoon = downloadDao.getExpired(tomorrow)
            .filter { it.expirationTimestamp != null && it.expirationTimestamp > now }

        for (download in expiringSoon) {
            showExpirationWarningNotification(download)
        }
    }

    private fun showExpirationWarningNotification(download: DownloadEntity) {
        val itemName = download.mediaSource.item?.name ?: download.itemId

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            download.itemId.hashCode(),
            intent,
            Constants.PENDING_INTENT_FLAGS,
        )

        val notification = NotificationCompat.Builder(context, Constants.EXPIRATION_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.download_expiring_soon_title))
            .setContentText(context.getString(R.string.download_expiring_soon_message, itemName))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID_BASE + download.itemId.hashCode(), notification)
        } catch (e: SecurityException) {
            Timber.w(e, "Cannot show notification: permission denied")
        }
    }

    private fun createExpirationNotificationChannel() {
        if (AndroidVersion.isAtLeastO) {
            val notificationManager: NotificationManager? = context.getSystemService()
            val channel = NotificationChannel(
                Constants.EXPIRATION_NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.expiration_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.expiration_notification_channel_description)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val WORK_NAME = "download_expiration_check"
        private const val EXPIRATION_WARNING_HOURS = 24L
        private const val NOTIFICATION_ID_BASE = 1000
    }
}
