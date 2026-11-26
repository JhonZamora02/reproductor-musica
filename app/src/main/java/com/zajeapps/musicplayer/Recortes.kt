@file:androidx.annotation.OptIn(UnstableApi::class)

package com.zajeapps.musicplayer

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.coroutines.resume

enum class FormatoRecorte(val extension: String, val mime: String) {
    MP3("mp3", "audio/mpeg"),
    M4A("m4a", "audio/mp4")
}

// ---------- Lector con ventana, para recorrer un MP3 grande sin cargarlo entero ----------

private class LectorArchivo(private val canal: FileChannel) {
    private val buffer = ByteBuffer.allocate(256 * 1024)
    private var inicio = -1L
    private var largo = 0
    val tamano: Long = canal.size()

    // Devuelve el byte en esa posición del archivo, o -1 si no existe
    fun byteEn(pos: Long): Int {
        if (pos < 0 || pos >= tamano) return -1
        if (inicio < 0 || pos < inicio || pos >= inicio + largo) {
            buffer.clear()
            canal.position(pos)
            val n = canal.read(buffer)
            if (n <= 0) return -1
            inicio = pos
            largo = n
        }
        return buffer.get((pos - inicio).toInt()).toInt() and 0xFF
    }
}

private class CabeceraMp3(val longitud: Int, val duracionMs: Double)

// ---------- Corte de MP3 sin recomprimir: se copian bloques ("frames") completos ----------

object CortadorMp3 {
    private val BITRATE_V1 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
    private val BITRATE_V2 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0)
    private val TASA_V1 = intArrayOf(44100, 48000, 32000)
    private val TASA_V2 = intArrayOf(22050, 24000, 16000)
    private val TASA_V25 = intArrayOf(11025, 12000, 8000)

    private fun cabeceraEn(l: LectorArchivo, pos: Long): CabeceraMp3? {
        val b0 = l.byteEn(pos)
        val b1 = l.byteEn(pos + 1)
        val b2 = l.byteEn(pos + 2)
        if (b0 != 0xFF || b1 < 0 || b2 < 0) return null
        if ((b1 and 0xE0) != 0xE0) return null

        val version = (b1 shr 3) and 3 // 0 = MPEG 2.5, 2 = MPEG 2, 3 = MPEG 1
        val capa = (b1 shr 1) and 3    // 1 = Layer III (el de los MP3)
        if (version == 1 || capa != 1) return null

        val idxBitrate = (b2 shr 4) and 0xF
        val idxTasa = (b2 shr 2) and 3
        if (idxBitrate == 0 || idxBitrate == 15 || idxTasa == 3) return null

        val padding = (b2 shr 1) and 1
        val mpeg1 = version == 3
        val bitrate = (if (mpeg1) BITRATE_V1 else BITRATE_V2)[idxBitrate] * 1000
        val tasa = when (version) {
            3 -> TASA_V1[idxTasa]
            2 -> TASA_V2[idxTasa]
            else -> TASA_V25[idxTasa]
        }
        val muestras = if (mpeg1) 1152 else 576
        val longitud = (if (mpeg1) 144 else 72) * bitrate / tasa + padding
        if (longitud < 24) return null
        return CabeceraMp3(longitud, muestras * 1000.0 / tasa)
    }

    // Salta la etiqueta ID3v2 del principio (título, carátula...)
    private fun saltarId3(l: LectorArchivo): Long {
        if (l.byteEn(0) == 'I'.code && l.byteEn(1) == 'D'.code && l.byteEn(2) == '3'.code) {
            val tam = ((l.byteEn(6) and 0x7F) shl 21) or
                    ((l.byteEn(7) and 0x7F) shl 14) or
                    ((l.byteEn(8) and 0x7F) shl 7) or
                    (l.byteEn(9) and 0x7F)
            val pie = if ((l.byteEn(5) and 0x10) != 0) 10 else 0
            return 10L + tam + pie
        }
        return 0L
    }

    // El primer bloque de muchos MP3 solo guarda datos de duración ("Xing", "Info", "VBRI").
    // No hay que copiarlo: describiría el archivo original y falsearía la duración del recorte.
    private fun esBloqueDeInfo(l: LectorArchivo, pos: Long, longitud: Int): Boolean {
        for (desplazamiento in intArrayOf(13, 21, 36)) {
            if (desplazamiento + 4 > longitud) continue
            val etiqueta = String(CharArray(4) { i -> l.byteEn(pos + desplazamiento + i).toChar() })
            if (etiqueta == "Xing" || etiqueta == "Info" || etiqueta == "VBRI") return true
        }
        return false
    }

    // Devuelve (byte inicial, byte final) del tramo de audio que cubre [inicioMs, finMs)
    fun localizar(canal: FileChannel, inicioMs: Long, finMs: Long): Pair<Long, Long>? {
        val l = LectorArchivo(canal)
        var pos = saltarId3(l)
        var tiempo = 0.0
        var desde = -1L
        var hasta = -1L
        var finUltimo = -1L

        while (pos < l.tamano) {
            val c = cabeceraEn(l, pos)
            if (c == null) {
                pos++ // basura o etiqueta final: se resincroniza byte a byte
                continue
            }
            if (pos + c.longitud > l.tamano) break
            if (esBloqueDeInfo(l, pos, c.longitud)) {
                pos += c.longitud
                continue
            }
            if (desde < 0 && tiempo + c.duracionMs > inicioMs) desde = pos
            if (desde >= 0 && tiempo >= finMs) {
                hasta = pos
                break
            }
            tiempo += c.duracionMs
            pos += c.longitud
            finUltimo = pos
        }

        if (desde < 0) return null
        if (hasta < 0) hasta = finUltimo
        return if (hasta > desde) Pair(desde, hasta) else null
    }

    fun copiar(canal: FileChannel, desde: Long, hasta: Long, salida: OutputStream) {
        canal.position(desde)
        val buf = ByteBuffer.allocate(64 * 1024)
        var restante = hasta - desde
        while (restante > 0) {
            buf.clear()
            if (restante < buf.capacity()) buf.limit(restante.toInt())
            val n = canal.read(buf)
            if (n <= 0) break
            salida.write(buf.array(), 0, n)
            restante -= n
        }
    }
}

