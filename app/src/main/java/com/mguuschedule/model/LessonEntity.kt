package com.mguuschedule.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lessons",
    indices = [Index(value = ["date", "lessonNumber", "startTime"], unique = true)]
)
data class LessonEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,
    val lessonNumber: Int,
    val startTime: String,
    val endTime: String,
    val subject: String,
    val teacher: String,
    val classroom: String,
    val type: String
)
