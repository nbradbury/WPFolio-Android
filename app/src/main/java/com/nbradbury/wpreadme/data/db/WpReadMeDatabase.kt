package com.nbradbury.wpreadme.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SiteEntity::class, PostEntity::class], version = 1)
abstract class WpReadMeDatabase : RoomDatabase() {
    abstract fun siteDao(): SiteDao
    abstract fun postDao(): PostDao

    companion object {
        fun create(context: Context): WpReadMeDatabase =
            Room.databaseBuilder(context, WpReadMeDatabase::class.java, "wpreadme.db").build()
    }
}
