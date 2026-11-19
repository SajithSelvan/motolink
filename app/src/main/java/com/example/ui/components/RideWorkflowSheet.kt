package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BikeProfile
import com.example.data.model.MaintenanceIntervalStatus
import com.example.data.navigation.NavigationOverlayUiState
import com.example.hardware.BluetoothUiState
import com.example.hardware.BtConnectionState
import com.example.hardware.LiveRideTelemetry
import com.example.ui.theme.BluetoothSky
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CarbonSurfaceElevated
import com.example.ui.theme.DiagnosticTeal
import com.example.ui.theme.FaultRed
import com.example.ui.theme.HealthyGreen
import com.example.ui.theme.RsCrimson
import com.example.ui.theme.TachAmber

enum class RideWorkflowStep(val index: Int, val shortTitle: String, val headline: String) {
    PERMISSIONS(0, "Access", "1. Permissions & Radio Check"),
    CONNECT_OBD(1, "OBD Link", "2. Connect Honda CB350RS"),
    PRE_RIDE_ECU(2, "ECU Check", "3. Pre-Ride ECU & Fuel Check"),
    ROUTE_LAUNCH(3, "Roll Out", "4. Route, Music & Ride Launch")
}

/**
 * Compact readiness card displayed on CockpitScreen showing live status across
 * the 4-step CB350RS pre-ride workflow and launching the interactive stepper sheet.
 */
