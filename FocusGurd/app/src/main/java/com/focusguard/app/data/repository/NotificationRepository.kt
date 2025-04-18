package com.focusguard.app.data.repository

import com.focusguard.app.data.dao.NotificationDao
import com.focusguard.app.data.entity.Notification
import com.focusguard.app.data.entity.NotificationType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

class NotificationRepository(private val notificationDao: NotificationDao) {
    
    fun getAllNotifications(): Flow<List<Notification>> = notificationDao.getAllNotifications()
    
    suspend fun getAllNotificationsAsList(): List<Notification> = 
        notificationDao.getAllNotificationsSync()
    
    fun getPendingNotifications(now: LocalDateTime = LocalDateTime.now()): Flow<List<Notification>> = 
        notificationDao.getPendingNotifications(now)
    
    fun getNotificationsByType(type: NotificationType): Flow<List<Notification>> = 
        notificationDao.getNotificationsByType(type)
    
    fun getRecentNotifications(): Flow<List<Notification>> = notificationDao.getRecentNotifications()
    
    suspend fun getNotificationById(notificationId: Long): Notification? = 
        notificationDao.getNotificationById(notificationId)
    
    suspend fun addNotification(notification: Notification): Long = 
        notificationDao.insert(notification)
    
    suspend fun addNotifications(notifications: List<Notification>) = 
        notificationDao.insertAll(notifications)
    
    suspend fun updateNotification(notification: Notification) = 
        notificationDao.update(notification)
    
    suspend fun deleteNotification(notification: Notification) = 
        notificationDao.delete(notification)
    
    suspend fun deleteNotificationById(notificationId: Long) = 
        notificationDao.deleteById(notificationId)
    
    suspend fun cleanupOldNotifications(olderThan: LocalDateTime = LocalDateTime.now().minusDays(7)) = 
        notificationDao.deleteOldNotifications(olderThan)
    
    suspend fun deleteAllNotifications() = 
        notificationDao.deleteAllNotifications()
} 