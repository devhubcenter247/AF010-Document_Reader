package com.af010.creator.docreader.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.af010.creator.docreader.model.FavoriteDocument
import com.af010.creator.docreader.model.RecentDocument
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: RecentDocument)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(documents: List<RecentDocument>)

    @Query("SELECT * FROM RecentDocument ORDER BY lastTimeView DESC")
    fun getAll(): Flow<List<RecentDocument>>

    @Query("SELECT COUNT(*) FROM RecentDocument WHERE mediaId = :mediaId")
    suspend fun isExistInFavorite(mediaId: Int): Int

    @Transaction
    suspend fun toggleFavorite(mediaId: RecentDocument, favorite: Boolean) {
        if (favorite) insert(mediaId) else delete(mediaId)
    }

    @Delete
    suspend fun delete(document: RecentDocument)

    @Query("DELETE FROM RecentDocument WHERE mediaId = :docId")
    suspend fun deleteById(docId: Long)

    @Update
    suspend fun update(document: RecentDocument)

    @Query("DELETE FROM RecentDocument")
    suspend fun clearAll()

    @Query("UPDATE RecentDocument SET path = :newPath WHERE mediaId = :id")
    suspend fun updatePath(id: Long, newPath: String)

    @Query("UPDATE OR REPLACE RecentDocument SET mediaId = :newId WHERE mediaId = :oldId")
    suspend fun updateId(oldId: Long, newId: Long)
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: FavoriteDocument)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(documents: List<FavoriteDocument>)

    @Query("SELECT mediaId FROM FavoriteDocument")
    fun favoriteIds(): Flow<List<Long>>

    @Query("SELECT * FROM FavoriteDocument")
    fun getAll(): Flow<List<FavoriteDocument>>

    @Query("SELECT COUNT(*) FROM FavoriteDocument WHERE mediaId = :mediaId")
    suspend fun isExistInFavorite(mediaId: Int): Int

    @Transaction
    suspend fun toggleFavorite(recent: RecentDocument, favorite: Boolean) {
        if (favorite) {
            insert(
                FavoriteDocument(
                    recent.mediaId,
                    recent.path,
                    recent.lastModified,
                    recent.size,
                    recent.type
                )
            )
        } else {
            deleteById(recent.mediaId)
        }
    }

    @Transaction
    suspend fun toggleFavorite(mediaId: FavoriteDocument, favorite: Boolean) {
        if (favorite) insert(mediaId) else delete(mediaId)
    }

    @Delete
    suspend fun delete(document: FavoriteDocument)

    @Query("DELETE FROM FavoriteDocument WHERE mediaId = :docId")
    suspend fun deleteById(docId: Long)

    @Update
    suspend fun update(document: FavoriteDocument)

    @Query("DELETE FROM FavoriteDocument")
    suspend fun clearAll()

    @Query("UPDATE FavoriteDocument SET path = :newPath WHERE mediaId = :documentId")
    suspend fun updateName(documentId: Long, newPath: String)

    @Query("UPDATE OR REPLACE FavoriteDocument SET mediaId = :newId WHERE mediaId = :oldId")
    suspend fun updateId(oldId: Long, newId: Long)
}