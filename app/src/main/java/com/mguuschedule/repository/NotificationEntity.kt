package com.mguuschedule.repository

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val message: String,
    val type: String, // "CHANGE", "REMINDER", "SYSTEM"
    val timestamp: Long, // epoch millis
    val isRead: Boolean = false
)
