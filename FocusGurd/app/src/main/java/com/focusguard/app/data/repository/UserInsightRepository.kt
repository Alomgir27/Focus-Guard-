package com.focusguard.app.data.repository

import com.focusguard.app.data.dao.UserInsightDao
import com.focusguard.app.data.entity.InsightSource
import com.focusguard.app.data.entity.UserInsight
import kotlinx.coroutines.flow.Flow

class UserInsightRepository(private val userInsightDao: UserInsightDao) {
    
    fun getAllActiveInsights(): Flow<List<UserInsight>> = userInsightDao.getAllActiveInsights()
    
    fun getArchivedInsights(): Flow<List<UserInsight>> = userInsightDao.getArchivedInsights()
    
    fun getInsightsBySource(source: InsightSource): Flow<List<UserInsight>> = 
        userInsightDao.getInsightsBySource(source)
    
    suspend fun getInsightById(insightId: Long): UserInsight? = 
        userInsightDao.getInsightById(insightId)
    
    suspend fun getNextUnshownInsight(): UserInsight? = 
        userInsightDao.getNextUnshownInsight()
    
    suspend fun addInsight(insight: UserInsight): Long = 
        userInsightDao.insert(insight)
    
    suspend fun addInsights(insights: List<UserInsight>) = 
        userInsightDao.insertAll(insights)
    
    suspend fun updateInsight(insight: UserInsight) = 
        userInsightDao.update(insight)
    
    suspend fun deleteInsight(insight: UserInsight) = 
        userInsightDao.delete(insight)
    
    suspend fun deleteInsightById(insightId: Long) = 
        userInsightDao.deleteById(insightId)
    
    suspend fun archiveInsight(insightId: Long) = 
        userInsightDao.archiveInsight(insightId)
} 