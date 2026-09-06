package com.mguuschedule.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonAddonsDao {
    // Note
    @Query("SELECT * FROM lesson_notes WHERE lessonKey = :lessonKey LIMIT 1")
    fun getNoteFlow(lessonKey: String): Flow<LessonNoteEntity?>

    @Query("SELECT * FROM lesson_notes WHERE lessonKey = :lessonKey LIMIT 1")
    suspend fun getNote(lessonKey: String): LessonNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNote(note: LessonNoteEntity): Long

    @Query("DELETE FROM lesson_notes WHERE lessonKey = :lessonKey")
    suspend fun deleteNote(lessonKey: String): Int

    // Tasks
    @Query("SELECT * FROM lesson_tasks WHERE lessonKey = :lessonKey ORDER BY isCompleted ASC, createdAt DESC")
    fun getTasksFlow(lessonKey: String): Flow<List<LessonTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: LessonTaskEntity): Long

    @Update
    suspend fun updateTask(task: LessonTaskEntity): Int

    @Query("DELETE FROM lesson_tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: Long): Int

    @Query("UPDATE lesson_tasks SET isCompleted = :isCompleted WHERE id = :taskId")
    suspend fun setTaskCompleted(taskId: Long, isCompleted: Boolean): Int

    // Keys with active addons (notes or uncompleted tasks)
    @Query("""
        SELECT DISTINCT lessonKey FROM (
            SELECT lessonKey FROM lesson_notes WHERE text != ''
            UNION
            SELECT lessonKey FROM lesson_tasks WHERE isCompleted = 0
        )
    """)
    fun getActiveAddonLessonKeysFlow(): Flow<List<String>>
}
