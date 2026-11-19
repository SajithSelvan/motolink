package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firestore.MotoRepository
import com.example.data.model.BikeProfile
import com.example.data.model.DiagnosticLog
import com.example.data.model.DiagnosticSource
import com.example.data.model.DiagnosticStatus
import com.example.data.model.MaintenanceIntervalStatus
import com.example.data.model.ServiceCategory
import com.example.data.model.ServiceRecord
import com.example.data.model.UiState
import com.example.data.navigation.ActiveNavigationRoute
import com.example.data.navigation.GoogleMapsDirectionsClient
import com.example.data.navigation.NavigationOverlayUiState
import android.view.KeyEvent
import com.example.hardware.BluetoothManager
import com.example.hardware.BluetoothUiState
import com.example.hardware.BtConnectionState
import com.example.hardware.BtLinkMode
import com.example.hardware.HandlebarMediaCommand
import com.example.hardware.LiveRideTelemetry
import com.example.hardware.MediaControlManager
import com.example.hardware.MediaControlState
import com.example.hardware.MediaController
import com.example.hardware.RideLocationManager
import com.example.hardware.ScannedBtDevice
import com.example.service.RelayStateBus
import com.example.service.RelayedMediaInfo
import com.example.service.RelayedNavManeuver
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

class MotoViewModel(
    private val repository: MotoRepository,
    private val currentUserId: String,
    val bluetoothManager: BluetoothManager,
    val mediaControlManager: MediaControlManager,
    val rideLocationManager: RideLocationManager,
    private val directionsClient: GoogleMapsDirectionsClient = GoogleMapsDirectionsClient()
) : ViewModel(), MediaController {

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _navigationOverlayState = MutableStateFlow(NavigationOverlayUiState())
    val navigationOverlayState: StateFlow<NavigationOverlayUiState> = _navigationOverlayState.asStateFlow()

    val bikeProfileState: StateFlow<UiState<BikeProfile?>> =
        repository.observeBikeProfile(currentUserId)
            .map<BikeProfile?, UiState<BikeProfile?>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing bike profile", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load CB350RS profile"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val serviceRecordsState: StateFlow<UiState<List<ServiceRecord>>> =
        repository.observeServiceRecords(currentUserId)
            .map<List<ServiceRecord>, UiState<List<ServiceRecord>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing service records", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load service history"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val diagnosticLogsState: StateFlow<UiState<List<DiagnosticLog>>> =
        repository.observeDiagnosticLogs(currentUserId)
            .map<List<DiagnosticLog>, UiState<List<DiagnosticLog>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing diagnostic logs", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load diagnostic logs"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val maintenanceIntervals: StateFlow<List<MaintenanceIntervalStatus>> =
        combine(bikeProfileState, serviceRecordsState) { bikeState, recordsState ->
            val currentOdo = (bikeState as? UiState.Success)?.data?.odometerKm ?: 0L
            val records = (recordsState as? UiState.Success)?.data.orEmpty()
            computeCb350RsIntervals(currentOdo, records)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val bluetoothState: StateFlow<BluetoothUiState> = bluetoothManager.state
    override val mediaState: StateFlow<MediaControlState> = mediaControlManager.state
    val rideState: StateFlow<LiveRideTelemetry> = rideLocationManager.state

    val relayedNav: StateFlow<RelayedNavManeuver?> = RelayStateBus.activeNavManeuver
    val relayedMedia: StateFlow<RelayedMediaInfo> = RelayStateBus.relayedMedia
    val relayListenerActive: StateFlow<Boolean> = RelayStateBus.listenerConnected
    val lastRelayPacket: StateFlow<String> = RelayStateBus.lastBluetoothPacket

    init {
        // Automatically relay incoming turn-by-turn navigation maneuvers to connected CB350RS Bluetooth link
        viewModelScope.launch {
            RelayStateBus.activeNavManeuver.collect { maneuver ->
                if (maneuver != null && bluetoothManager.state.value.connectionState == BtConnectionState.CONNECTED) {
                    bluetoothManager.relayNavigationToBike(maneuver)
                }
            }
        }

        // Continuously recalculate real-time distance-to-next-turn from live GPS fixes
        viewModelScope.launch {
            rideLocationManager.state.collect { gps ->
                val lat = gps.currentLatitude ?: return@collect
                val lng = gps.currentLongitude ?: return@collect
                val currentRoute = _navigationOverlayState.value.activeRoute ?: return@collect
                val updatedRoute = GoogleMapsDirectionsClient.updateRouteWithGps(currentRoute, lat, lng)
                val stepChanged = updatedRoute.currentStepIndex != currentRoute.currentStepIndex
                _navigationOverlayState.update { it.copy(activeRoute = updatedRoute) }
                if (stepChanged) {
                    relayActiveApiRouteStep(updatedRoute)
                }
            }
        }
    }

    fun currentBikeOrNull(): BikeProfile? = (bikeProfileState.value as? UiState.Success)?.data

    // --- Bluetooth Actions ---

    fun refreshBluetoothState() {
        bluetoothManager.refreshAdapterState()
    }

    fun setBluetoothLinkMode(mode: BtLinkMode) {
        bluetoothManager.setLinkMode(mode)
    }

    fun startBluetoothDiscovery() {
        bluetoothManager.startDiscovery()
    }

    fun stopBluetoothDiscovery() {
        bluetoothManager.stopDiscovery()
    }

    fun connectToBluetoothDevice(device: ScannedBtDevice, mode: BtLinkMode = bluetoothState.value.linkMode) {
        bluetoothManager.connectToDevice(device, mode)
        viewModelScope.launch {
            repository.updatePairedDevice(
                userId = currentUserId,
                deviceName = device.name,
                deviceAddress = device.address,
                currentProfile = currentBikeOrNull()
            )
        }
    }

    fun toggleSafetySimulationMode() {
        if (bluetoothState.value.safetySimulationActive) {
            bluetoothManager.disableSafetySimulationMode()
            emitEvent("Safety Simulation Mode turned off")
        } else {
            bluetoothManager.enableSafetySimulationMode()
            emitEvent("Safety Simulation Mode active (off-road bench test)")
        }
    }

    fun triggerSimulatedNavRelay(destination: String) {
        bluetoothManager.triggerSimulatedNavRelay(destination)
        emitEvent("Simulated TBT maneuver relayed to dash")
    }

    fun showMessage(message: String) {
        emitEvent(message)
    }

    fun disconnectBluetooth() {
        bluetoothManager.disconnect()
        emitEvent("Bluetooth disconnected")
    }

    fun pollObdNow() {
        val ok = bluetoothManager.pollObdNow()
        emitEvent(if (ok) "Live ECU PIDs polled" else "Connect an ELM327 Bluetooth adapter to poll ECU")
    }

    fun scanObdDtcs() {
        val ok = bluetoothManager.scanDtcFaultCodes()
        emitEvent(if (ok) "OBD-II Mode 03 fault scan complete" else "Connect an ELM327 Bluetooth adapter to scan DTCs")
    }

    fun clearObdDtcs() {
        val ok = bluetoothManager.clearDtcFaultCodes()
        emitEvent(if (ok) "OBD-II Mode 04 clear command sent" else "Connect an ELM327 Bluetooth adapter to clear DTCs")
    }

    // --- MediaController Implementation (Motorcycle Handlebar Switchgear) ---

    override fun play() {
        mediaControlManager.play { responded ->
            if (!responded) {
                emitEvent("Sent Play command • Open a music app if playback did not start")
            }
        }
        bluetoothManager.relayMediaActionToBike(HandlebarMediaCommand.PLAY.bleRelayCode)
    }

    override fun pause() {
        mediaControlManager.pause { responded ->
            emitEvent(if (responded) "Playback paused" else "Sent Pause command to Android")
        }
        bluetoothManager.relayMediaActionToBike(HandlebarMediaCommand.PAUSE.bleRelayCode)
    }

    override fun togglePlayPause() {
        mediaControlManager.togglePlayPause { responded ->
            if (!responded) {
                emitEvent("Sent Play/Pause key • Open a music app if playback did not start")
            }
        }
        bluetoothManager.relayMediaActionToBike(HandlebarMediaCommand.TOGGLE_PLAY_PAUSE.bleRelayCode)
    }

    override fun skipToNext() {
        mediaControlManager.skipToNext { active ->
            emitEvent(if (active) "Skipped to next track" else "Sent Next Track key to Android")
        }
        bluetoothManager.relayMediaActionToBike(HandlebarMediaCommand.SKIP_NEXT.bleRelayCode)
    }

    override fun skipToPrevious() {
        mediaControlManager.skipToPrevious { active ->
            emitEvent(if (active) "Returned to previous track" else "Sent Previous Track key to Android")
        }
        bluetoothManager.relayMediaActionToBike(HandlebarMediaCommand.SKIP_PREVIOUS.bleRelayCode)
    }

    override fun volumeUp() {
        mediaControlManager.volumeUp()
        bluetoothManager.relayMediaActionToBike(HandlebarMediaCommand.VOLUME_UP.bleRelayCode)
    }

    override fun volumeDown() {
        mediaControlManager.volumeDown()
        bluetoothManager.relayMediaActionToBike(HandlebarMediaCommand.VOLUME_DOWN.bleRelayCode)
    }

    override fun setVolume(level: Int) {
        mediaControlManager.setVolume(level)
    }

    override fun dispatchHandlebarCommand(command: HandlebarMediaCommand) {
        when (command) {
            HandlebarMediaCommand.PLAY -> play()
            HandlebarMediaCommand.PAUSE -> pause()
            HandlebarMediaCommand.TOGGLE_PLAY_PAUSE -> togglePlayPause()
            HandlebarMediaCommand.SKIP_NEXT -> skipToNext()
            HandlebarMediaCommand.SKIP_PREVIOUS -> skipToPrevious()
            HandlebarMediaCommand.VOLUME_UP -> volumeUp()
            HandlebarMediaCommand.VOLUME_DOWN -> volumeDown()
        }
    }

    override fun handleHandlebarKeyCode(keyCode: Int): Boolean {
        val command = when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> HandlebarMediaCommand.PLAY
            KeyEvent.KEYCODE_MEDIA_PAUSE -> HandlebarMediaCommand.PAUSE
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> HandlebarMediaCommand.TOGGLE_PLAY_PAUSE
            KeyEvent.KEYCODE_MEDIA_NEXT -> HandlebarMediaCommand.SKIP_NEXT
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> HandlebarMediaCommand.SKIP_PREVIOUS
            else -> return false
        }
        dispatchHandlebarCommand(command)
        return true
    }

    fun nextTrack() = skipToNext()

    fun previousTrack() = skipToPrevious()

    // --- Google Maps Directions API Turn-by-Turn Overlay Actions ---

    fun startGoogleMapsNavigation(destination: String, originOverride: String = "") {
        val cleanDest = destination.trim()
        if (cleanDest.isEmpty()) {
            emitEvent("Enter a destination to start turn-by-turn navigation")
            return
        }
        if (!GoogleMapsDirectionsClient.hasConfiguredApiKey()) {
            val msg = "Add GOOGLE_MAPS_API_KEY in the AI Studio Secrets panel to enable Google Maps API routing."
            _navigationOverlayState.update {
                it.copy(isLoading = false, errorMessage = msg, apiKeyConfigured = false)
            }
            emitEvent(msg)
            return
        }

        _navigationOverlayState.update {
            it.copy(isLoading = true, errorMessage = null, apiKeyConfigured = true)
        }

        val manualOrigin = originOverride.trim()
        if (manualOrigin.isNotEmpty()) {
            fetchRouteWithOrigin(manualOrigin, cleanDest)
            return
        }

        rideLocationManager.fetchLastKnownLocation { coords ->
            val resolvedOrigin = when {
                coords != null -> "${coords.first},${coords.second}"
                !currentBikeOrNull()?.homeAddress.isNullOrBlank() -> currentBikeOrNull()?.homeAddress.orEmpty()
                else -> ""
            }
            if (resolvedOrigin.isBlank()) {
                val msg = "GPS location unavailable — grant Location permission or enter a starting location."
                _navigationOverlayState.update { it.copy(isLoading = false, errorMessage = msg) }
                emitEvent(msg)
            } else {
                fetchRouteWithOrigin(resolvedOrigin, cleanDest)
            }
        }
    }

    private fun fetchRouteWithOrigin(origin: String, destination: String) {
        viewModelScope.launch {
            val result = directionsClient.fetchTurnByTurnRoute(origin = origin, destination = destination)
            if (result.isSuccess) {
                val route = result.getOrThrow()
                _navigationOverlayState.update {
                    it.copy(isLoading = false, activeRoute = route, errorMessage = null)
                }
                if (rideLocationManager.hasLocationPermission() && !rideState.value.trackingActive) {
                    rideLocationManager.startRideTracking()
                }
                relayActiveApiRouteStep(route)
                emitEvent(
                    "Route ready • ${GoogleMapsDirectionsClient.formatDistance(route.totalDistanceMeters)} (${GoogleMapsDirectionsClient.formatEta(route.totalDurationSeconds)})"
                )
            } else {
                val msg = result.exceptionOrNull()?.localizedMessage ?: "Failed to fetch Google Maps directions"
                _navigationOverlayState.update { it.copy(isLoading = false, errorMessage = msg) }
                emitEvent(msg)
            }
        }
    }

    fun advanceNavigationStep() {
        val current = _navigationOverlayState.value.activeRoute ?: return
        val next = GoogleMapsDirectionsClient.moveToStep(current, current.currentStepIndex + 1)
        _navigationOverlayState.update { it.copy(activeRoute = next) }
        relayActiveApiRouteStep(next)
    }

    fun previousNavigationStep() {
        val current = _navigationOverlayState.value.activeRoute ?: return
        val prev = GoogleMapsDirectionsClient.moveToStep(current, current.currentStepIndex - 1)
        _navigationOverlayState.update { it.copy(activeRoute = prev) }
        relayActiveApiRouteStep(prev)
    }

    fun stopNavigationOverlay() {
        _navigationOverlayState.update { it.copy(activeRoute = null, errorMessage = null, isLoading = false) }
        RelayStateBus.updateNavManeuver(null)
    }

    private fun relayActiveApiRouteStep(route: ActiveNavigationRoute) {
        val step = route.currentStep ?: return
        val distLabel = GoogleMapsDirectionsClient.formatDistance(route.distanceToNextTurnMeters)
        val maneuver = RelayedNavManeuver(
            maneuver = step.maneuver,
            primaryInstruction = "$distLabel • ${step.instruction}",
            secondaryDetails = "${GoogleMapsDirectionsClient.formatEta(route.remainingDurationSeconds)} remaining (${GoogleMapsDirectionsClient.formatDistance(route.remainingDistanceMeters)})",
            sourcePackage = "com.google.maps.directions.api",
            timestampMs = System.currentTimeMillis()
        )
        if (bluetoothManager.state.value.connectionState == BtConnectionState.CONNECTED) {
            bluetoothManager.relayNavigationToBike(maneuver)
        }
    }

    // --- GPS Ride Actions ---

    fun toggleRideTracking() {
        if (rideState.value.trackingActive) {
            rideLocationManager.stopRideTracking()
        } else {
            rideLocationManager.startRideTracking()
        }
    }

    fun resetRideTrip() {
        rideLocationManager.resetTrip()
    }

    fun addTripDistanceToOdometer() {
        val tripKm = rideState.value.tripDistanceKm.roundToLong()
        if (tripKm <= 0L) {
            emitEvent("Ride distance is under 1 km — keep riding before adding to odometer")
            return
        }
        val currentBike = currentBikeOrNull()
        val updatedOdo = (currentBike?.odometerKm ?: 0L) + tripKm
        viewModelScope.launch {
            val result = repository.updateOdometer(currentUserId, updatedOdo, currentBike)
            if (result.isSuccess) {
                rideLocationManager.resetTrip()
                emitEvent("Added $tripKm km to CB350RS odometer ($updatedOdo km)")
            } else {
                emitEvent(result.exceptionOrNull()?.localizedMessage ?: "Failed to update odometer")
            }
        }
    }

    // --- Cloud Firestore Mutations (Crash-Safe Result Handling) ---

    fun saveBikeProfile(
        nickname: String,
        modelYear: Long,
        plateNumber: String,
        odometerKm: Long,
        homeAddress: String,
        serviceCenterName: String
    ) {
        val current = currentBikeOrNull()
        viewModelScope.launch {
            val result = repository.saveBikeProfile(
                userId = currentUserId,
                modelName = current?.modelName?.ifBlank { "Honda CB350RS" } ?: "Honda CB350RS",
                nickname = nickname,
                modelYear = modelYear,
                plateNumber = plateNumber,
                odometerKm = odometerKm,
                pairedDeviceName = current?.pairedDeviceName.orEmpty(),
                pairedDeviceAddress = current?.pairedDeviceAddress.orEmpty(),
                homeAddress = homeAddress,
                serviceCenterName = serviceCenterName,
                existsAlready = current != null && current.id.isNotBlank()
            )
            if (result.isSuccess) {
                emitEvent("CB350RS profile synced to cloud")
            } else {
                emitEvent(result.exceptionOrNull()?.localizedMessage ?: "Could not save bike profile")
            }
        }
    }

    fun addServiceRecord(
        title: String,
        category: ServiceCategory,
        odometerKm: Long,
        costAmount: Double,
        serviceDate: String,
        workshopName: String,
        notes: String
    ) {
        viewModelScope.launch {
            val result = repository.addServiceRecord(
                userId = currentUserId,
                title = title,
                category = category,
                odometerKm = odometerKm,
                costAmount = costAmount,
                serviceDate = serviceDate,
                workshopName = workshopName,
                notes = notes
            )
            if (result.isSuccess) {
                val bike = currentBikeOrNull()
                if (bike == null || odometerKm > bike.odometerKm) {
                    repository.updateOdometer(currentUserId, odometerKm, bike)
                }
                emitEvent("Service record saved")
            } else {
                emitEvent(result.exceptionOrNull()?.localizedMessage ?: "Could not save service record")
            }
        }
    }

    fun updateServiceRecord(
        recordId: String,
        title: String,
        category: ServiceCategory,
        odometerKm: Long,
        costAmount: Double,
        serviceDate: String,
        workshopName: String,
        notes: String
    ) {
        viewModelScope.launch {
            val result = repository.updateServiceRecord(
                userId = currentUserId,
                recordId = recordId,
                title = title,
                category = category,
                odometerKm = odometerKm,
                costAmount = costAmount,
                serviceDate = serviceDate,
                workshopName = workshopName,
                notes = notes
            )
            if (result.isSuccess) {
                emitEvent("Service record updated")
            } else {
                emitEvent(result.exceptionOrNull()?.localizedMessage ?: "Could not update service record")
            }
        }
    }

    fun deleteServiceRecord(recordId: String) {
        viewModelScope.launch {
            val result = repository.deleteServiceRecord(currentUserId, recordId)
            if (result.isSuccess) {
                emitEvent("Service record removed")
            } else {
                emitEvent(result.exceptionOrNull()?.localizedMessage ?: "Could not delete record")
            }
        }
    }

    fun saveLiveDiagnosticSnapshot(notes: String = "") {
        val bt = bluetoothState.value
        val tel = bt.telemetry
        val bikeOdo = currentBikeOrNull()?.odometerKm ?: 0L
        val voltage = tel.batteryVoltage ?: 0.0
        val tempC = (tel.engineTempC ?: 0).toLong()
        val rpm = (tel.rpm ?: 0).toLong()
        val dtcs = tel.storedDtcs

        val computedStatus = when {
            dtcs.isNotEmpty() -> DiagnosticStatus.FAULT
            voltage in 0.1..11.9 || voltage > 15.0 || tempC > 125L -> DiagnosticStatus.WARNING
            else -> DiagnosticStatus.HEALTHY
        }

        viewModelScope.launch {
            val result = repository.addDiagnosticLog(
                userId = currentUserId,
                source = DiagnosticSource.BLUETOOTH_OBD,
                batteryVoltage = voltage,
                engineTempC = tempC,
                rpmPeak = rpm,
                odometerKm = bikeOdo,
                dtcCodes = dtcs,
                status = computedStatus,
                notes = notes.ifBlank {
                    "Device: ${bt.connectedDevice?.name ?: "OBD-II"} (${tel.ecuProtocol.ifBlank { "Bluetooth" }})"
                }
            )
            if (result.isSuccess) {
                emitEvent("ECU diagnostic snapshot saved to cloud")
            } else {
                emitEvent(result.exceptionOrNull()?.localizedMessage ?: "Failed to save diagnostic snapshot")
            }
        }
    }

    fun saveManualDiagnosticCheck(
        batteryVoltage: Double,
        engineTempC: Long,
        odometerKm: Long,
        dtcCodesInput: String,
        notes: String
    ) {
        val parsedCodes = dtcCodesInput
            .split(",", " ", ";")
            .map { it.trim().uppercase() }
            .filter { it.length in 4..8 }
            .distinct()
            .take(20)

        val status = when {
            parsedCodes.isNotEmpty() -> DiagnosticStatus.FAULT
            batteryVoltage in 0.1..11.9 || batteryVoltage > 15.0 || engineTempC > 125L -> DiagnosticStatus.WARNING
            else -> DiagnosticStatus.HEALTHY
        }

        viewModelScope.launch {
            val result = repository.addDiagnosticLog(
                userId = currentUserId,
                source = DiagnosticSource.MANUAL_CHECK,
                batteryVoltage = batteryVoltage,
                engineTempC = engineTempC,
                rpmPeak = 0L,
                odometerKm = odometerKm,
                dtcCodes = parsedCodes,
                status = status,
                notes = notes
            )
            if (result.isSuccess) {
                emitEvent("Pre-ride inspection logged")
            } else {
                emitEvent(result.exceptionOrNull()?.localizedMessage ?: "Failed to log check")
            }
        }
    }

    fun deleteDiagnosticLog(logId: String) {
        viewModelScope.launch {
            val result = repository.deleteDiagnosticLog(currentUserId, logId)
            if (result.isSuccess) {
                emitEvent("Diagnostic log deleted")
            } else {
                emitEvent(result.exceptionOrNull()?.localizedMessage ?: "Failed to delete log")
            }
        }
    }

    fun cleanupHardwareOnSignOut() {
        bluetoothManager.release()
        rideLocationManager.release()
        mediaControlManager.release()
    }

    override fun onCleared() {
        super.onCleared()
        cleanupHardwareOnSignOut()
    }

    private fun emitEvent(message: String) {
        _events.tryEmit(message)
    }

    private fun computeCb350RsIntervals(
        currentOdoKm: Long,
        records: List<ServiceRecord>
    ): List<MaintenanceIntervalStatus> {
        val trackedCategories = listOf(
            ServiceCategory.CHAIN_CARE,
            ServiceCategory.ENGINE_OIL,
            ServiceCategory.BRAKES,
            ServiceCategory.AIR_FILTER,
            ServiceCategory.BATTERY,
            ServiceCategory.TIRES
        )
        return trackedCategories.map { category ->
            val interval = category.recommendedIntervalKm ?: 6000L
            val lastDoneKm = records
                .filter {
                    it.resolvedCategory() == category ||
                        (category == ServiceCategory.ENGINE_OIL && it.resolvedCategory() == ServiceCategory.GENERAL_SERVICE)
                }
                .maxOfOrNull { it.odometerKm }

            val baselineKm = lastDoneKm ?: 0L
            val nextDueKm = baselineKm + interval
            val traveledSince = (currentOdoKm - baselineKm).coerceAtLeast(0L)
            val remainingKm = nextDueKm - currentOdoKm
            val progress = (traveledSince.toFloat() / interval.toFloat()).coerceIn(0f, 1f)
            val overdue = remainingKm <= 0L && currentOdoKm > 0L
            val dueSoon = !overdue && remainingKm <= (interval * 0.2).toLong().coerceAtLeast(100L)

            MaintenanceIntervalStatus(
                category = category,
                intervalKm = interval,
                lastDoneKm = lastDoneKm,
                nextDueKm = nextDueKm,
                remainingKm = remainingKm,
                progressFraction = progress,
                overdue = overdue,
                dueSoon = dueSoon
            )
        }.sortedWith(
            compareByDescending<MaintenanceIntervalStatus> { it.overdue }
                .thenByDescending { it.dueSoon }
                .thenBy { it.remainingKm }
        )
    }

    private companion object {
        const val TAG = "MotoViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
