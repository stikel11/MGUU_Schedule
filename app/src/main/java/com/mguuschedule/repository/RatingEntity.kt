package com.mguuschedule.repository

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rating_cache",
    indices = [Index(value = ["groupId", "zachetka", "yearId", "semId"], unique = true)]
)
data class RatingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupId: String,
    val zachetka: String,
    val yearId: String,
    val semId: String,
    val studentJson: String,
    val subjectsJson: String,
    val studentsListJson: String,
    val controlPointsJson: String = "{}",
    val updatedAt: Long
)
