package com.example.data.navigation

import android.location.Location
import androidx.core.text.HtmlCompat
import com.example.BuildConfig
import com.example.service.NavManeuverType
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class NavigationStep(
    val index: Int,
    val instruction: String,
    val maneuver: NavManeuverType,
    val stepDistanceMeters: Int,
    val stepDurationSeconds: Int,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double
)

data class ActiveNavigationRoute(
    val destinationLabel: String,
    val resolvedEndAddress: String,
    val summaryRoad: String,
    val totalDistanceMeters: Int,
    val totalDurationSeconds: Int,
    val steps: List<NavigationStep>,
    val currentStepIndex: Int = 0,
    val distanceToNextTurnMeters: Int = 0,
    val remainingDistanceMeters: Int = totalDistanceMeters,
    val remainingDurationSeconds: Int = totalDurationSeconds,
    val gpsDrivenDistance: Boolean = false,
    val sourceLabel: String = "Google Maps API"
) {
    val currentStep: NavigationStep?
        get() = steps.getOrNull(currentStepIndex)

    val upcomingStep: NavigationStep?
        get() = steps.getOrNull(currentStepIndex + 1)

    val currentStepProgressFraction: Float
        get() {
            val stepTotal = currentStep?.stepDistanceMeters?.coerceAtLeast(1) ?: return 0f
            val covered = (stepTotal - distanceToNextTurnMeters).coerceIn(0, stepTotal)
            return covered.toFloat() / stepTotal.toFloat()
        }
}

data class NavigationOverlayUiState(
    val isLoading: Boolean = false,
    val activeRoute: ActiveNavigationRoute? = null,
    val errorMessage: String? = null,
    val apiKeyConfigured: Boolean = GoogleMapsDirectionsClient.hasConfiguredApiKey()
)

// --- Google Maps Directions API DTOs ---

private interface GoogleDirectionsApi {
    @GET("maps/api/directions/json")
    suspend fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("mode") mode: String = "driving",
        @Query("units") units: String = "metric",
        @Query("alternatives") alternatives: Boolean = false,
        @Query("key") apiKey: String
    ): DirectionsResponseDto
}

private data class DirectionsResponseDto(
    @Json(name = "status") val status: String = "",
    @Json(name = "error_message") val errorMessage: String? = null,
    @Json(name = "routes") val routes: List<DirectionsRouteDto> = emptyList()
)

private data class DirectionsRouteDto(
    @Json(name = "summary") val summary: String = "",
    @Json(name = "legs") val legs: List<DirectionsLegDto> = emptyList()
)

private data class DirectionsLegDto(
    @Json(name = "end_address") val endAddress: String = "",
    @Json(name = "distance") val distance: ValueTextDto? = null,
    @Json(name = "duration") val duration: ValueTextDto? = null,
    @Json(name = "steps") val steps: List<DirectionsStepDto> = emptyList()
)

private data class DirectionsStepDto(
    @Json(name = "html_instructions") val htmlInstructions: String = "",
    @Json(name = "maneuver") val maneuver: String? = null,
    @Json(name = "distance") val distance: ValueTextDto? = null,
    @Json(name = "duration") val duration: ValueTextDto? = null,
    @Json(name = "start_location") val startLocation: LatLngDto? = null,
    @Json(name = "end_location") val endLocation: LatLngDto? = null
)

private data class ValueTextDto(
    @Json(name = "value") val value: Int = 0,
    @Json(name = "text") val text: String = ""
)

private data class LatLngDto(
    @Json(name = "lat") val lat: Double = 0.0,
    @Json(name = "lng") val lng: Double = 0.0
)

class GoogleMapsDirectionsClient {

