package com.witt3d.autosteer.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.witt3d.autosteer.data.models.GpsConfig
import com.witt3d.autosteer.data.models.NtripConfig

object GpsRepository {
    private const val PREFS_NAME = "gps_ntrip_prefs"

    private const val KEY_GPS_BAUD = "gps_baud_rate"
    private const val KEY_GPS_SIM = "gps_sim_mode"

    private const val KEY_NTRIP_HOST = "ntrip_host"
    private const val KEY_NTRIP_PORT = "ntrip_port"
    private const val KEY_NTRIP_MOUNTPOINT = "ntrip_mountpoint"
    private const val KEY_NTRIP_USERNAME = "ntrip_username"
    private const val KEY_NTRIP_PASSWORD = "ntrip_password"
    private const val KEY_NTRIP_AUTOCONNECT = "ntrip_autoconnect"

    fun loadGpsConfig(context: Context): GpsConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return GpsConfig(
            baudRate = prefs.getInt(KEY_GPS_BAUD, 115200),
            isSimulationMode = prefs.getBoolean(KEY_GPS_SIM, false)
        )
    }

    fun saveGpsConfig(context: Context, config: GpsConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_GPS_BAUD, config.baudRate)
            .putBoolean(KEY_GPS_SIM, config.isSimulationMode)
            .apply()
    }

    fun loadNtripConfig(context: Context): NtripConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return NtripConfig(
            host = prefs.getString(KEY_NTRIP_HOST, "") ?: "",
            port = prefs.getInt(KEY_NTRIP_PORT, 2101),
            mountpoint = prefs.getString(KEY_NTRIP_MOUNTPOINT, "") ?: "",
            username = prefs.getString(KEY_NTRIP_USERNAME, "") ?: "",
            password = prefs.getString(KEY_NTRIP_PASSWORD, "") ?: "",
            autoConnect = prefs.getBoolean(KEY_NTRIP_AUTOCONNECT, false)
        )
    }

    fun saveNtripConfig(context: Context, config: NtripConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_NTRIP_HOST, config.host)
            .putInt(KEY_NTRIP_PORT, config.port)
            .putString(KEY_NTRIP_MOUNTPOINT, config.mountpoint)
            .putString(KEY_NTRIP_USERNAME, config.username)
            .putString(KEY_NTRIP_PASSWORD, config.password)
            .putBoolean(KEY_NTRIP_AUTOCONNECT, config.autoConnect)
            .apply()
    }
}
