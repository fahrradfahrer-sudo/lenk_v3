package com.witt3d.autosteer

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.witt3d.autosteer.ui.navigation.MainNavigation

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permissions results if necessary
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            com.phidget22.usb.Manager.Initialize(this)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to initialize Phidgets USB Manager", e)
        }

        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        startHardwareService()
        scanAndConnectHardware()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        scanAndConnectHardware()
    }

    private fun scanAndConnectHardware() {
        try {
            com.witt3d.autosteer.data.repository.HardwareRepository.scanAndConnectAll(this)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to scan and connect hardware", e)
        }
    }

    private fun startHardwareService() {
        try {
            val intent = android.content.Intent(this, com.witt3d.autosteer.hardware.HardwareService::class.java)
            startService(intent)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to start HardwareService", e)
        }
    }
}
