package com.mguuschedule.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RatingDao {
    @Query("SELECT * FROM rating_cache WHERE groupId = :groupId AND (zachetka = :zachetka OR :zachetka = '') AND (yearId = :yearId OR :yearId = '') AND (semId = :semId OR :semId = '') ORDER BY updatedAt DESC LIMIT 1")
    fun getRatingCacheFlow(groupId: String, zachetka: String, yearId: String, semId: String): Flow<RatingEntity?>

    @Query("SELECT * FROM rating_cache WHERE groupId = :groupId AND (zachetka = :zachetka OR :zachetka = '') AND (yearId = :yearId OR :yearId = '') AND (semId = :semId OR :semId = '') ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getRatingCache(groupId: String, zachetka: String, yearId: String, semId: String): RatingEntity?

    @Query("SELECT * FROM rating_cache ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestRatingCache(): RatingEntity?

    @Query("SELECT * FROM rating_cache ORDER BY updatedAt DESC LIMIT 1")
    fun getLatestRatingCacheFlow(): Flow<RatingEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRatingCache(rating: RatingEntity): Long

    @Query("DELETE FROM rating_cache WHERE groupId = :groupId")
    suspend fun clearGroupRatingCache(groupId: String): Int

    @Query("DELETE FROM rating_cache")
    suspend fun clearAllRatingCache(): Int
}
