package com.example

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.AuthScreen
import com.example.auth.authStateFlow
import com.example.auth.signOutRider
import com.example.data.firestore.MotoRepository
import com.example.data.model.UiState
import com.example.hardware.BluetoothManager
import com.example.hardware.MediaControlManager
import com.example.hardware.MediaController
import com.example.hardware.RideLocationManager
import com.example.ui.MotoViewModel
import com.example.ui.components.BluetoothDeviceSheet
import com.example.ui.components.BluetoothPermissionBanner
import com.example.ui.components.BluetoothPermissionRationaleDialog
import com.example.ui.components.RideWorkflowSheet
import com.example.ui.components.SafetySimulationBanner
import com.example.ui.components.TurnByTurnNavOverlay
import com.example.ui.components.evaluateBluetoothPermissions
import com.example.ui.components.openAppPermissionSettings
import com.example.ui.screens.BikeProfileEditorDialog
import com.example.ui.screens.CockpitScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.RideNavScreen
import com.example.ui.screens.ServiceTrackerScreen
import com.example.ui.theme.MotoLinkTheme
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RsCrimson
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    private var activeMediaController: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MotoLinkTheme(darkTheme = true) {
                MotoLinkRoot(
                    onBindMediaController = { controller ->
                        activeMediaController = controller
                    }
                )
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val controller = activeMediaController
        if (controller != null && event?.repeatCount == 0 && controller.handleHandlebarKeyCode(keyCode)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

private enum class MotoTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    COCKPIT("cockpit", "Cockpit", Icons.Filled.Speed, Icons.Outlined.Speed),
    RIDE_NAV("ride_nav", "Ride & Nav", Icons.Filled.Navigation, Icons.Outlined.Navigation),
    DIAGNOSTICS("diagnostics", "OBD", Icons.Filled.Memory, Icons.Outlined.Memory),
    SERVICE("service", "Service", Icons.Filled.Build, Icons.Outlined.Build)
}

@Composable
fun MotoLinkRoot(
    auth: FirebaseAuth = Firebase.auth,
    onBindMediaController: (MediaController?) -> Unit = {}
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        DisposableEffect(Unit) {
            onBindMediaController(null)
            onDispose { }
        }
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            MotoLinkAuthenticatedShell(
                currentUserId = user.uid,
                riderDisplayName = user.displayName?.takeIf { it.isNotBlank() }
                    ?: user.email?.substringBefore("@")
                    ?: "CB350RS Rider",
                onBindMediaController = onBindMediaController
            )
        }
    }
}

