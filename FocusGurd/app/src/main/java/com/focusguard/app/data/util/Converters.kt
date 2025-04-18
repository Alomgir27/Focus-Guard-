package com.focusguard.app.data.util

import android.util.Log
import androidx.room.TypeConverter
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class Converters {
    private val TAG = "Converters"
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    
    @TypeConverter
    fun fromTimestamp(value: String?): LocalDateTime? {
        return try {
            value?.let { LocalDateTime.parse(it, dateTimeFormatter) }
        } catch (e: DateTimeParseException) {
            Log.e(TAG, "Error parsing datetime: $value", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in fromTimestamp: ${e.message}", e)
            null
        }
    }

    @TypeConverter
    fun toTimestamp(date: LocalDateTime?): String? {
        return try {
            date?.format(dateTimeFormatter)
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting datetime: $date", e)
            null
        }
    }
    
    @TypeConverter
    fun fromDaysList(days: List<DayOfWeek>?): String? {
        return try {
            days?.joinToString(",") { it.value.toString() }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting days list to string: $days", e)
            null
        }
    }
    
    @TypeConverter
    fun toDaysList(daysString: String?): List<DayOfWeek>? {
        return try {
            daysString?.split(",")?.mapNotNull { 
                try {
                    DayOfWeek.of(it.trim().toInt())
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing day value: $it", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting string to days list: $daysString", e)
            null
        }
    }
} 