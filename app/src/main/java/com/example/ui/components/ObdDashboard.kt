package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
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
import com.example.hardware.LiveObdTelemetry
import com.example.hardware.LiveRideTelemetry
import com.example.ui.theme.BluetoothSky
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CarbonSurfaceElevated
import com.example.ui.theme.DiagnosticTeal
import com.example.ui.theme.FaultRed
import com.example.ui.theme.HealthyGreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RsCrimson
import com.example.ui.theme.TachAmber
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val CB350RS_MAX_RPM = 7000f
private const val CB350RS_REDLINE_RPM = 5500
private const val CB350RS_MAX_SPEED_KMH = 140f
private const val CB350RS_TANK_LITERS = 15.0
private const val CB350RS_AVG_KM_PER_LITER = 35.0

/**
 * Real-time Honda CB350RS OBD-II instrument dashboard.
 * Automatically transitions from a Bluetooth standby state to the live twin-pod
 * Engine RPM, Speed, and segmented Fuel Level cluster once connected.
 */
@Composable
fun ObdLiveDashboard(
    btState: BluetoothUiState,
    rideState: LiveRideTelemetry,
    onConnectBluetooth: () -> Unit,
    onPollObdNow: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenRideMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = btState.connectionState == BtConnectionState.CONNECTED

    AnimatedContent(
        targetState = isConnected,
        transitionSpec = {
            fadeIn(animationSpec = tween(260)) togetherWith fadeOut(animationSpec = tween(200))
        },
        label = "obd_dashboard_connection_transition",
        modifier = modifier.fillMaxWidth()
    ) { connected ->
        if (connected) {
            ObdConnectedInstrumentCluster(
                btState = btState,
                rideState = rideState,
                onPollObdNow = onPollObdNow,
                onOpenDiagnostics = onOpenDiagnostics,
                onOpenRideMode = onOpenRideMode
            )
        } else {
            ObdStandbyDashboardCard(
                btState = btState,
                rideState = rideState,
                onConnectBluetooth = onConnectBluetooth,
                onOpenRideMode = onOpenRideMode
            )
        }
    }
}

