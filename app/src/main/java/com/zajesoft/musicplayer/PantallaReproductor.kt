package com.zajesoft.musicplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val LADO_GRANDE_PX = 720

// Carátula en tamaño grande (solo se pide para la canción que suena)
private suspend fun cargarCaratulaGrande(context: Context, cancion: Song): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(
                    cancion.uri, Size(LADO_GRANDE_PX, LADO_GRANDE_PX), null
                )
            } else {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, cancion.uri)
                    retriever.embeddedPicture?.let { decodificarGrande(it) }
                } finally {
                    retriever.release()
                }
            }
        } catch (e: Exception) {
            null
        }
    }

private fun decodificarGrande(bytes: ByteArray): Bitmap? {
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, limites)
    var muestra = 1
    while (limites.outWidth / (muestra * 2) >= LADO_GRANDE_PX &&
        limites.outHeight / (muestra * 2) >= LADO_GRANDE_PX
    ) {
        muestra *= 2
    }
    val opciones = BitmapFactory.Options().apply { inSampleSize = muestra }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opciones)
}

@Composable
private fun CaratulaGrande(cancion: Song, lado: Dp) {
    val context = LocalContext.current
    var grande by remember(cancion.id) { mutableStateOf<ImageBitmap?>(null) }

    // Mientras llega la versión grande, se muestra la miniatura que ya estaba en caché
    val miniatura = remember(cancion.id) { Caratulas.enCache(cancion.id)?.asImageBitmap() }

    LaunchedEffect(cancion.id) {
        grande = cargarCaratulaGrande(context, cancion)?.asImageBitmap()
    }

    val imagen = grande ?: miniatura
    Box(
        modifier = Modifier
            .size(lado)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (imagen != null) {
            Image(
                bitmap = imagen,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Iconos.Nota,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(lado / 3)
            )
        }
    }
}

@Composable
fun PantallaReproductor(
    controller: MediaController?,
    cancion: Song,
    sonando: Boolean,
    aleatorio: Boolean,
    repetir: Int,
    onCerrar: () -> Unit,
    onLetra: () -> Unit
) {
    var posicion by remember(cancion.id) { mutableStateOf(0L) }
    var duracion by remember(cancion.id) { mutableStateOf(cancion.durationMs) }
    var arrastrando by remember { mutableStateOf(false) }
    var valorArrastre by remember { mutableStateOf(0f) }

    val lifecycle = (LocalContext.current as ComponentActivity).lifecycle

    // El botón "atrás" del teléfono cierra el reproductor
    BackHandler(onBack = onCerrar)

    // Solo trabaja con el reproductor abierto, la música sonando y la pantalla visible
    LaunchedEffect(controller, cancion.id, sonando) {
        val c = controller ?: return@LaunchedEffect
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp)
        ) {
            // ----- Cabecera -----
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                IconButton(
                    onClick = onCerrar,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(Iconos.Bajar, contentDescription = "Cerrar reproductor")
                }
                Text(
                    "Reproduciendo ahora",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // ----- Carátula en el centro -----
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                val lado = minOf(maxWidth, maxHeight)
                CaratulaGrande(cancion, lado)
            }

            // ----- Título y artista -----
            Text(
                cancion.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                cancion.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // ----- Progreso -----
            Spacer(modifier = Modifier.height(8.dp))
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

            // ----- Controles -----
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
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
                    onClick = {
                        controller?.let { if (it.isPlaying) it.pause() else it.play() }
                    },
                    modifier = Modifier.size(68.dp)
                ) {
                    Icon(
                        imageVector = if (sonando) Iconos.Pausa else Iconos.Play,
                        contentDescription = if (sonando) "Pausar" else "Reproducir",
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = { controller?.siguienteCiclico() }) {
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

            // ----- Letra / Temporizador -----
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = onLetra) { Text("Letra") }
                BotonTemporizador()
            }
        }
    }
}