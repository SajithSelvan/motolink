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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BikeProfile
import com.example.data.model.MaintenanceIntervalStatus
import com.example.data.model.ServiceCategory
import com.example.data.model.ServiceRecord
import com.example.data.model.UiState
import com.example.ui.theme.DiagnosticTeal
import com.example.ui.theme.FaultRed
import com.example.ui.theme.RsCrimson
import com.example.ui.theme.TachAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ServiceTrackerScreen(
    bikeProfile: BikeProfile?,
    serviceRecordsState: UiState<List<ServiceRecord>>,
    intervals: List<MaintenanceIntervalStatus>,
    onOpenBikeProfileEditor: () -> Unit,
    onAddRecord: (String, ServiceCategory, Long, Double, String, String, String) -> Unit,
    onUpdateRecord: (String, String, ServiceCategory, Long, Double, String, String, String) -> Unit,
    onDeleteRecord: (String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf<ServiceCategory?>(null) }
    var editingRecord by remember { mutableStateOf<ServiceRecord?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val allRecords = (serviceRecordsState as? UiState.Success)?.data.orEmpty()
    val filteredRecords = remember(allRecords, selectedCategory) {
        if (selectedCategory == null) allRecords
        else allRecords.filter { it.resolvedCategory() == selectedCategory }
    }
    val totalSpend = remember(allRecords) { allRecords.sumOf { it.costAmount } }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 14.dp,
                bottom = 88.dp
            ),
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
                            text = "HONDA CB350RS GARAGE",
                            style = MaterialTheme.typography.labelSmall,
                            color = RsCrimson
                        )
                        Text(
                            text = "Service Tracking",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenBikeProfileEditor,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("service_edit_bike_button")
                    ) {
                        Icon(Icons.Filled.TwoWheeler, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Bike Info")
                    }
                }
            }

            // Odometer & Spend Summary Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryStatCard(
                        label = "ODOMETER",
                        value = "%,d km".format(bikeProfile?.odometerKm ?: 0L),
                        accent = TachAmber,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryStatCard(
                        label = "TOTAL SPEND",
                        value = "%,.0f".format(totalSpend),
                        accent = DiagnosticTeal,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryStatCard(
                        label = "LOGS",
                        value = allRecords.size.toString(),
                        accent = RsCrimson,
                        modifier = Modifier.weight(0.7f)
                    )
                }
            }

            // Honda CB350RS Factory Interval Cards (horizontal scroll)
            if (intervals.isNotEmpty()) {
                item {
                    Text(
                        text = "FACTORY MAINTENANCE INTERVALS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(intervals, key = { it.category.name }) { item ->
                            IntervalStatusCard(
                                status = item,
                                onQuickLog = {
                                    editingRecord = ServiceRecord(
                                        title = item.category.displayName,
                                        category = item.category.wireValue,
                                        odometerKm = bikeProfile?.odometerKm ?: 0L,
                                        serviceDate = todayIsoDate(),
                                        workshopName = bikeProfile?.serviceCenterName.orEmpty()
                                    )
                                    showAddDialog = true
                                }
                            )
                        }
                    }
                }
            }

            // Category Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("All (${allRecords.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RsCrimson.copy(alpha = 0.22f),
                                selectedLabelColor = RsCrimson
                            )
                        )
                    }
                    items(ServiceCategory.entries) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                selectedCategory = if (selectedCategory == cat) null else cat
                            },
                            label = { Text(cat.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RsCrimson.copy(alpha = 0.22f),
                                selectedLabelColor = RsCrimson
                            )
                        )
                    }
                }
            }

            // Service Logbook List
            when (serviceRecordsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = RsCrimson)
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Text(
                            text = serviceRecordsState.message,
                            color = FaultRed,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is UiState.Success -> {
                    if (filteredRecords.isEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.large,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Build,
                                        contentDescription = null,
                                        tint = TachAmber,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        text = if (selectedCategory == null) {
                                            "No service records logged yet"
                                        } else {
                                            "No ${selectedCategory?.displayName} records"
                                        },
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Tap Log Service to track oil changes, chain lubes, brake pads, and BigWing invoices.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredRecords, key = { it.id }) { record ->
                            ServiceRecordCard(
                                record = record,
                                onEdit = {
                                    editingRecord = record
                                    showAddDialog = true
                                },
                                onDelete = { onDeleteRecord(record.id) }
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                editingRecord = null
                showAddDialog = true
            },
            containerColor = RsCrimson,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp)
                .testTag("service_add_fab")
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Log service record")
        }
    }

    if (showAddDialog) {
        ServiceRecordEditorDialog(
            existing = editingRecord,
            defaultOdoKm = bikeProfile?.odometerKm ?: 0L,
            defaultWorkshop = bikeProfile?.serviceCenterName.orEmpty(),
            onDismiss = {
                showAddDialog = false
                editingRecord = null
            },
            onSave = { title, cat, odo, cost, date, workshop, notes ->
                val existingId = editingRecord?.id.orEmpty()
                if (existingId.isNotBlank()) {
                    onUpdateRecord(existingId, title, cat, odo, cost, date, workshop, notes)
                } else {
                    onAddRecord(title, cat, odo, cost, date, workshop, notes)
                }
                showAddDialog = false
                editingRecord = null
            }
        )
    }
}

