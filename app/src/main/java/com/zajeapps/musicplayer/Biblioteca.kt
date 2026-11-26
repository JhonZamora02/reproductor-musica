package com.zajeapps.musicplayer

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController

// @Immutable: le dice a Compose que una canción no cambia, así reutiliza las filas
@Immutable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val uri: Uri,
    // "Artista · 3:45", calculado una sola vez al cargar la biblioteca
    val detalle: String = "",
    // Segundos desde 1970 en que se añadió al teléfono
    val fecha: Long = 0L,
    // Claves para ordenar: sin tildes ni mayúsculas, y con números "rellenados"
    val claveTitulo: String = "",
    val claveArtista: String = ""
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

// 3:45 o, si pasa de una hora, 1:19:57
fun formatearDuracion(ms: Long): String {
    val total = ms / 1000
    val horas = total / 3600
    val minutos = (total % 3600) / 60
    val segundos = total % 60
    return if (horas > 0) "%d:%02d:%02d".format(horas, minutos, segundos)
    else "%d:%02d".format(minutos, segundos)
}

private val regexNumeros = Regex("\\d+")

// "Pista 10" -> "pista 000000000010": así "Pista 2" queda antes que "Pista 10"
fun claveOrden(texto: String): String =
    normalizar(texto.trim()).replace(regexNumeros) { it.value.padStart(12, '0') }

fun cargarCanciones(context: Context, minimoMs: Long = 30_000L): List<Song> {
    val lista = mutableListOf<Song>()
    val coleccion = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    val proyeccion = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATE_ADDED
    )
    val filtro = "${MediaStore.Audio.Media.DURATION} >= $minimoMs"
    val orden = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

    context.contentResolver.query(coleccion, proyeccion, filtro, null, orden)?.use { cursor ->
        val colId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val colTitulo = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val colArtista = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val colDuracion = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val colFecha = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(colId)
            val titulo = cursor.getString(colTitulo) ?: "Sin título"
            val artistaCrudo = cursor.getString(colArtista)
            val artista =
                if (artistaCrudo == null || artistaCrudo == "<unknown>") "Artista desconocido"
                else artistaCrudo
            val duracion = cursor.getLong(colDuracion)

            lista.add(
                Song(
                    id = id,
                    title = titulo,
                    artist = artista,
                    durationMs = duracion,
                    uri = ContentUris.withAppendedId(coleccion, id),
                    detalle = "$artista · ${formatearDuracion(duracion)}",
                    fecha = cursor.getLong(colFecha),
                    claveTitulo = claveOrden(titulo),
                    claveArtista = claveOrden(artista)
                )
            )
        }
    }
    return lista
}