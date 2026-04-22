package com.gasprice.ui.capture

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceCaptureScreen(
    stationName: String,
    stationPlaceId: String?,
    stationAddress: String?,
    latitude: Double,
    longitude: Double,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
    vm: PriceCaptureViewModel = hiltViewModel()
) {
    val uiState by vm.uiState.collectAsState()

    // Navigate away on successful save
    LaunchedEffect(uiState.savedSuccessfully) {
        if (uiState.savedSuccessfully) onComplete()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Gas Price") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss")
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Station info
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalGasStation, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stationName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (!stationAddress.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stationAddress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Voice capture button
            VoiceCaptureButton(
                isListening = uiState.isListening,
                onStart = { vm.startListening() },
                onStop = { vm.stopListening() }
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "Tap to speak the price you see displayed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Transcript feedback
            if (uiState.transcript.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Heard:", style = MaterialTheme.typography.labelSmall)
                        Text("\"${uiState.transcript}\"", style = MaterialTheme.typography.bodyMedium)
                        if (uiState.parsedPrice != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Parsed: ${uiState.parsedPrice}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (uiState.parsingStatus == com.gasprice.domain.model.ParsingStatus.LOW_CONFIDENCE) {
                            Text(
                                "⚠ Low confidence — please verify",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Manual input fallback
            Text(
                "Or enter manually:",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.manualInput,
                onValueChange = { vm.onManualInputChanged(it) },
                label = { Text("Price (e.g. 167.9)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (uiState.parsedPrice != null) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Valid",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )

            // Error
            if (uiState.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.weight(1f))

            // Save button
            val canSave = uiState.parsedPrice != null && !uiState.isSaving
            Button(
                onClick = {
                    vm.saveObservation(stationName, stationPlaceId, stationAddress, latitude, longitude)
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Price")
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss) { Text("Skip this station") }
        }
    }
}

@Composable
fun VoiceCaptureButton(
    isListening: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isListening) 1.15f else 1f,
        animationSpec = if (isListening)
            infiniteRepeatable(tween(600), RepeatMode.Reverse)
        else spring(),
        label = "voiceScale"
    )

    FloatingActionButton(
        onClick = { if (isListening) onStop() else onStart() },
        modifier = Modifier
            .size(80.dp)
            .scale(scale),
        shape = CircleShape,
        containerColor = if (isListening) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.primary
    ) {
        Icon(
            imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
            contentDescription = if (isListening) "Stop listening" else "Start voice capture",
            modifier = Modifier.size(36.dp)
        )
    }
}
