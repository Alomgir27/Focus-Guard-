package com.focusguard.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focusguard.app.data.entity.InsightSource
import com.focusguard.app.data.entity.UserInsight
import kotlinx.coroutines.flow.Flow

@Dao
interface UserInsightDao {
    @Query("SELECT * FROM user_insights WHERE isArchived = 0 ORDER BY createdAt DESC")
    fun getAllActiveInsights(): Flow<List<UserInsight>>
    
    @Query("SELECT * FROM user_insights WHERE isArchived = 1 ORDER BY createdAt DESC")
    fun getArchivedInsights(): Flow<List<UserInsight>>
    
    @Query("SELECT * FROM user_insights WHERE source = :source AND isArchived = 0 ORDER BY createdAt DESC")
    fun getInsightsBySource(source: InsightSource): Flow<List<UserInsight>>
    
    @Query("SELECT * FROM user_insights WHERE id = :insightId")
    suspend fun getInsightById(insightId: Long): UserInsight?
    
    @Query("SELECT * FROM user_insights WHERE wasShown = 0 AND isArchived = 0 ORDER BY createdAt DESC LIMIT 1")
    suspend fun getNextUnshownInsight(): UserInsight?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(insight: UserInsight): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(insights: List<UserInsight>)
    
    @Update
    suspend fun update(insight: UserInsight)
    
    @Delete
    suspend fun delete(insight: UserInsight)
    
    @Query("DELETE FROM user_insights WHERE id = :insightId")
    suspend fun deleteById(insightId: Long)
    
    @Query("UPDATE user_insights SET isArchived = 1 WHERE id = :insightId")
    suspend fun archiveInsight(insightId: Long)
} 