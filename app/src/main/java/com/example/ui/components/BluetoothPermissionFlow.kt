package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ui.theme.BluetoothSky
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CarbonSurfaceElevated
import com.example.ui.theme.DiagnosticTeal
import com.example.ui.theme.FaultRed
import com.example.ui.theme.HealthyGreen
import com.example.ui.theme.RsCrimson
import com.example.ui.theme.TachAmber

data class BluetoothPermissionSnapshot(
    val isAndroid12OrHigher: Boolean,
    val scanGranted: Boolean,
    val connectGranted: Boolean,
    val fineLocationGranted: Boolean,
    val missingRequiredBluetoothPermissions: List<String>,
    val permissionsToRequest: List<String>,
    val shouldShowRationale: Boolean,
    val permanentlyDenied: Boolean
) {
    val allBluetoothRequiredGranted: Boolean
        get() = missingRequiredBluetoothPermissions.isEmpty()

    val allPermissionsGranted: Boolean
        get() = if (isAndroid12OrHigher) {
            scanGranted && connectGranted && fineLocationGranted
        } else {
            fineLocationGranted
        }
}

tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

/**
 * Evaluates current grant state for BLUETOOTH_SCAN, BLUETOOTH_CONNECT, and ACCESS_FINE_LOCATION
 * across Android 12+ (API 31+) and Android 11 and below (API <= 30).
 */
fun evaluateBluetoothPermissions(
    context: Context,
    hasRequestedOnce: Boolean
): BluetoothPermissionSnapshot {
    val isApi31Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scanGranted = if (isApi31Plus) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    val connectGranted = if (isApi31Plus) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    val fineLocationGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val requiredBtPerms = if (isApi31Plus) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val missingRequiredBt = requiredBtPerms.filter { perm ->
        ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
    }

    // Request all relevant missing runtime permissions in one clean prompt
    val allRelevant = if (isApi31Plus) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val toRequest = allRelevant.filter { perm ->
        ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
    }

    val activity = context.findHostActivity()
    val showRationale = activity != null && missingRequiredBt.any { perm ->
        ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
    }
    val permanentlyDenied = hasRequestedOnce &&
        missingRequiredBt.isNotEmpty() &&
        activity != null &&
        !showRationale

    return BluetoothPermissionSnapshot(
        isAndroid12OrHigher = isApi31Plus,
        scanGranted = scanGranted,
        connectGranted = connectGranted,
        fineLocationGranted = fineLocationGranted,
        missingRequiredBluetoothPermissions = missingRequiredBt,
        permissionsToRequest = toRequest,
        shouldShowRationale = showRationale,
        permanentlyDenied = permanentlyDenied
    )
}

fun openAppPermissionSettings(context: Context): Boolean {
    return try {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}

/**
 * Compact, dismissible top banner displayed in the main Composable when required
 * Bluetooth runtime permissions are not yet granted.
 */
@Composable
fun BluetoothPermissionBanner(
    snapshot: BluetoothPermissionSnapshot,
    onRequestOrOpenSettings: () -> Unit,
    onShowDetails: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (snapshot.permanentlyDenied) FaultRed else BluetoothSky

    Surface(
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("main_bt_permission_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (snapshot.permanentlyDenied) Icons.Filled.Settings else Icons.Filled.Bluetooth,
                    contentDescription = "Bluetooth permissions needed",
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.bt_perm_banner_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.bt_perm_banner_body),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.width(6.dp))
            TextButton(
                onClick = onShowDetails,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("main_bt_permission_details_button")
            ) {
                Text(
                    text = "Details",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onRequestOrOpenSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (snapshot.permanentlyDenied) FaultRed else RsCrimson,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("main_bt_permission_grant_button")
            ) {
                Text(
                    text = if (snapshot.permanentlyDenied) "Settings" else "Allow",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("main_bt_permission_dismiss_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss Bluetooth permission banner",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Guided permission rationale & recovery dialog detailing BLUETOOTH_SCAN,
 * BLUETOOTH_CONNECT, and ACCESS_FINE_LOCATION for the Honda CB350RS OBD-II link.
 */
@Composable
fun BluetoothPermissionRationaleDialog(
    snapshot: BluetoothPermissionSnapshot,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CarbonSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = BluetoothSky,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.bt_perm_dialog_title),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.bt_perm_dialog_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                PermissionRequirementRow(
                    icon = Icons.Filled.Bluetooth,
                    permissionName = "BLUETOOTH_SCAN",
                    purpose = "Discover nearby Honda CB350RS & ELM327 OBD-II Bluetooth adapters",
                    granted = snapshot.scanGranted,
                    note = if (snapshot.isAndroid12OrHigher) "Required (Android 12+)" else "Legacy BT active"
                )

                PermissionRequirementRow(
                    icon = Icons.Filled.Bluetooth,
                    permissionName = "BLUETOOTH_CONNECT",
                    purpose = "Connect RFCOMM SPP / BLE GATT socket and read live RPM, speed & fuel",
                    granted = snapshot.connectGranted,
                    note = if (snapshot.isAndroid12OrHigher) "Required (Android 12+)" else "Legacy BT active"
                )

                PermissionRequirementRow(
                    icon = Icons.Filled.LocationOn,
                    permissionName = "ACCESS_FINE_LOCATION",
                    purpose = if (snapshot.isAndroid12OrHigher) {
                        "Live GPS speed fallback & trip distance odometer tracking"
                    } else {
                        "Required on Android 11 & below for Bluetooth OBD discovery & GPS speed"
                    },
                    granted = snapshot.fineLocationGranted,
                    note = if (snapshot.isAndroid12OrHigher) "GPS Telemetry" else "Required (Android <= 11)"
                )

                if (snapshot.permanentlyDenied) {
                    Spacer(Modifier.height(2.dp))
                    Surface(
                        color = FaultRed.copy(alpha = 0.14f),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, FaultRed.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.bt_perm_permanently_denied_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = FaultRed,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (snapshot.permanentlyDenied) {
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TachAmber,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("bt_perm_dialog_open_settings_button")
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.bt_perm_open_settings),
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onRequestPermissions,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RsCrimson,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("bt_perm_dialog_grant_button")
                ) {
                    Text(
                        text = stringResource(R.string.bt_perm_grant_cta),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("bt_perm_dialog_dismiss_button")
            ) {
                Text(stringResource(R.string.bt_perm_not_now))
            }
        }
    )
}

@Composable
private fun PermissionRequirementRow(
    icon: ImageVector,
    permissionName: String,
    purpose: String,
    granted: Boolean,
    note: String
) {
    Surface(
        color = CarbonSurfaceElevated,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(
            1.dp,
            if (granted) HealthyGreen.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (granted) HealthyGreen else DiagnosticTeal,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = permissionName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (granted) "GRANTED" else note,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (granted) HealthyGreen else TachAmber
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = purpose,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = if (granted) "$permissionName granted" else "$permissionName not granted",
                tint = if (granted) HealthyGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp)
            )
        }
    }
}