@Composable
private fun ObdConnectedInstrumentCluster(
    btState: BluetoothUiState,
    rideState: LiveRideTelemetry,
    onPollObdNow: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenRideMode: () -> Unit
) {
    val telemetry = btState.telemetry
    val rpm = telemetry.rpm
    val speedKmh = telemetry.speedKmh ?: if (rideState.trackingActive) rideState.speedKmh else null
    val speedSourceLabel = when {
        telemetry.speedKmh != null -> "OBD PID 0D"
        rideState.trackingActive -> "GPS LIVE"
        else -> "AWAITING PID"
    }
    val isRedline = (rpm ?: 0) >= CB350RS_REDLINE_RPM

    val pulseTransition = rememberInfiniteTransition(label = "obd_live_pulse")
    val liveDotAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_dot_alpha"
    )

    val borderAccent = when {
        telemetry.storedDtcs.isNotEmpty() -> FaultRed.copy(alpha = 0.65f)
        isRedline -> RsCrimson.copy(alpha = 0.7f)
        btState.safetySimulationActive -> TachAmber.copy(alpha = 0.55f)
        else -> DiagnosticTeal.copy(alpha = 0.5f)
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = CarbonSurface,
        border = BorderStroke(1.dp, borderAccent),
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("obd_live_dashboard_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                (if (isRedline) RsCrimson else DiagnosticTeal).copy(alpha = 0.10f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.25f),
                            radius = size.maxDimension * 0.7f
                        )
                    )
                }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Live OBD Connection Header + Manual Refresh
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                (if (btState.safetySimulationActive) TachAmber else HealthyGreen)
                                    .copy(alpha = liveDotAlpha)
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (btState.safetySimulationActive) {
                                "LIVE OBD DASH • BENCH SIM"
                            } else {
                                "LIVE OBD-II TELEMETRY"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (btState.safetySimulationActive) TachAmber else DiagnosticTeal,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = buildString {
                                append(btState.connectedDevice?.name ?: "Honda CB350RS ECU")
                                if (telemetry.ecuProtocol.isNotBlank()) {
                                    append(" • ")
                                    append(telemetry.ecuProtocol)
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onPollObdNow,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("obd_dashboard_poll_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh live OBD telemetry",
                        tint = DiagnosticTeal
                    )
                }
            }

            // 2. Horizontal Neo-Retro RPM Shift Strip
            RpmShiftLightStrip(
                rpm = rpm,
                isRedline = isRedline,
                modifier = Modifier.fillMaxWidth()
            )

            // 3. Twin-Pod Primary Gauges: Engine RPM + Vehicle Speed
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val compact = maxWidth < 310.dp
                if (compact) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ObdTwinPodGauge(
                            title = "ENGINE RPM",
                            primaryValue = rpm?.let { "%,d".format(it) } ?: "--",
                            unit = "RPM",
                            subLabel = if (isRedline) "REDLINE ZONE" else "PID 010C",
                            progressFraction = ((rpm ?: 0) / CB350RS_MAX_RPM).coerceIn(0f, 1f),
                            redlineStartFraction = CB350RS_REDLINE_RPM / CB350RS_MAX_RPM,
                            accentColor = if (isRedline) RsCrimson else TachAmber,
                            gradientColors = listOf(DiagnosticTeal, TachAmber, RsCrimson),
                            onClick = onOpenDiagnostics,
                            modifier = Modifier
                                .size(176.dp)
                                .testTag("obd_rpm_gauge")
                        )
                        ObdTwinPodGauge(
                            title = "SPEED",
                            primaryValue = speedKmh?.toString() ?: "--",
                            unit = "KM/H",
                            subLabel = speedSourceLabel,
                            progressFraction = ((speedKmh ?: 0) / CB350RS_MAX_SPEED_KMH).coerceIn(0f, 1f),
                            redlineStartFraction = 0.82f,
                            accentColor = DiagnosticTeal,
                            gradientColors = listOf(BluetoothSky, DiagnosticTeal, TachAmber),
                            onClick = onOpenRideMode,
                            modifier = Modifier
                                .size(176.dp)
                                .testTag("obd_speed_gauge")
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ObdTwinPodGauge(
                            title = "ENGINE RPM",
                            primaryValue = rpm?.let { "%,d".format(it) } ?: "--",
                            unit = "RPM",
                            subLabel = if (isRedline) "SHIFT NOW" else "PID 010C",
                            progressFraction = ((rpm ?: 0) / CB350RS_MAX_RPM).coerceIn(0f, 1f),
                            redlineStartFraction = CB350RS_REDLINE_RPM / CB350RS_MAX_RPM,
                            accentColor = if (isRedline) RsCrimson else TachAmber,
                            gradientColors = listOf(DiagnosticTeal, TachAmber, RsCrimson),
                            onClick = onOpenDiagnostics,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .testTag("obd_rpm_gauge")
                        )
                        ObdTwinPodGauge(
                            title = "SPEED",
                            primaryValue = speedKmh?.toString() ?: "--",
                            unit = "KM/H",
                            subLabel = speedSourceLabel,
                            progressFraction = ((speedKmh ?: 0) / CB350RS_MAX_SPEED_KMH).coerceIn(0f, 1f),
                            redlineStartFraction = 0.82f,
                            accentColor = DiagnosticTeal,
                            gradientColors = listOf(BluetoothSky, DiagnosticTeal, TachAmber),
                            onClick = onOpenRideMode,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .testTag("obd_speed_gauge")
                        )
                    }
                }
            }

            // 4. Segmented CB350RS Fuel Level Meter (OBD-II PID 012F)
            ObdFuelLevelCard(
                fuelPercent = telemetry.fuelLevelPercent,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("obd_fuel_gauge")
            )

            // 5. Secondary Live ECU Strip (Battery, Engine Temp, Throttle)
            SecondaryEcuTelemetryRow(
                telemetry = telemetry,
                onClick = onOpenDiagnostics
            )

            // 6. Active DTC Fault Alert Pill (when faults detected)
            if (telemetry.storedDtcs.isNotEmpty()) {
                Surface(
                    onClick = onOpenDiagnostics,
                    color = FaultRed.copy(alpha = 0.16f),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, FaultRed.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("obd_dashboard_dtc_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Active ECU fault warning",
                            tint = FaultRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${telemetry.storedDtcs.size} Active ECU Fault(s): ${telemetry.storedDtcs.joinToString(", ")}",
                            style = MaterialTheme.typography.labelLarge,
                            color = FaultRed,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Inspect",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RpmShiftLightStrip(
    rpm: Int?,
    isRedline: Boolean,
    modifier: Modifier = Modifier
) {
    val targetFraction = ((rpm ?: 0) / CB350RS_MAX_RPM).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "rpm_shift_strip"
    )
    val inactiveColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val segments = 16

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TACHOMETER BAR • 0–7K RPM",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isRedline) {
                Surface(
                    color = RsCrimson,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "REDLINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            } else {
                Text(
                    text = rpm?.let { "%,d RPM".format(it) } ?: "-- RPM",
                    style = MaterialTheme.typography.labelMedium,
                    color = TachAmber,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
        ) {
            val gap = 4.dp.toPx()
            val segWidth = (size.width - gap * (segments - 1)) / segments
            val activeCount = (animatedFraction * segments).roundToInt()

            for (i in 0 until segments) {
                val x = i * (segWidth + gap)
                val ratio = (i + 1f) / segments
                val segColor = when {
                    i >= activeCount -> inactiveColor
                    ratio >= 0.78f -> RsCrimson
                    ratio >= 0.52f -> TachAmber
                    else -> DiagnosticTeal
                }
                drawRoundRect(
                    color = segColor,
                    topLeft = Offset(x, 0f),
                    size = Size(segWidth, size.height),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun ObdTwinPodGauge(
    title: String,
    primaryValue: String,
    unit: String,
    subLabel: String,
    progressFraction: Float,
    redlineStartFraction: Float,
    accentColor: Color,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 240f
        ),
        label = "obd_pod_progress_$title"
    )
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)

    Surface(
        onClick = onClick,
        color = ObsidianBg.copy(alpha = 0.72f),
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokePx = size.minDimension * 0.082f
                val inset = strokePx * 0.9f
                val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                val topLeft = Offset(inset, inset)
                val startAngle = 135f
                val sweepTotal = 270f

                // Track arc
                drawArc(
                    color = trackColor,
                    startAngle = startAngle,
                    sweepAngle = sweepTotal,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )

                // Redline / high-speed upper band hint
                val redStart = startAngle + (sweepTotal * redlineStartFraction)
                val redSweep = sweepTotal * (1f - redlineStartFraction)
                drawArc(
                    color = RsCrimson.copy(alpha = 0.25f),
                    startAngle = redStart,
                    sweepAngle = redSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )

                // Animated live value arc
                if (animatedProgress > 0.004f) {
                    val activeSweep = sweepTotal * animatedProgress
                    drawArc(
                        brush = Brush.sweepGradient(
                            0.0f to gradientColors.first(),
                            0.55f to gradientColors.getOrElse(1) { accentColor },
                            0.9f to gradientColors.last(),
                            1.0f to gradientColors.last()
                        ),
                        startAngle = startAngle,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )

                    // Luminous needle tip dot on the arc perimeter
                    val tipDeg = startAngle + activeSweep
                    val tipRad = tipDeg * (PI / 180.0)
                    val radius = arcSize.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val tipOffset = Offset(
                        center.x + (radius * cos(tipRad)).toFloat(),
                        center.y + (radius * sin(tipRad)).toFloat()
                    )
                    drawCircle(
                        color = accentColor.copy(alpha = 0.35f),
                        radius = strokePx * 0.95f,
                        center = tipOffset
                    )
                    drawCircle(
                        color = Color.White,
                        radius = strokePx * 0.38f,
                        center = tipOffset
                    )
                }

                // Precision inner radial ticks
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerR = (size.minDimension / 2f) - strokePx * 1.55f
                val ticks = 14
                for (i in 0..ticks) {
                    val frac = i.toFloat() / ticks.toFloat()
                    val rad = (startAngle + sweepTotal * frac) * (PI / 180.0)
                    val major = i % 2 == 0
                    val innerR = outerR - if (major) size.minDimension * 0.055f else size.minDimension * 0.028f
                    drawLine(
                        color = if (frac >= redlineStartFraction) RsCrimson.copy(alpha = 0.8f) else tickColor,
                        start = Offset(
                            center.x + (outerR * cos(rad)).toFloat(),
                            center.y + (outerR * sin(rad)).toFloat()
                        ),
                        end = Offset(
                            center.x + (innerR * cos(rad)).toFloat(),
                            center.y + (innerR * sin(rad)).toFloat()
                        ),
                        strokeWidth = if (major) 2.4f else 1.4f
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = primaryValue,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ObdFuelLevelCard(
    fuelPercent: Int?,
    modifier: Modifier = Modifier
) {
    val safePercent = fuelPercent?.coerceIn(0, 100)
    val animatedFuelFraction by animateFloatAsState(
        targetValue = (safePercent ?: 0) / 100f,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = 220f
        ),
        label = "obd_fuel_level_anim"
    )

    val isLowFuel = safePercent != null && safePercent <= 20
    val fuelAccent = when {
        safePercent == null -> MaterialTheme.colorScheme.onSurfaceVariant
        safePercent <= 20 -> FaultRed
        safePercent <= 35 -> TachAmber
        else -> DiagnosticTeal
    }

    val estimatedLiters = safePercent?.let { (it / 100.0) * CB350RS_TANK_LITERS }
    val estimatedRangeKm = estimatedLiters?.let { (it * CB350RS_AVG_KM_PER_LITER).roundToInt() }
    val inactiveSegment = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val segments = 10

    Surface(
        color = CarbonSurfaceElevated,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            1.dp,
            if (isLowFuel) FaultRed.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(fuelAccent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocalGasStation,
                            contentDescription = "Fuel tank level",
                            tint = fuelAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "FUEL LEVEL",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isLowFuel) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    color = FaultRed,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "LOW FUEL",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (estimatedLiters != null && estimatedRangeKm != null) {
                                String.format("%.1f L / 15 L • Est. %d km range", estimatedLiters, estimatedRangeKm)
                            } else {
                                "PID 012F • Waiting for ECU fuel sensor"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = safePercent?.let { "$it%" } ?: "--%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = fuelAccent,
                    fontWeight = FontWeight.Bold
                )
            }

            // 10-segment CB350RS LCD fuel bar with E / F endpoints
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "E",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isLowFuel) FaultRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                ) {
                    val gap = 4.dp.toPx()
                    val segWidth = (size.width - gap * (segments - 1)) / segments
                    val litSegments = (animatedFuelFraction * segments).roundToInt()
                    for (i in 0 until segments) {
                        val x = i * (segWidth + gap)
                        val isLit = safePercent != null && i < litSegments
                        val barColor = when {
                            !isLit -> inactiveSegment
                            i < 2 -> FaultRed
                            i < 4 -> TachAmber
                            else -> DiagnosticTeal
                        }
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, 0f),
                            size = Size(segWidth, size.height),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }
                Text(
                    text = "F",
                    style = MaterialTheme.typography.labelMedium,
                    color = DiagnosticTeal,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SecondaryEcuTelemetryRow(
    telemetry: LiveObdTelemetry,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompactEcuPill(
            icon = Icons.Filled.BatteryChargingFull,
            label = "BATTERY",
            value = telemetry.batteryVoltage?.let { String.format("%.1f V", it) } ?: "-- V",
            tint = when {
                telemetry.batteryVoltage == null -> MaterialTheme.colorScheme.onSurfaceVariant
                telemetry.batteryVoltage < 12.0 -> FaultRed
                telemetry.batteryVoltage > 14.8 -> TachAmber
                else -> HealthyGreen
            },
            onClick = onClick,
            modifier = Modifier.weight(1f)
        )
        CompactEcuPill(
            icon = Icons.Filled.DeviceThermostat,
            label = "TEMP",
            value = telemetry.engineTempC?.let { "$it °C" } ?: "-- °C",
            tint = if ((telemetry.engineTempC ?: 0) > 120) FaultRed else DiagnosticTeal,
            onClick = onClick,
            modifier = Modifier.weight(1f)
        )
        CompactEcuPill(
            icon = Icons.Filled.Tune,
            label = "THROTTLE",
            value = telemetry.throttlePercent?.let { "$it%" } ?: "--%",
            tint = TachAmber,
            onClick = onClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CompactEcuPill(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = CarbonSurfaceElevated,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        modifier = modifier.heightIn(min = 56.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ObdStandbyDashboardCard(
    btState: BluetoothUiState,
    rideState: LiveRideTelemetry,
    onConnectBluetooth: () -> Unit,
    onOpenRideMode: () -> Unit
) {
    val isBusy = btState.connectionState == BtConnectionState.SCANNING ||
        btState.connectionState == BtConnectionState.CONNECTING
    val isError = btState.connectionState == BtConnectionState.ERROR

    val badgeColor = when {
        isError -> FaultRed
        isBusy -> TachAmber
        else -> BluetoothSky
    }
    val badgeText = when (btState.connectionState) {
        BtConnectionState.SCANNING -> "DISCOVERING OBD-II…"
        BtConnectionState.CONNECTING -> "LINKING CB350RS ECU…"
        BtConnectionState.ERROR -> "OBD LINK ERROR"
        else -> "OBD-II CLUSTER STANDBY"
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = CarbonSurface,
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.42f)),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("obd_standby_dashboard_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                RsCrimson.copy(alpha = 0.08f),
                                Color.Transparent,
                                BluetoothSky.copy(alpha = 0.08f)
                            )
                        )
                    )
                }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(badgeColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isBusy) Icons.Filled.BluetoothSearching else Icons.Filled.Bluetooth,
                            contentDescription = "Bluetooth OBD status",
                            tint = badgeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Real-Time Engine RPM, Speed & Fuel",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Dimmed instrument readout preview row so riders see what unlocks on connect
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StandbyGaugeChip(
                    icon = Icons.Filled.Speed,
                    label = "ENGINE RPM",
                    value = "-- RPM",
                    tint = TachAmber,
                    modifier = Modifier.weight(1f)
                )
                StandbyGaugeChip(
                    icon = Icons.Filled.Speed,
                    label = if (rideState.trackingActive) "GPS SPEED" else "SPEED",
                    value = if (rideState.trackingActive) "${rideState.speedKmh} km/h" else "-- km/h",
                    tint = DiagnosticTeal,
                    onClick = if (rideState.trackingActive) onOpenRideMode else null,
                    modifier = Modifier.weight(1f)
                )
                StandbyGaugeChip(
                    icon = Icons.Filled.LocalGasStation,
                    label = "FUEL LEVEL",
                    value = "-- %",
                    tint = RsCrimson,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = btState.statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onConnectBluetooth,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RsCrimson,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("obd_dashboard_connect_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Bluetooth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isBusy) "View Bluetooth Discovery" else "Connect CB350RS OBD-II",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StandbyGaugeChip(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val baseModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Surface(
        color = ObsidianBg.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = baseModifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
