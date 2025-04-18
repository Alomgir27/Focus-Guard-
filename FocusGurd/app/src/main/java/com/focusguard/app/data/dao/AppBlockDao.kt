package com.focusguard.app.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusguard.app.data.entity.BlockedAppEntity

@Dao
interface AppBlockDao {
    // BlockedAppEntity operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedApp(blockedApp: BlockedAppEntity)

    @Query("UPDATE blocked_apps SET isActive = :isActive WHERE packageName = :packageName")
    suspend fun updateBlockedAppStatus(packageName: String, isActive: Boolean)

    @Query("UPDATE blocked_apps SET startTime = :startTime, endTime = :endTime, blockAllDay = :blockAllDay, enabledDays = :enabledDays, password = :password WHERE packageName = :packageName")
    suspend fun updateBlockedAppSchedule(packageName: String, startTime: String?, endTime: String?, blockAllDay: Boolean, enabledDays: Int, password: String?)

    @Query("SELECT * FROM blocked_apps")
    suspend fun getAllBlockedApps(): List<BlockedAppEntity>
    
    @Query("SELECT * FROM blocked_apps")
    fun getAllBlockedAppsLiveData(): LiveData<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps WHERE isActive = 1")
    suspend fun getActiveBlockedApps(): List<BlockedAppEntity>

    @Query("SELECT * FROM blocked_apps WHERE packageName = :packageName")
    suspend fun getBlockedApp(packageName: String): BlockedAppEntity?

    @Query("DELETE FROM blocked_apps WHERE packageName = :packageName")
    suspend fun deleteBlockedApp(packageName: String)

    @Query("UPDATE blocked_apps SET password = :password WHERE packageName = :packageName")
    suspend fun updateBlockedAppPassword(packageName: String, password: String)
} 