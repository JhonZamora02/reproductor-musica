package com.zajesoft.musicplayer

import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// finMs: momento (reloj del sistema) en que se apagará. 0 = sin temporizador por tiempo
data class EstadoTemporizador(
    val finMs: Long = 0L,
    val alFinalDeCancion: Boolean = false
) {
    val activo: Boolean get() = finMs > 0L || alFinalDeCancion
}

object Temporizador {
    private const val FADE_MS = 10_000L
    private const val PASO_MS = 200L

    private val _estado = MutableStateFlow(EstadoTemporizador())
    val estado: StateFlow<EstadoTemporizador> = _estado.asStateFlow()

    private var player: ExoPlayer? = null
    private var scope: CoroutineScope? = null
    private var trabajo: Job? = null

    // Detecta cuándo el reproductor se pausó al terminar la canción
    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                _estado.value.alFinalDeCancion
            ) {
                player?.setPauseAtEndOfMediaItems(false)
                _estado.value = EstadoTemporizador()
            }
        }
    }

    // Lo llama el servicio al crearse
    fun vincular(p: ExoPlayer, s: CoroutineScope) {
        desvincular()
        player = p
        scope = s
        p.addListener(listener)
    }

    // Lo llama el servicio al destruirse
    fun desvincular() {
        cancelar()
        player?.removeListener(listener)
        player = null
        scope = null
    }

    fun iniciarEnMinutos(minutos: Int) {
        val p = player ?: return
        val s = scope ?: return
        cancelar()

        val duracionMs = minutos * 60_000L
        _estado.value = EstadoTemporizador(finMs = SystemClock.elapsedRealtime() + duracionMs)

        trabajo = s.launch {
            // Una sola espera larga: no despierta el procesador cada segundo
            delay((duracionMs - FADE_MS).coerceAtLeast(0L))

            // Fade-out: baja el volumen poco a poco
            val pasos = (FADE_MS / PASO_MS).toInt()
            for (i in 1..pasos) {
                p.volume = 1f - i.toFloat() / pasos
                delay(PASO_MS)
            }
            p.pause()
            p.volume = 1f // lo dejamos listo para la próxima vez
            _estado.value = EstadoTemporizador()
            trabajo = null
        }
    }

    // ExoPlayer se pausa solo cuando termina la canción actual
    fun iniciarAlFinalDeCancion() {
        val p = player ?: return
        cancelar()
        p.setPauseAtEndOfMediaItems(true)
        _estado.value = EstadoTemporizador(alFinalDeCancion = true)
    }

    fun cancelar() {
        trabajo?.cancel()
        trabajo = null
        player?.let {
            it.volume = 1f // si se canceló en pleno fade, devuelve el volumen
            it.setPauseAtEndOfMediaItems(false)
        }
        _estado.value = EstadoTemporizador()
    }
}