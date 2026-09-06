package com.mguuschedule.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity): Long

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotificationsFlow(): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun getUnreadCountFlow(): Flow<Int>

    @Query("UPDATE notifications SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllAsRead(): Int

    @Query("DELETE FROM notifications")
    suspend fun clearAll(): Int
}
