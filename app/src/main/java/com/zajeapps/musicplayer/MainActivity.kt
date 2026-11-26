package com.zajeapps.musicplayer

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.zajeapps.musicplayer.ui.theme.ReproductorMusicaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ajustes.cargar(this)
        Anuncios.cargar(this)
        enableEdgeToEdge()
        setContent {
            ReproductorMusicaTheme(
                modo = Ajustes.modoTema,
                dinamico = Ajustes.colorDinamico
            ) {
                AppMusica()
            }
        }
        // Consentimiento + inicio del SDK de anuncios (si el usuario no ha pagado)
        Anuncios.iniciar(this)
    }
}

// Quita de la cola de reproducción las canciones cuyos IDs se indican
private fun quitarDeLaCola(c: MediaController?, ids: Set<String>) {
    if (c == null) return
    for (i in c.mediaItemCount - 1 downTo 0) {
        if (c.getMediaItemAt(i).mediaId in ids) c.removeMediaItem(i)
    }
}

@Composable
fun AppMusica() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Base de datos y listas (se actualizan solas)
    val dao = remember { AppDatabase.obtener(context).listasDao() }
    val listas by remember { dao.observarListas() }
        .collectAsState(initial = emptyList<ListaConConteo>())

    var pestana by rememberSaveable { mutableStateOf(0) }
    var mostrarLetra by remember { mutableStateOf(false) }
    var recortando by remember { mutableStateOf(false) }
    var reproductorAbierto by rememberSaveable { mutableStateOf(false) }

    // Si cambias de pestaña, se cierran las pantallas de letra y de corte
    LaunchedEffect(pestana) {
        mostrarLetra = false
        recortando = false
    }

    var tienePermiso by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permisoAudio) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var canciones by remember { mutableStateOf<List<Song>>(emptyList()) }
    var cargado by remember { mutableStateOf(false) }

    // Cada vez que sube este número, se vuelve a leer la biblioteca
    var recarga by remember { mutableStateOf(0) }

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var actual by remember { mutableStateOf<Song?>(null) }
    var sonando by remember { mutableStateOf(false) }
    var aleatorio by remember { mutableStateOf(false) }
    var repetir by remember { mutableStateOf(Player.REPEAT_MODE_OFF) }

    val cancionesActuales by rememberUpdatedState(canciones)

    // Conexión con el servicio de música
    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, ServicioMusica::class.java))
        val futuro = MediaController.Builder(context, token).buildAsync()
        futuro.addListener(
            { controller = try { futuro.get() } catch (e: Exception) { null } },
            ContextCompat.getMainExecutor(context)
        )
        onDispose {
            MediaController.releaseFuture(futuro)
            controller = null
        }
    }

    // Escucha los cambios del reproductor
    DisposableEffect(controller) {
        val c = controller
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                sonando = isPlaying
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                aleatorio = shuffleModeEnabled
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                repetir = repeatMode
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId
                actual = cancionesActuales.find { it.id.toString() == id }
            }
        }
        if (c != null) {
            sonando = c.isPlaying
            aleatorio = c.shuffleModeEnabled
            repetir = c.repeatMode
            c.addListener(listener)
        }
        onDispose { c?.removeListener(listener) }
    }

    // Si la música ya sonaba al reabrir la app, recupera la barra
    LaunchedEffect(controller, canciones) {
        val id = controller?.currentMediaItem?.mediaId
        if (actual == null && id != null) {
            actual = canciones.find { it.id.toString() == id }
        }
    }

    val lanzador = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido -> tienePermiso = concedido }

    val lanzadorNotificaciones = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // Se relee la biblioteca al dar permiso, al pulsar Actualizar o al cambiar el filtro de duración
    val minimoSeg = Ajustes.duracionMinimaSeg
    LaunchedEffect(tienePermiso, recarga, minimoSeg) {
        if (tienePermiso) {
            canciones = withContext(Dispatchers.IO) {
                cargarCanciones(context, minimoSeg * 1000L)
            }
            cargado = true
        }
    }

    // Lo que usan las pestañas para reproducir una cola
    val alReproducir: (List<Song>, Int) -> Unit = { cola, indice ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            lanzadorNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        actual = cola.getOrNull(indice)
        reproducirCola(controller, cola, indice)
    }

    // ----- Acciones sobre canciones seleccionadas -----

    val agregarALista: (List<Song>, Long) -> Unit = { songs, listaId ->
        scope.launch { songs.forEach { dao.agregarCancion(listaId, it.id) } }
    }

    val crearListaConCanciones: (String, List<Song>) -> Unit = { nombre, songs ->
        scope.launch {
            val id = dao.crearLista(ListaEntity(nombre = nombre))
            songs.forEach { dao.agregarCancion(id, it.id) }
        }
    }

    val compartir: (List<Song>) -> Unit = { songs ->
        if (songs.isNotEmpty()) {
            val uris = ArrayList(songs.map { it.uri })
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
            }
            intent.type = "audio/*"
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                context.startActivity(Intent.createChooser(intent, "Compartir"))
            } catch (e: Exception) {
                Toast.makeText(context, "No se pudo compartir", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Eliminar: el sistema pide confirmación (Android 11 o superior)
    var pendientesEliminar by remember { mutableStateOf<List<Song>>(emptyList()) }

    val lanzadorEliminar = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { resultado ->
        if (resultado.resultCode == Activity.RESULT_OK) {
            val ids = pendientesEliminar.map { it.id.toString() }.toSet()
            quitarDeLaCola(controller, ids)
            actual = actual?.takeIf { it.id.toString() !in ids }
            recarga++
            Toast.makeText(context, "Canciones eliminadas", Toast.LENGTH_SHORT).show()
        }
        pendientesEliminar = emptyList()
    }

    val eliminar: (List<Song>) -> Unit = { songs ->
        if (songs.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val pendiente = MediaStore.createDeleteRequest(
                        context.contentResolver,
                        songs.map { it.uri }
                    )
                    pendientesEliminar = songs
                    lanzadorEliminar.launch(
                        IntentSenderRequest.Builder(pendiente.intentSender).build()
                    )
                } catch (e: Exception) {
                    Toast.makeText(context, "No se pudo iniciar el borrado", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(
                    context,
                    "Eliminar canciones requiere Android 11 o superior",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // El banner se oculta mientras el reproductor, la letra o el corte ocupan la pantalla
    val mostrarBanner = tienePermiso && !reproductorAbierto && !mostrarLetra && !recortando

    Box(modifier = Modifier.fillMaxSize()) {

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (tienePermiso) {
                    Column {
                        actual?.let { cancion ->
                            BarraReproduccion(
                                cancion = cancion,
                                sonando = sonando,
                                controller = controller,
                                onAbrir = { reproductorAbierto = true }
                            )
                        }
                        NavigationBar {
                            NavigationBarItem(
                                selected = pestana == 0,
                                onClick = { pestana = 0 },
                                icon = { Icon(Iconos.Nota, contentDescription = null) },
                                label = { Text("Canciones") }
                            )
                            NavigationBarItem(
                                selected = pestana == 1,
                                onClick = { pestana = 1 },
                                icon = { Icon(Iconos.Lista, contentDescription = null) },
                                label = { Text("Listas") }
                            )
                            NavigationBarItem(
                                selected = pestana == 2,
                                onClick = { pestana = 2 },
                                icon = { Icon(Iconos.Ecualizador, contentDescription = null) },
                                label = { Text("Sonido") }
                            )
                            NavigationBarItem(
                                selected = pestana == 3,
                                onClick = { pestana = 3 },
                                icon = { Icon(Iconos.Opciones, contentDescription = null) },
                                label = { Text("Ajustes") }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {

                // Contenido de la pestaña
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (!tienePermiso) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Necesito acceso a tu música para mostrarla.")
                            Button(
                                onClick = { lanzador.launch(permisoAudio) },
                                modifier = Modifier.padding(top = 16.dp)
                            ) { Text("Dar permiso") }
                        }
                    } else if (recortando) {
                        actual?.let { cancion ->
                            PantallaRecorte(
                                cancion = cancion,
                                controller = controller,
                                onCerrar = { recortando = false },
                                onGuardado = { recarga++ }
                            )
                        }
                    } else if (mostrarLetra) {
                        actual?.let { cancion ->
                            PantallaLetra(
                                cancion = cancion,
                                controller = controller,
                                sonando = sonando,
                                onCerrar = { mostrarLetra = false }
                            )
                        }
                    } else if (pestana == 0) {
                        PantallaCanciones(
                            canciones = canciones,
                            cargado = cargado,
                            listas = listas,
                            onReproducir = alReproducir,
                            onAgregarALista = agregarALista,
                            onCrearListaConCanciones = crearListaConCanciones,
                            onCompartir = compartir,
                            onEliminar = eliminar,
                            onActualizar = { recarga++ }
                        )
                    } else if (pestana == 1) {
                        PantallaListas(
                            dao = dao,
                            listas = listas,
                            canciones = canciones,
                            onReproducir = alReproducir
                        )
                    } else if (pestana == 2) {
                        PantallaEcualizador()
                    } else {
                        PantallaAjustes()
                    }
                }

                // Un único banner para las cuatro pestañas: al cambiar de pestaña no se recarga
                if (mostrarBanner) BannerAnuncio()
            }
        }

        // Reproductor a pantalla completa, por encima de todo
        AnimatedVisibility(
            visible = reproductorAbierto && actual != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            actual?.let { cancion ->
                PantallaReproductor(
                    controller = controller,
                    cancion = cancion,
                    sonando = sonando,
                    aleatorio = aleatorio,
                    repetir = repetir,
                    onCerrar = { reproductorAbierto = false },
                    onLetra = {
                        reproductorAbierto = false
                        mostrarLetra = true
                    },
                    onRecortar = {
                        reproductorAbierto = false
                        recortando = true
                    }
                )
            }
        }
    }
}