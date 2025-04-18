package com.focusguard.app.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.focusguard.app.MyApplication
import com.focusguard.app.data.entity.BlockedAppEntity
import com.focusguard.app.data.repository.AppBlockRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for handling app block setup operations
 */
class AppBlockViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppBlockRepository = MyApplication.appBlockRepository
    private val TAG = "AppBlockViewModel"

    // LiveData for the current blocked app
    private val _blockedApp = MutableLiveData<BlockedAppEntity?>()
    val blockedApp: LiveData<BlockedAppEntity?> = _blockedApp

    // LiveData for operation result
    private val _operationResult = MutableLiveData<OperationResult>()
    val operationResult: LiveData<OperationResult> = _operationResult

    // LiveData for password
    private val _password = MutableLiveData<String>()
    val password: LiveData<String> = _password

    /**
     * Load a blocked app by package name
     */
    fun loadBlockedApp(packageName: String) {
        viewModelScope.launch {
            try {
                val app = repository.getBlockedApp(packageName)
                _blockedApp.value = app
            } catch (e: Exception) {
                Log.e(TAG, "Error loading blocked app: ${e.message}", e)
                _operationResult.value = OperationResult.Error("Failed to load app: ${e.message}")
            }
        }
    }

    /**
     * Save app blocking settings
     */
    fun saveBlockSettings(
        packageName: String,
        appName: String,
        startTime: String?,
        endTime: String?,
        blockAllDay: Boolean,
        enabledDays: Int,
        password: String
    ) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Saving block settings for $packageName")
                
                // Refresh repository cache first to ensure we have the latest data
                repository.refreshCache()
                
                // First ensure the app is in the blocked list
                repository.insertBlockedApp(packageName, appName, true)

                // Then update the schedule
                repository.updateBlockedAppSchedule(
                    packageName = packageName,
                    startTime = startTime,
                    endTime = endTime,
                    blockAllDay = blockAllDay,
                    enabledDays = enabledDays,
                    password = password
                )
                
                // Refresh cache again to ensure changes are reflected
                repository.refreshCache()

                _operationResult.value = OperationResult.Success("Block settings saved")
                
                Log.d(TAG, "Block settings saved successfully for $packageName")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving block settings: ${e.message}", e)
                _operationResult.value = OperationResult.Error("Failed to save settings: ${e.message}")
            }
        }
    }

    /**
     * Generate a new random 8-digit password
     */
    fun generateNewPassword() {
        val newPassword = repository.generateRandomPassword()
        _password.value = newPassword
    }

    /**
     * Verify if a password matches the stored password for an app
     */
    fun verifyPassword(packageName: String, password: String) {
        viewModelScope.launch {
            try {
                val isCorrect = repository.verifyPassword(packageName, password)
                if (isCorrect) {
                    _operationResult.value = OperationResult.Success("Password correct")
                } else {
                    _operationResult.value = OperationResult.Error("Incorrect password")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error verifying password: ${e.message}", e)
                _operationResult.value = OperationResult.Error("Failed to verify password: ${e.message}")
            }
        }
    }

    /**
     * Result class for operations
     */
    sealed class OperationResult {
        data class Success(val message: String) : OperationResult()
        data class Error(val message: String) : OperationResult()
    }
} 