package com.zajesoft.musicplayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController

@Composable
fun BarraReproduccion(
    cancion: Song,
    sonando: Boolean,
    controller: MediaController?,
    onAbrir: () -> Unit
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        // Tocar en cualquier punto de la barra abre el reproductor
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAbrir() }
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
            IconButton(onClick = {
                controller?.let { if (it.isPlaying) it.pause() else it.play() }
            }) {
                Icon(
                    imageVector = if (sonando) Iconos.Pausa else Iconos.Play,
                    contentDescription = if (sonando) "Pausar" else "Reproducir"
                )
            }
            IconButton(onClick = onAbrir) {
                Icon(Iconos.Subir, contentDescription = "Abrir reproductor")
            }
        }
    }
}