package com.example.hardware

import kotlinx.coroutines.flow.StateFlow

/**
 * Logical commands mapped from Honda CB350RS handlebar switchgear buttons,
 * Bluetooth AVRCP remotes, or the on-screen rider media deck.
 */
enum class HandlebarMediaCommand(
    val displayLabel: String,
    val bleRelayCode: String
) {
    PLAY("Play", "PLAY"),
    PAUSE("Pause", "PAUSE"),
    TOGGLE_PLAY_PAUSE("Play / Pause", "PLAY_PAUSE"),
    SKIP_NEXT("Next Track", "NEXT"),
    SKIP_PREVIOUS("Previous Track", "PREV"),
    VOLUME_UP("Volume +", "VOL_UP"),
    VOLUME_DOWN("Volume -", "VOL_DOWN")
}

/**
 * Contract implemented by the ViewModel to handle play, pause, track skip,
 * volume, and hardware KeyEvent commands from motorcycle handlebar buttons.
 */
interface MediaController {
    val mediaState: StateFlow<MediaControlState>

    fun play()
    fun pause()
    fun togglePlayPause()
    fun skipToNext()
    fun skipToPrevious()
    fun volumeUp()
    fun volumeDown()
    fun setVolume(level: Int)
    fun dispatchHandlebarCommand(command: HandlebarMediaCommand)
    fun handleHandlebarKeyCode(keyCode: Int): Boolean
}
