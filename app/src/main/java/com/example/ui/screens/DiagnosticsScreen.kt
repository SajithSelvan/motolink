package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.BikeProfile
import com.example.data.model.DiagnosticLog
import com.example.data.model.DiagnosticStatus
import com.example.data.model.HondaDtcDictionary
import com.example.data.model.UiState
import com.example.hardware.BluetoothUiState
import com.example.hardware.BtConnectionState
import com.example.ui.components.BluetoothStatusPill
import com.example.ui.components.Cb350RsDialGauge
import com.example.ui.theme.DiagnosticTeal
import com.example.ui.theme.FaultRed
import com.example.ui.theme.HealthyGreen
import com.example.ui.theme.RsCrimson
import com.example.ui.theme.TachAmber
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    bikeProfile: BikeProfile?,
    btState: BluetoothUiState,
    diagnosticLogsState: UiState<List<DiagnosticLog>>,
    onOpenBluetoothSheet: () -> Unit,
    onPollObdNow: () -> Unit,
    onScanDtcs: () -> Unit,
    onClearDtcs: () -> Unit,
    onSaveLiveSnapshot: () -> Unit,
    onSaveManualCheck: (Double, Long, Long, String, String) -> Unit,
    onDeleteLog: (String) -> Unit
) {
    var showManualDialog by remember { mutableStateOf(false) }
    var showDtcReference by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val connected = btState.connectionState == BtConnectionState.CONNECTED
    val tel = btState.telemetry

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ECU & OBD-II SCANNER",
                            style = MaterialTheme.typography.labelSmall,
                            color = DiagnosticTeal
                        )
                        Text(
                            text = "Vehicle Diagnostics",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                    BluetoothStatusPill(
                        btState = btState,
                        onClick = onOpenBluetoothSheet
                    )
                }
            }

            // Live ECU Tachometer & PID Sensor Board
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(
                        1.dp,
                        if (connected) DiagnosticTeal.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Memory,
                                    contentDescription = null,
                                    tint = if (connected) DiagnosticTeal else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (connected) {
                                        tel.ecuProtocol.ifBlank { "ELM327 / BLE LINK ACTIVE" }
                                    } else {
                                        "AWAITING 6-PIN EURO5 OBD-II LINK"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (connected) DiagnosticTeal else TachAmber
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Cb350RsDialGauge(
                                primaryValue = tel.rpm?.let { "%,d".format(it) } ?: "--",
                                primaryUnit = "RPM",
                                secondaryLabel = "REDLINE 7,000",
                                progressFraction = ((tel.rpm ?: 0) / 7000f).coerceIn(0f, 1f),
                                accentColor = RsCrimson,
                                modifier = Modifier.size(144.dp)
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        EcuPidTile(
                                            label = "BATTERY",
                                            value = tel.batteryVoltage?.let { String.format("%.1f V", it) } ?: "-- V",
                                            valueColor = when {
                                                tel.batteryVoltage == null -> MaterialTheme.colorScheme.onSurface
                                                tel.batteryVoltage < 12.0 -> FaultRed
                                                tel.batteryVoltage > 14.8 -> TachAmber
                                                else -> HealthyGreen
                                            }
                                        )
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        EcuPidTile(
                                            label = "FUEL LEVEL",
                                            value = tel.fuelLevelPercent?.let { "$it%" } ?: "--%",
                                            valueColor = when {
                                                tel.fuelLevelPercent == null -> MaterialTheme.colorScheme.onSurface
                                                tel.fuelLevelPercent <= 20 -> FaultRed
                                                tel.fuelLevelPercent <= 35 -> TachAmber
                                                else -> DiagnosticTeal
                                            }
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        EcuPidTile(
                                            label = "SPEED",
                                            value = tel.speedKmh?.let { "$it km/h" } ?: "--",
                                            valueColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        EcuPidTile(
                                            label = "ENGINE TEMP",
                                            value = tel.engineTempC?.let { "$it °C" } ?: "-- °C",
                                            valueColor = if ((tel.engineTempC ?: 0) > 125) FaultRed else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        EcuPidTile(
                                            label = "THROTTLE",
                                            value = tel.throttlePercent?.let { "$it%" } ?: "--%",
                                            valueColor = TachAmber
                                        )
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        EcuPidTile(
                                            label = "LOAD",
                                            value = tel.engineLoadPercent?.let { "$it%" } ?: "--%",
                                            valueColor = DiagnosticTeal
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Action Controls
                        if (connected) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onPollObdNow,
                                    colors = ButtonDefaults.buttonColors(containerColor = DiagnosticTeal, contentColor = Color.Black),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("diag_poll_ecu_button")
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Poll ECU", fontWeight = FontWeight.Bold)
                                }
                                FilledTonalButton(
                                    onClick = onScanDtcs,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("diag_scan_dtc_button")
                                ) {
                                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Read DTCs")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onSaveLiveSnapshot,
                                    colors = ButtonDefaults.buttonColors(containerColor = RsCrimson),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("diag_save_live_button")
                                ) {
                                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Save Snapshot")
                                }
                                OutlinedButton(
                                    onClick = { showClearConfirm = true },
                                    border = BorderStroke(1.dp, FaultRed.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("diag_clear_dtc_button")
                                ) {
                                    Text("Clear MIL", color = FaultRed)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = onOpenBluetoothSheet,
                                    colors = ButtonDefaults.buttonColors(containerColor = RsCrimson),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("diag_connect_obd_button")
                                ) {
                                    Text("Connect ELM327 / BT")
                                }
                                OutlinedButton(
                                    onClick = { showManualDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("diag_log_manual_button")
                                ) {
                                    Icon(Icons.Filled.AddTask, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Pre-Ride Check")
                                }
                            }
                        }

                        if (tel.recentLog.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.background,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "LIVE BLUETOOTH / ELM327 CONSOLE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DiagnosticTeal
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    tel.recentLog.take(4).forEach { line ->
                                        Text(
                                            text = "> $line",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Active DTC Faults Section (if any detected over OBD-II)
            if (tel.storedDtcs.isNotEmpty()) {
                item {
                    Surface(
                        color = FaultRed.copy(alpha = 0.14f),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, FaultRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "ACTIVE ECU FAULT CODES (${tel.storedDtcs.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = FaultRed
                            )
                            Spacer(Modifier.height(8.dp))
                            tel.storedDtcs.forEach { code ->
                                val def = HondaDtcDictionary.describe(code)
                                Text(
                                    text = "${def.code} • ${def.summary}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White
                                )
                                Text(
                                    text = "${def.system}: ${def.riderAdvice}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }

            // Honda CB350RS PGM-FI DTC Reference toggle
            item {
                Surface(
                    onClick = { showDtcReference = !showDtcReference },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("diag_dtc_reference_toggle")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Honda CB350RS PGM-FI & ABS Code Reference",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (showDtcReference) "Hide" else "View 16 Codes",
                                style = MaterialTheme.typography.labelLarge,
                                color = TachAmber
                            )
                        }
                        if (showDtcReference) {
                            Spacer(Modifier.height(10.dp))
                            HondaDtcDictionary.allCommonCodes().forEach { dtc ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = dtc.code,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TachAmber,
                                        modifier = Modifier.width(62.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = dtc.summary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = dtc.riderAdvice,
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

            // Saved Cloud Diagnostic Snapshots Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CLOUD DIAGNOSTIC HISTORY",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { showManualDialog = true },
                        modifier = Modifier.testTag("diag_add_check_text_button")
                    ) {
                        Text("+ Log Inspection", color = TachAmber)
                    }
                }
            }

            when (diagnosticLogsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = DiagnosticTeal)
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Text(
                            text = diagnosticLogsState.message,
                            color = FaultRed,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is UiState.Success -> {
                    val logs = diagnosticLogsState.data
                    if (logs.isEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Text(
                                        text = "No diagnostic snapshots saved yet",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Connect an ELM327 Bluetooth adapter to capture live ECU readings, or tap Pre-Ride Check to record battery voltage and inspection notes.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(logs, key = { it.id }) { log ->
                            DiagnosticLogCard(
                                log = log,
                                onDelete = { onDeleteLog(log.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showManualDialog) {
        ManualDiagnosticDialog(
            defaultOdoKm = bikeProfile?.odometerKm ?: 0L,
            onDismiss = { showManualDialog = false },
            onSave = { voltage, tempC, odo, dtcs, notes ->
                onSaveManualCheck(voltage, tempC, odo, dtcs, notes)
                showManualDialog = false
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear ECU Fault Codes?") },
            text = {
                Text("This sends OBD-II Mode 04 to clear stored DTCs and reset the MIL lamp on your CB350RS. Only clear codes after inspecting the cause.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        onClearDtcs()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FaultRed)
                ) {
                    Text("Send Mode 04")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EcuPidTile(
    label: String,
    value: String,
    valueColor: Color
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        }
    }
}

@Composable
private fun DiagnosticLogCard(
    log: DiagnosticLog,
    onDelete: () -> Unit
) {
    val status = log.resolvedStatus()
    val statusColor = when (status) {
        DiagnosticStatus.HEALTHY -> HealthyGreen
        DiagnosticStatus.WARNING -> TachAmber
        DiagnosticStatus.FAULT -> FaultRed
    }
    val dateLabel = remember(log.createdAt) {
        val ts = log.createdAt ?: Timestamp.now()
        SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault()).format(ts.toDate())
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (status == DiagnosticStatus.HEALTHY) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = status.label,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${status.label} • ${log.resolvedSource().label}",
                        style = MaterialTheme.typography.titleSmall,
                        color = statusColor
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete diagnostic log",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "$dateLabel • %,d km".format(log.odometerKm),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricChip("Batt ${String.format("%.1fV", log.batteryVoltage)}")
                MetricChip("Temp ${log.engineTempC}°C")
                if (log.rpmPeak > 0L) {
                    MetricChip("${log.rpmPeak} RPM")
                }
            }

            if (log.dtcCodes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "DTCs: ${log.dtcCodes.joinToString(", ")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = FaultRed
                )
            }
            if (log.notes.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = log.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MetricChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ManualDiagnosticDialog(
    defaultOdoKm: Long,
    onDismiss: () -> Unit,
    onSave: (Double, Long, Long, String, String) -> Unit
) {
    var voltageText by remember { mutableStateOf("12.7") }
    var tempText by remember { mutableStateOf("85") }
    var odoText by remember { mutableStateOf(defaultOdoKm.toString()) }
    var dtcsText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log CB350RS Diagnostic Check") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = voltageText,
                    onValueChange = { voltageText = it },
                    label = { Text("Battery Voltage (V)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tempText,
                        onValueChange = { tempText = it },
                        label = { Text("Engine °C") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = odoText,
                        onValueChange = { odoText = it },
                        label = { Text("Odometer km") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = dtcsText,
                    onValueChange = { dtcsText = it },
                    label = { Text("DTC Codes (optional, comma separated)") },
                    placeholder = { Text("e.g., P0562") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Inspection notes") },
                    placeholder = { Text("Tire pressure 29/33 PSI, chain slack ok") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val v = voltageText.toDoubleOrNull()?.coerceIn(0.0, 24.0) ?: 12.6
                    val t = tempText.toLongOrNull()?.coerceIn(-40L, 250L) ?: 85L
                    val o = odoText.toLongOrNull()?.coerceIn(0L, 999999L) ?: defaultOdoKm
                    onSave(v, t, o, dtcsText, notesText)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RsCrimson),
                modifier = Modifier.testTag("diag_manual_save_confirm")
            ) {
                Text("Save Check")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
