package com.example.reproductormusica

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import com.example.reproductormusica.ui.theme.ReproductorMusicaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReproductorMusicaTheme {
                AppMusica()
            }
        }
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

    // Si cambias de pestaña, se cierra la pantalla de letra
    LaunchedEffect(pestana) { mostrarLetra = false }

    var tienePermiso by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permisoAudio) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var canciones by remember { mutableStateOf<List<Song>>(emptyList()) }
    var cargado by remember { mutableStateOf(false) }

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

    LaunchedEffect(tienePermiso) {
        if (tienePermiso) {
            canciones = withContext(Dispatchers.IO) { cargarCanciones(context) }
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (tienePermiso) {
                Column {
                    actual?.let { cancion ->
                        BarraReproduccion(
                            controller = controller,
                            cancion = cancion,
                            sonando = sonando,
                            aleatorio = aleatorio,
                            repetir = repetir,
                            onLetra = { mostrarLetra = true }
                        )
                    }
                    NavigationBar {
                        NavigationBarItem(
                            selected = pestana == 0,
                            onClick = { pestana = 0 },
                            icon = { Text("♫") },
                            label = { Text("Canciones") }
                        )
                        NavigationBarItem(
                            selected = pestana == 1,
                            onClick = { pestana = 1 },
                            icon = { Text("☰") },
                            label = { Text("Listas") }
                        )
                        NavigationBarItem(
                            selected = pestana == 2,
                            onClick = { pestana = 2 },
                            icon = { Text("≋") },
                            label = { Text("Sonido") }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
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
                    onAgregarALista = { cancion, listaId ->
                        scope.launch { dao.agregarCancion(listaId, cancion.id) }
                    }
                )
            } else if (pestana == 1) {
                PantallaListas(
                    dao = dao,
                    listas = listas,
                    canciones = canciones,
                    onReproducir = alReproducir
                )
            } else {
                PantallaEcualizador()
            }
        }
    }
}