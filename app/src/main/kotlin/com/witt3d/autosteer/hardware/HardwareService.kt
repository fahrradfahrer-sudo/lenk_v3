package com.witt3d.autosteer.hardware

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log

class HardwareService : Service() {
    private val TAG = "HardwareService"
    val imuHandler = ImuHandler()

    inner class HardwareBinder : Binder() {
        fun getService(): HardwareService = this@HardwareService
    }

    private val binder = HardwareBinder()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Hardware Service Created")
        // Auto-connect IMU on startup as per requirements
        imuHandler.connect()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Hardware Service Started")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        imuHandler.disconnect()
        Log.d(TAG, "Hardware Service Destroyed")
    }
}
