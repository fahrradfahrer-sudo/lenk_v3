package com.witt3d.autosteer.viewmodel

import androidx.lifecycle.ViewModel
import com.witt3d.autosteer.data.models.TelemetryData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel : ViewModel() {
    private val _telemetry = MutableStateFlow(TelemetryData())
    val telemetry: StateFlow<TelemetryData> = _telemetry.asStateFlow()

    fun updateTelemetry(newData: TelemetryData) {
        _telemetry.value = newData
    }
}