@Composable
fun RideWorkflowLauncherCard(
    permissionSnapshot: BluetoothPermissionSnapshot,
    btState: BluetoothUiState,
    navOverlayState: NavigationOverlayUiState,
    rideState: LiveRideTelemetry,
    onOpenWorkflow: (initialStep: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val step1Ready = permissionSnapshot.allBluetoothRequiredGranted || !btState.hardwareSupported
    val step2Ready = btState.connectionState == BtConnectionState.CONNECTED
    val step3Ready = step2Ready && btState.telemetry.storedDtcs.isEmpty() && btState.telemetry.lastUpdatedMs != null
    val step4Ready = navOverlayState.activeRoute != null || rideState.trackingActive

    val completedCount = listOf(step1Ready, step2Ready, step3Ready, step4Ready).count { it }
    val nextRecommendedStep = when {
        !step1Ready -> 0
        !step2Ready -> 1
        !step3Ready -> 2
        else -> 3
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = CarbonSurface,
        border = BorderStroke(
            1.dp,
            if (completedCount == 4) HealthyGreen.copy(alpha = 0.55f) else TachAmber.copy(alpha = 0.45f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("cockpit_ride_workflow_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            TachAmber.copy(alpha = 0.10f),
                            Color.Transparent,
                            DiagnosticTeal.copy(alpha = 0.08f)
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PRE-RIDE WORKFLOW • $completedCount/4 READY",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (completedCount == 4) HealthyGreen else TachAmber,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "CB350RS Ride Launch Checklist",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Button(
                    onClick = { onOpenWorkflow(nextRecommendedStep) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RsCrimson,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("cockpit_start_ride_workflow_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (completedCount == 4) "Review" else "Start Flow",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WorkflowStageChip(
                    number = "1",
                    label = "Access",
                    done = step1Ready,
                    onClick = { onOpenWorkflow(0) },
                    modifier = Modifier.weight(1f)
                )
                WorkflowStageChip(
                    number = "2",
                    label = "OBD",
                    done = step2Ready,
                    onClick = { onOpenWorkflow(1) },
                    modifier = Modifier.weight(1f)
                )
                WorkflowStageChip(
                    number = "3",
                    label = "ECU",
                    done = step3Ready,
                    onClick = { onOpenWorkflow(2) },
                    modifier = Modifier.weight(1f)
                )
                WorkflowStageChip(
                    number = "4",
                    label = "Ride",
                    done = step4Ready,
                    onClick = { onOpenWorkflow(3) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun WorkflowStageChip(
    number: String,
    label: String,
    done: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (done) HealthyGreen.copy(alpha = 0.14f) else CarbonSurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (done) HealthyGreen.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (done) HealthyGreen else MaterialTheme.colorScheme.outlineVariant),
                contentAlignment = Alignment.Center
            ) {
                if (done) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "$label complete",
                        tint = Color.Black,
                        modifier = Modifier.size(12.dp)
                    )
                } else {
                    Text(
                        text = number,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (done) HealthyGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Interactive 4-step Pre-Ride & Ride Launch Workflow sheet chaining:
 * 1. Runtime Bluetooth & Location Permissions
 * 2. Honda CB350RS OBD-II Bluetooth Connection (or Bench Simulation)
 * 3. Live ECU, Fuel & Maintenance Pre-Ride Check
 * 4. Google Maps Turn-by-Turn Overlay, Handlebar Music & Live GPS Launch
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideWorkflowSheet(
    initialStep: Int,
    bikeProfile: BikeProfile?,
    intervals: List<MaintenanceIntervalStatus>,
    permissionSnapshot: BluetoothPermissionSnapshot,
    btState: BluetoothUiState,
    rideState: LiveRideTelemetry,
    onDismiss: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBluetoothSheet: () -> Unit,
    onToggleSafetySimulation: () -> Unit,
    onPollObdAndScanDtcs: () -> Unit,
    onSaveLiveSnapshot: () -> Unit,
    onLaunchRide: (destination: String, startMusic: Boolean, startGps: Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentStepIndex by rememberSaveable { mutableIntStateOf(initialStep.coerceIn(0, 3)) }
    var destinationInput by rememberSaveable { mutableStateOf("") }
    var autoStartMusic by rememberSaveable { mutableStateOf(true) }
    var autoStartGps by rememberSaveable { mutableStateOf(true) }
    val steps = RideWorkflowStep.entries
    val currentStep = steps[currentStepIndex]

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CarbonSurface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header + Step Indicator
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "HONDA CB350RS RIDE WORKFLOW",
                    style = MaterialTheme.typography.labelSmall,
                    color = RsCrimson,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = currentStep.headline,
                    style = MaterialTheme.typography.titleLarge
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    steps.forEach { step ->
                        val active = step.index == currentStepIndex
                        val passed = step.index < currentStepIndex
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(
                                    when {
                                        active -> RsCrimson
                                        passed -> DiagnosticTeal
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    }
                                )
                                .clickable { currentStepIndex = step.index }
                        )
                    }
                }
            }

            // Step Body
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "ride_workflow_step_content"
            ) { step ->
                when (step) {
                    RideWorkflowStep.PERMISSIONS -> WorkflowPermissionsStep(
                        snapshot = permissionSnapshot,
                        hardwareSupported = btState.hardwareSupported,
                        onRequestPermissions = onRequestPermissions,
                        onOpenAppSettings = onOpenAppSettings
                    )
                    RideWorkflowStep.CONNECT_OBD -> WorkflowConnectObdStep(
                        btState = btState,
                        onOpenBluetoothSheet = onOpenBluetoothSheet,
                        onToggleSafetySimulation = onToggleSafetySimulation
                    )
                    RideWorkflowStep.PRE_RIDE_ECU -> WorkflowPreRideEcuStep(
                        btState = btState,
                        urgentInterval = intervals.firstOrNull(),
                        onPollObdAndScanDtcs = onPollObdAndScanDtcs,
                        onSaveLiveSnapshot = onSaveLiveSnapshot
                    )
                    RideWorkflowStep.ROUTE_LAUNCH -> WorkflowRouteAndLaunchStep(
                        bikeProfile = bikeProfile,
                        destination = destinationInput,
                        onDestinationChange = { destinationInput = it },
                        startMusic = autoStartMusic,
                        onStartMusicChange = { autoStartMusic = it },
                        startGps = autoStartGps,
                        onStartGpsChange = { autoStartGps = it },
                        gpsAlreadyRunning = rideState.trackingActive
                    )
                }
            }

            // Footer Navigation Controls (Back / Next / Launch)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStepIndex > 0) {
                    OutlinedButton(
                        onClick = { currentStepIndex-- },
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("workflow_prev_step_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Back")
                    }
                } else {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(50.dp)
                    ) {
                        Text("Close")
                    }
                }

                if (currentStepIndex < steps.lastIndex) {
                    Button(
                        onClick = { currentStepIndex++ },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RsCrimson,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("workflow_next_step_button")
                    ) {
                        Text("Next: ${steps[currentStepIndex + 1].shortTitle}", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                } else {
                    Button(
                        onClick = {
                            onLaunchRide(destinationInput, autoStartMusic, autoStartGps)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DiagnosticTeal,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("workflow_launch_ride_button")
                    ) {
                        Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Launch Ride Cockpit", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkflowPermissionsStep(
    snapshot: BluetoothPermissionSnapshot,
    hardwareSupported: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WorkflowCheckItem(
            title = "BLUETOOTH_SCAN & BLUETOOTH_CONNECT",
            subtitle = if (snapshot.allBluetoothRequiredGranted) {
                "Granted — ready for Honda CB350RS OBD-II discovery & socket link"
            } else if (!hardwareSupported) {
                "No Bluetooth radio detected — you can use Bench Simulation in Step 2"
            } else {
                "Required to discover and pair with your ELM327 OBD-II Bluetooth adapter"
            },
            ready = snapshot.allBluetoothRequiredGranted || !hardwareSupported
        )
        WorkflowCheckItem(
            title = "ACCESS_FINE_LOCATION",
            subtitle = if (snapshot.fineLocationGranted) {
                "Granted — live GPS speed, trip odometer & distance-to-next-turn enabled"
            } else {
                "Needed for live GPS speed tracking and turn-by-turn distance updates"
            },
            ready = snapshot.fineLocationGranted
        )

        if (!snapshot.allPermissionsGranted && hardwareSupported) {
            Button(
                onClick = if (snapshot.permanentlyDenied) onOpenAppSettings else onRequestPermissions,
                colors = ButtonDefaults.buttonColors(containerColor = BluetoothSky, contentColor = Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("workflow_grant_permissions_button")
            ) {
                Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (snapshot.permanentlyDenied) "Open Android App Settings" else "Grant Runtime Permissions",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun WorkflowConnectObdStep(
    btState: BluetoothUiState,
    onOpenBluetoothSheet: () -> Unit,
    onToggleSafetySimulation: () -> Unit
) {
    val connected = btState.connectionState == BtConnectionState.CONNECTED
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WorkflowCheckItem(
            title = if (connected) {
                "Connected: ${btState.connectedDevice?.name ?: "CB350RS ECU"}"
            } else {
                "Honda CB350RS OBD-II Adapter"
            },
            subtitle = btState.statusMessage,
            ready = connected
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onOpenBluetoothSheet,
                colors = ButtonDefaults.buttonColors(containerColor = RsCrimson, contentColor = Color.White),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("workflow_open_bt_sheet_button")
            ) {
                Icon(
                    imageVector = if (connected) Icons.Filled.BluetoothConnected else Icons.Filled.Bluetooth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (connected) "Manage Link" else "Discover & Pair")
            }

            OutlinedButton(
                onClick = onToggleSafetySimulation,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("workflow_toggle_sim_button")
            ) {
                Text(if (btState.safetySimulationActive) "Exit Sim" else "Bench Sim")
            }
        }
    }
}

@Composable
private fun WorkflowPreRideEcuStep(
    btState: BluetoothUiState,
    urgentInterval: MaintenanceIntervalStatus?,
    onPollObdAndScanDtcs: () -> Unit,
    onSaveLiveSnapshot: () -> Unit
) {
    val tel = btState.telemetry
    val connected = btState.connectionState == BtConnectionState.CONNECTED

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WorkflowMetricBox(
                label = "BATTERY",
                value = tel.batteryVoltage?.let { String.format("%.1f V", it) } ?: "-- V",
                accent = DiagnosticTeal,
                modifier = Modifier.weight(1f)
            )
            WorkflowMetricBox(
                label = "FUEL",
                value = tel.fuelLevelPercent?.let { "$it%" } ?: "--%",
                accent = if ((tel.fuelLevelPercent ?: 100) <= 20) FaultRed else TachAmber,
                modifier = Modifier.weight(1f)
            )
            WorkflowMetricBox(
                label = "FAULTS",
                value = if (connected) "${tel.storedDtcs.size} DTC" else "--",
                accent = if (tel.storedDtcs.isNotEmpty()) FaultRed else HealthyGreen,
                modifier = Modifier.weight(1f)
            )
        }

        if (urgentInterval != null && (urgentInterval.overdue || urgentInterval.dueSoon)) {
            Surface(
                color = (if (urgentInterval.overdue) FaultRed else TachAmber).copy(alpha = 0.14f),
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, if (urgentInterval.overdue) FaultRed else TachAmber),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (urgentInterval.overdue) FaultRed else TachAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${urgentInterval.category.displayName} ${
                            if (urgentInterval.overdue) "overdue by ${-urgentInterval.remainingKm} km"
                            else "due in ${urgentInterval.remainingKm} km"
                        }",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = onPollObdAndScanDtcs,
                enabled = connected,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("workflow_run_ecu_scan_button")
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Poll ECU & DTCs")
            }
            OutlinedButton(
                onClick = onSaveLiveSnapshot,
                enabled = connected,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("workflow_save_snapshot_button")
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save Log")
            }
        }
    }
}

@Composable
private fun WorkflowRouteAndLaunchStep(
    bikeProfile: BikeProfile?,
    destination: String,
    onDestinationChange: (String) -> Unit,
    startMusic: Boolean,
    onStartMusicChange: (Boolean) -> Unit,
    startGps: Boolean,
    onStartGpsChange: (Boolean) -> Unit,
    gpsAlreadyRunning: Boolean
) {
    val presets = remember(bikeProfile) {
        buildList {
            bikeProfile?.homeAddress?.takeIf { it.isNotBlank() }?.let { add(it) }
            add(bikeProfile?.serviceCenterName?.ifBlank { "Honda BigWing Service" } ?: "Honda BigWing Service")
            add("Nearest Petrol Pump")
            add("Nandi Hills")
        }.distinct()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = destination,
            onValueChange = onDestinationChange,
            label = { Text("Destination for Turn-by-Turn Overlay (optional)") },
            placeholder = { Text("e.g., Honda BigWing Service") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Navigation, contentDescription = null, tint = TachAmber) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("workflow_destination_input")
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(presets) { preset ->
                AssistChip(
                    onClick = { onDestinationChange(preset) },
                    label = { Text(preset, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }

        WorkflowToggleRow(
            icon = Icons.Filled.MusicNote,
            title = "Start Handlebar Media Playback",
            subtitle = "Dispatches Play via ViewModel MediaController on launch",
            checked = startMusic,
            onCheckedChange = onStartMusicChange
        )

        WorkflowToggleRow(
            icon = Icons.Filled.Memory,
            title = if (gpsAlreadyRunning) "Live GPS Trip Tracking (Active)" else "Start Live GPS Trip & Turn Distance",
            subtitle = "Tracks speed, trip odometer & distance-to-next-turn",
            checked = startGps,
            onCheckedChange = onStartGpsChange
        )
    }
}

@Composable
private fun WorkflowCheckItem(
    title: String,
    subtitle: String,
    ready: Boolean
) {
    Surface(
        color = CarbonSurfaceElevated,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            1.dp,
            if (ready) HealthyGreen.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (ready) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = if (ready) "Ready" else "Action needed",
                tint = if (ready) HealthyGreen else TachAmber,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WorkflowMetricBox(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CarbonSurfaceElevated,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun WorkflowToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = CarbonSurfaceElevated,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DiagnosticTeal,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = DiagnosticTeal
                )
            )
        }
    }
}
