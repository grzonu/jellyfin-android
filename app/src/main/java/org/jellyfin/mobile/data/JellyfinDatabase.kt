package org.jellyfin.mobile.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.dao.ServerDao
import org.jellyfin.mobile.data.dao.UserDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.data.entity.ServerEntity
import org.jellyfin.mobile.data.entity.UserEntity

@Database(
    entities = [
        ServerEntity::class,
        UserEntity::class,
        DownloadEntity::class,
    ],
    version = 4,
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
    ],
)
abstract class JellyfinDatabase : RoomDatabase() {
    abstract val serverDao: ServerDao
    abstract val userDao: UserDao
    abstract val downloadDao: DownloadDao
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Download ADD COLUMN quality_bitrate INTEGER")
        db.execSQL("ALTER TABLE Download ADD COLUMN quality_max_height INTEGER")
        db.execSQL("ALTER TABLE Download ADD COLUMN download_timestamp INTEGER")
        db.execSQL("ALTER TABLE Download ADD COLUMN expiration_timestamp INTEGER")
        db.execSQL("ALTER TABLE Download ADD COLUMN download_status TEXT NOT NULL DEFAULT 'PENDING'")
        db.execSQL("ALTER TABLE Download ADD COLUMN download_progress REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE Download ADD COLUMN file_size_bytes INTEGER")
    }
}
