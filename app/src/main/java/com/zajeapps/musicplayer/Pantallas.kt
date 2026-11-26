package com.zajeapps.musicplayer

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.Normalizer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.LaunchedEffect

// ---------- Utilidades ----------

private val regexAcentos = Regex("\\p{M}+")

// Minúsculas y sin tildes, para que "cancion" encuentre "Canción"
fun normalizar(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD)
        .replace(regexAcentos, "")
        .lowercase()

// ---------- Componentes reutilizables ----------

// Fila con botón de acción a la derecha (se usa dentro de las listas de reproducción)
@Composable
fun FilaCancion(
    cancion: Song,
    icono: ImageVector,
    descripcionAccion: String,
    onClick: () -> Unit,
    onAccion: () -> Unit,
    permitirCarga: () -> Boolean = { true }
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Caratula(cancion, 48.dp, permitirCarga = permitirCarga)
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
        ) {
            Text(
                cancion.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                cancion.detalle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onAccion) {
            Icon(imageVector = icono, contentDescription = descripcionAccion)
        }
    }
}

// Fila de la biblioteca: toque = reproducir, pulsación larga = seleccionar
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilaCancionSeleccion(
    cancion: Song,
    seleccionada: Boolean,
    modoSeleccion: Boolean,
    permitirCarga: () -> Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val fondo = if (seleccionada) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(fondo)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            // el margen derecho deja sitio al deslizador rápido
            .padding(start = 16.dp, end = 32.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (modoSeleccion) {
            Checkbox(checked = seleccionada, onCheckedChange = null)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Caratula(cancion, 48.dp, permitirCarga = permitirCarga)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                cancion.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                cancion.detalle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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

// Barra que sustituye a la cabecera mientras hay canciones seleccionadas
@Composable
private fun BarraSeleccion(
    total: Int,
    onCerrar: () -> Unit,
    onTodas: () -> Unit,
    onAgregar: () -> Unit,
    onCompartir: () -> Unit,
    onEliminar: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCerrar) {
                Icon(Iconos.Cerrar, contentDescription = "Cancelar selección")
            }
            Text(
                if (total == 1) "1 seleccionada" else "$total seleccionadas",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onTodas) { Text("Todas") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = onAgregar) { Text("A una lista") }
            TextButton(onClick = onCompartir) { Text("Compartir") }
            TextButton(
                onClick = onEliminar,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) { Text("Eliminar") }
        }
    }
}

// ---------- Pestaña Canciones ----------

