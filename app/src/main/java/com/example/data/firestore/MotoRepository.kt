package com.example.data.firestore

import android.content.Context
import com.example.R
import com.example.data.model.BikeProfile
import com.example.data.model.DiagnosticLog
import com.example.data.model.DiagnosticSource
import com.example.data.model.DiagnosticStatus
import com.example.data.model.ServiceCategory
import com.example.data.model.ServiceRecord
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class MotoRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        const val PRIMARY_BIKE_ID = "cb350rs_primary"
    }

    private fun requireSignedInUid(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(uid == expectedUserId) { "Authenticated user mismatch; refusing cross-user operation." }
        return uid
    }

    fun observeBikeProfile(userId: String): Flow<BikeProfile?> {
        val path = "users/$userId/bikes/$PRIMARY_BIKE_ID"
        return db.collection("users")
            .document(userId)
            .collection("bikes")
            .document(PRIMARY_BIKE_ID)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        BikeProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, path)
                }
                throw error
            }
    }

    fun observeServiceRecords(userId: String): Flow<List<ServiceRecord>> {
        val path = "users/$userId/serviceRecords"
        return db.collection("users")
            .document(userId)
            .collection("serviceRecords")
            .orderBy("odometerKm", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ServiceRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeDiagnosticLogs(userId: String): Flow<List<DiagnosticLog>> {
        val path = "users/$userId/diagnosticLogs"
        return db.collection("users")
            .document(userId)
            .collection("diagnosticLogs")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(40)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    DiagnosticLog::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun saveBikeProfile(
        userId: String,
        modelName: String,
        nickname: String,
        modelYear: Long,
        plateNumber: String,
        odometerKm: Long,
        pairedDeviceName: String,
        pairedDeviceAddress: String,
        homeAddress: String,
        serviceCenterName: String,
        existsAlready: Boolean
    ): Result<Unit> {
        val path = "users/$userId/bikes/$PRIMARY_BIKE_ID"
        return try {
            val uid = requireSignedInUid(userId)
            val safeModel = modelName.trim().ifBlank { "Honda CB350RS" }.take(60)
            val safeNick = nickname.trim().ifBlank { "My CB350RS" }.take(40)
            val safeYear = modelYear.coerceIn(2000L, 2035L)
            val safeOdo = odometerKm.coerceIn(0L, 999999L)

            val docRef = db.collection("users")
                .document(uid)
                .collection("bikes")
                .document(PRIMARY_BIKE_ID)

            val docSnapshot = if (existsAlready) null else docRef.get().await()
            val alreadyExists = existsAlready || (docSnapshot?.exists() == true)

            if (alreadyExists) {
                val updates = mutableMapOf<String, Any>(
                    "modelName" to safeModel,
                    "nickname" to safeNick,
                    "modelYear" to safeYear,
                    "plateNumber" to plateNumber.trim().take(20),
                    "odometerKm" to safeOdo,
                    "pairedDeviceName" to pairedDeviceName.trim().take(80),
                    "pairedDeviceAddress" to pairedDeviceAddress.trim().take(32),
                    "homeAddress" to homeAddress.trim().take(120),
                    "serviceCenterName" to serviceCenterName.trim().take(80),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "modelName" to safeModel,
                    "nickname" to safeNick,
                    "modelYear" to safeYear,
                    "plateNumber" to plateNumber.trim().take(20),
                    "odometerKm" to safeOdo,
                    "pairedDeviceName" to pairedDeviceName.trim().take(80),
                    "pairedDeviceAddress" to pairedDeviceAddress.trim().take(32),
                    "homeAddress" to homeAddress.trim().take(120),
                    "serviceCenterName" to serviceCenterName.trim().take(80),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (existsAlready) OperationType.UPDATE else OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateOdometer(
        userId: String,
        newOdometerKm: Long,
        currentProfile: BikeProfile?
    ): Result<Unit> {
        val profile = currentProfile ?: BikeProfile(userId = userId)
        return saveBikeProfile(
            userId = userId,
            modelName = profile.modelName,
            nickname = profile.nickname,
            modelYear = profile.modelYear,
            plateNumber = profile.plateNumber,
            odometerKm = newOdometerKm.coerceIn(0L, 999999L),
            pairedDeviceName = profile.pairedDeviceName,
            pairedDeviceAddress = profile.pairedDeviceAddress,
            homeAddress = profile.homeAddress,
            serviceCenterName = profile.serviceCenterName,
            existsAlready = currentProfile != null && currentProfile.id.isNotBlank()
        )
    }

    suspend fun updatePairedDevice(
        userId: String,
        deviceName: String,
        deviceAddress: String,
        currentProfile: BikeProfile?
    ): Result<Unit> {
        val profile = currentProfile ?: BikeProfile(userId = userId)
        return saveBikeProfile(
            userId = userId,
            modelName = profile.modelName,
            nickname = profile.nickname,
            modelYear = profile.modelYear,
            plateNumber = profile.plateNumber,
            odometerKm = profile.odometerKm,
            pairedDeviceName = deviceName,
            pairedDeviceAddress = deviceAddress,
            homeAddress = profile.homeAddress,
            serviceCenterName = profile.serviceCenterName,
            existsAlready = currentProfile != null && currentProfile.id.isNotBlank()
        )
    }

    suspend fun addServiceRecord(
        userId: String,
        title: String,
        category: ServiceCategory,
        odometerKm: Long,
        costAmount: Double,
        serviceDate: String,
        workshopName: String,
        notes: String
    ): Result<Unit> {
        var path = "users/$userId/serviceRecords"
        return try {
            val uid = requireSignedInUid(userId)
            val cleanTitle = title.trim().take(80)
            require(cleanTitle.isNotEmpty()) { "Service title is required" }
            val cleanDate = serviceDate.trim().take(20)
            require(cleanDate.length >= 4) { "Service date is required" }

            val docRef = db.collection("users")
                .document(uid)
                .collection("serviceRecords")
                .document()
            path = docRef.path

            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "category" to category.wireValue,
                "odometerKm" to odometerKm.coerceIn(0L, 999999L),
                "costAmount" to costAmount.coerceIn(0.0, 1000000.0),
                "serviceDate" to cleanDate,
                "workshopName" to workshopName.trim().take(80),
                "notes" to notes.trim().take(400),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateServiceRecord(
        userId: String,
        recordId: String,
        title: String,
        category: ServiceCategory,
        odometerKm: Long,
        costAmount: Double,
        serviceDate: String,
        workshopName: String,
        notes: String
    ): Result<Unit> {
        val path = "users/$userId/serviceRecords/$recordId"
        return try {
            val uid = requireSignedInUid(userId)
            require(recordId.isNotBlank()) { "Record ID must not be blank" }
            val cleanTitle = title.trim().take(80)
            require(cleanTitle.isNotEmpty()) { "Service title is required" }
            val cleanDate = serviceDate.trim().take(20)
            require(cleanDate.length >= 4) { "Service date is required" }

            val updates = mapOf(
                "title" to cleanTitle,
                "category" to category.wireValue,
                "odometerKm" to odometerKm.coerceIn(0L, 999999L),
                "costAmount" to costAmount.coerceIn(0.0, 1000000.0),
                "serviceDate" to cleanDate,
                "workshopName" to workshopName.trim().take(80),
                "notes" to notes.trim().take(400),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users")
                .document(uid)
                .collection("serviceRecords")
                .document(recordId)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteServiceRecord(userId: String, recordId: String): Result<Unit> {
        val path = "users/$userId/serviceRecords/$recordId"
        return try {
            val uid = requireSignedInUid(userId)
            require(recordId.isNotBlank()) { "Record ID must not be blank" }
            db.collection("users")
                .document(uid)
                .collection("serviceRecords")
                .document(recordId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun addDiagnosticLog(
        userId: String,
        source: DiagnosticSource,
        batteryVoltage: Double,
        engineTempC: Long,
        rpmPeak: Long,
        odometerKm: Long,
        dtcCodes: List<String>,
        status: DiagnosticStatus,
        notes: String
    ): Result<Unit> {
        var path = "users/$userId/diagnosticLogs"
        return try {
            val uid = requireSignedInUid(userId)
            val docRef = db.collection("users")
                .document(uid)
                .collection("diagnosticLogs")
                .document()
            path = docRef.path

            val sanitizedCodes = dtcCodes
                .map { it.trim().uppercase().take(24) }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(20)

            val payload = mapOf(
                "userId" to uid,
                "source" to source.wireValue,
                "batteryVoltage" to batteryVoltage.coerceIn(0.0, 24.0),
                "engineTempC" to engineTempC.coerceIn(-40L, 250L),
                "rpmPeak" to rpmPeak.coerceIn(0L, 15000L),
                "odometerKm" to odometerKm.coerceIn(0L, 999999L),
                "dtcCodes" to sanitizedCodes,
                "status" to status.wireValue,
                "notes" to notes.trim().take(300),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteDiagnosticLog(userId: String, logId: String): Result<Unit> {
        val path = "users/$userId/diagnosticLogs/$logId"
        return try {
            val uid = requireSignedInUid(userId)
            require(logId.isNotBlank()) { "Log ID must not be blank" }
            db.collection("users")
                .document(uid)
                .collection("diagnosticLogs")
                .document(logId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