@Composable
private fun SummaryStatCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun IntervalStatusCard(
    status: MaintenanceIntervalStatus,
    onQuickLog: () -> Unit
) {
    val accent = when {
        status.overdue -> FaultRed
        status.dueSoon -> TachAmber
        else -> DiagnosticTeal
    }
    Surface(
        onClick = onQuickLog,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
        modifier = Modifier.width(196.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = status.category.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (status.overdue) {
                    "Overdue ${-status.remainingKm} km"
                } else {
                    "${status.remainingKm} km remaining"
                },
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { status.progressFraction },
                color = accent,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp))
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Every %,d km • Tap to log".format(status.intervalKm),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ServiceRecordCard(
    record: ServiceRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val category = record.resolvedCategory()
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
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = TachAmber.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = category.displayName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TachAmber,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = record.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${record.serviceDate} • %,d km".format(record.odometerKm) +
                            if (record.workshopName.isNotBlank()) " • ${record.workshopName}" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "%,.0f".format(record.costAmount),
                        style = MaterialTheme.typography.titleMedium,
                        color = DiagnosticTeal,
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit service record",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Delete service record",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            if (record.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = record.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ServiceRecordEditorDialog(
    existing: ServiceRecord?,
    defaultOdoKm: Long,
    defaultWorkshop: String,
    onDismiss: () -> Unit,
    onSave: (String, ServiceCategory, Long, Double, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var category by remember { mutableStateOf(existing?.resolvedCategory() ?: ServiceCategory.ENGINE_OIL) }
    var odoText by remember { mutableStateOf((existing?.odometerKm ?: defaultOdoKm).toString()) }
    var costText by remember { mutableStateOf(if ((existing?.costAmount ?: 0.0) > 0.0) existing?.costAmount.toString() else "") }
    var dateText by remember { mutableStateOf(existing?.serviceDate?.ifBlank { todayIsoDate() } ?: todayIsoDate()) }
    var workshop by remember { mutableStateOf(existing?.workshopName?.ifBlank { defaultWorkshop } ?: defaultWorkshop) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }

    val isEditing = !existing?.id.isNullOrBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Service Record" else "Log CB350RS Service") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text("Service title *") },
                    placeholder = { Text("e.g., 6,000 km Synthetic Oil & Filter") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("service_dialog_title")
                )

                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ServiceCategory.entries) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.displayName) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = odoText,
                        onValueChange = { odoText = it },
                        label = { Text("Odometer km") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = costText,
                        onValueChange = { costText = it },
                        label = { Text("Cost") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it.take(20) },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = workshop,
                    onValueChange = { workshop = it.take(80) },
                    label = { Text("Workshop / BigWing Center") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(400) },
                    label = { Text("Notes (oil grade, parts, invoice)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanTitle = title.trim().ifBlank { category.displayName }
                    val odo = odoText.toLongOrNull()?.coerceIn(0L, 999999L) ?: defaultOdoKm
                    val cost = costText.toDoubleOrNull()?.coerceIn(0.0, 1000000.0) ?: 0.0
                    val date = dateText.trim().ifBlank { todayIsoDate() }
                    onSave(cleanTitle, category, odo, cost, date, workshop, notes)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RsCrimson),
                modifier = Modifier.testTag("service_dialog_save_button")
            ) {
                Text(if (isEditing) "Update" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BikeProfileEditorDialog(
    current: BikeProfile?,
    onDismiss: () -> Unit,
    onSave: (String, Long, String, Long, String, String) -> Unit
) {
    var nickname by remember { mutableStateOf(current?.nickname?.ifBlank { "My CB350RS" } ?: "My CB350RS") }
    var yearText by remember { mutableStateOf((current?.modelYear ?: 2024L).toString()) }
    var plate by remember { mutableStateOf(current?.plateNumber.orEmpty()) }
    var odoText by remember { mutableStateOf((current?.odometerKm ?: 0L).toString()) }
    var homeAddress by remember { mutableStateOf(current?.homeAddress.orEmpty()) }
    var serviceCenter by remember { mutableStateOf(current?.serviceCenterName?.ifBlank { "Honda BigWing Service" } ?: "Honda BigWing Service") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Honda CB350RS Profile") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it.take(40) },
                    label = { Text("Bike Nickname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = yearText,
                        onValueChange = { yearText = it },
                        label = { Text("Model Year") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = plate,
                        onValueChange = { plate = it.uppercase().take(20) },
                        label = { Text("Plate No.") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = odoText,
                    onValueChange = { odoText = it },
                    label = { Text("Current Odometer (km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = homeAddress,
                    onValueChange = { homeAddress = it.take(120) },
                    label = { Text("Home Navigation Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = serviceCenter,
                    onValueChange = { serviceCenter = it.take(80) },
                    label = { Text("Preferred BigWing Service Center") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val y = yearText.toLongOrNull()?.coerceIn(2000L, 2035L) ?: 2024L
                    val odo = odoText.toLongOrNull()?.coerceIn(0L, 999999L) ?: 0L
                    onSave(nickname, y, plate, odo, homeAddress, serviceCenter)
                },
                colors = ButtonDefaults.buttonColors(containerColor = RsCrimson),
                modifier = Modifier.testTag("bike_profile_save_button")
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun todayIsoDate(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
