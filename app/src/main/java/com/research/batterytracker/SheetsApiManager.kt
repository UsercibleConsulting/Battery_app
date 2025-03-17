package com.research.batterytracker

import android.content.Context
import android.database.Cursor
import android.util.Log
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.AppendValuesResponse
import com.google.api.services.sheets.v4.model.ValueRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Manager class for Google Sheets API integration
 */
class SheetsApiManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SheetsApiManager"
        private const val SHEET_RANGE = "A:C" // Columns for Device ID, Timestamp, Battery Percentage
        private const val VALUE_INPUT_OPTION = "RAW"
        private const val INSERT_DATA_OPTION = "INSERT_ROWS"
    }
    
    /**
     * Sync unsynced battery data to Google Sheets
     * Returns the number of rows successfully synced
     */
    suspend fun syncBatteryData(dbHelper: DatabaseHelper): Int = withContext(Dispatchers.IO) {
        val deviceId = DeviceUtils.getDeviceId(context)
        val sheetId = DeviceUtils.getSheetId(context) ?: return@withContext 0
        val accessToken = DeviceUtils.getAccessToken(context) ?: return@withContext 0
        
        // Get unsynced data from the database
        val cursor = dbHelper.getUnsyncedData()
        if (cursor.count == 0) {
            cursor.close()
            return@withContext 0
        }
        
        try {
            // Prepare data for Google Sheets
            val data = mutableListOf<List<Any>>()
            val syncedIds = mutableListOf<Long>()
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ID))
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TIMESTAMP))
                val batteryPercentage = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_BATTERY_PERCENTAGE))
                
                // Add row data (Device ID, Timestamp, Battery Percentage)
                data.add(listOf(deviceId, timestamp, batteryPercentage))
                syncedIds.add(id)
            }
            
            // Create the ValueRange object with the data
            val valueRange = ValueRange().setValues(data.map { it as MutableList<Any> })
            
            // Initialize the Sheets service
            val sheetsService = createSheetsService(accessToken)
            
            // Append the data to the sheet
            val response = sheetsService.spreadsheets().values()
                .append(sheetId, SHEET_RANGE, valueRange)
                .setValueInputOption(VALUE_INPUT_OPTION)
                .setInsertDataOption(INSERT_DATA_OPTION)
                .execute()
            
            // Mark data as synced if successfully appended
            if (response.updates != null) {
                val updatedRows = response.updates.updatedRows ?: 0
                if (updatedRows > 0) {
                    dbHelper.markAsSynced(syncedIds)
                    return@withContext updatedRows
                }
            }
            
            return@withContext 0
        } catch (e: IOException) {
            Log.e(TAG, "Error syncing data to Google Sheets", e)
            return@withContext 0
        } finally {
            cursor.close()
        }
    }
    
    /**
     * Create a Google Sheets service with the provided access token
     */
    private fun createSheetsService(accessToken: String): Sheets {
        val credential = GoogleCredential.Builder()
            .setTransport(NetHttpTransport())
            .setJsonFactory(JacksonFactory.getDefaultInstance())
            .build()
            .setAccessToken(accessToken)
        
        return Sheets.Builder(
            NetHttpTransport(),
            JacksonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("Battery Tracker")
            .build()
    }
    
    /**
     * Check if the sheet is accessible with the given credentials
     */
    suspend fun checkSheetAccess(sheetId: String, accessToken: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val sheetsService = createSheetsService(accessToken)
            // Try to get the metadata of the spreadsheet
            sheetsService.spreadsheets().get(sheetId).execute()
            return@withContext true
        } catch (e: IOException) {
            Log.e(TAG, "Error accessing Google Sheet", e)
            return@withContext false
        }
    }
} 