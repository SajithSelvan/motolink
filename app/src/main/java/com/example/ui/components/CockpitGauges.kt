package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hardware.BluetoothUiState
import com.example.hardware.BtConnectionState
import com.example.hardware.BtLinkMode
import com.example.hardware.ScannedBtDevice
import com.example.service.NavManeuverType
import com.example.ui.theme.BluetoothSky
import com.example.ui.theme.DiagnosticTeal
import com.example.ui.theme.FaultRed
import com.example.ui.theme.HealthyGreen
import com.example.ui.theme.RsCrimson
import com.example.ui.theme.TachAmber
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Cb350RsDialGauge(
    primaryValue: String,
    primaryUnit: String,
    secondaryLabel: String,
    progressFraction: Float,
    accentColor: Color = RsCrimson,
    redlineStartFraction: Float = 0.78f,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = size.minDimension * 0.085f
            val inset = strokePx * 0.85f
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val topLeft = Offset(inset, inset)
            val startAngle = 140f
            val sweepTotal = 260f

            // Background arc track
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweepTotal,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Redline zone hint arc (Honda CB350RS 5500-7000 RPM)
            val redlineStart = startAngle + (sweepTotal * redlineStartFraction)
            val redlineSweep = sweepTotal * (1f - redlineStartFraction)
            drawArc(
                color = RsCrimson.copy(alpha = 0.28f),
                startAngle = redlineStart,
                sweepAngle = redlineSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Active telemetry sweep
            val clamped = progressFraction.coerceIn(0f, 1f)
            if (clamped > 0.005f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to DiagnosticTeal,
                        0.55f to TachAmber,
                        0.85f to accentColor,
                        1.0f to RsCrimson
                    ),
                    startAngle = startAngle,
                    sweepAngle = sweepTotal * clamped,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }

            // Classic analog-digital tick marks inside the ring
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerR = (size.minDimension / 2f) - strokePx * 1.45f
            val majorTicks = 12
            for (i in 0..majorTicks) {
                val frac = i.toFloat() / majorTicks.toFloat()
                val angleDeg = startAngle + sweepTotal * frac
                val rad = angleDeg * (PI / 180.0)
                val innerR = outerR - if (i % 3 == 0) 11f else 6f
                val start = Offset(
                    center.x + (outerR * cos(rad)).toFloat(),
                    center.y + (outerR * sin(rad)).toFloat()
                )
                val end = Offset(
                    center.x + (innerR * cos(rad)).toFloat(),
                    center.y + (innerR * sin(rad)).toFloat()
                )
                drawLine(
                    color = if (frac >= redlineStartFraction) RsCrimson.copy(alpha = 0.7f) else tickColor,
                    start = start,
                    end = end,
                    strokeWidth = if (i % 3 == 0) 2.5f else 1.5f
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = primaryValue,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = primaryUnit,
                style = MaterialTheme.typography.labelMedium,
                color = accentColor
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = secondaryLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BluetoothStatusPill(
    btState: BluetoothUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (bg, fg, icon, label) = when (btState.connectionState) {
        BtConnectionState.CONNECTED -> Quad(
            DiagnosticTeal.copy(alpha = 0.18f),
            DiagnosticTeal,
            Icons.Filled.BluetoothConnected,
            btState.connectedDevice?.name?.take(18) ?: "Connected"
        )
        BtConnectionState.SCANNING, BtConnectionState.CONNECTING -> Quad(
            TachAmber.copy(alpha = 0.18f),
            TachAmber,
            Icons.Filled.BluetoothSearching,
            if (btState.connectionState == BtConnectionState.SCANNING) "Scanning…" else "Linking…"
        )
        BtConnectionState.ERROR -> Quad(
            FaultRed.copy(alpha = 0.18f),
            FaultRed,
            Icons.Filled.BluetoothDisabled,
            "Link Error"
        )
        BtConnectionState.DISCONNECTED -> Quad(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            BluetoothSky,
            Icons.Filled.Bluetooth,
            "Connect BT"
        )
    }

    Surface(
        onClick = onClick,
        color = bg,
        shape = RoundedCornerShape(99.dp),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.45f)),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Bluetooth connection status",
                tint = fg,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

fun maneuverIcon(type: NavManeuverType): ImageVector = when (type) {
    NavManeuverType.STRAIGHT -> Icons.Filled.Straight
    NavManeuverType.LEFT -> Icons.Filled.TurnLeft
    NavManeuverType.RIGHT -> Icons.Filled.TurnRight
    NavManeuverType.SLIGHT_LEFT -> Icons.Filled.TurnSlightLeft
    NavManeuverType.SLIGHT_RIGHT -> Icons.Filled.TurnSlightRight
    NavManeuverType.U_TURN -> Icons.Filled.UTurnLeft
    NavManeuverType.ROUNDABOUT -> Icons.Filled.RoundaboutRight
    NavManeuverType.ARRIVE -> Icons.Filled.Flag
}

@Composable
fun SafetySimulationBanner(
    onExitSimulation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = TachAmber.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, TachAmber),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SAFETY SIMULATION MODE",
                    style = MaterialTheme.typography.labelMedium,
                    color = TachAmber,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Simulated CB350RS ECU telemetry for off-road bench testing",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            TextButton(
                onClick = onExitSimulation,
                modifier = Modifier.testTag("exit_safety_simulation_button")
            ) {
                Text("Exit", color = TachAmber, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothDeviceSheet(
    btState: BluetoothUiState,
    onDismiss: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onSelectMode: (BtLinkMode) -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectDevice: (ScannedBtDevice) -> Unit,
    onDisconnect: () -> Unit,
    onToggleSafetySimulation: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Bluetooth CB350RS Link",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = btState.statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (btState.connectionState == BtConnectionState.CONNECTED) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        border = BorderStroke(1.dp, FaultRed),
                        modifier = Modifier.testTag("bt_sheet_disconnect_button")
                    ) {
                        Text("Disconnect", color = FaultRed)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Explicit Safety Simulation Mode toggle for off-road / emulator bench testing
            Surface(
                color = if (btState.safetySimulationActive) TachAmber.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(
                    1.dp,
                    if (btState.safetySimulationActive) TachAmber else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Safety Simulation Mode (Bench Test)",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (btState.safetySimulationActive) TachAmber else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Test CB350RS OBD-II gauges, DTCs & dash relay safely off the road",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Switch(
                        checked = btState.safetySimulationActive,
                        onCheckedChange = { onToggleSafetySimulation() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = TachAmber
                        ),
                        modifier = Modifier.testTag("bt_safety_simulation_switch")
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Protocol Mode selector: ELM327 OBD-II SPP vs BLE GATT Cluster
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BtLinkMode.entries.forEach { mode ->
                    val selected = btState.linkMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectMode(mode) },
                        label = {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RsCrimson.copy(alpha = 0.22f),
                            selectedLabelColor = RsCrimson
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (!btState.hardwareSupported) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Bluetooth hardware unavailable on this device",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Turn on Safety Simulation Mode above to test CB350RS ECU diagnostics and dash relay, or install on an Android phone with Bluetooth.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (!btState.permissionsGranted) {
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("bt_request_permission_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = RsCrimson)
                ) {
                    Icon(Icons.Filled.Bluetooth, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Grant Bluetooth Permissions")
                }
            } else if (!btState.adapterEnabled) {
                Button(
                    onClick = onOpenBluetoothSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("bt_open_settings_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = TachAmber, contentColor = Color.Black)
                ) {
                    Icon(Icons.Filled.BluetoothDisabled, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Turn On Bluetooth in Settings")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isScanning = btState.connectionState == BtConnectionState.SCANNING
                    Button(
                        onClick = { if (isScanning) onStopScan() else onStartScan() },
                        colors = ButtonDefaults.buttonColors(containerColor = RsCrimson),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("bt_scan_toggle_button")
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Stop Discovery")
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Discover OBD / Bike")
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenBluetoothSettings,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("OS Pairing")
                    }
                }

                Spacer(Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (btState.bondedDevices.isNotEmpty()) {
                        item {
                            Text(
                                text = "PAIRED DEVICES (ELM327 / INTERCOM / BIKE)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(btState.bondedDevices, key = { "bonded_${it.address}" }) { device ->
                            BluetoothDeviceRow(
                                device = device,
                                connected = btState.connectedDevice?.address == device.address,
                                onClick = { onConnectDevice(device) }
                            )
                        }
                    }

                    if (btState.discoveredDevices.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "DISCOVERED DEVICES (CLASSIC OBD & BLE)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(btState.discoveredDevices, key = { "ble_${it.address}" }) { device ->
                            BluetoothDeviceRow(
                                device = device,
                                connected = btState.connectedDevice?.address == device.address,
                                onClick = { onConnectDevice(device) }
                            )
                        }
                    }

                    if (btState.bondedDevices.isEmpty() && btState.discoveredDevices.isEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "No paired or nearby Bluetooth devices detected",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Pair your Honda CB350RS dash, helmet intercom, or 6-pin Euro5 ELM327 OBD-II Bluetooth adapter in Android Settings, then tap it here to connect.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BluetoothDeviceRow(
    device: ScannedBtDevice,
    connected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (connected) DiagnosticTeal.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            1.dp,
            if (connected) DiagnosticTeal else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("bt_device_row_${device.address}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (device.looksLikeBikeOrObd) RsCrimson.copy(alpha = 0.2f)
                        else BluetoothSky.copy(alpha = 0.16f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (device.looksLikeBikeOrObd) Icons.Filled.TwoWheeler else Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = if (device.looksLikeBikeOrObd) RsCrimson else BluetoothSky
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(device.address)
                        append(" • ${device.transportLabel}")
                        if (device.rssi != null) append(" • ${device.rssi} dBm")
                        if (device.bonded && device.transportLabel != "Paired") append(" • Paired")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (connected) {
                Text(
                    text = "LIVE",
                    style = MaterialTheme.typography.labelMedium,
                    color = HealthyGreen
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Connect",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