@Composable
fun PantallaCanciones(
    canciones: List<Song>,
    cargado: Boolean,
    listas: List<ListaConConteo>,
    onReproducir: (List<Song>, Int) -> Unit,
    onAgregarALista: (List<Song>, Long) -> Unit,
    onCrearListaConCanciones: (String, List<Song>) -> Unit,
    onCompartir: (List<Song>) -> Unit,
    onEliminar: (List<Song>) -> Unit,
    onActualizar: () -> Unit
) {
    val context = LocalContext.current
    val foco = LocalFocusManager.current
    val vibracion = LocalHapticFeedback.current

    var busqueda by rememberSaveable { mutableStateOf("") }
    var seleccion by remember { mutableStateOf(setOf<Long>()) }
    var dialogoLista by remember { mutableStateOf(false) }
    var dialogoNueva by remember { mutableStateOf(false) }
    var confirmarEliminar by remember { mutableStateOf(false) }
    var menuOrden by remember { mutableStateOf(false) }

    val orden = Ajustes.orden

    val listState = rememberLazyListState()
    // Las carátulas solo se cargan cuando la lista deja de moverse
    val permitirCarga: () -> Boolean = remember(listState) { { !listState.isScrollInProgress } }

    // Al cambiar el orden, la lista vuelve al principio
    LaunchedEffect(orden) { listState.scrollToItem(0) }

    // Canciones en el orden elegido (las claves ya vienen calculadas)
    val ordenadas = remember(canciones, orden) {
        when (orden) {
            OrdenCanciones.TITULO_ASC -> canciones.sortedBy { it.claveTitulo }
            OrdenCanciones.TITULO_DESC -> canciones.sortedByDescending { it.claveTitulo }
            OrdenCanciones.ARTISTA_ASC ->
                canciones.sortedWith(compareBy({ it.claveArtista }, { it.claveTitulo }))
            OrdenCanciones.ARTISTA_DESC ->
                canciones.sortedWith(
                    compareByDescending<Song> { it.claveArtista }.thenBy { it.claveTitulo }
                )
            OrdenCanciones.DURACION_ASC -> canciones.sortedBy { it.durationMs }
            OrdenCanciones.DURACION_DESC -> canciones.sortedByDescending { it.durationMs }
            OrdenCanciones.RECIENTES -> canciones.sortedByDescending { it.fecha }
            OrdenCanciones.ANTIGUAS -> canciones.sortedBy { it.fecha }
        }
    }

    // Texto normalizado de cada canción, calculado una sola vez por orden
    val indice = remember(ordenadas) {
        ordenadas.map { it to normalizar("${it.title} ${it.artist}") }
    }
    val filtradas = remember(indice, busqueda) {
        val q = normalizar(busqueda.trim())
        if (q.isEmpty()) ordenadas else indice.filter { it.second.contains(q) }.map { it.first }
    }
    val seleccionadas = remember(seleccion, ordenadas) {
        ordenadas.filter { it.id in seleccion }
    }

    // "Atrás" cancela la selección
    BackHandler(enabled = seleccion.isNotEmpty()) { seleccion = emptySet() }

    Column(modifier = Modifier.fillMaxSize()) {
        if (seleccion.isEmpty()) {
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
                Box {
                    IconButton(onClick = { menuOrden = true }) {
                        Icon(Iconos.Ordenar, contentDescription = "Ordenar canciones")
                    }
                    DropdownMenu(
                        expanded = menuOrden,
                        onDismissRequest = { menuOrden = false }
                    ) {
                        OrdenCanciones.entries.forEach { opcion ->
                            DropdownMenuItem(
                                text = {
                                    Text((if (opcion == orden) "✓  " else "     ") + opcion.etiqueta)
                                },
                                onClick = {
                                    Ajustes.ponerOrden(context, opcion)
                                    menuOrden = false
                                }
                            )
                        }
                    }
                }
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
        } else {
            BarraSeleccion(
                total = seleccion.size,
                onCerrar = { seleccion = emptySet() },
                onTodas = { seleccion = filtradas.map { it.id }.toSet() },
                onAgregar = { dialogoLista = true },
                onCompartir = {
                    onCompartir(seleccionadas)
                    seleccion = emptySet()
                },
                onEliminar = { confirmarEliminar = true }
            )
        }

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
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(
                            filtradas,
                            key = { _, c -> c.id },
                            contentType = { _, _ -> "cancion" }
                        ) { posicion, cancion ->
                            FilaCancionSeleccion(
                                cancion = cancion,
                                seleccionada = cancion.id in seleccion,
                                modoSeleccion = seleccion.isNotEmpty(),
                                permitirCarga = permitirCarga,
                                onClick = {
                                    if (seleccion.isNotEmpty()) {
                                        seleccion = if (cancion.id in seleccion) {
                                            seleccion - cancion.id
                                        } else {
                                            seleccion + cancion.id
                                        }
                                    } else {
                                        foco.clearFocus()
                                        onReproducir(filtradas, posicion)
                                    }
                                },
                                onLongClick = {
                                    vibracion.performHapticFeedback(HapticFeedbackType.LongPress)
                                    seleccion = if (cancion.id in seleccion) {
                                        seleccion - cancion.id
                                    } else {
                                        seleccion + cancion.id
                                    }
                                }
                            )
                        }
                    }
                    // En orden alfabético la burbuja muestra la letra; en los demás, la posición
                    BarraRapida(
                        listState = listState,
                        total = filtradas.size,
                        etiqueta = { i ->
                            when (orden) {
                                OrdenCanciones.TITULO_ASC, OrdenCanciones.TITULO_DESC ->
                                    letraDe(filtradas.getOrNull(i)?.title ?: "")
                                OrdenCanciones.ARTISTA_ASC, OrdenCanciones.ARTISTA_DESC ->
                                    letraDe(filtradas.getOrNull(i)?.artist ?: "")
                                else -> (i + 1).toString()
                            }
                        }
                    )
                }
            }
        }
    }

    // ----- Diálogo: elegir lista -----
    if (dialogoLista) {
        AlertDialog(
            onDismissRequest = { dialogoLista = false },
            title = {
                Text(
                    if (seleccion.size == 1) "Añadir 1 canción a una lista"
                    else "Añadir ${seleccion.size} canciones a una lista"
                )
            },
            text = {
                LazyColumn {
                    item {
                        TextButton(
                            onClick = {
                                dialogoLista = false
                                dialogoNueva = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("+ Nueva lista…") }
                    }
                    items(listas, key = { it.id }) { lista ->
                        TextButton(
                            onClick = {
                                onAgregarALista(seleccionadas, lista.id)
                                Toast.makeText(
                                    context,
                                    "Añadidas a \"${lista.nombre}\"",
                                    Toast.LENGTH_SHORT
                                ).show()
                                seleccion = emptySet()
                                dialogoLista = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("${lista.nombre} (${lista.total})") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { dialogoLista = false }) { Text("Cancelar") }
            }
        )
    }

    // ----- Diálogo: nueva lista con las canciones seleccionadas -----
    if (dialogoNueva) {
        DialogoNombre(
            titulo = "Nueva lista",
            onConfirmar = { nombre ->
                onCrearListaConCanciones(nombre, seleccionadas)
                Toast.makeText(context, "Lista \"$nombre\" creada", Toast.LENGTH_SHORT).show()
                seleccion = emptySet()
                dialogoNueva = false
            },
            onCancelar = { dialogoNueva = false }
        )
    }

    // ----- Confirmación de borrado -----
    if (confirmarEliminar) {
        AlertDialog(
            onDismissRequest = { confirmarEliminar = false },
            title = {
                Text(
                    if (seleccion.size == 1) "¿Eliminar 1 canción?"
                    else "¿Eliminar ${seleccion.size} canciones?"
                )
            },
            text = { Text("Se borrarán los archivos del teléfono. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmarEliminar = false
                        onEliminar(seleccionadas)
                        seleccion = emptySet()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarEliminar = false }) { Text("Cancelar") }
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
                    "Crea tu primera lista con el botón de arriba, o selecciona canciones con una pulsación larga en la pestaña Canciones.",
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

    val listState = rememberLazyListState()
    val permitirCarga: () -> Boolean = remember(listState) { { !listState.isScrollInProgress } }

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
                "Esta lista está vacía. Mantén pulsada una canción en la pestaña Canciones para añadirla.",
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            LazyColumn(state = listState) {
                itemsIndexed(
                    cola,
                    key = { _, c -> c.id },
                    contentType = { _, _ -> "cancion" }
                ) { posicion, cancion ->
                    FilaCancion(
                        cancion = cancion,
                        icono = Iconos.Cerrar,
                        descripcionAccion = "Quitar de la lista",
                        onClick = { onReproducir(cola, posicion) },
                        onAccion = {
                            scope.launch { dao.quitarCancion(lista.id, cancion.id) }
                        },
                        permitirCarga = permitirCarga
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