    companion object {
        private const val BASE_URL = "https://maps.googleapis.com/"
        private const val PLACEHOLDER_KEY = "MY_GOOGLE_MAPS_API_KEY"
        private const val STEP_ARRIVAL_THRESHOLD_METERS = 30f

        fun hasConfiguredApiKey(): Boolean {
            val key = BuildConfig.GOOGLE_MAPS_API_KEY.trim()
            return key.isNotEmpty() && key != PLACEHOLDER_KEY
        }

        fun formatDistance(meters: Int): String {
            val safe = meters.coerceAtLeast(0)
            return when {
                safe < 1000 -> "${((safe + 5) / 10 * 10).coerceAtLeast(safe.coerceAtMost(50))} m"
                safe < 10_000 -> String.format("%.1f km", safe / 1000.0)
                else -> "${(safe / 1000.0).roundToInt()} km"
            }
        }

        fun formatEta(seconds: Int): String {
            val totalMins = (seconds / 60.0).roundToInt().coerceAtLeast(1)
            val hours = totalMins / 60
            val mins = totalMins % 60
            return if (hours > 0) "${hours}h ${mins}m" else "$totalMins min"
        }

        /**
         * Updates real-time distance-to-next-turn and advances the active maneuver step
         * based on the rider's current GPS latitude/longitude.
         */
        fun updateRouteWithGps(
            route: ActiveNavigationRoute,
            currentLat: Double,
            currentLng: Double
        ): ActiveNavigationRoute {
            if (route.steps.isEmpty()) return route

            var stepIdx = route.currentStepIndex.coerceIn(0, route.steps.lastIndex)
            val result = FloatArray(1)

            // Advance if the rider has reached the end coordinate of the active turn
            while (stepIdx < route.steps.lastIndex) {
                val activeStep = route.steps[stepIdx]
                Location.distanceBetween(
                    currentLat,
                    currentLng,
                    activeStep.endLat,
                    activeStep.endLng,
                    result
                )
                if (result[0] <= STEP_ARRIVAL_THRESHOLD_METERS) {
                    stepIdx++
                } else {
                    break
                }
            }

            val currentStep = route.steps[stepIdx]
            Location.distanceBetween(
                currentLat,
                currentLng,
                currentStep.endLat,
                currentStep.endLng,
                result
            )
            val liveDistanceToTurn = result[0].roundToInt().coerceAtLeast(0)
            val futureSteps = if (stepIdx < route.steps.lastIndex) {
                route.steps.subList(stepIdx + 1, route.steps.size)
            } else {
                emptyList()
            }
            val remainingDist = liveDistanceToTurn + futureSteps.sumOf { it.stepDistanceMeters }
            val stepRatio = if (currentStep.stepDistanceMeters > 0) {
                (liveDistanceToTurn.toFloat() / currentStep.stepDistanceMeters.toFloat()).coerceIn(0f, 1f)
            } else {
                1f
            }
            val remainingSec = (currentStep.stepDurationSeconds * stepRatio).roundToInt() +
                futureSteps.sumOf { it.stepDurationSeconds }

            return route.copy(
                currentStepIndex = stepIdx,
                distanceToNextTurnMeters = liveDistanceToTurn,
                remainingDistanceMeters = remainingDist,
                remainingDurationSeconds = remainingSec.coerceAtLeast(30),
                gpsDrivenDistance = true
            )
        }

        fun moveToStep(route: ActiveNavigationRoute, targetIndex: Int): ActiveNavigationRoute {
            if (route.steps.isEmpty()) return route
            val clamped = targetIndex.coerceIn(0, route.steps.lastIndex)
            val activeStep = route.steps[clamped]
            val remainingSteps = route.steps.subList(clamped, route.steps.size)
            return route.copy(
                currentStepIndex = clamped,
                distanceToNextTurnMeters = activeStep.stepDistanceMeters,
                remainingDistanceMeters = remainingSteps.sumOf { it.stepDistanceMeters },
                remainingDurationSeconds = remainingSteps.sumOf { it.stepDurationSeconds },
                gpsDrivenDistance = false
            )
        }
    }

