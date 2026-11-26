package com.zajeapps.musicplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.CancellationSignal
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

// La caché usa 1/16 de la memoria máxima de la app (entre 6 y 20 MB)
private fun tamanoCacheKb(): Int {
    val kb = (Runtime.getRuntime().maxMemory() / 1024 / 16).toInt()
    return kb.coerceIn(6 * 1024, 20 * 1024)
}

object Caratulas {
    // 96 px bastan para una miniatura de 48 dp y pesan 2,25 veces menos que 144 px
    private const val TAMANO_PX = 96

    private val cache = object : LruCache<Long, Bitmap>(tamanoCacheKb()) {
        override fun sizeOf(key: Long, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    // Canciones sin carátula: no se vuelve a intentar en esta sesión
    private val sinCaratula: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    // Como mucho 3 lecturas a la vez, para no saturar equipos modestos
    private val limite = Semaphore(3)

    fun enCache(id: Long): Bitmap? = cache.get(id)

    fun sabidaSinCaratula(id: Long): Boolean = id in sinCaratula

    suspend fun cargar(context: Context, cancion: Song): Bitmap? {
        cache.get(cancion.id)?.let { return it }
        if (cancion.id in sinCaratula) return null

        return limite.withPermit {
            withContext(Dispatchers.IO) {
                val senal = CancellationSignal()
                // Si la fila sale de pantalla, la lectura en curso se aborta
                val aviso = coroutineContext[Job]?.invokeOnCompletion { causa ->
                    if (causa != null) senal.cancel()
                }
                try {
                    val bitmap = leer(context.applicationContext, cancion, senal)
                    ensureActive() // si se canceló, no guardamos nada (ni marcamos "sin carátula")
                    if (bitmap != null) cache.put(cancion.id, bitmap) else sinCaratula.add(cancion.id)
                    bitmap
                } finally {
                    aviso?.dispose()
                }
            }
        }
    }

    private fun leer(context: Context, cancion: Song, senal: CancellationSignal): Bitmap? =
        try {
            val original: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(
                    cancion.uri, Size(TAMANO_PX, TAMANO_PX), senal
                )
            } else {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, cancion.uri)
                    retriever.embeddedPicture?.let { decodificar(it) }
                } finally {
                    retriever.release()
                }
            }
            original?.let { reducir(it) }
        } catch (e: Exception) {
            null
        }

    // Si el sistema devuelve una imagen mucho mayor de lo pedido, la reducimos
    private fun reducir(b: Bitmap): Bitmap {
        val mayor = maxOf(b.width, b.height)
        if (mayor <= TAMANO_PX * 3 / 2) return b
        return try {
            val f = TAMANO_PX.toFloat() / mayor
            Bitmap.createScaledBitmap(
                b,
                (b.width * f).toInt().coerceAtLeast(1),
                (b.height * f).toInt().coerceAtLeast(1),
                true
            )
        } catch (e: Exception) {
            b
        }
    }

    // Reduce la imagen al leerla, para no cargar una portada enorme en memoria
    private fun decodificar(bytes: ByteArray): Bitmap? {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, limites)
        var muestra = 1
        while (limites.outWidth / (muestra * 2) >= TAMANO_PX &&
            limites.outHeight / (muestra * 2) >= TAMANO_PX
        ) {
            muestra *= 2
        }
        val opciones = BitmapFactory.Options().apply { inSampleSize = muestra }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opciones)
    }
}

// permitirCarga: en las listas es "la lista no se está moviendo".
// Mientras sea falso, la fila muestra la notita y no pide la imagen.
@Composable
fun Caratula(
    cancion: Song,
    tamano: Dp,
    modifier: Modifier = Modifier,
    permitirCarga: () -> Boolean = { true }
) {
    val context = LocalContext.current
    var imagen by remember(cancion.id) {
        mutableStateOf(Caratulas.enCache(cancion.id)?.asImageBitmap())
    }

    LaunchedEffect(cancion.id) {
        if (imagen == null && !Caratulas.sabidaSinCaratula(cancion.id)) {
            // Espera a que la lista deje de moverse, y un instante más
            snapshotFlow { permitirCarga() }.first { it }
            delay(120)
            imagen = Caratulas.cargar(context, cancion)?.asImageBitmap()
        }
    }

    Box(
        modifier = modifier
            .size(tamano)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val img = imagen
        if (img != null) {
            Image(
                bitmap = img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Iconos.Nota,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}