package com.mguuschedule.repository

import androidx.room.*
import com.mguuschedule.model.LessonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM lessons WHERE date = :date")
    fun getLessonsForDate(date: String): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons")
    suspend fun getAllLessons(): List<LessonEntity>

    @Query("SELECT * FROM lessons")
    fun getAllLessonsFlow(): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE date >= :startDate")
    suspend fun getUpcomingLessons(startDate: String): List<LessonEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLessons(lessons: List<LessonEntity>): List<Long>

    @Query("DELETE FROM lessons WHERE date BETWEEN :startDate AND :endDate")
    suspend fun deleteRange(startDate: String, endDate: String): Int

    @Query("DELETE FROM lessons")
    suspend fun clearSchedule(): Int

    @Transaction
    suspend fun replacePeriod(lessons: List<LessonEntity>, startDate: String, endDate: String): Int {
        deleteRange(startDate, endDate)
        insertLessons(lessons)
        return 0
    }

    @Query("SELECT COUNT(*) FROM lessons")
    suspend fun getLessonsCount(): Int

    @Query("SELECT COUNT(*) FROM lessons")
    fun getTotalLessonsCountFlow(): Flow<Int>

    @Query("SELECT MIN(date) FROM lessons")
    suspend fun getEarliestDate(): String?

    @Query("SELECT MAX(date) FROM lessons")
    suspend fun getLatestDate(): String?
}
