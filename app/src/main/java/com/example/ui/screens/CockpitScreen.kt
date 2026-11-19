package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BikeProfile
import com.example.data.model.MaintenanceIntervalStatus
import com.example.data.model.UiState
import com.example.data.navigation.NavigationOverlayUiState
import com.example.hardware.BluetoothUiState
import com.example.hardware.BtConnectionState
import com.example.hardware.LiveRideTelemetry
import com.example.hardware.MediaControlState
import com.example.hardware.MediaController
import com.example.service.RelayedMediaInfo
import com.example.service.RelayedNavManeuver
import com.example.ui.components.BluetoothPermissionSnapshot
import com.example.ui.components.BluetoothStatusPill
import com.example.ui.components.ObdLiveDashboard
import com.example.ui.components.RideWorkflowLauncherCard
import com.example.ui.components.maneuverIcon
import com.example.ui.theme.DiagnosticTeal
import com.example.ui.theme.FaultRed
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RsCrimson
import com.example.ui.theme.TachAmber

@Composable
fun CockpitScreen(
    riderDisplayName: String,
    bikeState: UiState<BikeProfile?>,
    intervals: List<MaintenanceIntervalStatus>,
    btState: BluetoothUiState,
    permissionSnapshot: BluetoothPermissionSnapshot,
    navOverlayState: NavigationOverlayUiState,
    mediaState: MediaControlState,
    mediaController: MediaController,
    relayedMedia: RelayedMediaInfo,
    relayedNav: RelayedNavManeuver?,
    rideState: LiveRideTelemetry,
    onOpenRideWorkflow: (initialStep: Int) -> Unit,
    onOpenBluetoothSheet: () -> Unit,
    onPollObdNow: () -> Unit,
    onOpenBikeProfileEditor: () -> Unit,
    onNavigateToRide: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToService: () -> Unit,
    onSignOut: () -> Unit
) {
    val bike = (bikeState as? UiState.Success)?.data
    val odoKm = bike?.odometerKm ?: 0L
    val urgentInterval = intervals.firstOrNull()

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
            // Top Bar: Rider greeting, BT Status Pill, Sign-Out
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MOTOLINK RS",
                            style = MaterialTheme.typography.labelSmall,
                            color = RsCrimson
                        )
                        Text(
                            text = bike?.nickname?.ifBlank { "Honda CB350RS" } ?: "Honda CB350RS",
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = riderDisplayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BluetoothStatusPill(
                            btState = btState,
                            onClick = onOpenBluetoothSheet,
                            modifier = Modifier.testTag("cockpit_bt_pill")
                        )
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = onSignOut,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Sign out",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Hero CB350RS Garage Card
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(196.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_cb350rs_hero_1795082590327),
                            contentDescription = "Honda CB350RS motorcycle",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            ObsidianBg.copy(alpha = 0.92f),
                                            ObsidianBg.copy(alpha = 0.55f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column {
                                    Text(
                                        text = "${bike?.modelYear ?: 2024} ${bike?.modelName ?: "Honda CB350RS"}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White
                                    )
                                    if (!bike?.plateNumber.isNullOrBlank()) {
                                        Spacer(Modifier.height(2.dp))
                                        Surface(
                                            color = Color.White.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = bike?.plateNumber.orEmpty(),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = TachAmber,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = onOpenBikeProfileEditor,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("cockpit_edit_bike_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "Edit CB350RS profile",
                                        tint = Color.White
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        text = "ODOMETER",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "%,d km".format(odoKm),
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = Color.White
                                    )
                                }
                                Surface(
                                    onClick = onOpenBluetoothSheet,
                                    color = if (btState.connectionState == BtConnectionState.CONNECTED) {
                                        DiagnosticTeal.copy(alpha = 0.25f)
                                    } else {
                                        RsCrimson
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("hero_connect_bt_button")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                    ) {
                                        Text(
                                            text = if (btState.connectionState == BtConnectionState.CONNECTED) {
                                                if (btState.safetySimulationActive) "Sim Linked" else "ECU Linked"
                                            } else {
                                                "Link Bluetooth"
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4-Step CB350RS Pre-Ride & Ride Launch Workflow Launcher
            item {
                RideWorkflowLauncherCard(
                    permissionSnapshot = permissionSnapshot,
                    btState = btState,
                    navOverlayState = navOverlayState,
                    rideState = rideState,
                    onOpenWorkflow = onOpenRideWorkflow
                )
            }

            // Active Turn-by-Turn Navigation Relay Banner (if live maneuver active)
            if (relayedNav != null) {
                item {
                    Surface(
                        onClick = onNavigateToRide,
                        color = TachAmber.copy(alpha = 0.16f),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, TachAmber.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(TachAmber),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = maneuverIcon(relayedNav.maneuver),
                                    contentDescription = relayedNav.maneuver.label,
                                    tint = Color.Black,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "LIVE NAV RELAY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TachAmber
                                )
                                Text(
                                    text = relayedNav.primaryInstruction,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (relayedNav.secondaryDetails.isNotBlank()) {
                                    Text(
                                        text = relayedNav.secondaryDetails,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Real-Time OBD-II Dashboard: Engine RPM, Speed & Fuel Level once Bluetooth connected
            item {
                ObdLiveDashboard(
                    btState = btState,
                    rideState = rideState,
                    onConnectBluetooth = onOpenBluetoothSheet,
                    onPollObdNow = onPollObdNow,
                    onOpenDiagnostics = onNavigateToDiagnostics,
                    onOpenRideMode = onNavigateToRide
                )
            }

            // Quick Handlebar Music Deck
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = TachAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = relayedMedia.title.ifBlank {
                                            if (mediaState.musicPlaying) "Audio Active on Phone" else "Handlebar Media Ready"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = relayedMedia.artist.ifBlank {
                                            if (mediaState.lastCommand.isNotBlank()) {
                                                "${mediaState.lastCommand} • Volume ${mediaState.volumePercent}%"
                                            } else {
                                                "Volume ${mediaState.volumePercent}% • Tap controls to dispatch media keys"
                                            }
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            TextButton(
                                onClick = onNavigateToRide,
                                modifier = Modifier.testTag("cockpit_ride_mode_button")
                            ) {
                                Text(
                                    text = "Ride Mode",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TachAmber
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledIconButton(
                                onClick = mediaController::skipToPrevious,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier
                                    .size(54.dp)
                                    .testTag("cockpit_music_prev")
                            ) {
                                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous track")
                            }

                            FilledIconButton(
                                onClick = {
                                    if (mediaState.musicPlaying) mediaController.pause() else mediaController.play()
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = RsCrimson,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .size(64.dp)
                                    .testTag("cockpit_music_play_pause")
                            ) {
                                Icon(
                                    imageVector = if (mediaState.musicPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (mediaState.musicPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            FilledIconButton(
                                onClick = mediaController::skipToNext,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier
                                    .size(54.dp)
                                    .testTag("cockpit_music_next")
                            ) {
                                Icon(Icons.Filled.SkipNext, contentDescription = "Next track")
                            }
                        }
                    }
                }
            }

            // Next CB350RS Service Due Card
            item {
                val accent = when {
                    urgentInterval == null -> DiagnosticTeal
                    urgentInterval.overdue -> FaultRed
                    urgentInterval.dueSoon -> TachAmber
                    else -> DiagnosticTeal
                }
                Surface(
                    onClick = onNavigateToService,
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cockpit_service_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(accent.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Build,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "NEXT CB350RS SERVICE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accent
                                    )
                                    Text(
                                        text = urgentInterval?.category?.displayName ?: "Set Odometer & Log Service",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                            if (urgentInterval != null) {
                                Text(
                                    text = if (urgentInterval.overdue) {
                                        "Overdue ${-urgentInterval.remainingKm} km"
                                    } else {
                                        "In ${urgentInterval.remainingKm} km"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = accent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (urgentInterval != null) {
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { urgentInterval.progressFraction },
                                color = accent,
                                trackColor = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(99.dp))
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Factory interval: every %,d km • Next due at %,d km".format(
                                    urgentInterval.intervalKm,
                                    urgentInterval.nextDueKm
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Quick Launch Ride & Nav Shortcut
            item {
                Surface(
                    onClick = onNavigateToRide,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Navigation,
                            contentDescription = null,
                            tint = DiagnosticTeal
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Turn-by-Turn Nav & GPS Trip Recorder",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Launch Google Maps, relay maneuvers over Bluetooth & track trip km",
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
