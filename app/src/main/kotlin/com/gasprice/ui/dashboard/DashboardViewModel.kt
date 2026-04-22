package com.gasprice.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gasprice.domain.model.MonitoringState
import com.gasprice.service.ActivityRecognitionManager
import com.gasprice.service.MonitoringController
import com.gasprice.service.MonitoringForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val monitoringController: MonitoringController,
    private val activityRecognitionManager: ActivityRecognitionManager
) : ViewModel() {

    val monitoringState: StateFlow<MonitoringState> = monitoringController.monitoringState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonitoringState.IDLE)

    val currentStation = monitoringController.currentStation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun startMonitoring() {
        viewModelScope.launch {
            activityRecognitionManager.registerTransitions()
            MonitoringForegroundService.start(context)
        }
    }

    fun stopMonitoring() {
        viewModelScope.launch {
            MonitoringForegroundService.stop(context)
            activityRecognitionManager.unregisterTransitions()
        }
    }
}
