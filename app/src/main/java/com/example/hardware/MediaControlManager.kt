package com.example.hardware

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController as SystemMediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.example.service.RelayStateBus
import com.example.service.RelayedMediaInfo
import com.example.service.RiderNotificationRelayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MediaControlState(
    val musicPlaying: Boolean = false,
    val volumeCurrent: Int = 0,
    val volumeMax: Int = 15,
    val lastCommand: String = "",
    val lastCommandAtMs: Long = 0L,
    val noActivePlayerHint: Boolean = false,
    val activeSessionApp: String = "",
    val usingMediaSessionApi: Boolean = false
) {
    val volumePercent: Int
        get() = if (volumeMax > 0) ((volumeCurrent * 100f) / volumeMax).toInt().coerceIn(0, 100) else 0
}

/**
 * Hardware media transport engine supporting Android MediaSessionManager
 * (direct System MediaController TransportControls when Notification Access is enabled)
 * with automatic fallback to AudioManager hardware media key dispatch.
 */
class MediaControlManager(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mediaSessionManager =
        appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    private val listenerComponent = ComponentName(appContext, RiderNotificationRelayService::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(readCurrentState())
    val state: StateFlow<MediaControlState> = _state.asStateFlow()

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshState()
        }
    }

    private var receiverRegistered = false

    init {
        registerReceiver()
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(volumeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                appContext.registerReceiver(volumeReceiver, filter)
            }
            receiverRegistered = true
        } catch (_: Exception) {
        }
    }

    fun refreshState() {
        val current = readCurrentState()
        _state.update { prev ->
            current.copy(
                lastCommand = prev.lastCommand,
                lastCommandAtMs = prev.lastCommandAtMs,
                noActivePlayerHint = if (current.musicPlaying || current.usingMediaSessionApi) false else prev.noActivePlayerHint
            )
        }
    }

    fun play(onResult: (Boolean) -> Unit = {}) {
        val activeSession = resolveActiveSystemController()
        if (activeSession != null) {
            activeSession.transportControls.play()
            syncMetadataFromSession(activeSession)
            updateCommandState("Play (${resolveAppLabel(activeSession.packageName)})", sessionActive = true)
            confirmPlaybackAsync(expectedPlaying = true, commandLabel = "Playing audio", onResult = onResult)
            return
        }

        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        updateCommandState("Sent Play key", sessionActive = false)
        confirmPlaybackAsync(expectedPlaying = true, commandLabel = "Playing audio", onResult = onResult)
    }

    fun pause(onResult: (Boolean) -> Unit = {}) {
        val activeSession = resolveActiveSystemController()
        if (activeSession != null) {
            activeSession.transportControls.pause()
            syncMetadataFromSession(activeSession)
            updateCommandState("Pause (${resolveAppLabel(activeSession.packageName)})", sessionActive = true)
            confirmPlaybackAsync(expectedPlaying = false, commandLabel = "Paused audio", onResult = onResult)
            return
        }

        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        updateCommandState("Sent Pause key", sessionActive = false)
        confirmPlaybackAsync(expectedPlaying = false, commandLabel = "Paused audio", onResult = onResult)
    }

    fun togglePlayPause(onResult: (Boolean) -> Unit = {}) {
        val activeSession = resolveActiveSystemController()
        if (activeSession != null) {
            val isPlaying = isControllerPlaying(activeSession)
            if (isPlaying) {
                pause(onResult)
            } else {
                play(onResult)
            }
            return
        }

        val wasPlaying = audioManager.isMusicActive
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        updateCommandState(if (wasPlaying) "Sent Pause key" else "Sent Play key", sessionActive = false)
        scope.launch {
            delay(280L)
            val updated = readCurrentState()
            val playerResponded = updated.musicPlaying != wasPlaying || updated.musicPlaying
            _state.update {
                updated.copy(
                    lastCommand = if (updated.musicPlaying) "Playing audio" else if (wasPlaying) "Paused audio" else "Sent Play/Pause key",
                    lastCommandAtMs = System.currentTimeMillis(),
                    noActivePlayerHint = !playerResponded
                )
            }
            onResult(playerResponded)
        }
    }

    fun skipToNext(onResult: (Boolean) -> Unit = {}) {
        val activeSession = resolveActiveSystemController()
        if (activeSession != null) {
            activeSession.transportControls.skipToNext()
            updateCommandState("Next Track (${resolveAppLabel(activeSession.packageName)})", sessionActive = true)
            scope.launch {
                delay(280L)
                resolveActiveSystemController()?.let { syncMetadataFromSession(it) }
                val updated = readCurrentState()
                _state.update {
                    updated.copy(
                        lastCommand = "Skipped to Next Track",
                        lastCommandAtMs = System.currentTimeMillis(),
                        noActivePlayerHint = false
                    )
                }
                onResult(true)
            }
            return
        }

        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        updateCommandState("Sent Next Track key", sessionActive = false)
        scope.launch {
            delay(250L)
            val updated = readCurrentState()
            _state.update {
                updated.copy(
                    lastCommand = "Next Track",
                    lastCommandAtMs = System.currentTimeMillis(),
                    noActivePlayerHint = !updated.musicPlaying
                )
            }
            onResult(updated.musicPlaying)
        }
    }

    fun skipToPrevious(onResult: (Boolean) -> Unit = {}) {
        val activeSession = resolveActiveSystemController()
        if (activeSession != null) {
            activeSession.transportControls.skipToPrevious()
            updateCommandState("Previous Track (${resolveAppLabel(activeSession.packageName)})", sessionActive = true)
            scope.launch {
                delay(280L)
                resolveActiveSystemController()?.let { syncMetadataFromSession(it) }
                val updated = readCurrentState()
                _state.update {
                    updated.copy(
                        lastCommand = "Returned to Previous Track",
                        lastCommandAtMs = System.currentTimeMillis(),
                        noActivePlayerHint = false
                    )
                }
                onResult(true)
            }
            return
        }

        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        updateCommandState("Sent Previous Track key", sessionActive = false)
        scope.launch {
            delay(250L)
            val updated = readCurrentState()
            _state.update {
                updated.copy(
                    lastCommand = "Previous Track",
                    lastCommandAtMs = System.currentTimeMillis(),
                    noActivePlayerHint = !updated.musicPlaying
                )
            }
            onResult(updated.musicPlaying)
        }
    }

    fun volumeUp() {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_RAISE,
            AudioManager.FLAG_SHOW_UI
        )
        _state.update {
            readCurrentState().copy(
                lastCommand = "Volume +",
                lastCommandAtMs = System.currentTimeMillis()
            )
        }
    }

    fun volumeDown() {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
        _state.update {
            readCurrentState().copy(
                lastCommand = "Volume -",
                lastCommandAtMs = System.currentTimeMillis()
            )
        }
    }

    fun setVolume(index: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            index.coerceIn(0, max),
            0
        )
        _state.update {
            readCurrentState().copy(
                lastCommand = "Volume set",
                lastCommandAtMs = System.currentTimeMillis()
            )
        }
    }

    fun openMusicApp(context: Context): Boolean {
        val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun confirmPlaybackAsync(
        expectedPlaying: Boolean,
        commandLabel: String,
        onResult: (Boolean) -> Unit
    ) {
        scope.launch {
            delay(280L)
            val updated = readCurrentState()
            val responded = updated.usingMediaSessionApi || updated.musicPlaying == expectedPlaying
            _state.update {
                updated.copy(
                    lastCommand = if (responded) commandLabel else it.lastCommand,
                    lastCommandAtMs = System.currentTimeMillis(),
                    noActivePlayerHint = !responded && expectedPlaying
                )
            }
            onResult(responded)
        }
    }

    private fun updateCommandState(label: String, sessionActive: Boolean) {
        _state.update {
            readCurrentState().copy(
                lastCommand = label,
                lastCommandAtMs = System.currentTimeMillis(),
                noActivePlayerHint = if (sessionActive) false else it.noActivePlayerHint
            )
        }
    }

    private fun resolveActiveSystemController(): SystemMediaController? {
        if (!RelayStateBus.isNotificationAccessEnabled(appContext)) return null
        return try {
            val controllers = mediaSessionManager?.getActiveSessions(listenerComponent).orEmpty()
            controllers.firstOrNull { isControllerPlaying(it) } ?: controllers.firstOrNull()
        } catch (e: SecurityException) {
            Log.d(TAG, "Notification access not active for MediaSessionManager", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "Unable to query active media sessions", e)
            null
        }
    }

    private fun isControllerPlaying(controller: SystemMediaController): Boolean {
        val state = controller.playbackState?.state ?: return false
        return state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
    }

    private fun syncMetadataFromSession(controller: SystemMediaController) {
        val metadata = controller.metadata ?: return
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim().orEmpty()
        if (title.isNotEmpty()) {
            RelayStateBus.updateMedia(
                RelayedMediaInfo(
                    title = title,
                    artist = artist,
                    sourceApp = resolveAppLabel(controller.packageName),
                    updatedAtMs = System.currentTimeMillis()
                )
            )
        }
    }

    private fun resolveAppLabel(packageName: String): String {
        return try {
            val appInfo = appContext.packageManager.getApplicationInfo(packageName, 0)
            appContext.packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.')
        }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val eventTime = SystemClock.uptimeMillis()
        val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
        val upEvent = KeyEvent(eventTime, eventTime + 25L, KeyEvent.ACTION_UP, keyCode, 0)
        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }

    private fun readCurrentState(): MediaControlState {
        val activeSession = resolveActiveSystemController()
        val playing = activeSession?.let { isControllerPlaying(it) } ?: audioManager.isMusicActive
        return MediaControlState(
            musicPlaying = playing,
            volumeCurrent = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
            volumeMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            activeSessionApp = activeSession?.let { resolveAppLabel(it.packageName) }.orEmpty(),
            usingMediaSessionApi = activeSession != null
        )
    }

    fun release() {
        if (receiverRegistered) {
            try {
                appContext.unregisterReceiver(volumeReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
    }

    private companion object {
        const val TAG = "MotoLinkMediaCtrl"
    }
}
