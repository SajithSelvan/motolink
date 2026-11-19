package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class BikeProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val modelName: String = "Honda CB350RS",
    val nickname: String = "My CB350RS",
    val modelYear: Long = 2024L,
    val plateNumber: String = "",
    val odometerKm: Long = 0L,
    val pairedDeviceName: String = "",
    val pairedDeviceAddress: String = "",
    val homeAddress: String = "",
    val serviceCenterName: String = "Honda BigWing Service",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class ServiceCategory(
    val wireValue: String,
    val displayName: String,
    val recommendedIntervalKm: Long?
) {
    ENGINE_OIL("ENGINE_OIL", "Engine Oil & Filter", 6000L),
    CHAIN_CARE("CHAIN_CARE", "Chain Clean & Lube", 500L),
    BRAKES("BRAKES", "Brakes & Fluid", 6000L),
    TIRES("TIRES", "Tires & Pressure", 15000L),
    AIR_FILTER("AIR_FILTER", "Air Filter", 12000L),
    BATTERY("BATTERY", "Battery & Charging", 6000L),
    GENERAL_SERVICE("GENERAL_SERVICE", "Periodic Service", 6000L),
    REPAIR("REPAIR", "Repair / Part", null);

    companion object {
        fun fromWire(raw: String): ServiceCategory =
            entries.firstOrNull { it.wireValue == raw } ?: GENERAL_SERVICE
    }
}

@Keep
@IgnoreExtraProperties
data class ServiceRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val category: String = ServiceCategory.GENERAL_SERVICE.wireValue,
    val odometerKm: Long = 0L,
    val costAmount: Double = 0.0,
    val serviceDate: String = "",
    val workshopName: String = "",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedCategory(): ServiceCategory = ServiceCategory.fromWire(category)
}

enum class DiagnosticSource(val wireValue: String, val label: String) {
    BLUETOOTH_OBD("BLUETOOTH_OBD", "Bluetooth OBD-II"),
    MANUAL_CHECK("MANUAL_CHECK", "Pre-Ride Check");

    companion object {
        fun fromWire(raw: String): DiagnosticSource =
            entries.firstOrNull { it.wireValue == raw } ?: MANUAL_CHECK
    }
}

enum class DiagnosticStatus(val wireValue: String, val label: String) {
    HEALTHY("HEALTHY", "Nominal"),
    WARNING("WARNING", "Attention"),
    FAULT("FAULT", "Fault Detected");

    companion object {
        fun fromWire(raw: String): DiagnosticStatus =
            entries.firstOrNull { it.wireValue == raw } ?: HEALTHY
    }
}

@Keep
@IgnoreExtraProperties
data class DiagnosticLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val source: String = DiagnosticSource.BLUETOOTH_OBD.wireValue,
    val batteryVoltage: Double = 0.0,
    val engineTempC: Long = 0L,
    val rpmPeak: Long = 0L,
    val odometerKm: Long = 0L,
    val dtcCodes: List<String> = emptyList(),
    val status: String = DiagnosticStatus.HEALTHY.wireValue,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedSource(): DiagnosticSource = DiagnosticSource.fromWire(source)
    fun resolvedStatus(): DiagnosticStatus = DiagnosticStatus.fromWire(status)
}

data class MaintenanceIntervalStatus(
    val category: ServiceCategory,
    val intervalKm: Long,
    val lastDoneKm: Long?,
    val nextDueKm: Long,
    val remainingKm: Long,
    val progressFraction: Float,
    val overdue: Boolean,
    val dueSoon: Boolean
)

data class DtcDefinition(
    val code: String,
    val system: String,
    val summary: String,
    val riderAdvice: String
)

object HondaDtcDictionary {
    private val definitions = listOf(
        DtcDefinition("P0107", "PGM-FI / Intake", "MAP Sensor Low Voltage", "Inspect manifold pressure connector and vacuum line."),
        DtcDefinition("P0108", "PGM-FI / Intake", "MAP Sensor High Voltage", "Check intake sensor harness for open circuit."),
        DtcDefinition("P0112", "PGM-FI / Air Temp", "Intake Air Temp Sensor Low", "Check IAT sensor plug inside airbox."),
        DtcDefinition("P0117", "Engine Thermal", "Engine Oil Temp (EOT) Sensor Low", "Inspect EOT sensor wiring near cylinder head."),
        DtcDefinition("P0118", "Engine Thermal", "Engine Oil Temp (EOT) Sensor High", "Allow engine to cool; inspect oil level and EOT connector."),
        DtcDefinition("P0122", "Throttle Body", "Throttle Position (TP) Sensor Low", "Check throttle body plug; avoid aggressive acceleration."),
        DtcDefinition("P0131", "Exhaust / O2", "O2 Sensor Circuit Low Voltage", "Inspect exhaust header O2 sensor lead and connector."),
        DtcDefinition("P0201", "Fuel System", "Fuel Injector Circuit Malfunction", "Check injector harness and fuel pump relay."),
        DtcDefinition("P0335", "Ignition", "Crankshaft Position (CKP) Sensor", "Engine may fail to start; inspect stator/CKP wiring."),
        DtcDefinition("P0351", "Ignition", "Ignition Coil Primary/Secondary", "Check spark plug cap, HT lead, and ignition coil."),
        DtcDefinition("P0500", "Speed / Cluster", "Vehicle Speed Sensor (VSS) Fault", "Check wheel speed sensor tone ring and clearance."),
        DtcDefinition("P0562", "Charging / Battery", "System Voltage Low (< 11.8V)", "Test 12V battery state-of-charge and RR rectifier output."),
        DtcDefinition("P0563", "Charging / Battery", "System Voltage High (> 15.2V)", "Stop riding soon; inspect regulator/rectifier unit."),
        DtcDefinition("C1011", "Dual-Channel ABS", "Front Wheel Speed Sensor Signal", "Clean front ABS pulser ring and inspect sensor gap."),
        DtcDefinition("C1021", "Dual-Channel ABS", "Rear Wheel Speed Sensor Signal", "Clean rear ABS sensor ring after wet/muddy rides."),
        DtcDefinition("U0100", "CAN Bus", "Lost Communication with ECM", "Verify 6-pin Euro5 OBD adapter seating and ignition ON.")
    ).associateBy { it.code }

    fun describe(code: String): DtcDefinition {
        val normalized = code.trim().uppercase()
        return definitions[normalized] ?: DtcDefinition(
            code = normalized,
            system = "OBD-II / ECU",
            summary = "Diagnostic Trouble Code $normalized",
            riderAdvice = "Log snapshot and consult Honda BigWing workshop with code $normalized."
        )
    }

    fun allCommonCodes(): List<DtcDefinition> = definitions.values.toList()
}
