package com.witt3d.autosteer.hardware

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.witt3d.autosteer.data.repository.GpsRepository
import com.witt3d.autosteer.data.repository.HardwareRepository
import com.witt3d.autosteer.data.repository.MotorRepository

class HardwareService : Service() {
    private val TAG = "HardwareService"
    val imuHandler = HardwareRepository.imuHandler
    val gpsHandler = HardwareRepository.gpsHandler
    val ntripClient = HardwareRepository.ntripClient
    val motorHandler = HardwareRepository.motorHandler

    inner class HardwareBinder : Binder() {
        fun getService(): HardwareService = this@HardwareService
    }

    private val binder = HardwareBinder()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Hardware Service Created")

        try {
            com.phidget22.usb.Manager.Initialize(this.applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Phidgets USB Manager in HardwareService", e)
        }

        // Load persisted GPS, NTRIP, and Motor configurations
        val savedGpsConfig = GpsRepository.loadGpsConfig(this)
        gpsHandler.updateConfig(savedGpsConfig)

        val savedNtripConfig = GpsRepository.loadNtripConfig(this)
        ntripClient.updateConfig(savedNtripConfig)

        val savedMotorConfig = MotorRepository.loadMotorConfig(this)
        motorHandler.updateConfig(savedMotorConfig)

        // Auto-connect hardware on startup
        imuHandler.connect(this)

        gpsHandler.scanUsbDevices(this)
        gpsHandler.connect(this)

        motorHandler.scanUsbDevices(this)
        motorHandler.connect(this)

        ntripClient.startGgaReporting(gpsHandler.lastRawGga)
        if (savedNtripConfig.autoConnect) {
            ntripClient.connect()
        }
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
        gpsHandler.disconnect()
        ntripClient.disconnect()
        motorHandler.disconnect()

        try {
            com.phidget22.usb.Manager.Uninitialize()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to uninitialize Phidgets USB Manager in HardwareService", e)
        }

        Log.d(TAG, "Hardware Service Destroyed")
    }
}
