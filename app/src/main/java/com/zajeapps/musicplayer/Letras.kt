package com.zajeapps.musicplayer

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

// tiempoMs = -1 en las letras sin sincronizar
data class LineaLetra(val tiempoMs: Long, val texto: String)

data class Letra(val sincronizada: Boolean, val lineas: List<LineaLetra>)

// [mm:ss.xx]  [mm:ss.xxx]  [mm:ss]  [mm:ss:xx]
private val regexTiempo = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]""")

// [offset:+500]  (positivo = la letra aparece antes)
private val regexOffset = Regex("""\[offset:\s*([+-]?\d+)\s*\]""", RegexOption.IGNORE_CASE)

// Marcas por palabra de algunos .lrc: <00:12.50>
private val regexPalabra = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

fun parsearLetra(contenido: String): Letra {
    val texto = contenido
        .removePrefix("\uFEFF")
        .replace("\r\n", "\n")
        .replace('\r', '\n')

    val sincronizada = regexTiempo.containsMatchIn(texto)

    if (!sincronizada) {
        val lineas = texto.lines()
            .map { it.trimEnd() }
            .dropWhile { it.isBlank() }
            .dropLastWhile { it.isBlank() }
            .map { LineaLetra(-1L, it) }
        return Letra(false, lineas)
    }

    val offset = regexOffset.find(texto)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    val lineas = mutableListOf<LineaLetra>()

    for (linea in texto.lines()) {
        val tiempos = regexTiempo.findAll(linea).toList()
        if (tiempos.isEmpty()) continue // etiquetas como [ar:...] o [ti:...]

        val textoLinea = linea
            .replace(regexTiempo, "")
            .replace(regexPalabra, "")
            .trim()

        // Una misma línea puede repetirse con varias marcas: [00:10.00][01:20.00]texto
        for (t in tiempos) {
            val min = t.groupValues[1].toLong()
            val seg = t.groupValues[2].toLong()
            val fraccion = t.groupValues[3].padEnd(3, '0').take(3).toLong()
            val ms = (min * 60 + seg) * 1000 + fraccion - offset
            lineas.add(LineaLetra(ms.coerceAtLeast(0L), textoLinea))
        }
    }
    return Letra(true, lineas.sortedBy { it.tiempoMs })
}

// Lee un archivo sin pasar del límite (por si alguien elige un archivo enorme por error)
private fun leerLimitado(stream: InputStream, limite: Int): ByteArray? {
    val salida = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val n = stream.read(buffer)
        if (n < 0) break
        total += n
        if (total > limite) return null
        salida.write(buffer, 0, n)
    }
    return salida.toByteArray()
}

object RepositorioLetras {
    private const val LIMITE_BYTES = 1_000_000

    // Un archivo por canción, nombrado con su ID de MediaStore
    private fun archivo(context: Context, songId: Long): File {
        val carpeta = File(context.filesDir, "letras")
        if (!carpeta.exists()) carpeta.mkdirs()
        return File(carpeta, "$songId.txt")
    }

    // Copia el archivo elegido al almacenamiento interno. Devuelve false si no sirve.
    suspend fun importar(context: Context, songId: Long, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use {
                    leerLimitado(it, LIMITE_BYTES)
                } ?: return@withContext false

                var texto = String(bytes, Charsets.UTF_8)
                // Muchos .lrc antiguos están en Latin-1: si UTF-8 falla, probamos ese
                if (texto.contains('\uFFFD')) texto = String(bytes, Charsets.ISO_8859_1)

                if (texto.isBlank() || texto.contains('\u0000')) return@withContext false
                if (parsearLetra(texto).lineas.isEmpty()) return@withContext false

                archivo(context, songId).writeText(texto)
                true
            } catch (e: Exception) {
                false
            }
        }

    suspend fun cargar(context: Context, songId: Long): Letra? =
        withContext(Dispatchers.IO) {
            try {
                val f = archivo(context, songId)
                if (!f.exists()) null
                else parsearLetra(f.readText()).takeIf { it.lineas.isNotEmpty() }
            } catch (e: Exception) {
                null
            }
        }

    suspend fun eliminar(context: Context, songId: Long) {
        withContext(Dispatchers.IO) { archivo(context, songId).delete() }
    }
}