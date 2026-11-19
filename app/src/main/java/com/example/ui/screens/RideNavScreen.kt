package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddRoad
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BikeProfile
import com.example.data.navigation.NavigationOverlayUiState
import com.example.hardware.BluetoothUiState
import com.example.hardware.BtConnectionState
import com.example.hardware.LiveRideTelemetry
import com.example.hardware.MediaControlState
import com.example.hardware.MediaController
import com.example.service.RelayStateBus
import com.example.service.RelayedMediaInfo
import com.example.service.RelayedNavManeuver
import com.example.ui.components.BluetoothStatusPill
import com.example.ui.components.Cb350RsDialGauge
import com.example.ui.components.maneuverIcon
import com.example.ui.theme.DiagnosticTeal
import com.example.ui.theme.FaultRed
import com.example.ui.theme.RsCrimson
import com.example.ui.theme.TachAmber

@Composable
fun RideNavScreen(
    bikeProfile: BikeProfile?,
    btState: BluetoothUiState,
    mediaState: MediaControlState,
    mediaController: MediaController,
    relayedMedia: RelayedMediaInfo,
    relayedNav: RelayedNavManeuver?,
    navOverlayState: NavigationOverlayUiState,
    lastRelayPacket: String,
    rideState: LiveRideTelemetry,
    onOpenBluetoothSheet: () -> Unit,
    onOpenBikeProfileEditor: () -> Unit,
    onOpenMusicApp: () -> Unit,
    onStartApiNavigation: (destination: String, originOverride: String) -> Unit,
    onStopApiNavigation: () -> Unit,
    onTriggerSimulatedNav: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    onRequestLocationAndStartRide: () -> Unit,
    onResetTrip: () -> Unit,
    onAddTripToOdometer: () -> Unit
) {
    val context = LocalContext.current
    val notificationAccessEnabled = remember(relayedMedia.updatedAtMs, relayedNav?.timestampMs) {
        RelayStateBus.isNotificationAccessEnabled(context)
    }
    var originOverride by remember { mutableStateOf("") }
    var destinationQuery by remember { mutableStateOf("") }
    val quickPlaces = remember { listOf("Honda BigWing Service", "Nearest Petrol Pump", "Nandi Hills", "Highway Cafe") }

    fun launchTurnByTurnNavigation(target: String) {
        val clean = target.trim()
        if (clean.isEmpty()) {
            onShowMessage("Enter a destination or tap a quick waypoint below")
            return
        }
        if (btState.safetySimulationActive) {
            onTriggerSimulatedNav(clean)
        }
        val navIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=${Uri.encode(clean)}&mode=d")
        ).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val geoIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:0,0?q=${Uri.encode(clean)}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(clean)}&travelmode=driving")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(navIntent)
        } catch (_: Exception) {
            try {
                context.startActivity(geoIntent)
            } catch (_: Exception) {
                try {
                    context.startActivity(webIntent)
                } catch (_: Exception) {
                    onShowMessage("No Maps or Browser app installed to open navigation for $clean")
                }
            }
        }
    }

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
                            text = "RIDER COCKPIT",
                            style = MaterialTheme.typography.labelSmall,
                            color = TachAmber
                        )
                        Text(
                            text = "Music & Navigation",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                    BluetoothStatusPill(
                        btState = btState,
                        onClick = onOpenBluetoothSheet
                    )
                }
            }

            // Notification Access Setup Prompt (only if not yet granted)
            if (!notificationAccessEnabled) {
                item {
                    Surface(
                        color = TachAmber.copy(alpha = 0.14f),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, TachAmber.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = TachAmber,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable Live Track & Nav Dash Relay",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Grant Notification Access so MotoLink RS can read Spotify/YouTube Music titles & Google Maps turns to relay over Bluetooth.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                FilledTonalButton(
                                    onClick = {
                                        try {
                                            context.startActivity(
                                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        } catch (_: Exception) {
                                            onShowMessage("Open Android Settings -> Notification Access -> MotoLink RS")
                                        }
                                    },
                                    modifier = Modifier.testTag("enable_notification_relay_button")
                                ) {
                                    Text("Open Notification Access")
                                }
                            }
                        }
                    }
                }
            }

            // Oversized Glove-Friendly Handlebar Music Switchgear Card
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = RsCrimson
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "HANDLEBAR SWITCHGEAR",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = RsCrimson
                                )
                            }
                            Text(
                                text = buildString {
                                    append(
                                        mediaState.activeSessionApp.ifBlank {
                                            relayedMedia.sourceApp.ifBlank {
                                                if (mediaState.musicPlaying) "PLAYING" else "STANDBY"
                                            }
                                        }
                                    )
                                    append(if (mediaState.usingMediaSessionApi) " • SESSION" else " • KEYS")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (mediaState.musicPlaying || mediaState.usingMediaSessionApi) {
                                    DiagnosticTeal
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = relayedMedia.title.ifBlank {
                                if (mediaState.musicPlaying) "Active Media Playback" else "Start Music on Phone or Tap Play"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = relayedMedia.artist.ifBlank {
                                if (mediaState.lastCommand.isNotBlank()) "Last action: ${mediaState.lastCommand}"
                                else "Supports CB350RS Bluetooth handlebar buttons & MediaSession controls"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(18.dp))

                        // Big CB350RS Left-Pod style 4-button transport row (glove-friendly 72dp height)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                onClick = mediaController::skipToPrevious,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(72.dp)
                                    .testTag("ride_music_prev")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.SkipPrevious,
                                        contentDescription = "Previous track",
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Surface(
                                onClick = mediaController::play,
                                color = if (!mediaState.musicPlaying) RsCrimson else MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(
                                    1.dp,
                                    if (!mediaState.musicPlaying) RsCrimson else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(72.dp)
                                    .testTag("ride_music_play")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Play",
                                        tint = if (!mediaState.musicPlaying) Color.White else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            Surface(
                                onClick = mediaController::pause,
                                color = if (mediaState.musicPlaying) RsCrimson else MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(
                                    1.dp,
                                    if (mediaState.musicPlaying) RsCrimson else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(72.dp)
                                    .testTag("ride_music_pause")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Pause,
                                        contentDescription = "Pause",
                                        tint = if (mediaState.musicPlaying) Color.White else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }

                            Surface(
                                onClick = mediaController::skipToNext,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(72.dp)
                                    .testTag("ride_music_next")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.SkipNext,
                                        contentDescription = "Next track",
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }

                        if (mediaState.noActivePlayerHint) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onOpenMusicApp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("ride_open_music_app")
                            ) {
                                Icon(Icons.Filled.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Open Music Player App")
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Helmet Audio Volume Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                onClick = mediaController::volumeDown,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .size(52.dp)
                                    .testTag("ride_volume_down")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = "Volume down")
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "HELMET / BT VOLUME",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${mediaState.volumePercent}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TachAmber
                                    )
                                }
                                Slider(
                                    value = mediaState.volumeCurrent.toFloat(),
                                    onValueChange = { mediaController.setVolume(it.toInt()) },
                                    valueRange = 0f..mediaState.volumeMax.coerceAtLeast(1).toFloat(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = TachAmber,
                                        activeTrackColor = TachAmber
                                    )
                                )
                            }

                            Surface(
                                onClick = mediaController::volumeUp,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .size(52.dp)
                                    .testTag("ride_volume_up")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Volume up")
                                }
                            }
                        }
                    }
                }
            }

            // Turn-by-Turn Navigation Launcher & Dash Relay Card
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Navigation,
                                    contentDescription = null,
                                    tint = TachAmber
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "TURN-BY-TURN DASH RELAY",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TachAmber
                                )
                            }
                            Text(
                                text = if (btState.connectionState == BtConnectionState.CONNECTED) "BT RELAY ON" else "PHONE HUD",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (btState.connectionState == BtConnectionState.CONNECTED) DiagnosticTeal else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        if (relayedNav != null) {
                            Surface(
                                color = TachAmber.copy(alpha = 0.14f),
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, TachAmber.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(TachAmber),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = maneuverIcon(relayedNav.maneuver),
                                            contentDescription = relayedNav.maneuver.label,
                                            tint = Color.Black,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = relayedNav.maneuver.label.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TachAmber
                                        )
                                        Text(
                                            text = relayedNav.primaryInstruction,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        if (relayedNav.secondaryDetails.isNotBlank()) {
                                            Text(
                                                text = relayedNav.secondaryDetails,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            if (lastRelayPacket.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = lastRelayPacket,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DiagnosticTeal
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                        }

                        OutlinedTextField(
                            value = originOverride,
                            onValueChange = { originOverride = it },
                            label = { Text("Start location (leave blank for live GPS)") },
                            placeholder = { Text("Auto GPS or e.g., Indiranagar, Bengaluru") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("nav_origin_input")
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = destinationQuery,
                            onValueChange = { destinationQuery = it },
                            label = { Text("Enter destination") },
                            placeholder = { Text("e.g., Nandi Hills, Honda BigWing") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("nav_destination_input")
                        )

                        Spacer(Modifier.height(8.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(quickPlaces) { place ->
                                AssistChip(
                                    onClick = { destinationQuery = place },
                                    label = { Text(place) }
                                )
                            }
                        }

                        if (!navOverlayState.errorMessage.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                color = FaultRed.copy(alpha = 0.14f),
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, FaultRed.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = navOverlayState.errorMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = FaultRed,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Button(
                            onClick = { onStartApiNavigation(destinationQuery, originOverride) },
                            enabled = !navOverlayState.isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = RsCrimson),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("nav_start_api_overlay_button")
                        ) {
                            if (navOverlayState.isLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Fetching Google Maps Route…", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Filled.Navigation, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Start Turn-by-Turn Overlay (Maps API)", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { launchTurnByTurnNavigation(destinationQuery) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("nav_launch_button")
                            ) {
                                Text("Open Google Maps App", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (navOverlayState.activeRoute != null) {
                                OutlinedButton(
                                    onClick = onStopApiNavigation,
                                    border = BorderStroke(1.dp, FaultRed.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("nav_stop_overlay_button")
                                ) {
                                    Text("End HUD", color = FaultRed)
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Quick Rider Waypoint Chips (Home & Honda BigWing from Firestore profile)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val homeTarget = bikeProfile?.homeAddress?.ifBlank { null }
                            val bigWingTarget = bikeProfile?.serviceCenterName?.ifBlank { "Honda BigWing Service Center" }
                                ?: "Honda BigWing Service Center"

                            OutlinedButton(
                                onClick = {
                                    if (homeTarget != null) {
                                        launchTurnByTurnNavigation(homeTarget)
                                    } else {
                                        onOpenBikeProfileEditor()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("nav_shortcut_home")
                            ) {
                                Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (homeTarget != null) "Ride Home" else "Set Home",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = { launchTurnByTurnNavigation(bigWingTarget) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("nav_shortcut_bigwing")
                            ) {
                                Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("BigWing", maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Live GPS Speedometer & Trip Odometer Recorder
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Explore,
                                    contentDescription = null,
                                    tint = DiagnosticTeal
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "LIVE GPS RIDE TELEMETRY",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = DiagnosticTeal
                                )
                            }
                            Text(
                                text = rideState.compassCardinal,
                                style = MaterialTheme.typography.labelMedium,
                                color = TachAmber
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Cb350RsDialGauge(
                                primaryValue = rideState.speedKmh.toString(),
                                primaryUnit = "KM/H",
                                secondaryLabel = if (rideState.trackingActive) "GPS LOCK" else "STANDBY",
                                progressFraction = (rideState.speedKmh / 140f).coerceIn(0f, 1f),
                                accentColor = DiagnosticTeal,
                                modifier = Modifier.size(142.dp)
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                GpsMetricRow("TRIP DISTANCE", String.format("%.2f km", rideState.tripDistanceKm))
                                GpsMetricRow("PEAK SPEED", "${rideState.maxSpeedKmh} km/h")
                                GpsMetricRow("HEADING", "${rideState.headingDegrees.toInt()}° ${rideState.compassCardinal}")
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = rideState.statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onRequestLocationAndStartRide,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (rideState.trackingActive) FaultRed else DiagnosticTeal,
                                    contentColor = if (rideState.trackingActive) Color.White else Color.Black
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("gps_toggle_ride_button")
                            ) {
                                Text(
                                    text = if (rideState.trackingActive) "Stop GPS Ride" else "Start Live GPS Ride",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = onResetTrip,
                                modifier = Modifier
                                    .height(50.dp)
                                    .testTag("gps_reset_trip_button")
                            ) {
                                Icon(Icons.Filled.RestartAlt, contentDescription = "Reset trip")
                            }
                        }

                        if (rideState.tripDistanceKm >= 1.0) {
                            Spacer(Modifier.height(10.dp))
                            FilledTonalButton(
                                onClick = onAddTripToOdometer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("gps_add_odometer_button")
                            ) {
                                Icon(Icons.Filled.AddRoad, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add ${String.format("%.1f", rideState.tripDistanceKm)} km to CB350RS Odometer")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GpsMetricRow(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
