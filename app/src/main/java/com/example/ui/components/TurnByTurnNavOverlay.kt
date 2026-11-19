package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.example.data.navigation.ActiveNavigationRoute
import com.example.data.navigation.GoogleMapsDirectionsClient
import com.example.service.NavManeuverType
import com.example.service.RelayedNavManeuver
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CarbonSurfaceElevated
import com.example.ui.theme.DiagnosticTeal
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RsCrimson
import com.example.ui.theme.TachAmber

private const val TURN_APPROACHING_THRESHOLD_METERS = 120

/**
 * Floating motorcycle cockpit turn-by-turn navigation overlay displaying
 * distance-to-next-turn, maneuver icon, street instruction, ETA, and upcoming turn preview
 * driven by the Google Maps Directions API (with automatic fallback to relayed Maps notifications).
 */
@Composable
fun TurnByTurnNavOverlay(
    activeRoute: ActiveNavigationRoute?,
    relayedNav: RelayedNavManeuver?,
    bluetoothConnected: Boolean,
    onPreviousStep: () -> Unit,
    onNextStep: () -> Unit,
    onDismissOverlay: () -> Unit,
    onOpenRideTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = activeRoute != null || relayedNav != null
    var expanded by rememberSaveable { mutableStateOf(true) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        if (activeRoute != null) {
            ApiRouteNavOverlayCard(
                route = activeRoute,
                bluetoothConnected = bluetoothConnected,
                expanded = expanded,
                onToggleExpanded = { expanded = !expanded },
                onPreviousStep = onPreviousStep,
                onNextStep = onNextStep,
                onDismissOverlay = onDismissOverlay,
                onOpenRideTab = onOpenRideTab
            )
        } else if (relayedNav != null) {
            RelayedNavOverlayCard(
                maneuver = relayedNav,
                bluetoothConnected = bluetoothConnected,
                expanded = expanded,
                onToggleExpanded = { expanded = !expanded },
                onDismissOverlay = onDismissOverlay,
                onOpenRideTab = onOpenRideTab
            )
        }
    }
}

