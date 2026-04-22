package com.gasprice.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gasprice.domain.model.MonitoringSettings

/**
 * Settings screen — V1 shows current defaults, sliders wired to in-memory settings only.
 * TODO: Persist to DataStore and inject into MonitoringController.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val defaults = MonitoringSettings()
    var geofenceRadius by remember { mutableFloatStateOf(defaults.geofenceRadiusMeters) }
    var dwellDelay by remember { mutableIntStateOf(defaults.dwellDelaySeconds) }
    var stationCooldown by remember { mutableIntStateOf(defaults.stationCooldownMinutes) }
    var maxPrompts by remember { mutableIntStateOf(defaults.maxPromptsPerSession) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text("Detection", style = MaterialTheme.typography.titleMedium)

            SettingsSlider(
                label = "Geofence radius: ${geofenceRadius.toInt()}m",
                value = geofenceRadius,
                onValueChange = { geofenceRadius = it },
                valueRange = 50f..500f
            )

            SettingsSlider(
                label = "Dwell delay: ${dwellDelay}s",
                value = dwellDelay.toFloat(),
                onValueChange = { dwellDelay = it.toInt() },
                valueRange = 10f..120f
            )

            HorizontalDivider()
            Text("Spam Control", style = MaterialTheme.typography.titleMedium)

            SettingsSlider(
                label = "Station re-prompt after: ${stationCooldown}min",
                value = stationCooldown.toFloat(),
                onValueChange = { stationCooldown = it.toInt() },
                valueRange = 15f..240f
            )

            SettingsSlider(
                label = "Max prompts per session: $maxPrompts",
                value = maxPrompts.toFloat(),
                onValueChange = { maxPrompts = it.toInt() },
                valueRange = 1f..20f
            )

            HorizontalDivider()

            Text(
                "Note: Settings are not yet persisted across sessions (V1 TODO).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
