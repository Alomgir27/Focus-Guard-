package com.focusguard.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focusguard.app.data.entity.Notification
import com.focusguard.app.data.entity.NotificationType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun getAllNotifications(): Flow<List<Notification>>
    
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    suspend fun getAllNotificationsSync(): List<Notification>
    
    @Query("SELECT * FROM notifications WHERE wasShown = 0 AND (scheduledFor IS NULL OR scheduledFor <= :now) ORDER BY scheduledFor, createdAt")
    fun getPendingNotifications(now: LocalDateTime): Flow<List<Notification>>
    
    @Query("SELECT * FROM notifications WHERE type = :type ORDER BY createdAt DESC")
    fun getNotificationsByType(type: NotificationType): Flow<List<Notification>>
    
    @Query("SELECT * FROM notifications WHERE id = :notificationId")
    suspend fun getNotificationById(notificationId: Long): Notification?
    
    @Query("SELECT * FROM notifications WHERE wasShown = 1 ORDER BY createdAt DESC LIMIT 10")
    fun getRecentNotifications(): Flow<List<Notification>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: Notification): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notifications: List<Notification>)
    
    @Update
    suspend fun update(notification: Notification)
    
    @Delete
    suspend fun delete(notification: Notification)
    
    @Query("DELETE FROM notifications WHERE id = :notificationId")
    suspend fun deleteById(notificationId: Long)
    
    @Query("DELETE FROM notifications WHERE wasShown = 1 AND createdAt < :olderThan")
    suspend fun deleteOldNotifications(olderThan: LocalDateTime)
    
    @Query("DELETE FROM notifications")
    suspend fun deleteAllNotifications()
} 