// ---------- Guardado en Música/Recortes ----------

object Recortes {
    const val MIN_MS = 1000L

    fun limpiarNombre(nombre: String): String =
        nombre.replace(Regex("[\\\\/:*?\"<>|]"), "").trim().take(100)

    fun esMp3(context: Context, uri: Uri): Boolean {
        val tipo = context.contentResolver.getType(uri)
        return tipo == "audio/mpeg" || tipo == "audio/mp3"
    }

    private fun crearEntrada(context: Context, nombreArchivo: String, mime: String): Uri? {
        val valores = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, nombreArchivo)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Recortes")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }
        return context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, valores)
    }

    private fun finalizarEntrada(context: Context, uri: Uri) {
        val valores = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
        context.contentResolver.update(uri, valores, null, null)
    }

    private fun borrarEntrada(context: Context, uri: Uri) {
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            // si no se puede borrar, no pasa nada
        }
    }

    // Devuelve null si todo fue bien, o un mensaje de error para mostrar al usuario
    suspend fun guardar(
        context: Context,
        cancion: Song,
        inicioMs: Long,
        finMs: Long,
        formato: FormatoRecorte,
        nombreBase: String
    ): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return "Cortar fragmentos requiere Android 10 o superior."
        }
        val nombre = limpiarNombre(nombreBase)
        if (nombre.isEmpty()) return "Escribe un nombre para el archivo."
        if (finMs - inicioMs < MIN_MS) return "El fragmento debe durar al menos 1 segundo."

        return try {
            when (formato) {
                FormatoRecorte.MP3 -> guardarMp3(context, cancion, inicioMs, finMs, nombre)
                FormatoRecorte.M4A -> guardarM4a(context, cancion, inicioMs, finMs, nombre)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "No se pudo guardar: ${e.message ?: "error desconocido"}"
        }
    }

    private suspend fun guardarMp3(
        context: Context, cancion: Song, inicioMs: Long, finMs: Long, nombre: String
    ): String? = withContext(Dispatchers.IO) {
        val pfd = context.contentResolver.openFileDescriptor(cancion.uri, "r")
            ?: return@withContext "No se pudo abrir la canción."

        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { entrada ->
            val canal = entrada.channel
            val rango = CortadorMp3.localizar(canal, inicioMs, finMs)
                ?: return@withContext "No se pudo leer el formato MP3 de esta canción. Prueba con M4A."
            val destino = crearEntrada(context, "$nombre.mp3", "audio/mpeg")
                ?: return@withContext "No se pudo crear el archivo."
            try {
                val salida = context.contentResolver.openOutputStream(destino)
                    ?: throw IOException("No se pudo escribir el archivo")
                salida.use { CortadorMp3.copiar(canal, rango.first, rango.second, it) }
                finalizarEntrada(context, destino)
                null
            } catch (e: Exception) {
                borrarEntrada(context, destino)
                throw e
            }
        }
    }

    private suspend fun guardarM4a(
        context: Context, cancion: Song, inicioMs: Long, finMs: Long, nombre: String
    ): String? {
        val temporal = File(context.cacheDir, "recorte_${System.currentTimeMillis()}.m4a")
        try {
            val fallo = exportarM4a(context, cancion.uri, inicioMs, finMs, temporal)
            if (fallo != null) return "No se pudo convertir el audio: ${fallo.message ?: "error"}"

            return withContext(Dispatchers.IO) {
                val destino = crearEntrada(context, "$nombre.m4a", "audio/mp4")
                    ?: return@withContext "No se pudo crear el archivo."
                try {
                    val salida = context.contentResolver.openOutputStream(destino)
                        ?: throw IOException("No se pudo escribir el archivo")
                    salida.use { out -> temporal.inputStream().use { it.copyTo(out) } }
                    finalizarEntrada(context, destino)
                    null
                } catch (e: Exception) {
                    borrarEntrada(context, destino)
                    throw e
                }
            }
        } finally {
            temporal.delete()
        }
    }

    // Debe llamarse desde el hilo principal (Transformer lo exige)
    private suspend fun exportarM4a(
        context: Context, origen: Uri, inicioMs: Long, finMs: Long, destino: File
    ): ExportException? = suspendCancellableCoroutine<ExportException?> { cont ->
        val recorte = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(inicioMs)
            .setEndPositionMs(finMs)
            .build()
        val elemento = MediaItem.Builder()
            .setUri(origen)
            .setClippingConfiguration(recorte)
            .build()
        val editado = EditedMediaItem.Builder(elemento)
            .setRemoveVideo(true)
            .build()

        val transformer = Transformer.Builder(context.applicationContext)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (cont.isActive) cont.resume(null)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    if (cont.isActive) cont.resume(exportException)
                }
            })
            .build()

        cont.invokeOnCancellation { transformer.cancel() }
        transformer.start(editado, destino.absolutePath)
    }
}