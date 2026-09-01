package com.witt3d.autosteer.hardware.gps

import android.util.Base64
import android.util.Log
import com.witt3d.autosteer.data.models.NtripConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

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
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun updateConfig(newConfig: NtripConfig) {
        _config.value = newConfig
    }

    fun connect() {
        disconnect()

        val currentConfig = _config.value
        if (currentConfig.host.isBlank() || currentConfig.mountpoint.isBlank()) {
            _config.value = currentConfig.copy(
                isConnected = false,
                errorMessage = "Host and Mountpoint are required."
            )
            return
        }

        connectionJob = scope.launch {
            var socket: Socket? = null
            try {
                _config.value = currentConfig.copy(
                    isConnected = false,
                    errorMessage = "Connecting to NTRIP caster..."
                )

                socket = Socket()
                socket.connect(InetSocketAddress(currentConfig.host, currentConfig.port), 10000)
                activeSocket = socket

                val outputStream = socket.getOutputStream()
                activeOutputStream = outputStream
                val inputStream = socket.getInputStream()

                // Send HTTP / NTRIP request
                val reqBuilder = StringBuilder()
                reqBuilder.append("GET /${currentConfig.mountpoint} HTTP/1.0\r\n")
                reqBuilder.append("Host: ${currentConfig.host}:${currentConfig.port}\r\n")
                reqBuilder.append("User-Agent: NTRIP Witt3DAutoSteer/1.0\r\n")

                if (currentConfig.username.isNotEmpty() || currentConfig.password.isNotEmpty()) {
                    val auth = "${currentConfig.username}:${currentConfig.password}"
                    val authEncoded = Base64.encodeToString(auth.toByteArray(), Base64.NO_WRAP)
                    reqBuilder.append("Authorization: Basic $authEncoded\r\n")
                }
                reqBuilder.append("Accept: */*\r\n")
                reqBuilder.append("Connection: close\r\n\r\n")

                outputStream.write(reqBuilder.toString().toByteArray(Charsets.US_ASCII))
                outputStream.flush()

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

                _config.value = currentConfig.copy(
                    isConnected = true,
                    errorMessage = null
                )

                // Buffer for RTCM binary stream
                val buffer = ByteArray(4096)
                while (isActive) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break
                    if (bytesRead > 0) {
                        val rtcmChunk = buffer.copyOf(bytesRead)
                        onRtcmReceived(rtcmChunk)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "NTRIP Connection Exception", e)
                    _config.value = _config.value.copy(
                        isConnected = false,
                        errorMessage = e.message ?: "NTRIP connection failed"
                    )
                }
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

    fun disconnect() {
        ggaJob?.cancel()
        ggaJob = null
        connectionJob?.cancel()
        connectionJob = null
        try {
            activeSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing active socket on disconnect", e)
        }
        activeSocket = null
        activeOutputStream = null
        _config.value = _config.value.copy(isConnected = false)
    }

    private fun readLine(inputStream: InputStream): String? {
        val sb = StringBuilder()
        var c: Int
        while (inputStream.read().also { c = it } != -1) {
            if (c == '\r'.code) continue
            if (c == '\n'.code) break
            sb.append(c.toChar())
        }
        if (sb.isEmpty() && c == -1) return null
        return sb.toString()
    }
}
