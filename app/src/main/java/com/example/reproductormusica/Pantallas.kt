package com.example.reproductormusica

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.Normalizer

// ---------- Utilidades ----------

private val regexAcentos = Regex("\\p{M}+")

// Minúsculas y sin tildes, para que "cancion" encuentre "Canción"
fun normalizar(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD)
        .replace(regexAcentos, "")
        .lowercase()

// ---------- Componentes reutilizables ----------

@Composable
fun FilaCancion(
    cancion: Song,
    icono: ImageVector,
    descripcionAccion: String,
    onClick: () -> Unit,
    onAccion: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Caratula(cancion, 48.dp)
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
        ) {
            Text(cancion.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                "${cancion.artist} · ${formatearDuracion(cancion.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
        IconButton(onClick = onAccion) {
            Icon(imageVector = icono, contentDescription = descripcionAccion)
        }
    }
}

@Composable
fun DialogoNombre(
    titulo: String,
    onConfirmar: (String) -> Unit,
    onCancelar: () -> Unit
) {
    var texto by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(titulo) },
        text = {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                singleLine = true,
                label = { Text("Nombre") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmar(texto.trim()) },
                enabled = texto.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

// ---------- Pestaña Canciones ----------

@Composable
fun PantallaCanciones(
    canciones: List<Song>,
    cargado: Boolean,
    listas: List<ListaConConteo>,
    onReproducir: (List<Song>, Int) -> Unit,
    onAgregarALista: (Song, Long) -> Unit,
    onActualizar: () -> Unit
) {
    val context = LocalContext.current
    val foco = LocalFocusManager.current
    var cancionParaAgregar by remember { mutableStateOf<Song?>(null) }
    var busqueda by rememberSaveable { mutableStateOf("") }

    // Texto normalizado de cada canción, calculado una sola vez
    val indice = remember(canciones) {
        canciones.map { it to normalizar("${it.title} ${it.artist}") }
    }
    val filtradas = remember(indice, busqueda) {
        val q = normalizar(busqueda.trim())
        if (q.isEmpty()) canciones else indice.filter { it.second.contains(q) }.map { it.first }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (busqueda.isBlank()) "${canciones.size} canciones"
                else "${filtradas.size} de ${canciones.size}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onActualizar) {
                Icon(Iconos.Actualizar, contentDescription = "Actualizar biblioteca")
            }
        }

        OutlinedTextField(
            value = busqueda,
            onValueChange = { busqueda = it },
            singleLine = true,
            placeholder = { Text("Buscar canción o artista") },
            leadingIcon = { Icon(Iconos.Buscar, contentDescription = null) },
            trailingIcon = {
                if (busqueda.isNotEmpty()) {
                    IconButton(onClick = { busqueda = "" }) {
                        Icon(Iconos.Cerrar, contentDescription = "Borrar búsqueda")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        )

        when {
            cargado && canciones.isEmpty() -> {
                Text(
                    "No se encontraron canciones. Copia música al teléfono y pulsa Actualizar.",
                    modifier = Modifier.padding(24.dp)
                )
            }
            filtradas.isEmpty() && busqueda.isNotBlank() -> {
                Text("Sin resultados.", modifier = Modifier.padding(24.dp))
            }
            else -> {
                LazyColumn {
                    itemsIndexed(filtradas, key = { _, c -> c.id }) { posicion, cancion ->
                        FilaCancion(
                            cancion = cancion,
                            icono = Iconos.Agregar,
                            descripcionAccion = "Añadir a una lista",
                            onClick = {
                                foco.clearFocus()
                                onReproducir(filtradas, posicion)
                            },
                            onAccion = { cancionParaAgregar = cancion }
                        )
                    }
                }
            }
        }
    }

    cancionParaAgregar?.let { cancion ->
        AlertDialog(
            onDismissRequest = { cancionParaAgregar = null },
            title = { Text("Añadir a una lista") },
            text = {
                if (listas.isEmpty()) {
                    Text("Aún no tienes listas. Crea una en la pestaña Listas.")
                } else {
                    LazyColumn {
                        items(listas, key = { it.id }) { lista ->
                            TextButton(
                                onClick = {
                                    onAgregarALista(cancion, lista.id)
                                    Toast.makeText(
                                        context,
                                        "Añadida a \"${lista.nombre}\"",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    cancionParaAgregar = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("${lista.nombre} (${lista.total})") }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { cancionParaAgregar = null }) { Text("Cancelar") }
            }
        )
    }
}

// ---------- Pestaña Listas ----------

@Composable
fun PantallaListas(
    dao: ListasDao,
    listas: List<ListaConConteo>,
    canciones: List<Song>,
    onReproducir: (List<Song>, Int) -> Unit
) {
    var abiertaId by rememberSaveable { mutableStateOf<Long?>(null) }
    var dialogoNueva by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val abierta = listas.find { it.id == abiertaId }

    // El botón "atrás" del teléfono vuelve de la lista abierta a la lista de listas
    BackHandler(enabled = abiertaId != null) { abiertaId = null }

    if (abierta != null) {
        DetalleLista(
            dao = dao,
            lista = abierta,
            canciones = canciones,
            onVolver = { abiertaId = null },
            onReproducir = onReproducir
        )
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${listas.size} listas", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { dialogoNueva = true }) { Text("Nueva lista") }
            }
            if (listas.isEmpty()) {
                Text(
                    "Crea tu primera lista con el botón de arriba.",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                LazyColumn {
                    items(listas, key = { it.id }) { lista ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { abiertaId = lista.id }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                lista.nombre,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1
                            )
                            Text(
                                "${lista.total} canciones",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }

    if (dialogoNueva) {
        DialogoNombre(
            titulo = "Nueva lista",
            onConfirmar = { nombre ->
                scope.launch { dao.crearLista(ListaEntity(nombre = nombre)) }
                dialogoNueva = false
            },
            onCancelar = { dialogoNueva = false }
        )
    }
}

@Composable
fun DetalleLista(
    dao: ListasDao,
    lista: ListaConConteo,
    canciones: List<Song>,
    onVolver: () -> Unit,
    onReproducir: (List<Song>, Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    var confirmarEliminar by remember { mutableStateOf(false) }

    // IDs guardados en la base de datos, siempre actualizados
    val ids by remember(lista.id) { dao.observarIdsDeLista(lista.id) }
        .collectAsState(initial = emptyList<Long>())

    // Cruzamos los IDs con las canciones que existen ahora en el teléfono.
    // Si alguna se borró del dispositivo, simplemente no aparece.
    val porId = remember(canciones) { canciones.associateBy { it.id } }
    val cola = remember(ids, porId) { ids.mapNotNull { porId[it] } }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onVolver) { Text("← Listas") }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { confirmarEliminar = true }) { Text("Eliminar") }
        }
        Text(
            lista.nombre,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Button(
            onClick = { onReproducir(cola, 0) },
            enabled = cola.isNotEmpty(),
            modifier = Modifier.padding(16.dp)
        ) { Text("Reproducir lista") }

        if (cola.isEmpty()) {
            Text(
                "Esta lista está vacía. Añade canciones desde la pestaña Canciones con el botón +.",
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            LazyColumn {
                itemsIndexed(cola, key = { _, c -> c.id }) { posicion, cancion ->
                    FilaCancion(
                        cancion = cancion,
                        icono = Iconos.Cerrar,
                        descripcionAccion = "Quitar de la lista",
                        onClick = { onReproducir(cola, posicion) },
                        onAccion = {
                            scope.launch { dao.quitarCancion(lista.id, cancion.id) }
                        }
                    )
                }
            }
        }
    }

    if (confirmarEliminar) {
        AlertDialog(
            onDismissRequest = { confirmarEliminar = false },
            title = { Text("¿Eliminar \"${lista.nombre}\"?") },
            text = { Text("Las canciones no se borran del teléfono, solo la lista.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { dao.eliminarLista(lista.id) }
                    confirmarEliminar = false
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarEliminar = false }) { Text("Cancelar") }
            }
        )
    }
}