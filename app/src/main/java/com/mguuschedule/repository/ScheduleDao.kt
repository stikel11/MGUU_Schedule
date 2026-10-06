package com.mguuschedule.repository

import androidx.room.*
import com.mguuschedule.model.LessonEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ScheduleDao {
    @Query("SELECT * FROM lessons WHERE date = :date")
    abstract fun getLessonsForDate(date: String): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons")
    abstract suspend fun getAllLessons(): List<LessonEntity>

    @Query("SELECT * FROM lessons")
    abstract fun getAllLessonsFlow(): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE date >= :startDate")
    abstract suspend fun getUpcomingLessons(startDate: String): List<LessonEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertLessons(lessons: List<LessonEntity>): List<Long>

    @Query("DELETE FROM lessons WHERE date BETWEEN :startDate AND :endDate")
    abstract suspend fun deleteRange(startDate: String, endDate: String): Int

    @Query("DELETE FROM lessons")
    abstract suspend fun clearSchedule(): Int

    @Transaction
    open suspend fun replacePeriod(lessons: List<LessonEntity>, startDate: String, endDate: String): Int {
        deleteRange(startDate, endDate)
        insertLessons(lessons)
        return 0
    }

    @Transaction
    open suspend fun replaceAll(lessons: List<LessonEntity>): Int {
        clearSchedule()
        insertLessons(lessons)
        return 0
    }

    @Query("SELECT COUNT(*) FROM lessons")
    abstract suspend fun getLessonsCount(): Int

    @Query("SELECT COUNT(*) FROM lessons")
    abstract fun getTotalLessonsCountFlow(): Flow<Int>

    @Query("SELECT MIN(date) FROM lessons")
    abstract suspend fun getEarliestDate(): String?

    @Query("SELECT MAX(date) FROM lessons")
    abstract suspend fun getLatestDate(): String?
}
