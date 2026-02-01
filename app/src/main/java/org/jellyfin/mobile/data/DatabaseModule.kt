package org.jellyfin.mobile.data

import androidx.room.Room
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val databaseModule = module {
    single {
        Room.databaseBuilder(androidApplication(), JellyfinDatabase::class.java, "jellyfin")
            .addMigrations(MIGRATION_3_4)
            .fallbackToDestructiveMigrationFrom(true, 1)
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .build()
    }
    single { get<JellyfinDatabase>().serverDao }
    single { get<JellyfinDatabase>().userDao }
    single { get<JellyfinDatabase>().downloadDao }
}
