package com.zajeapps.musicplayer

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun SelectorTiempo(
    titulo: String,
    valorMs: Long,
    maximoMs: Long,
    onValor: (Long) -> Unit,
    onDelta: (Long) -> Unit,
    onAhora: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(titulo, style = MaterialTheme.typography.titleSmall)
        Text(
            formatearDuracion(valorMs),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
    Slider(
        value = valorMs.toFloat(),
        onValueChange = { onValor(it.toLong()) },
        valueRange = 0f..maximoMs.toFloat()
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(
            -10_000L to "−10 s",
            -1_000L to "−1 s",
            1_000L to "+1 s",
            10_000L to "+10 s"
        ).forEach { (delta, texto) ->
            OutlinedButton(
                onClick = { onDelta(delta) },
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
            ) { Text(texto) }
        }
    }
    TextButton(onClick = onAhora) { Text("Usar la posición actual") }
}

@Composable
private fun FilaFormato(
    titulo: String,
    detalle: String,
    seleccionado: Boolean,
    habilitado: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = seleccionado,
                enabled = habilitado,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = seleccionado, onClick = null, enabled = habilitado)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                titulo,
                color = if (habilitado) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Text(
                detalle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (habilitado) 0.7f else 0.4f)
            )
        }
    }
}

@Composable
fun PantallaRecorte(
    cancion: Song,
    controller: MediaController?,
    onCerrar: () -> Unit,
    onGuardado: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val duracion = cancion.durationMs.coerceAtLeast(Recortes.MIN_MS * 2)
    val puedeCortar = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    val esMp3 = remember(cancion.id) { Recortes.esMp3(context, cancion.uri) }

    // Por defecto: 30 segundos desde donde va la canción
    val inicial = remember(cancion.id) {
        val pos = (controller?.currentPosition ?: 0L).coerceIn(0L, duracion - Recortes.MIN_MS)
        val ini = if (duracion - pos < 5_000L) maxOf(0L, duracion - 30_000L) else pos
        ini to minOf(duracion, ini + 30_000L)
    }
    var inicioMs by remember(cancion.id) { mutableStateOf(inicial.first) }
    var finMs by remember(cancion.id) { mutableStateOf(inicial.second) }
    var formato by remember(cancion.id) {
        mutableStateOf(if (esMp3) FormatoRecorte.MP3 else FormatoRecorte.M4A)
    }
    var nombre by remember(cancion.id) { mutableStateOf("${cancion.title} (recorte)") }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var escuchando by remember { mutableStateOf(false) }

    val inicioActual by rememberUpdatedState(inicioMs)
    val finActual by rememberUpdatedState(finMs)

    BackHandler { if (!guardando) onCerrar() }

    // Escuchar el fragmento: salta al inicio y se detiene sola al llegar al final
    LaunchedEffect(escuchando) {
        if (!escuchando) return@LaunchedEffect
        val c = controller
        if (c == null) {
            escuchando = false
            return@LaunchedEffect
        }
        c.seekTo(inicioActual)
        c.play()
        delay(700)
        while (escuchando) {
            if (!c.isPlaying || c.currentPosition >= finActual) {
                c.pause()
                escuchando = false
                break
            }
            delay(200)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (!guardando) onCerrar() }) { Text("← Volver") }
                Spacer(modifier = Modifier.weight(1f))
            }
            Text("Cortar fragmento", style = MaterialTheme.typography.headlineSmall)
            Text(
                cancion.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1
            )
            Text(
                "Duración total: ${formatearDuracion(cancion.durationMs)}",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(16.dp))
            SelectorTiempo(
                titulo = "Inicio",
                valorMs = inicioMs,
                maximoMs = duracion,
                onValor = { inicioMs = it.coerceIn(0L, finMs - Recortes.MIN_MS) },
                onDelta = { inicioMs = (inicioMs + it).coerceIn(0L, finMs - Recortes.MIN_MS) },
                onAhora = {
                    inicioMs = (controller?.currentPosition ?: 0L)
                        .coerceIn(0L, finMs - Recortes.MIN_MS)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
            SelectorTiempo(
                titulo = "Fin",
                valorMs = finMs,
                maximoMs = duracion,
                onValor = { finMs = it.coerceIn(inicioMs + Recortes.MIN_MS, duracion) },
                onDelta = { finMs = (finMs + it).coerceIn(inicioMs + Recortes.MIN_MS, duracion) },
                onAhora = {
                    finMs = (controller?.currentPosition ?: duracion)
                        .coerceIn(inicioMs + Recortes.MIN_MS, duracion)
                }
            )

            Text(
                "Fragmento: ${formatearDuracion(finMs - inicioMs)}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            OutlinedButton(
                onClick = { escuchando = !escuchando },
                enabled = controller != null && !guardando,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text(if (escuchando) "Detener" else "Escuchar fragmento") }

            Text(
                "Formato",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
            )
            FilaFormato(
                titulo = "MP3",
                detalle = if (esMp3) "Sin pérdida de calidad, corte exacto"
                else "Solo disponible si la canción original es MP3",
                seleccionado = formato == FormatoRecorte.MP3,
                habilitado = esMp3,
                onClick = { formato = FormatoRecorte.MP3 }
            )
            FilaFormato(
                titulo = "M4A (AAC)",
                detalle = "Funciona con cualquier canción; se vuelve a comprimir",
                seleccionado = formato == FormatoRecorte.M4A,
                habilitado = true,
                onClick = { formato = FormatoRecorte.M4A }
            )

            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                singleLine = true,
                label = { Text("Nombre del archivo") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            Text(
                "Se guarda en Música ▸ Recortes. La canción original no se modifica.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (!puedeCortar) {
                Text(
                    "Cortar fragmentos requiere Android 10 o superior.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Button(
                onClick = {
                    escuchando = false
                    error = null
                    guardando = true
                    scope.launch {
                        val resultado = Recortes.guardar(
                            context, cancion, inicioMs, finMs, formato, nombre
                        )
                        guardando = false
                        if (resultado == null) {
                            Toast.makeText(
                                context, "Guardado en Música/Recortes", Toast.LENGTH_LONG
                            ).show()
                            onGuardado()
                            onCerrar()
                        } else {
                            error = resultado
                        }
                    }
                },
                enabled = !guardando && puedeCortar,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp)
            ) {
                if (guardando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardando…")
                } else {
                    Text("Guardar fragmento")
                }
            }
        }
    }
}