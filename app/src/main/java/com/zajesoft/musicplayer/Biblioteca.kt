package com.zajesoft.musicplayer

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val uri: Uri
)

val permisoAudio =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_AUDIO
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

fun Song.aMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .build()
        )
        .build()

// Carga una cola completa y empieza a sonar desde "indice"
fun reproducirCola(controller: MediaController?, cola: List<Song>, indice: Int) {
    if (controller == null || cola.isEmpty()) return
    controller.setMediaItems(cola.map { it.aMediaItem() }, indice, 0L)
    controller.prepare()
    controller.play()
}

fun formatearDuracion(ms: Long): String {
    val totalSegundos = ms / 1000
    return "%d:%02d".format(totalSegundos / 60, totalSegundos % 60)
}

fun cargarCanciones(context: Context, minimoMs: Long = 30_000L): List<Song> {
    val lista = mutableListOf<Song>()
    val coleccion = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    val proyeccion = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION
    )
    val filtro = "${MediaStore.Audio.Media.DURATION} >= $minimoMs"
    val orden = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

    context.contentResolver.query(coleccion, proyeccion, filtro, null, orden)?.use { cursor ->
        val colId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val colTitulo = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val colArtista = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val colDuracion = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(colId)
            val artista = cursor.getString(colArtista)
            lista.add(
                Song(
                    id = id,
                    title = cursor.getString(colTitulo) ?: "Sin título",
                    artist = if (artista == null || artista == "<unknown>") "Artista desconocido" else artista,
                    durationMs = cursor.getLong(colDuracion),
                    uri = ContentUris.withAppendedId(coleccion, id)
                )
            )
        }
    }
    return lista
}