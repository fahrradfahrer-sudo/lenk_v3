package com.witt3d.autosteer.hardware.gps

import android.util.Base64
import android.util.Log
import com.witt3d.autosteer.data.models.NtripConfig
import com.witt3d.autosteer.data.models.NtripProfile
import com.witt3d.autosteer.data.models.NtripStreamStats
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

class NtripClient(
    private val onRtcmReceived: (ByteArray) -> Unit
) {
    private val TAG = "NtripClient"

    private val _config = MutableStateFlow(NtripConfig())
    val config: StateFlow<NtripConfig> = _config.asStateFlow()

    private var activeSocket: Socket? = null
    private var activeOutputStream: OutputStream? = null

    private var connectionJob: Job? = null
    private var ggaJob: Job? = null
    private var statsJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Datastream counters
    private val rxBytesCurrentSec = AtomicLong(0)
    private val txBytesCurrentSec = AtomicLong(0)
    private val totalRxBytes = AtomicLong(0)
    private val totalTxBytes = AtomicLong(0)

    init {
        startStatsCalculator()
    }

    fun updateConfig(newConfig: NtripConfig) {
        _config.value = newConfig
    }

    private fun startStatsCalculator() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                delay(1000)
                val rxSec = rxBytesCurrentSec.getAndSet(0)
                val txSec = txBytesCurrentSec.getAndSet(0)

                val currentStats = _config.value.stats.copy(
                    bytesReceivedPerSec = rxSec,
                    bytesSentPerSec = txSec,
                    totalBytesReceived = totalRxBytes.get(),
                    totalBytesSent = totalTxBytes.get()
                )
                _config.value = _config.value.copy(stats = currentStats)
            }
        }
    }

    fun connect() {
        disconnectInternal()

        val enabledProfiles = _config.value.profiles
            .filter { it.enabled && it.host.isNotBlank() && it.mountpoint.isNotBlank() }
            .sortedBy { it.priority }

        if (enabledProfiles.isEmpty()) {
            _config.value = _config.value.copy(
                isConnected = false,
                activeProfileId = null,
                errorMessage = "No enabled NTRIP profiles configured."
            )
            return
        }

        connectionJob = scope.launch {
            for (profile in enabledProfiles) {
                if (!isActive) break

                Log.d(TAG, "Attempting connection to NTRIP profile: ${profile.name} (${profile.host}:${profile.port}/${profile.mountpoint})")
                _config.value = _config.value.copy(
                    isConnected = false,
                    activeProfileId = profile.id,
                    errorMessage = "Connecting to ${profile.name}..."
                )

                val success = connectToProfile(profile)
                if (success) {
                    // Connected and streaming. connectToProfile blocks while connection remains alive.
                    Log.d(TAG, "NTRIP connection closed for profile ${profile.name}")
                } else {
                    Log.w(TAG, "Failed connection to profile ${profile.name}, attempting next profile if available...")
                }
            }

            if (isActive && !_config.value.isConnected) {
                _config.value = _config.value.copy(
                    isConnected = false,
                    activeProfileId = null,
                    errorMessage = "All enabled NTRIP profiles failed to connect."
                )
            }
        }
    }

    private suspend fun connectToProfile(profile: NtripProfile): Boolean {
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(profile.host, profile.port), 10000)
            activeSocket = socket

            val outputStream = socket.getOutputStream()
            activeOutputStream = outputStream
            val inputStream = socket.getInputStream()

            // Construct HTTP / NTRIP request
            val reqBuilder = StringBuilder()
            reqBuilder.append("GET /${profile.mountpoint} HTTP/1.0\r\n")
            reqBuilder.append("Host: ${profile.host}:${profile.port}\r\n")
            reqBuilder.append("User-Agent: NTRIP Witt3DAutoSteer/1.0\r\n")

            if (profile.username.isNotEmpty() || profile.password.isNotEmpty()) {
                val auth = "${profile.username}:${profile.password}"
                val authEncoded = Base64.encodeToString(auth.toByteArray(), Base64.NO_WRAP)
                reqBuilder.append("Authorization: Basic $authEncoded\r\n")
            }
            reqBuilder.append("Accept: */*\r\n")
            reqBuilder.append("Connection: close\r\n\r\n")

            val reqBytes = reqBuilder.toString().toByteArray(Charsets.US_ASCII)
            outputStream.write(reqBytes)
            outputStream.flush()

            txBytesCurrentSec.addAndGet(reqBytes.size.toLong())
            totalTxBytes.addAndGet(reqBytes.size.toLong())

            // Read response headers
            val responseLine = readLine(inputStream)
            Log.d(TAG, "NTRIP Response: $responseLine")

            if (responseLine == null || (!responseLine.contains("200 OK") && !responseLine.contains("ICY 200"))) {
                throw Exception("NTRIP Server response: $responseLine")
            }

            // Skip header trailing lines
            var line: String?
            do {
                line = readLine(inputStream)
            } while (line != null && line.isNotEmpty())

            _config.value = _config.value.copy(
                isConnected = true,
                activeProfileId = profile.id,
                errorMessage = null
            )

            // Buffer for RTCM binary stream
            val buffer = ByteArray(4096)
            while (currentCoroutineContext().isActive) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break
                if (bytesRead > 0) {
                    rxBytesCurrentSec.addAndGet(bytesRead.toLong())
                    totalRxBytes.addAndGet(bytesRead.toLong())

                    val rtcmChunk = buffer.copyOf(bytesRead)
                    onRtcmReceived(rtcmChunk)
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "NTRIP Connection Exception for profile ${profile.name}", e)
            return false
        } finally {
            activeOutputStream = null
            activeSocket = null
            try {
                socket?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing socket", e)
            }
            _config.value = _config.value.copy(isConnected = false)
        }
    }

    fun startGgaReporting(lastRawGgaFlow: StateFlow<String?>) {
        ggaJob?.cancel()
        ggaJob = scope.launch {
            while (isActive) {
                delay(1000) // Send every 1s
                if (_config.value.isConnected) {
                    val gga = lastRawGgaFlow.value
                    if (!gga.isNullOrEmpty()) {
                        val out = activeOutputStream
                        if (out != null) {
                            try {
                                val bytes = (gga + "\r\n").toByteArray(Charsets.US_ASCII)
                                out.write(bytes)
                                out.flush()

                                txBytesCurrentSec.addAndGet(bytes.size.toLong())
                                totalTxBytes.addAndGet(bytes.size.toLong())

                                Log.d(TAG, "Sent NMEA GGA to NTRIP caster: $gga")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to send GGA to NTRIP caster", e)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun disconnectInternal() {
        connectionJob?.cancel()
        connectionJob = null
        try {
            activeSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing active socket on disconnect", e)
        }
        activeSocket = null
        activeOutputStream = null
        _config.value = _config.value.copy(
            isConnected = false,
            activeProfileId = null
        )
    }

    fun disconnect() {
        ggaJob?.cancel()
        ggaJob = null
        disconnectInternal()
    }

    private fun readLine(inputStream: InputStream): String? {
        val sb = StringBuilder()
        var c: Int
        while (inputStream.read().also { c = it } != -1) {
            rxBytesCurrentSec.incrementAndGet()
            totalRxBytes.incrementAndGet()

            if (c == '\r'.code) continue
            if (c == '\n'.code) break
            sb.append(c.toChar())
        }
        if (sb.isEmpty() && c == -1) return null
        return sb.toString()
    }
}
