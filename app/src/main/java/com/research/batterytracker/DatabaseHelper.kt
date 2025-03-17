package com.research.batterytracker

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * DatabaseHelper for managing local SQLite storage of battery data
 */
class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "BatteryTracker.db"

        // Table and column names
        const val TABLE_BATTERY_DATA = "battery_data"
        const val COLUMN_ID = "id"
        const val COLUMN_DEVICE_ID = "device_id"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_BATTERY_PERCENTAGE = "battery_percentage"
        const val COLUMN_SYNCED = "synced"

        // Create table SQL query
        private const val SQL_CREATE_ENTRIES = """
            CREATE TABLE $TABLE_BATTERY_DATA (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_DEVICE_ID TEXT,
                $COLUMN_TIMESTAMP INTEGER,
                $COLUMN_BATTERY_PERCENTAGE INTEGER,
                $COLUMN_SYNCED INTEGER DEFAULT 0
            )
        """

        // Drop table SQL query
        private const val SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS $TABLE_BATTERY_DATA"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_ENTRIES)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // This database is only a cache for battery data, so its upgrade policy is
        // to simply discard the data and start over
        db.execSQL(SQL_DELETE_ENTRIES)
        onCreate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }

    /**
     * Insert a new battery data record
     */
    fun insertBatteryData(deviceId: String, timestamp: Long, batteryPercentage: Int): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_DEVICE_ID, deviceId)
            put(COLUMN_TIMESTAMP, timestamp)
            put(COLUMN_BATTERY_PERCENTAGE, batteryPercentage)
            put(COLUMN_SYNCED, 0)
        }
        return db.insert(TABLE_BATTERY_DATA, null, values)
    }

    /**
     * Get unsynced battery data
     */
    fun getUnsyncedData(): Cursor {
        val db = readableDatabase
        val selection = "$COLUMN_SYNCED = ?"
        val selectionArgs = arrayOf("0")
        
        return db.query(
            TABLE_BATTERY_DATA,
            null,
            selection,
            selectionArgs,
            null,
            null,
            "$COLUMN_TIMESTAMP ASC"
        )
    }

    /**
     * Mark records as synced
     */
    fun markAsSynced(ids: List<Long>): Int {
        if (ids.isEmpty()) return 0
        
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_SYNCED, 1)
        }
        
        val idList = ids.joinToString(",")
        val whereClause = "$COLUMN_ID IN ($idList)"
        
        return db.update(TABLE_BATTERY_DATA, values, whereClause, null)
    }

    /**
     * Clear old synced data to prevent the database from growing too large
     */
    fun clearSyncedData(): Int {
        val db = writableDatabase
        val whereClause = "$COLUMN_SYNCED = ?"
        val whereArgs = arrayOf("1")
        return db.delete(TABLE_BATTERY_DATA, whereClause, whereArgs)
    }
} 