@Composable
private fun ApiRouteNavOverlayCard(
    route: ActiveNavigationRoute,
    bluetoothConnected: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onPreviousStep: () -> Unit,
    onNextStep: () -> Unit,
    onDismissOverlay: () -> Unit,
    onOpenRideTab: () -> Unit
) {
    val step = route.currentStep ?: return
    val approachingTurn = route.distanceToNextTurnMeters <= TURN_APPROACHING_THRESHOLD_METERS
    val accentColor = if (approachingTurn) RsCrimson else TachAmber
    val distanceLabel = GoogleMapsDirectionsClient.formatDistance(route.distanceToNextTurnMeters)
    val remainingDistLabel = GoogleMapsDirectionsClient.formatDistance(route.remainingDistanceMeters)
    val etaLabel = GoogleMapsDirectionsClient.formatEta(route.remainingDurationSeconds)

    val animatedProgress by animateFloatAsState(
        targetValue = route.currentStepProgressFraction,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 240f),
        label = "tbt_step_progress"
    )

    Surface(
        onClick = onOpenRideTab,
        shape = RoundedCornerShape(18.dp),
        color = CarbonSurface.copy(alpha = 0.97f),
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.85f)),
        shadowElevation = 10.dp,
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .testTag("tbt_nav_overlay_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.16f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Primary Row: Maneuver Arrow + Distance-to-Next-Turn + Instruction + Collapse/End Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(if (expanded) 56.dp else 44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = maneuverIcon(step.maneuver),
                        contentDescription = step.maneuver.label,
                        tint = if (approachingTurn) Color.White else Color.Black,
                        modifier = Modifier.size(if (expanded) 34.dp else 26.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = distanceLabel,
                            style = if (expanded) {
                                MaterialTheme.typography.headlineMedium
                            } else {
                                MaterialTheme.typography.titleLarge
                            },
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("tbt_distance_to_turn_text")
                        )
                        Surface(
                            color = ObsidianBg.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (route.gpsDrivenDistance) {
                                    Icon(
                                        imageVector = Icons.Filled.GpsFixed,
                                        contentDescription = "GPS tracked distance",
                                        tint = DiagnosticTeal,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                }
                                Text(
                                    text = if (approachingTurn) "TURN SOON" else route.sourceLabel.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (approachingTurn) RsCrimson else DiagnosticTeal
                                )
                            }
                        }
                    }

                    Text(
                        text = step.instruction,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (expanded) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("tbt_instruction_text")
                    )
                }

                IconButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("tbt_overlay_expand_toggle")
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse navigation overlay" else "Expand navigation overlay",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDismissOverlay,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("tbt_overlay_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "End turn-by-turn navigation",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Live distance-to-turn progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(99.dp))
            )

            if (expanded) {
                // Upcoming maneuver preview ("Then...") + Step navigation & ETA strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val next = route.upcomingStep
                        if (next != null) {
                            Surface(
                                color = CarbonSurfaceElevated,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "THEN",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = maneuverIcon(next.maneuver),
                                        contentDescription = next.maneuver.label,
                                        tint = TachAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = GoogleMapsDirectionsClient.formatDistance(next.stepDistanceMeters),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$etaLabel • $remainingDistLabel left",
                                style = MaterialTheme.typography.labelMedium,
                                color = DiagnosticTeal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Step ${route.currentStepIndex + 1}/${route.steps.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (bluetoothConnected) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Filled.BluetoothConnected,
                                        contentDescription = "Relaying turn-by-turn over Bluetooth",
                                        tint = DiagnosticTeal,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text(
                                        text = "BT TX",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DiagnosticTeal
                                    )
                                }
                            }
                        }
                    }

                    // Step Prev/Next controls so riders can inspect upcoming turns
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onPreviousStep,
                            enabled = route.currentStepIndex > 0,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("tbt_overlay_prev_step")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous maneuver step"
                            )
                        }
                        IconButton(
                            onClick = onNextStep,
                            enabled = route.currentStepIndex < route.steps.lastIndex,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("tbt_overlay_next_step")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next maneuver step"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RelayedNavOverlayCard(
    maneuver: RelayedNavManeuver,
    bluetoothConnected: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onDismissOverlay: () -> Unit,
    onOpenRideTab: () -> Unit
) {
    val parsedDistance = extractDistanceToken("${maneuver.primaryInstruction} ${maneuver.secondaryDetails}")

    Surface(
        onClick = onOpenRideTab,
        shape = RoundedCornerShape(18.dp),
        color = CarbonSurface.copy(alpha = 0.97f),
        border = BorderStroke(1.5.dp, TachAmber.copy(alpha = 0.8f)),
        shadowElevation = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .animateContentSize()
            .testTag("tbt_relayed_overlay_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TachAmber),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = maneuverIcon(maneuver.maneuver),
                    contentDescription = maneuver.maneuver.label,
                    tint = Color.Black,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Navigation,
                        contentDescription = null,
                        tint = TachAmber,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (parsedDistance != null) {
                            "$parsedDistance • MAPS RELAY"
                        } else {
                            "LIVE MAPS RELAY"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TachAmber,
                        fontWeight = FontWeight.Bold
                    )
                    if (bluetoothConnected) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "• BT TX",
                            style = MaterialTheme.typography.labelSmall,
                            color = DiagnosticTeal
                        )
                    }
                }
                Text(
                    text = maneuver.primaryInstruction,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (expanded) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (expanded && maneuver.secondaryDetails.isNotBlank()) {
                    Text(
                        text = maneuver.secondaryDetails,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = onToggleExpanded,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = "Toggle relay overlay size",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDismissOverlay,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss navigation overlay",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun extractDistanceToken(raw: String): String? {
    val match = Regex("""\b(\d+(?:\.\d+)?\s?(?:km|m|mi|ft))\b""", RegexOption.IGNORE_CASE).find(raw)
    return match?.groupValues?.getOrNull(1)
}
