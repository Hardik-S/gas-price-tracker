package com.gasprice.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

data class PermissionItem(
    val title: String,
    val rationale: String,
    val permission: String,
    val icon: ImageVector
)

val requiredPermissions = buildList {
    add(PermissionItem(
        "Precise Location",
        "Required to detect nearby gas stations and register geofences while you drive.",
        Manifest.permission.ACCESS_FINE_LOCATION,
        Icons.Default.LocationOn
    ))
    add(PermissionItem(
        "Background Location",
        "Required for geofences to trigger even when the app is not open. We only use this while you are driving.",
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        Icons.Default.LocationSearching
    ))
    add(PermissionItem(
        "Activity Recognition",
        "Detects when you enter or exit a vehicle so monitoring starts and stops automatically.",
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            Manifest.permission.ACTIVITY_RECOGNITION
        else
            "com.google.android.gms.permission.ACTIVITY_RECOGNITION",
        Icons.Default.DirectionsCar
    ))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(PermissionItem(
            "Notifications",
            "Used to alert you when a gas station is detected so you can log the price.",
            Manifest.permission.POST_NOTIFICATIONS,
            Icons.Default.Notifications
        ))
    }
    add(PermissionItem(
        "Microphone",
        "Used only after you explicitly tap the voice button inside the app. Never recorded in background.",
        Manifest.permission.RECORD_AUDIO,
        Icons.Default.Mic
    ))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current

    fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var grantedMap by remember {
        mutableStateOf(requiredPermissions.associate { it.permission to isGranted(it.permission) })
    }

    var currentRequestIndex by remember { mutableIntStateOf(-1) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val perm = requiredPermissions.getOrNull(currentRequestIndex)?.permission
        if (perm != null) {
            grantedMap = grantedMap + (perm to granted)
        }
        currentRequestIndex = -1
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Setup") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                "Permissions needed",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Gas Price Tracker needs these permissions to detect stations while you drive.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))

            requiredPermissions.forEachIndexed { index, item ->
                val granted = grantedMap[item.permission] == true
                PermissionCard(
                    item = item,
                    granted = granted,
                    onRequest = {
                        currentRequestIndex = index
                        permissionLauncher.launch(item.permission)
                    }
                )
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(24.dp))

            val coreGranted = grantedMap[Manifest.permission.ACCESS_FINE_LOCATION] == true
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = coreGranted
            ) {
                Text(if (coreGranted) "Continue" else "Grant location to continue")
            }

            if (!coreGranted) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Fine location is the only required permission. Others enable full functionality.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PermissionCard(
    item: PermissionItem,
    granted: Boolean,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (granted)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                item.icon,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    item.rationale,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            if (granted) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Granted",
                    tint = MaterialTheme.colorScheme.primary)
            } else {
                TextButton(onClick = onRequest, contentPadding = PaddingValues(4.dp)) {
                    Text("Grant")
                }
            }
        }
    }
}