    private val api: GoogleDirectionsApi by lazy {
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GoogleDirectionsApi::class.java)
    }

    suspend fun fetchTurnByTurnRoute(
        origin: String,
        destination: String
    ): Result<ActiveNavigationRoute> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GOOGLE_MAPS_API_KEY.trim()
        if (apiKey.isEmpty() || apiKey == PLACEHOLDER_KEY) {
            return@withContext Result.failure(
                IllegalStateException(
                    "GOOGLE_MAPS_API_KEY is not configured. Add it in the AI Studio Secrets panel to fetch live Google Maps Directions."
                )
            )
        }

        try {
            val response = api.getDirections(
                origin = origin.trim(),
                destination = destination.trim(),
                apiKey = apiKey
            )
            if (response.status != "OK") {
                val detail = response.errorMessage?.takeIf { it.isNotBlank() } ?: response.status
                return@withContext Result.failure(
                    IllegalStateException("Google Maps Directions API: $detail")
                )
            }

            val routeDto = response.routes.firstOrNull()
                ?: return@withContext Result.failure(IllegalStateException("No route found for $destination"))
            val legDto = routeDto.legs.firstOrNull()
                ?: return@withContext Result.failure(IllegalStateException("Route returned no navigation legs"))

            val parsedSteps = legDto.steps.mapIndexed { idx, stepDto ->
                val cleanText = stripHtml(stepDto.htmlInstructions)
                    .ifBlank { "Continue on route" }
                val isFinalStep = idx == legDto.steps.lastIndex
                val maneuver = mapGoogleManeuver(stepDto.maneuver, cleanText, isFinalStep)
                NavigationStep(
                    index = idx,
                    instruction = cleanText,
                    maneuver = maneuver,
                    stepDistanceMeters = stepDto.distance?.value ?: 0,
                    stepDurationSeconds = stepDto.duration?.value ?: 0,
                    startLat = stepDto.startLocation?.lat ?: 0.0,
                    startLng = stepDto.startLocation?.lng ?: 0.0,
                    endLat = stepDto.endLocation?.lat ?: 0.0,
                    endLng = stepDto.endLocation?.lng ?: 0.0
                )
            }

            if (parsedSteps.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Google Maps route contains no turn-by-turn steps"))
            }

            val totalDistance = legDto.distance?.value ?: parsedSteps.sumOf { it.stepDistanceMeters }
            val totalDuration = legDto.duration?.value ?: parsedSteps.sumOf { it.stepDurationSeconds }
            val firstStep = parsedSteps.first()

            Result.success(
                ActiveNavigationRoute(
                    destinationLabel = destination.trim(),
                    resolvedEndAddress = legDto.endAddress.ifBlank { destination.trim() },
                    summaryRoad = routeDto.summary,
                    totalDistanceMeters = totalDistance,
                    totalDurationSeconds = totalDuration,
                    steps = parsedSteps,
                    currentStepIndex = 0,
                    distanceToNextTurnMeters = firstStep.stepDistanceMeters,
                    remainingDistanceMeters = totalDistance,
                    remainingDurationSeconds = totalDuration,
                    gpsDrivenDistance = false,
                    sourceLabel = "Google Maps API"
                )
            )
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    "Unable to reach Google Maps Directions API: ${e.localizedMessage ?: "Check network & API key"}"
                )
            )
        }
    }

    private fun stripHtml(html: String): String {
        val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
        return spanned.replace(Regex("\\s+"), " ").trim()
    }

    private fun mapGoogleManeuver(
        rawManeuver: String?,
        instructionText: String,
        isFinal: Boolean
    ): NavManeuverType {
        val normalized = rawManeuver?.lowercase().orEmpty()
        return when {
            normalized.contains("uturn") -> NavManeuverType.U_TURN
            normalized.contains("roundabout") -> NavManeuverType.ROUNDABOUT
            normalized.contains("slight-left") || normalized.contains("keep-left") || normalized.contains("fork-left") ->
                NavManeuverType.SLIGHT_LEFT
            normalized.contains("slight-right") || normalized.contains("keep-right") || normalized.contains("fork-right") ->
                NavManeuverType.SLIGHT_RIGHT
            normalized.contains("left") -> NavManeuverType.LEFT
            normalized.contains("right") -> NavManeuverType.RIGHT
            isFinal && instructionText.lowercase().contains("destination") -> NavManeuverType.ARRIVE
            else -> {
                val lower = instructionText.lowercase()
                when {
                    lower.contains("u-turn") -> NavManeuverType.U_TURN
                    lower.contains("roundabout") -> NavManeuverType.ROUNDABOUT
                    lower.contains("slight left") || lower.contains("keep left") -> NavManeuverType.SLIGHT_LEFT
                    lower.contains("slight right") || lower.contains("keep right") -> NavManeuverType.SLIGHT_RIGHT
                    lower.contains("turn left") -> NavManeuverType.LEFT
                    lower.contains("turn right") -> NavManeuverType.RIGHT
                    isFinal -> NavManeuverType.ARRIVE
                    else -> NavManeuverType.STRAIGHT
                }
            }
        }
    }
}
