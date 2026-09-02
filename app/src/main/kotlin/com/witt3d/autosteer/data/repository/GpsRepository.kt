package com.witt3d.autosteer.data.repository

import android.content.Context
import com.witt3d.autosteer.data.models.GpsConfig
import com.witt3d.autosteer.data.models.NtripConfig
import com.witt3d.autosteer.data.models.NtripProfile
import org.json.JSONArray
import org.json.JSONObject

object GpsRepository {
    private const val PREFS_NAME = "gps_ntrip_prefs"

    private const val KEY_GPS_BAUD = "gps_baud_rate"
    private const val KEY_GPS_SIM = "gps_sim_mode"

    private const val KEY_NTRIP_PROFILES_JSON = "ntrip_profiles_json"
    private const val KEY_NTRIP_AUTOCONNECT = "ntrip_autoconnect"

    // Legacy keys for backward compatibility migration
    private const val KEY_NTRIP_HOST = "ntrip_host"
    private const val KEY_NTRIP_PORT = "ntrip_port"
    private const val KEY_NTRIP_MOUNTPOINT = "ntrip_mountpoint"
    private const val KEY_NTRIP_USERNAME = "ntrip_username"
    private const val KEY_NTRIP_PASSWORD = "ntrip_password"

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
        val autoConnect = prefs.getBoolean(KEY_NTRIP_AUTOCONNECT, false)
        val jsonStr = prefs.getString(KEY_NTRIP_PROFILES_JSON, null)

        val profiles = mutableListOf<NtripProfile>()
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    profiles.add(
                        NtripProfile(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            name = obj.optString("name", "NTRIP Service ${i + 1}"),
                            host = obj.optString("host", ""),
                            port = obj.optInt("port", 2101),
                            mountpoint = obj.optString("mountpoint", ""),
                            username = obj.optString("username", ""),
                            password = obj.optString("password", ""),
                            enabled = obj.optBoolean("enabled", true),
                            priority = obj.optInt("priority", i)
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // Migrate single legacy config if present
            val legacyHost = prefs.getString(KEY_NTRIP_HOST, "") ?: ""
            if (legacyHost.isNotEmpty()) {
                profiles.add(
                    NtripProfile(
                        name = "Default Service",
                        host = legacyHost,
                        port = prefs.getInt(KEY_NTRIP_PORT, 2101),
                        mountpoint = prefs.getString(KEY_NTRIP_MOUNTPOINT, "") ?: "",
                        username = prefs.getString(KEY_NTRIP_USERNAME, "") ?: "",
                        password = prefs.getString(KEY_NTRIP_PASSWORD, "") ?: "",
                        enabled = true,
                        priority = 0
                    )
                )
            }
        }

        // Sort by priority ascending
        profiles.sortBy { it.priority }

        return NtripConfig(
            profiles = profiles,
            autoConnect = autoConnect
        )
    }

    fun saveNtripConfig(context: Context, config: NtripConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        config.profiles.forEachIndexed { index, profile ->
            val obj = JSONObject()
            obj.put("id", profile.id)
            obj.put("name", profile.name)
            obj.put("host", profile.host)
            obj.put("port", profile.port)
            obj.put("mountpoint", profile.mountpoint)
            obj.put("username", profile.username)
            obj.put("password", profile.password)
            obj.put("enabled", profile.enabled)
            obj.put("priority", index)
            array.put(obj)
        }

        prefs.edit()
            .putString(KEY_NTRIP_PROFILES_JSON, array.toString())
            .putBoolean(KEY_NTRIP_AUTOCONNECT, config.autoConnect)
            .apply()
    }
}
