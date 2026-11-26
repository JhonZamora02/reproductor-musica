package com.zajesoft.musicplayer

import androidx.media3.common.Player
import androidx.media3.session.MediaController

// "Siguiente" que da la vuelta: en la última canción pasa a la primera.
// No depende de que el reproductor ofrezca el comando "siguiente".
fun MediaController.siguienteCiclico() {
    val lineaTiempo = currentTimeline
    if (lineaTiempo.isEmpty) return
    if (hasNextMediaItem()) {
        seekToNextMediaItem()
    } else {
        val habiaTerminado = playbackState == Player.STATE_ENDED
        // Primera canción del orden actual (respeta el modo aleatorio)
        seekToDefaultPosition(lineaTiempo.getFirstWindowIndex(shuffleModeEnabled))
        if (habiaTerminado) play()
    }
}