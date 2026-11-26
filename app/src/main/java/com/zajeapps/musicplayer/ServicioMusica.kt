@file:androidx.annotation.OptIn(UnstableApi::class)

package com.zajeapps.musicplayer

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

// Envuelve al reproductor para que la cola sea circular:
// "Siguiente" en la última canción vuelve a la primera,
// "Anterior" en la primera canción salta a la última.
private class ReproductorCiclico(private val base: Player) : ForwardingPlayer(base) {

    private val correctores = HashMap<Player.Listener, Player.Listener>()

    private fun esSiguiente(comando: Int) =
        comando == Player.COMMAND_SEEK_TO_NEXT ||
                comando == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM

    private fun esAnterior(comando: Int) =
        comando == Player.COMMAND_SEEK_TO_PREVIOUS ||
                comando == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM

    // Cuando el reproductor avisa de un cambio de comandos, el aviso original no incluye
    // mi ajuste. Después de él, reenvío la lista completa para que prevalezca.
    override fun addListener(listener: Player.Listener) {
        super.addListener(listener)
        val corrector = object : Player.Listener {
            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                listener.onAvailableCommandsChanged(this@ReproductorCiclico.getAvailableCommands())
            }
        }
        correctores[listener] = corrector
        base.addListener(corrector)
    }

    override fun removeListener(listener: Player.Listener) {
        super.removeListener(listener)
        correctores.remove(listener)?.let { base.removeListener(it) }
    }

    // "Siguiente" y "Anterior" deben aparecer disponibles siempre que haya canciones
    override fun getAvailableCommands(): Player.Commands {
        val comandos = super.getAvailableCommands()
        if (currentTimeline.isEmpty) return comandos
        return comandos.buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        if (super.isCommandAvailable(command)) return true
        return (esSiguiente(command) || esAnterior(command)) && !currentTimeline.isEmpty
    }

    // ----- Siguiente -----

    override fun seekToNext() {
        if (hasNextMediaItem()) super.seekToNext() else irAlPrimero()
    }

    override fun seekToNextMediaItem() {
        if (hasNextMediaItem()) super.seekToNextMediaItem() else irAlPrimero()
    }

    // ----- Anterior -----

    override fun seekToPrevious() {
        // Con más de 3 s reproducidos, "Anterior" reinicia la canción (comportamiento estándar)
        val pasoElInicio = currentPosition > maxSeekToPreviousPosition
        if (hasPreviousMediaItem() || pasoElInicio) super.seekToPrevious() else irAlUltimo()
    }

    override fun seekToPreviousMediaItem() {
        if (hasPreviousMediaItem()) super.seekToPreviousMediaItem() else irAlUltimo()
    }

    // ----- Saltos de un extremo a otro -----

    private fun irAlPrimero() {
        val lineaTiempo = currentTimeline
        if (lineaTiempo.isEmpty) return
        val habiaTerminado = playbackState == Player.STATE_ENDED
        seekToDefaultPosition(lineaTiempo.getFirstWindowIndex(shuffleModeEnabled))
        if (habiaTerminado) play()
    }

    private fun irAlUltimo() {
        val lineaTiempo = currentTimeline
        if (lineaTiempo.isEmpty) return
        val habiaTerminado = playbackState == Player.STATE_ENDED
        seekToDefaultPosition(lineaTiempo.getLastWindowIndex(shuffleModeEnabled))
        if (habiaTerminado) play()
    }
}

class ServicioMusica : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        // Lo que ocurre al tocar la notificación: abrir la app
        val intentApp = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val abrirApp = PendingIntent.getActivity(
            this,
            0,
            intentApp,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, ReproductorCiclico(exoPlayer))
            .setSessionActivity(abrirApp)
            .build()

        // El temporizador y el ecualizador necesitan el ExoPlayer real
        Temporizador.vincular(exoPlayer, scope)
        Ecualizador.vincular(this, exoPlayer)
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    // Si el usuario cierra la app deslizándola de recientes, se detiene la música
    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        Ecualizador.desvincular()
        Temporizador.desvincular()
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}