package com.mguuschedule.repository

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lesson_notes",
    indices = [Index(value = ["lessonKey"], unique = true)]
)
data class LessonNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val lessonKey: String, // "${date}_${number}_${startTime}"
    val text: String,
    val updatedAt: Long
)

@Entity(
    tableName = "lesson_tasks",
    indices = [Index(value = ["lessonKey"])]
)
data class LessonTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val lessonKey: String, // "${date}_${number}_${startTime}"
    val title: String,
    val deadlineEpoch: Long? = null, // epoch millis
    val isCompleted: Boolean = false,
    val createdAt: Long
)

@Entity(
    tableName = "lesson_materials",
    indices = [Index(value = ["lessonKey"])]
)
data class LessonMaterialEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val lessonKey: String, // "${date}_${number}_${startTime}"
    val title: String,
    val type: String, // "URL", "PDF", "PPTX", "FILE"
    val uriOrUrl: String,
    val fileName: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
