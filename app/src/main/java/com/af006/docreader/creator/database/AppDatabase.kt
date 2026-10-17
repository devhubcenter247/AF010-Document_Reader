package com.af010.creator.docreader.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.af010.creator.docreader.model.Converters
import com.af010.creator.docreader.model.FavoriteDocument
import com.af010.creator.docreader.model.RecentDocument

@Database(
    entities = [RecentDocument::class, FavoriteDocument::class],
    version = 1, exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): RecentDao
    abstract fun favoriteDao(): FavoriteDao
}