package com.zajeapps.musicplayer

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.session.MediaController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PantallaLetra(
    cancion: Song,
    controller: MediaController?,
    sonando: Boolean,
    onCerrar: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = (context as ComponentActivity).lifecycle

    // Todo se reinicia al cambiar de canción
    var letra by remember(cancion.id) { mutableStateOf<Letra?>(null) }
    var cargando by remember(cancion.id) { mutableStateOf(true) }
    var indiceActual by remember(cancion.id) { mutableStateOf(-1) }
    val listState = remember(cancion.id) { LazyListState() }

    BackHandler(onBack = onCerrar)

    LaunchedEffect(cancion.id) {
        letra = RepositorioLetras.cargar(context, cancion.id)
        cargando = false
    }

    // Selector de archivos del sistema (no necesita permisos)
    val selector = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = RepositorioLetras.importar(context, cancion.id, uri)
                if (ok) {
                    letra = RepositorioLetras.cargar(context, cancion.id)
                } else {
                    Toast.makeText(
                        context,
                        "No se pudo leer ese archivo. Debe ser texto (.lrc o .txt) en UTF-8.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // Línea actual: duerme hasta la próxima línea en vez de consultar cada poco
    LaunchedEffect(controller, letra, sonando) {
        val c = controller ?: return@LaunchedEffect
        val l = letra ?: return@LaunchedEffect
        if (!l.sincronizada) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val pos = c.currentPosition
                val idx = l.lineas.indexOfLast { it.tiempoMs <= pos }
                indiceActual = idx
                if (!sonando) break
                val siguiente = l.lineas.getOrNull(idx + 1)?.tiempoMs
                val espera = if (siguiente != null) (siguiente - pos).coerceIn(50L, 1000L) else 1000L
                delay(espera)
            }
        }
    }

    // Mantiene la línea actual a la vista (con 3 líneas de contexto arriba)
    LaunchedEffect(indiceActual) {
        if (indiceActual >= 0) {
            listState.animateScrollToItem((indiceActual - 3).coerceAtLeast(0))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCerrar) { Text("← Volver") }
            Spacer(modifier = Modifier.weight(1f))
            if (letra != null) {
                TextButton(onClick = { selector.launch(arrayOf("*/*")) }) { Text("Cambiar") }
                TextButton(onClick = {
                    scope.launch {
                        RepositorioLetras.eliminar(context, cancion.id)
                        letra = null
                        indiceActual = -1
                    }
                }) { Text("Quitar") }
            }
        }

        Text(
            cancion.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        val actual = letra
        when {
            cargando -> {}
            actual == null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Esta canción no tiene letra.\nImporta un archivo .lrc (sincronizado) o .txt.",
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { selector.launch(arrayOf("*/*")) },
                        modifier = Modifier.padding(top = 16.dp)
                    ) { Text("Importar letra") }
                }
            }
            else -> {
                if (!actual.sincronizada) {
                    Text(
                        "Letra sin sincronizar",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                ) {
                    itemsIndexed(actual.lineas) { i, linea ->
                        val esActual = actual.sincronizada && i == indiceActual
                        Text(
                            text = if (linea.texto.isBlank()) " " else linea.texto,
                            style = if (esActual) MaterialTheme.typography.titleLarge
                            else MaterialTheme.typography.bodyLarge,
                            fontWeight = if (esActual) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                !actual.sincronizada -> MaterialTheme.colorScheme.onSurface
                                esActual -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            },
                            textAlign = if (actual.sincronizada) TextAlign.Center else TextAlign.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (actual.sincronizada) Modifier.clickable {
                                        controller?.seekTo(linea.tiempoMs)
                                        indiceActual = i
                                    } else Modifier
                                )
                                .padding(vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}