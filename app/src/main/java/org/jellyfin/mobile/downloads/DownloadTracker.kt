package org.jellyfin.mobile.downloads

import android.net.Uri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadIndex
import androidx.media3.exoplayer.offline.DownloadManager
import com.google.common.base.Preconditions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadStatus
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet

class DownloadTracker(
    downloadManager: DownloadManager,
    private val downloadDao: DownloadDao,
) {
    interface Listener {
        fun onDownloadsChanged()
    }

    private val listeners: CopyOnWriteArraySet<Listener> = CopyOnWriteArraySet()
    private val downloads: HashMap<Uri, Download> = HashMap()
    private val downloadIndex: DownloadIndex
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        downloadIndex = downloadManager.downloadIndex
        downloadManager.addListener(DownloadManagerListener())
        loadDownloads()
    }

    fun addListener(listener: Listener?) {
        listeners.add(Preconditions.checkNotNull(listener))
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun isDownloaded(uri: Uri): Boolean {
        val download = downloads[uri]
        return download != null && download.state == Download.STATE_COMPLETED
    }

    fun getDownloadSize(uri: Uri): Long {
        val download = downloads[uri]
        return download?.bytesDownloaded ?: 0
    }

    fun isFailed(uri: Uri): Boolean {
        val download = downloads[uri]
        return download != null && download.state == Download.STATE_FAILED
    }

    private fun loadDownloads() {
        try {
            downloadIndex.getDownloads().use { loadedDownloads ->
                while (loadedDownloads.moveToNext()) {
                    val download = loadedDownloads.download
                    downloads[download.request.uri] = download
                }
            }
        } catch (e: IOException) {
            Timber.e(e, "Failed to load downloads")
        }
    }

    private inner class DownloadManagerListener : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?,
        ) {
            downloads[download.request.uri] = download
            updateDatabaseProgress(download)
            for (listener in listeners) {
                listener.onDownloadsChanged()
            }
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            downloads.remove(download.request.uri)
            for (listener in listeners) {
                listener.onDownloadsChanged()
            }
        }
    }

    private fun updateDatabaseProgress(download: Download) {
        val contentId = download.request.id
        val itemId = contentId.replace("-", "")

        val status = when (download.state) {
            Download.STATE_QUEUED -> DownloadStatus.PENDING
            Download.STATE_DOWNLOADING -> DownloadStatus.DOWNLOADING
            Download.STATE_COMPLETED -> DownloadStatus.COMPLETED
            Download.STATE_FAILED -> DownloadStatus.FAILED
            Download.STATE_STOPPED -> DownloadStatus.CANCELLED
            else -> DownloadStatus.PENDING
        }

        val progress = download.percentDownloaded

        scope.launch {
            try {
                downloadDao.updateProgress(itemId, progress, status)
                Timber.d("Updated download progress for $itemId: ${progress.toInt()}% - $status")
            } catch (e: Exception) {
                Timber.e(e, "Failed to update download progress for $itemId")
            }
        }
    }
}
