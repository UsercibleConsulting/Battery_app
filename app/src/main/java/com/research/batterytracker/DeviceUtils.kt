package com.research.batterytracker

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * Utility class for device identification
 */
object DeviceUtils {
    private const val PREFS_NAME = "BatteryTrackerPrefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_SHEET_ID = "sheet_id"
    private const val KEY_ACCESS_TOKEN = "access_token"

    /**
     * Get or create a unique device ID
     */
    fun getDeviceId(context: Context): String {
        val prefs = getPreferences(context)
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        
        if (deviceId == null) {
            // Generate a new UUID if no device ID exists
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        
        return deviceId
    }

    /**
     * Store the Google Sheet ID
     */
    fun saveSheetId(context: Context, sheetId: String) {
        getPreferences(context).edit().putString(KEY_SHEET_ID, sheetId).apply()
    }

    /**
     * Get the stored Google Sheet ID
     */
    fun getSheetId(context: Context): String? {
        return getPreferences(context).getString(KEY_SHEET_ID, null)
    }

    /**
     * Store the access token for Google Sheets API
     */
    fun saveAccessToken(context: Context, token: String) {
        getPreferences(context).edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    /**
     * Get the stored access token
     */
    fun getAccessToken(context: Context): String? {
        return getPreferences(context).getString(KEY_ACCESS_TOKEN, null)
    }
    
    /**
     * Clear authentication data - useful for troubleshooting sign-in issues
     */
    fun clearAuthData(context: Context) {
        getPreferences(context).edit().remove(KEY_ACCESS_TOKEN).apply()
    }

    /**
     * Get the SharedPreferences instance
     */
    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
} 