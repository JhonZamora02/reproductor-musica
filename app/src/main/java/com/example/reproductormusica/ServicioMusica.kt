package com.example.reproductormusica

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class `ServicioMusica` : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // gestiona el audio focus (llamadas, otras apps)
            )
            .setHandleAudioBecomingNoisy(true) // pausa al desconectar auriculares
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
    }

    // Quien se conecte (tu pantalla, Bluetooth, notificación) recibe esta sesión
    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    // Si el usuario cierra la app deslizándola de recientes, detenemos la música
    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}