@Composable
private fun MotoLinkAuthenticatedShell(
    currentUserId: String,
    riderDisplayName: String,
    onBindMediaController: (MediaController?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    // Keyed by currentUserId so signing out or switching accounts recreates ViewModel & Firestore listeners cleanly
    val viewModel: MotoViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MotoViewModel(
                    repository = MotoRepository(db),
                    currentUserId = currentUserId,
                    bluetoothManager = BluetoothManager(app),
                    mediaControlManager = MediaControlManager(app),
                    rideLocationManager = RideLocationManager(app)
                )
            }
        }
    )

    val bikeState by viewModel.bikeProfileState.collectAsStateWithLifecycle()
    val serviceRecordsState by viewModel.serviceRecordsState.collectAsStateWithLifecycle()
    val diagnosticLogsState by viewModel.diagnosticLogsState.collectAsStateWithLifecycle()
    val intervals by viewModel.maintenanceIntervals.collectAsStateWithLifecycle()
    val btState by viewModel.bluetoothState.collectAsStateWithLifecycle()
    val mediaState by viewModel.mediaState.collectAsStateWithLifecycle()
    val rideState by viewModel.rideState.collectAsStateWithLifecycle()
    val relayedNav by viewModel.relayedNav.collectAsStateWithLifecycle()
    val relayedMedia by viewModel.relayedMedia.collectAsStateWithLifecycle()
    val lastRelayPacket by viewModel.lastRelayPacket.collectAsStateWithLifecycle()
    val navOverlayState by viewModel.navigationOverlayState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    var showBluetoothSheet by remember { mutableStateOf(false) }
    var showBikeProfileDialog by remember { mutableStateOf(false) }
    var showRideWorkflowSheet by remember { mutableStateOf(false) }
    var workflowInitialStep by remember { mutableIntStateOf(0) }
    var hasRequestedBtPermissions by remember { mutableStateOf(false) }
    var showBtPermissionDialog by remember { mutableStateOf(false) }
    var btPermissionBannerDismissed by remember { mutableStateOf(false) }
    var btPermissionSnapshot by remember(context) {
        mutableStateOf(evaluateBluetoothPermissions(context, hasRequestedOnce = false))
    }

    DisposableEffect(viewModel) {
        onBindMediaController(viewModel)
        onDispose { onBindMediaController(null) }
    }

    fun refreshAllPermissions() {
        btPermissionSnapshot = evaluateBluetoothPermissions(context, hasRequestedBtPermissions)
        viewModel.refreshBluetoothState()
        viewModel.rideLocationManager.refreshPermission()
        viewModel.mediaControlManager.refreshState()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refreshAllPermissions()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasRequestedBtPermissions = true
        val updated = evaluateBluetoothPermissions(context, hasRequestedOnce = true)
        btPermissionSnapshot = updated
        viewModel.refreshBluetoothState()
        viewModel.rideLocationManager.refreshPermission()

        if (updated.allBluetoothRequiredGranted) {
            showBtPermissionDialog = false
            btPermissionBannerDismissed = true
            if (viewModel.bluetoothState.value.adapterEnabled) {
                viewModel.startBluetoothDiscovery()
            }
            viewModel.showMessage("Bluetooth permissions granted — ready to link CB350RS OBD-II")
        } else if (updated.permanentlyDenied) {
            showBtPermissionDialog = true
            viewModel.showMessage("Bluetooth permissions denied — enable Nearby Devices in App Settings")
        } else {
            viewModel.showMessage("Grant Bluetooth permissions to discover & connect your CB350RS OBD-II")
        }
    }

    fun requestBluetoothPermissionsFlow() {
        val current = evaluateBluetoothPermissions(context, hasRequestedBtPermissions)
        btPermissionSnapshot = current
        when {
            current.allBluetoothRequiredGranted -> {
                showBtPermissionDialog = false
                viewModel.refreshBluetoothState()
                if (viewModel.bluetoothState.value.adapterEnabled) {
                    viewModel.startBluetoothDiscovery()
                }
            }
            current.permanentlyDenied -> {
                showBtPermissionDialog = true
            }
            current.permissionsToRequest.isNotEmpty() -> {
                btPermissionLauncher.launch(current.permissionsToRequest.toTypedArray())
            }
            else -> {
                btPermissionLauncher.launch(viewModel.bluetoothManager.requiredPermissions())
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        refreshAllPermissions()
        val granted = grants.values.any { it }
        if (granted) {
            viewModel.toggleRideTracking()
        } else {
            viewModel.showMessage("Location permission denied — grant Location to track live GPS speed")
        }
    }

    fun navigateToTab(tab: MotoTab) {
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val bikeProfile = (bikeState as? UiState.Success)?.data

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = ObsidianBg,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        MotoTab.entries.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = RsCrimson,
                                    indicatorColor = RsCrimson,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.statusBarsPadding()
                    ) {
                        MotoTab.entries.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = RsCrimson,
                                    indicatorColor = RsCrimson
                                )
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    if (btState.safetySimulationActive) {
                        SafetySimulationBanner(
                            onExitSimulation = viewModel::toggleSafetySimulationMode
                        )
                    } else if (btState.hardwareSupported &&
                        !btPermissionSnapshot.allBluetoothRequiredGranted &&
                        !btPermissionBannerDismissed
                    ) {
                        BluetoothPermissionBanner(
                            snapshot = btPermissionSnapshot,
                            onRequestOrOpenSettings = {
                                if (btPermissionSnapshot.permanentlyDenied) {
                                    if (!openAppPermissionSettings(context)) {
                                        viewModel.showMessage("Open Android Settings -> Apps -> MotoLink RS -> Permissions")
                                    }
                                } else {
                                    requestBluetoothPermissionsFlow()
                                }
                            },
                            onShowDetails = {
                                btPermissionSnapshot = evaluateBluetoothPermissions(context, hasRequestedBtPermissions)
                                showBtPermissionDialog = true
                            },
                            onDismiss = { btPermissionBannerDismissed = true }
                        )
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = MotoTab.COCKPIT.route
                    ) {
                        composable(MotoTab.COCKPIT.route) {
                            CockpitScreen(
                                riderDisplayName = riderDisplayName,
                                bikeState = bikeState,
                                intervals = intervals,
                                btState = btState,
                                permissionSnapshot = btPermissionSnapshot,
                                navOverlayState = navOverlayState,
                                mediaState = mediaState,
                                mediaController = viewModel,
                                relayedMedia = relayedMedia,
                                relayedNav = relayedNav,
                                rideState = rideState,
                                onOpenRideWorkflow = { step ->
                                    refreshAllPermissions()
                                    workflowInitialStep = step
                                    showRideWorkflowSheet = true
                                },
                                onOpenBluetoothSheet = {
                                    viewModel.refreshBluetoothState()
                                    showBluetoothSheet = true
                                },
                                onPollObdNow = viewModel::pollObdNow,
                                onOpenBikeProfileEditor = { showBikeProfileDialog = true },
                                onNavigateToRide = { navigateToTab(MotoTab.RIDE_NAV) },
                                onNavigateToDiagnostics = { navigateToTab(MotoTab.DIAGNOSTICS) },
                                onNavigateToService = { navigateToTab(MotoTab.SERVICE) },
                                onSignOut = {
                                    signOutRider(
                                        credentialManager = credentialManager,
                                        scope = scope,
                                        onBeforeSignOut = viewModel::cleanupHardwareOnSignOut
                                    )
                                }
                            )
                        }

                        composable(MotoTab.RIDE_NAV.route) {
                            RideNavScreen(
                                bikeProfile = bikeProfile,
                                btState = btState,
                                mediaState = mediaState,
                                mediaController = viewModel,
                                relayedMedia = relayedMedia,
                                relayedNav = relayedNav,
                                navOverlayState = navOverlayState,
                                lastRelayPacket = lastRelayPacket,
                                rideState = rideState,
                                onOpenBluetoothSheet = {
                                    viewModel.refreshBluetoothState()
                                    showBluetoothSheet = true
                                },
                                onOpenBikeProfileEditor = { showBikeProfileDialog = true },
                                onOpenMusicApp = {
                                    val launched = viewModel.mediaControlManager.openMusicApp(context)
                                    if (!launched) {
                                        viewModel.showMessage("No music player app found on this device")
                                    }
                                },
                                onStartApiNavigation = viewModel::startGoogleMapsNavigation,
                                onStopApiNavigation = viewModel::stopNavigationOverlay,
                                onTriggerSimulatedNav = viewModel::triggerSimulatedNavRelay,
                                onShowMessage = viewModel::showMessage,
                                onRequestLocationAndStartRide = {
                                    if (viewModel.rideLocationManager.hasLocationPermission()) {
                                        viewModel.toggleRideTracking()
                                    } else {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                },
                                onResetTrip = viewModel::resetRideTrip,
                                onAddTripToOdometer = viewModel::addTripDistanceToOdometer
                            )
                        }

                        composable(MotoTab.DIAGNOSTICS.route) {
                            DiagnosticsScreen(
                                bikeProfile = bikeProfile,
                                btState = btState,
                                diagnosticLogsState = diagnosticLogsState,
                                onOpenBluetoothSheet = {
                                    viewModel.refreshBluetoothState()
                                    showBluetoothSheet = true
                                },
                                onPollObdNow = viewModel::pollObdNow,
                                onScanDtcs = viewModel::scanObdDtcs,
                                onClearDtcs = viewModel::clearObdDtcs,
                                onSaveLiveSnapshot = { viewModel.saveLiveDiagnosticSnapshot() },
                                onSaveManualCheck = viewModel::saveManualDiagnosticCheck,
                                onDeleteLog = viewModel::deleteDiagnosticLog
                            )
                        }

                        composable(MotoTab.SERVICE.route) {
                            ServiceTrackerScreen(
                                bikeProfile = bikeProfile,
                                serviceRecordsState = serviceRecordsState,
                                intervals = intervals,
                                onOpenBikeProfileEditor = { showBikeProfileDialog = true },
                                onAddRecord = viewModel::addServiceRecord,
                                onUpdateRecord = viewModel::updateServiceRecord,
                                onDeleteRecord = viewModel::deleteServiceRecord
                            )
                        }
                    }

                    // Persistent Turn-by-Turn Navigation Overlay across all tabs
                    TurnByTurnNavOverlay(
                        activeRoute = navOverlayState.activeRoute,
                        relayedNav = relayedNav,
                        bluetoothConnected = btState.connectionState == com.example.hardware.BtConnectionState.CONNECTED,
                        onPreviousStep = viewModel::previousNavigationStep,
                        onNextStep = viewModel::advanceNavigationStep,
                        onDismissOverlay = viewModel::stopNavigationOverlay,
                        onOpenRideTab = { navigateToTab(MotoTab.RIDE_NAV) },
                        modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter)
                    )
                    }
                }
            }
        }
    }

    if (showBluetoothSheet) {
        BluetoothDeviceSheet(
            btState = btState,
            onDismiss = {
                viewModel.stopBluetoothDiscovery()
                showBluetoothSheet = false
            },
            onRequestPermissions = {
                requestBluetoothPermissionsFlow()
            },
            onOpenBluetoothSettings = {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {
                    viewModel.showMessage("Open Android Settings -> Connected Devices -> Bluetooth")
                }
            },
            onSelectMode = viewModel::setBluetoothLinkMode,
            onStartScan = viewModel::startBluetoothDiscovery,
            onStopScan = viewModel::stopBluetoothDiscovery,
            onConnectDevice = { device ->
                viewModel.connectToBluetoothDevice(device)
                showBluetoothSheet = false
            },
            onDisconnect = {
                viewModel.disconnectBluetooth()
                showBluetoothSheet = false
            },
            onToggleSafetySimulation = {
                viewModel.toggleSafetySimulationMode()
            }
        )
    }

    if (showBtPermissionDialog) {
        BluetoothPermissionRationaleDialog(
            snapshot = btPermissionSnapshot,
            onRequestPermissions = {
                showBtPermissionDialog = false
                requestBluetoothPermissionsFlow()
            },
            onOpenSettings = {
                showBtPermissionDialog = false
                if (!openAppPermissionSettings(context)) {
                    viewModel.showMessage("Open Android Settings -> Apps -> MotoLink RS -> Permissions")
                }
            },
            onDismiss = { showBtPermissionDialog = false }
        )
    }

    if (showRideWorkflowSheet) {
        RideWorkflowSheet(
            initialStep = workflowInitialStep,
            bikeProfile = bikeProfile,
            intervals = intervals,
            permissionSnapshot = btPermissionSnapshot,
            btState = btState,
            rideState = rideState,
            onDismiss = { showRideWorkflowSheet = false },
            onRequestPermissions = { requestBluetoothPermissionsFlow() },
            onOpenAppSettings = {
                if (!openAppPermissionSettings(context)) {
                    viewModel.showMessage("Open Android Settings -> Apps -> MotoLink RS -> Permissions")
                }
            },
            onOpenBluetoothSheet = {
                showRideWorkflowSheet = false
                viewModel.refreshBluetoothState()
                showBluetoothSheet = true
            },
            onToggleSafetySimulation = viewModel::toggleSafetySimulationMode,
            onPollObdAndScanDtcs = {
                viewModel.pollObdNow()
                viewModel.scanObdDtcs()
            },
            onSaveLiveSnapshot = { viewModel.saveLiveDiagnosticSnapshot("Pre-ride workflow check") },
            onLaunchRide = { destination, startMusic, startGps ->
                if (startGps && !rideState.trackingActive) {
                    if (viewModel.rideLocationManager.hasLocationPermission()) {
                        viewModel.toggleRideTracking()
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }
                if (startMusic) {
                    viewModel.play()
                }
                if (destination.isNotBlank()) {
                    viewModel.startGoogleMapsNavigation(destination)
                }
                viewModel.showMessage("CB350RS Ride Workflow launched — ride safe!")
            }
        )
    }

    if (showBikeProfileDialog) {
        BikeProfileEditorDialog(
            current = bikeProfile,
            onDismiss = { showBikeProfileDialog = false },
            onSave = { nick, year, plate, odo, home, center ->
                viewModel.saveBikeProfile(nick, year, plate, odo, home, center)
                showBikeProfileDialog = false
            }
        )
    }
}
