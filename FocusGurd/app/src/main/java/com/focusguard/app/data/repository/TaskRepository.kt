package com.focusguard.app.data.repository

import androidx.lifecycle.LiveData
import com.focusguard.app.data.dao.TaskDao
import com.focusguard.app.data.entity.Task
import java.time.LocalDate

class TaskRepository(private val taskDao: TaskDao) {
    
    val allTasks: LiveData<List<Task>> = taskDao.getAllTasks()
    val activeTasks: LiveData<List<Task>> = taskDao.getActiveTasks()
    val completedTasks: LiveData<List<Task>> = taskDao.getCompletedTasks()
    
    suspend fun insert(task: Task) {
        taskDao.insert(task)
    }
    
    suspend fun update(task: Task) {
        taskDao.update(task)
    }
    
    suspend fun delete(task: Task) {
        taskDao.delete(task)
    }
    
    suspend fun getTaskById(id: Int): Task? {
        return taskDao.getTaskById(id)
    }
    
    suspend fun setTaskCompleted(taskId: Int, isCompleted: Boolean) {
        taskDao.updateTaskCompletionStatus(taskId, isCompleted)
    }
    
    suspend fun getActiveTasksSync(): List<Task> {
        return taskDao.getActiveTasksSync()
    }
    
    suspend fun getTasksForDate(date: LocalDate): List<Task> {
        // Get active tasks and filter by date
        return getActiveTasksSync().filter { task ->
            task.dueDate.toLocalDate() == date
        }
    }
} 