package com.example.reproductormusica

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
    repetir: Int
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

    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {

            // Parte compacta
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(cancion.title, maxLines = 1)
                    Text(
                        cancion.artist,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
                Button(onClick = {
                    controller?.let { if (it.isPlaying) it.pause() else it.play() }
                }) { Text(if (sonando) "Pausa" else "Play") }
                TextButton(onClick = { expandida = !expandida }) {
                    Text(if (expandida) "▼" else "▲")
                }
            }

            // Parte desplegada
            if (expandida) {
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatearDuracion(if (arrastrando) valorArrastre.toLong() else posicion),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(formatearDuracion(duracion), style = MaterialTheme.typography.bodySmall)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { controller?.seekToPrevious() }) { Text("Anterior") }
                    TextButton(onClick = { controller?.seekToNext() }) { Text("Siguiente") }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = {
                        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
                    }) { Text(if (aleatorio) "Aleatorio: sí" else "Aleatorio: no") }

                    TextButton(onClick = {
                        controller?.let {
                            it.repeatMode = when (it.repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                        }
                    }) {
                        Text(
                            when (repetir) {
                                Player.REPEAT_MODE_ALL -> "Repetir: todo"
                                Player.REPEAT_MODE_ONE -> "Repetir: una"
                                else -> "Repetir: no"
                            }
                        )
                    }
                    TextButton(onClick = {
                        controller?.let {
                            it.repeatMode = when (it.repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                        }
                    }) {
                        Text(
                            when (repetir) {
                                Player.REPEAT_MODE_ALL -> "Repetir: todo"
                                Player.REPEAT_MODE_ONE -> "Repetir: una"
                                else -> "Repetir: no"
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    BotonTemporizador()
                }
            }
        }
    }
}