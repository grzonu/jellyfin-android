package org.jellyfin.mobile.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.decodeFromString
import org.jellyfin.mobile.data.entity.DownloadEntity.Key.ITEM_ID
import org.jellyfin.mobile.data.entity.DownloadEntity.Key.TABLE_NAME
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.mobile.utils.extensions.toFileSize
import kotlin.time.Duration

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Entity(
    tableName = TABLE_NAME,
    indices = [
        Index(value = [ITEM_ID], unique = true),
    ],
)
@TypeConverters(LocalJellyfinMediaSourceConverter::class, DownloadStatusConverter::class)
data class DownloadEntity(
    @PrimaryKey
    @ColumnInfo(name = ITEM_ID)
    val itemId: String,
    @ColumnInfo(name = MEDIA_SOURCE)
    val mediaSource: LocalJellyfinMediaSource,
    @ColumnInfo(name = QUALITY_BITRATE)
    val qualityBitrate: Int? = null,
    @ColumnInfo(name = QUALITY_MAX_HEIGHT)
    val qualityMaxHeight: Int? = null,
    @ColumnInfo(name = DOWNLOAD_TIMESTAMP)
    val downloadTimestamp: Long? = null,
    @ColumnInfo(name = EXPIRATION_TIMESTAMP)
    val expirationTimestamp: Long? = null,
    @ColumnInfo(name = DOWNLOAD_STATUS)
    val downloadStatus: DownloadStatus = DownloadStatus.PENDING,
    @ColumnInfo(name = DOWNLOAD_PROGRESS)
    val downloadProgress: Float = 0f,
    @ColumnInfo(name = FILE_SIZE_BYTES)
    val fileSizeBytes: Long? = null,
) {
    /**
     * Converts the [mediaSource] string to a [LocalJellyfinMediaSource] object.
     *
     * @param startTime The start time as a [Duration]. If null, the default start time is used.
     * @param audioStreamIndex The index of the audio stream to select. If null, the default audio stream is used.
     * @param subtitleStreamIndex The index of the subtitle stream to select. If -1, subtitles are disabled. If null, the default subtitle stream is used.
     */
    fun asMediaSource(
        startTime: Duration? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): LocalJellyfinMediaSource = mediaSource
        .also { localJellyfinMediaSource ->
            startTime
                ?.let { localJellyfinMediaSource.startTime = it }
            audioStreamIndex
                ?.let { localJellyfinMediaSource.mediaStreams[it] }
                ?.let(localJellyfinMediaSource::selectAudioStream)
            subtitleStreamIndex
                ?.run {
                    takeUnless { it == -1 }
                        ?.let { localJellyfinMediaSource.mediaStreams[it] }
                        ?: localJellyfinMediaSource.selectSubtitleStream(null)
                }
        }

    constructor(mediaSource: LocalJellyfinMediaSource) :
        this(mediaSource.id, mediaSource)

    @Ignore
    val fileSize: String = mediaSource.downloadSize.toFileSize()

    companion object Key {
        const val BYTES_PER_BINARY_UNIT: Int = 1024
        const val TABLE_NAME: String = "Download"
        const val ID: String = "id"
        const val ITEM_ID: String = "item_id"
        const val MEDIA_SOURCE: String = "media_source"
        const val QUALITY_BITRATE: String = "quality_bitrate"
        const val QUALITY_MAX_HEIGHT: String = "quality_max_height"
        const val DOWNLOAD_TIMESTAMP: String = "download_timestamp"
        const val EXPIRATION_TIMESTAMP: String = "expiration_timestamp"
        const val DOWNLOAD_STATUS: String = "download_status"
        const val DOWNLOAD_PROGRESS: String = "download_progress"
        const val FILE_SIZE_BYTES: String = "file_size_bytes"
    }
}

class LocalJellyfinMediaSourceConverter {
    @TypeConverter
    fun toLocalJellyfinMediaSource(value: String): LocalJellyfinMediaSource = decodeFromString(value)

    @TypeConverter
    fun fromLocalJellyfinMediaSource(value: LocalJellyfinMediaSource): String = Json.encodeToString(value)
}

class DownloadStatusConverter {
    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus = DownloadStatus.valueOf(value)

    @TypeConverter
    fun fromDownloadStatus(value: DownloadStatus): String = value.name
}
