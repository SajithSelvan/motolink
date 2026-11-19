package com.example.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NavManeuverType(val label: String, val bleCode: Byte) {
    STRAIGHT("Continue Straight", 0x01),
    LEFT("Turn Left", 0x02),
    RIGHT("Turn Right", 0x03),
    SLIGHT_LEFT("Keep Left", 0x04),
    SLIGHT_RIGHT("Keep Right", 0x05),
    U_TURN("Make U-Turn", 0x06),
    ROUNDABOUT("Roundabout", 0x07),
    ARRIVE("Destination Ahead", 0x08)
}

data class RelayedNavManeuver(
    val maneuver: NavManeuverType,
    val primaryInstruction: String,
    val secondaryDetails: String,
    val sourcePackage: String,
    val timestampMs: Long
)

data class RelayedMediaInfo(
    val title: String = "",
    val artist: String = "",
    val sourceApp: String = "",
    val updatedAtMs: Long = 0L
)

object RelayStateBus {
    private val _listenerConnected = MutableStateFlow(false)
    val listenerConnected: StateFlow<Boolean> = _listenerConnected.asStateFlow()

    private val _activeNavManeuver = MutableStateFlow<RelayedNavManeuver?>(null)
    val activeNavManeuver: StateFlow<RelayedNavManeuver?> = _activeNavManeuver.asStateFlow()

    private val _relayedMedia = MutableStateFlow(RelayedMediaInfo())
    val relayedMedia: StateFlow<RelayedMediaInfo> = _relayedMedia.asStateFlow()

    private val _lastBluetoothPacket = MutableStateFlow("")
    val lastBluetoothPacket: StateFlow<String> = _lastBluetoothPacket.asStateFlow()

    internal fun setListenerConnected(connected: Boolean) {
        _listenerConnected.value = connected
    }

    internal fun updateNavManeuver(maneuver: RelayedNavManeuver?) {
        _activeNavManeuver.value = maneuver
    }

    internal fun updateMedia(media: RelayedMediaInfo) {
        _relayedMedia.value = media
    }

    fun recordRelayedPacket(summary: String) {
        _lastBluetoothPacket.value = summary
    }

    fun isNotificationAccessEnabled(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val expectedComponent = ComponentName(context, RiderNotificationRelayService::class.java).flattenToString()
        return enabledListeners.contains(expectedComponent) ||
            enabledListeners.contains(context.packageName)
    }
}

class RiderNotificationRelayService : NotificationListenerService() {

    private val navigationPackages = setOf(
        "com.google.android.apps.maps",
        "com.waze",
        "com.mapmyindia.mmi",
        "net.osmand",
        "net.osmand.plus",
        "com.here.app.maps"
    )

    override fun onListenerConnected() {
        super.onListenerConnected()
        RelayStateBus.setListenerConnected(true)
        try {
            activeNotifications?.forEach { processNotification(it) }
        } catch (_: Exception) {
            // Safe fallback if system notification service is still binding
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        RelayStateBus.setListenerConnected(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        processNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName in navigationPackages) {
            RelayStateBus.updateNavManeuver(null)
        }
    }

    private fun processNotification(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()

        if (sbn.packageName in navigationPackages) {
            val combined = "$title $text $subText".lowercase()
            if (title.isNotEmpty() || text.isNotEmpty()) {
                val maneuver = classifyManeuver(combined)
                RelayStateBus.updateNavManeuver(
                    RelayedNavManeuver(
                        maneuver = maneuver,
                        primaryInstruction = title.ifEmpty { text },
                        secondaryDetails = listOf(text, subText)
                            .filter { it.isNotEmpty() && it != title }
                            .joinToString(" • "),
                        sourcePackage = sbn.packageName,
                        timestampMs = System.currentTimeMillis()
                    )
                )
            }
            return
        }

        // Capture real active media player notifications (Spotify, YT Music, etc.)
        val isMediaStyle = extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
            sbn.notification.category == Notification.CATEGORY_TRANSPORT
        if (isMediaStyle && title.isNotEmpty()) {
            val appLabel = try {
                val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                sbn.packageName.substringAfterLast('.')
            }
            RelayStateBus.updateMedia(
                RelayedMediaInfo(
                    title = title,
                    artist = text,
                    sourceApp = appLabel,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
        }
    }

    private fun classifyManeuver(text: String): NavManeuverType = when {
        text.contains("u-turn") || text.contains("u turn") -> NavManeuverType.U_TURN
        text.contains("roundabout") || text.contains("rotary") -> NavManeuverType.ROUNDABOUT
        text.contains("slight left") || text.contains("keep left") -> NavManeuverType.SLIGHT_LEFT
        text.contains("slight right") || text.contains("keep right") -> NavManeuverType.SLIGHT_RIGHT
        text.contains("left") -> NavManeuverType.LEFT
        text.contains("right") -> NavManeuverType.RIGHT
        text.contains("arrive") || text.contains("destination") -> NavManeuverType.ARRIVE
        else -> NavManeuverType.STRAIGHT
    }
}
