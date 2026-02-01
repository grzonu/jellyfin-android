package org.jellyfin.mobile.downloads

import android.content.Context
import android.os.StatFs
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.data.entity.DownloadStatus
import org.jellyfin.mobile.player.deviceprofile.DeviceProfileBuilder
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.mobile.player.source.MediaSourceResolver
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.extractId
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.ImageType
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit

class InsufficientStorageException(val requiredBytes: Long, val availableBytes: Long) : Exception(
    "Not enough storage space. Required: $requiredBytes bytes, Available: $availableBytes bytes",
)

data class DownloadProgress(
    val itemId: String,
    val status: DownloadStatus,
    val progress: Float,
    val bytesDownloaded: Long,
)

data class ValidationResult(
    val itemId: String,
    val isValid: Boolean,
    val errorMessage: String? = null,
)

interface OfflineDownloadManager {
    suspend fun startDownload(
        itemId: UUID,
        qualityOption: DownloadQualityOption,
        expirationDays: Int? = null,
    ): Result<Unit>

    suspend fun cancelDownload(itemId: UUID): Result<Unit>

    suspend fun deleteDownload(itemId: UUID): Result<Unit>

    suspend fun extendExpiration(itemId: UUID, additionalDays: Int): Result<Unit>

    suspend fun validateDownloads(): List<ValidationResult>

    fun getDownloadProgress(itemId: UUID): Flow<DownloadProgress>
}

