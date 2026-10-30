package com.nbradbury.folio.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SiteEntity::class, PostEntity::class], version = 1)
abstract class FolioDatabase : RoomDatabase() {
    abstract fun siteDao(): SiteDao
    abstract fun postDao(): PostDao

    companion object {
        fun create(context: Context): FolioDatabase =
            Room.databaseBuilder(context, FolioDatabase::class.java, "folio.db").build()
    }
}
