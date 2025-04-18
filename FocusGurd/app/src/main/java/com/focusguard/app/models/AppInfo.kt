package com.focusguard.app.models

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    var isBlocked: Boolean = false,
    val isSystemApp: Boolean = false,
    val canBeBlocked: Boolean = true
) {
    // Override equals to compare all relevant fields except Drawable (which doesn't support equals properly)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppInfo) return false

        if (packageName != other.packageName) return false
        if (appName != other.appName) return false
        if (isBlocked != other.isBlocked) return false
        if (isSystemApp != other.isSystemApp) return false
        if (canBeBlocked != other.canBeBlocked) return false

        return true
    }

    override fun hashCode(): Int {
        var result = packageName.hashCode()
        result = 31 * result + appName.hashCode()
        result = 31 * result + isBlocked.hashCode()
        result = 31 * result + isSystemApp.hashCode()
        result = 31 * result + canBeBlocked.hashCode()
        return result
    }
} 