class OfflineDownloadManagerImpl(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val apiClient: ApiClient,
    private val mediaSourceResolver: MediaSourceResolver,
    private val deviceProfileBuilder: DeviceProfileBuilder,
) : OfflineDownloadManager {

    private val downloadTracker: DownloadTracker
        get() = DownloadServiceUtil.getDownloadTracker()

    override suspend fun startDownload(
        itemId: UUID,
        qualityOption: DownloadQualityOption,
        expirationDays: Int?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val estimatedSize = qualityOption.estimatedFileSizeBytes
            checkStorageSpace(estimatedSize)

            val deviceProfile = deviceProfileBuilder.getDeviceProfile()
            val jellyfinMediaSource = mediaSourceResolver.resolveMediaSource(
                itemId = itemId,
                mediaSourceId = itemId.toString().replace("-", ""),
                deviceProfile = deviceProfile,
            ).getOrThrow()

            val downloadUrl = buildTranscodingDownloadUrl(itemId, qualityOption)
            val downloadUri = downloadUrl.toUri()
            val cacheKey = downloadUri.extractId()
            val contentId = itemId.toString()

            val downloadFolder = File(context.filesDir, "/Downloads/${itemId.toString().replace("-", "")}/")
            downloadFolder.mkdirs()

            val now = System.currentTimeMillis()
            val expirationTimestamp = expirationDays?.let {
                now + TimeUnit.DAYS.toMillis(it.toLong())
            }

            val localMediaSource = LocalJellyfinMediaSource(
                jellyfinMediaSource,
                downloadFolder.canonicalPath,
                downloadUrl,
                qualityOption.estimatedFileSizeBytes,
            )

            val downloadEntity = DownloadEntity(
                itemId = itemId.toString().replace("-", ""),
                mediaSource = localMediaSource,
                qualityBitrate = qualityOption.bitrate,
                qualityMaxHeight = qualityOption.maxHeight,
                downloadTimestamp = now,
                expirationTimestamp = expirationTimestamp,
                downloadStatus = DownloadStatus.DOWNLOADING,
                downloadProgress = 0f,
                fileSizeBytes = qualityOption.estimatedFileSizeBytes,
            )
            downloadDao.insert(downloadEntity)

            val itemName = jellyfinMediaSource.item?.name ?: "Download"
            val downloadRequest = DownloadRequest.Builder(contentId, downloadUri)
                .setData(itemName.encodeToByteArray())
                .setCustomCacheKey(cacheKey)
                .build()

            DownloadService.sendAddDownload(
                context,
                JellyfinDownloadService::class.java,
                downloadRequest,
                false,
            )

            downloadThumbnail(itemId, downloadFolder)
        }
    }

    private fun downloadThumbnail(itemId: UUID, downloadFolder: File) {
        try {
            val thumbnailUrl = apiClient.imageApi.getItemImageUrl(
                itemId = itemId,
                imageType = ImageType.PRIMARY,
                fillWidth = THUMBNAIL_SIZE,
                fillHeight = THUMBNAIL_SIZE,
            )
            val thumbnailFile = File(downloadFolder, Constants.DOWNLOAD_THUMBNAIL_FILENAME)
            URL(thumbnailUrl).openStream().use { input ->
                FileOutputStream(thumbnailFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Thumbnail download is best-effort, don't fail the download
        }
    }

    override suspend fun cancelDownload(itemId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val contentId = itemId.toString()

            DownloadService.sendRemoveDownload(
                context,
                JellyfinDownloadService::class.java,
                contentId,
                false,
            )

            val itemIdString = itemId.toString().replace("-", "")
            downloadDao.updateProgress(itemIdString, 0f, DownloadStatus.CANCELLED)
        }
    }

    override suspend fun deleteDownload(itemId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val itemIdString = itemId.toString().replace("-", "")
            val contentId = itemId.toString()

            DownloadService.sendRemoveDownload(
                context,
                JellyfinDownloadService::class.java,
                contentId,
                false,
            )

            val downloadFolder = File(context.filesDir, "/Downloads/$itemIdString/")
            downloadFolder.deleteRecursively()

            downloadDao.delete(itemIdString)
        }
    }

    override suspend fun extendExpiration(itemId: UUID, additionalDays: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val itemIdString = itemId.toString().replace("-", "")
            val download = downloadDao.get(itemIdString)
                ?: throw IllegalStateException("Download not found: $itemIdString")

            val currentExpiration = download.expirationTimestamp ?: System.currentTimeMillis()
            val newExpiration = currentExpiration + TimeUnit.DAYS.toMillis(additionalDays.toLong())

            downloadDao.updateExpiration(itemIdString, newExpiration)
        }
    }

    override suspend fun validateDownloads(): List<ValidationResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ValidationResult>()
        val downloads = downloadDao.getByStatus(DownloadStatus.COMPLETED)

        downloads.first().forEach { download ->
            val downloadFolder = File(context.filesDir, "/Downloads/${download.itemId}/")
            val cacheDir = File(context.filesDir, Constants.DOWNLOAD_PATH)

            val isValid = downloadFolder.exists() || cacheDir.exists()

            results.add(
                ValidationResult(
                    itemId = download.itemId,
                    isValid = isValid,
                    errorMessage = if (!isValid) "Download files not found" else null,
                ),
            )

            if (!isValid) {
                downloadDao.updateProgress(download.itemId, 0f, DownloadStatus.FAILED)
            }
        }

        results
    }

    override fun getDownloadProgress(itemId: UUID): Flow<DownloadProgress> = flow {
        val itemIdString = itemId.toString().replace("-", "")
        val download = downloadDao.get(itemIdString)

        if (download != null) {
            emit(
                DownloadProgress(
                    itemId = itemIdString,
                    status = download.downloadStatus,
                    progress = download.downloadProgress,
                    bytesDownloaded = (download.downloadProgress * (download.fileSizeBytes ?: 0L) / 100f).toLong(),
                ),
            )
        }
    }.flowOn(Dispatchers.IO)

    private fun checkStorageSpace(requiredBytes: Long) {
        val statFs = StatFs(context.filesDir.path)
        val availableBytes = statFs.availableBytes
        val bufferBytes = STORAGE_BUFFER_BYTES
        if (availableBytes < requiredBytes + bufferBytes) {
            throw InsufficientStorageException(requiredBytes + bufferBytes, availableBytes)
        }
    }

    private fun buildTranscodingDownloadUrl(
        itemId: UUID,
        qualityOption: DownloadQualityOption,
    ): String {
        val params = buildMap {
            put("static", "false")
            put("mediaSourceId", itemId.toString().replace("-", ""))
            put("deviceId", apiClient.deviceInfo.id)
            put("api_key", apiClient.accessToken ?: "")
            put("audioCodec", "aac")
            put("videoCodec", "h264")
            put("container", "ts")

            if (!qualityOption.isAuto) {
                put("maxStreamingBitrate", qualityOption.bitrate.toString())
                put("maxHeight", qualityOption.maxHeight.toString())
                put("videoBitrate", (qualityOption.bitrate * 9 / 10).toString())
                put("audioBitrate", (qualityOption.bitrate / 10).toString())
            }
        }

        val queryString = params.entries.joinToString("&") { (k, v) -> "$k=$v" }
        return "${apiClient.baseUrl}/Videos/$itemId/stream?$queryString"
    }

    companion object {
        private const val STORAGE_BUFFER_BYTES = 100L * 1024 * 1024
        private const val THUMBNAIL_SIZE = 300
    }
}
