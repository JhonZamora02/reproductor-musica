package com.zajesoft.musicplayer

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.delay

@Composable
fun BarraReproduccion(
    controller: MediaController?,
    cancion: Song,
    sonando: Boolean,
    aleatorio: Boolean,
    repetir: Int,
    onLetra: () -> Unit
) {
    var expandida by rememberSaveable { mutableStateOf(false) }
    var posicion by remember { mutableStateOf(0L) }
    var duracion by remember { mutableStateOf(cancion.durationMs) }
    var arrastrando by remember { mutableStateOf(false) }
    var valorArrastre by remember { mutableStateOf(0f) }

    val lifecycle = (LocalContext.current as ComponentActivity).lifecycle

    // Solo trabaja si está desplegada, suena y la pantalla es visible
    LaunchedEffect(controller, cancion.id, sonando, expandida) {
        val c = controller ?: return@LaunchedEffect
        if (!expandida) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                posicion = c.currentPosition
                duracion = if (c.duration > 0) c.duration else cancion.durationMs
                if (!sonando) break
                delay(500)
            }
        }
    }

    val maximo = duracion.coerceAtLeast(1L).toFloat()
    val activo = MaterialTheme.colorScheme.primary
    val apagado = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    val alternarPlay: () -> Unit = {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ----- Parte compacta: tocar en cualquier punto despliega o contrae -----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandida = !expandida }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Caratula(cancion, 48.dp)
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                ) {
                    Text(cancion.title, maxLines = 1)
                    Text(
                        cancion.artist,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
                if (!expandida) {
                    IconButton(onClick = alternarPlay) {
                        Icon(
                            imageVector = if (sonando) Iconos.Pausa else Iconos.Play,
                            contentDescription = if (sonando) "Pausar" else "Reproducir"
                        )
                    }
                }
                IconButton(onClick = { expandida = !expandida }) {
                    Icon(
                        imageVector = if (expandida) Iconos.Bajar else Iconos.Subir,
                        contentDescription = if (expandida) "Contraer" else "Expandir"
                    )
                }
            }

            // ----- Parte desplegada -----
            if (expandida) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
                    Slider(
                        value = if (arrastrando) valorArrastre else posicion.toFloat().coerceIn(0f, maximo),
                        onValueChange = {
                            arrastrando = true
                            valorArrastre = it
                        },
                        onValueChangeFinished = {
                            controller?.seekTo(valorArrastre.toLong())
                            posicion = valorArrastre.toLong()
                            arrastrando = false
                        },
                        valueRange = 0f..maximo
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            formatearDuracion(if (arrastrando) valorArrastre.toLong() else posicion),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(formatearDuracion(duracion), style = MaterialTheme.typography.bodySmall)
                    }

                    // Aleatorio, anterior, play/pausa, siguiente, repetir
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
                        }) {
                            Icon(
                                imageVector = Iconos.Aleatorio,
                                contentDescription = if (aleatorio) "Aleatorio activado" else "Aleatorio desactivado",
                                tint = if (aleatorio) activo else apagado
                            )
                        }
                        IconButton(onClick = { controller?.seekToPrevious() }) {
                            Icon(Iconos.Anterior, contentDescription = "Anterior")
                        }
                        FilledIconButton(
                            onClick = alternarPlay,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = if (sonando) Iconos.Pausa else Iconos.Play,
                                contentDescription = if (sonando) "Pausar" else "Reproducir",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        IconButton(onClick = { controller?.seekToNext() }) {
                            Icon(Iconos.Siguiente, contentDescription = "Siguiente")
                        }
                        IconButton(onClick = {
                            controller?.let {
                                it.repeatMode = when (it.repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                }
                            }
                        }) {
                            Icon(
                                imageVector = if (repetir == Player.REPEAT_MODE_ONE) Iconos.RepetirUna else Iconos.Repetir,
                                contentDescription = when (repetir) {
                                    Player.REPEAT_MODE_ALL -> "Repetir todo"
                                    Player.REPEAT_MODE_ONE -> "Repetir una"
                                    else -> "Repetir desactivado"
                                },
                                tint = if (repetir == Player.REPEAT_MODE_OFF) apagado else activo
                            )
                        }
                    }

                    // Letra / Temporizador
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(onClick = onLetra) { Text("Letra") }
                        BotonTemporizador()
                    }
                }
            }
        }
    }
}