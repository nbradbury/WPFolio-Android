package com.nbradbury.wpfolio.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SiteEntity::class, PostEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class WpFolioDatabase : RoomDatabase() {
    abstract fun siteDao(): SiteDao
    abstract fun postDao(): PostDao

    companion object {
        fun create(context: Context): WpFolioDatabase =
            Room.databaseBuilder(context, WpFolioDatabase::class.java, "wpfolio.db").build()
    }
}
