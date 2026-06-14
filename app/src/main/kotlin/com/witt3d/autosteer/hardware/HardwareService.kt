package com.witt3d.autosteer.hardware

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class HardwareService : Service() {
    private val TAG = "HardwareService"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Hardware Service Created")
        // TODO: Initialize USB and Tinkerforge listeners
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Hardware Service Started")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Hardware Service Destroyed")
    }
}
