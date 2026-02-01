package org.jellyfin.mobile.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.data.entity.DownloadEntity.Key.TABLE_NAME
import org.jellyfin.mobile.data.entity.DownloadStatus

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity): Long

    @Query("DELETE FROM $TABLE_NAME WHERE item_id LIKE :downloadId")
    suspend fun delete(downloadId: String)

    @Query("SELECT * FROM $TABLE_NAME ORDER BY item_id DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM $TABLE_NAME WHERE item_id LIKE :downloadId")
    suspend fun get(downloadId: String): DownloadEntity?

    @Query("SELECT EXISTS(SELECT * FROM $TABLE_NAME WHERE item_id LIKE :downloadId)")
    suspend fun downloadExists(downloadId: String): Boolean

    @Query("SELECT * FROM $TABLE_NAME WHERE download_status = :status")
    fun getByStatus(status: DownloadStatus): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM $TABLE_NAME WHERE expiration_timestamp IS NOT NULL AND expiration_timestamp <= :currentTimestamp")
    suspend fun getExpired(currentTimestamp: Long): List<DownloadEntity>

    @Query("UPDATE $TABLE_NAME SET download_progress = :progress, download_status = :status WHERE item_id = :itemId")
    suspend fun updateProgress(itemId: String, progress: Float, status: DownloadStatus)

    @Query("UPDATE $TABLE_NAME SET expiration_timestamp = :newExpirationTimestamp WHERE item_id = :itemId")
    suspend fun updateExpiration(itemId: String, newExpirationTimestamp: Long)
}
