package com.gasprice.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gasprice.domain.model.MonitoringState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateHistory: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateOnboarding: () -> Unit,
    onNavigatePriceCapture: (String, String?, String?, Double, Double) -> Unit,
    vm: DashboardViewModel = hiltViewModel()
) {
    val state by vm.monitoringState.collectAsState()
    val currentStation by vm.currentStation.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gas Price Tracker") },
                actions = {
                    IconButton(onClick = onNavigateHistory) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                    IconButton(onClick = onNavigateSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Status card
            StatusCard(state = state)

            Spacer(Modifier.height(32.dp))

            // Monitoring toggle
            if (state == MonitoringState.IDLE) {
                Button(
                    onClick = { vm.startMonitoring() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start Monitoring", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                OutlinedButton(
                    onClick = { vm.stopMonitoring() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Stop Monitoring", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(16.dp))

            // If AWAITING_PRICE_INPUT and we have a station, show quick action
            if (state == MonitoringState.AWAITING_PRICE_INPUT && currentStation != null) {
                ElevatedButton(
                    onClick = {
                        currentStation?.let { s ->
                            onNavigatePriceCapture(s.name, s.placeId, s.address, s.latitude, s.longitude)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.LocalGasStation, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Log Price for ${currentStation?.name}")
                }
            }

            Spacer(Modifier.height(32.dp))

            // Permissions hint
            TextButton(onClick = onNavigateOnboarding) {
                Text("Review permissions", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun StatusCard(state: MonitoringState) {
    val (icon, title, subtitle, color) = when (state) {
        MonitoringState.IDLE -> StatusDisplay(
            Icons.Default.PauseCircle, "Idle",
            "Tap Start Monitoring to begin",
            MaterialTheme.colorScheme.outline
        )
        MonitoringState.DRIVING_DETECTED -> StatusDisplay(
            Icons.Default.DirectionsCar, "Driving Detected",
            "Setting up gas station detection…",
            MaterialTheme.colorScheme.primary
        )
        MonitoringState.MONITORING_STATIONS -> StatusDisplay(
            Icons.Default.Radar, "Monitoring Stations",
            "Watching for nearby gas stations",
            MaterialTheme.colorScheme.primary
        )
        MonitoringState.AWAITING_PRICE_INPUT -> StatusDisplay(
            Icons.Default.LocalGasStation, "Station Detected!",
            "Tap to log the gas price",
            MaterialTheme.colorScheme.secondary
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = color
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class StatusDisplay(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String,
    val color: androidx.compose.ui.graphics.Color
)
