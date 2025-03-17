package com.research.batterytracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Foreground service for tracking battery levels every second
 */
class BatteryTrackingService : Service() {
    
    companion object {
        private const val TAG = "BatteryTrackingService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "battery_tracker_channel"
        private const val TEN_HOURS_IN_MILLIS = 10 * 60 * 60 * 1000L // 10 hours in milliseconds
        private const val SYNC_INTERVAL_MILLIS = 60 * 1000L // Sync every minute
        
        // Intent action to manually stop the service
        const val ACTION_STOP_SERVICE = "com.research.batterytracker.STOP_SERVICE"
    }
    
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    
    private lateinit var batteryManager: BatteryManager
    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var sheetsApiManager: SheetsApiManager
    private lateinit var deviceId: String
    
    private val batteryHandler = Handler(Looper.getMainLooper())
    private val syncHandler = Handler(Looper.getMainLooper())
    
    private var startTime = 0L
    private var lastSyncTime = 0L
    
    private val batteryRunnable = object : Runnable {
        override fun run() {
            // Check if 10 hours have elapsed
            val currentTime = System.currentTimeMillis()
            if (currentTime - startTime >= TEN_HOURS_IN_MILLIS) {
                Log.i(TAG, "10 hours completed, stopping service")
                stopSelf()
                return
            }
            
            // Get battery level
            val batteryPercentage = getBatteryPercentage()
            Log.d(TAG, "Battery level: $batteryPercentage%")
            
            // Store in database
            databaseHelper.insertBatteryData(deviceId, currentTime, batteryPercentage)
            
            // Schedule next check
            batteryHandler.postDelayed(this, 1000) // Run every second
        }
    }
    
    private val syncRunnable = object : Runnable {
        override fun run() {
            // Sync data to Google Sheets
            serviceScope.launch {
                try {
                    val syncedRows = sheetsApiManager.syncBatteryData(databaseHelper)
                    if (syncedRows > 0) {
                        Log.i(TAG, "Synced $syncedRows rows to Google Sheets")
                        // Periodically clear old synced data
                        databaseHelper.clearSyncedData()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during sync", e)
                }
            }
            
            // Schedule next sync
            syncHandler.postDelayed(this, SYNC_INTERVAL_MILLIS)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        databaseHelper = DatabaseHelper(this)
        sheetsApiManager = SheetsApiManager(this)
        deviceId = DeviceUtils.getDeviceId(this)
        
        // Create notification channel for Android 8+
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        
        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.i(TAG, "Stop service action received")
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Start as a foreground service
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Record start time
        startTime = System.currentTimeMillis()
        lastSyncTime = startTime
        
        // Start the battery monitoring task
        batteryHandler.post(batteryRunnable)
        
        // Start the sync task
        syncHandler.post(syncRunnable)
        
        // If service is killed, restart it
        return START_STICKY
    }
    
    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        
        // Remove callbacks to prevent memory leaks
        batteryHandler.removeCallbacks(batteryRunnable)
        syncHandler.removeCallbacks(syncRunnable)
        
        // Cancel the coroutine job
        serviceJob.cancel()
        
        // Do a final sync before stopping
        serviceScope.launch {
            try {
                sheetsApiManager.syncBatteryData(databaseHelper)
            } catch (e: Exception) {
                Log.e(TAG, "Error during final sync", e)
            }
        }
        
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    /**
     * Get the current battery percentage
     */
    private fun getBatteryPercentage(): Int {
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
    
    /**
     * Create the notification channel (required for Android 8+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.app_name)
            val description = getString(R.string.service_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                this.description = description
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Create the foreground service notification
     */
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        
        // Create a stop action for the notification
        val stopIntent = Intent(this, BatteryTrackingService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_running))
            .setContentText(getString(R.string.service_description))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop_service), stopPendingIntent)
            .setOngoing(true)
            .build()
    }
} 