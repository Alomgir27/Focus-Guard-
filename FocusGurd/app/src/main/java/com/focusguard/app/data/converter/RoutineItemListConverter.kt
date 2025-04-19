package com.focusguard.app.data.converter

import androidx.room.TypeConverter
import com.focusguard.app.data.entity.RoutineItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RoutineItemListConverter {
    private val gson = Gson()
    
    @TypeConverter
    fun fromRoutineItemList(items: List<RoutineItem>): String {
        return gson.toJson(items)
    }
    
    @TypeConverter
    fun toRoutineItemList(json: String): List<RoutineItem> {
        val type = object : TypeToken<List<RoutineItem>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
} 