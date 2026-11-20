package com.zajesoft.musicplayer.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.zajesoft.musicplayer.ModoTema

// Paleta inspirada en el logo: azul cian y violeta
private val EsquemaClaro = lightColorScheme(
    primary = Color(0xFF00629B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE5FF),
    onPrimaryContainer = Color(0xFF001D33),
    secondary = Color(0xFF6A3FD0),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9DDFF),
    onSecondaryContainer = Color(0xFF220060),
    background = Color(0xFFFAF9FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFAF9FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F)
)

private val EsquemaOscuro = darkColorScheme(
    primary = Color(0xFF5AC8FF),
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF004B70),
    onPrimaryContainer = Color(0xFFCDE5FF),
    secondary = Color(0xFFC3A8FF),
    onSecondary = Color(0xFF38128F),
    secondaryContainer = Color(0xFF4F2AA6),
    onSecondaryContainer = Color(0xFFE9DDFF),
    background = Color(0xFF0D0E16),
    onBackground = Color(0xFFE3E2EA),
    surface = Color(0xFF0D0E16),
    onSurface = Color(0xFFE3E2EA),
    surfaceVariant = Color(0xFF2A2C38),
    onSurfaceVariant = Color(0xFFC5C6D0),
    outline = Color(0xFF8F909A),
    surfaceContainerLowest = Color(0xFF080910),
    surfaceContainerLow = Color(0xFF141520),
    surfaceContainer = Color(0xFF191A25),
    surfaceContainerHigh = Color(0xFF232430),
    surfaceContainerHighest = Color(0xFF2E2F3B)
)

// Convierte un esquema oscuro en AMOLED: fondos negros puros
private fun aAmoled(base: ColorScheme): ColorScheme = base.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF16171F),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF07070B),
    surfaceContainer = Color(0xFF0C0C12),
    surfaceContainerHigh = Color(0xFF12121A),
    surfaceContainerHighest = Color(0xFF181821)
)

@Composable
fun ReproductorMusicaTheme(
    modo: ModoTema = ModoTema.SISTEMA,
    dinamico: Boolean = false,
    content: @Composable () -> Unit
) {
    val contexto = LocalContext.current
    val sistemaOscuro = isSystemInDarkTheme()

    val oscuro = when (modo) {
        ModoTema.SISTEMA -> sistemaOscuro
        ModoTema.CLARO -> false
        ModoTema.OSCURO, ModoTema.AMOLED -> true
    }

    val base = when {
        dinamico && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (oscuro) dynamicDarkColorScheme(contexto) else dynamicLightColorScheme(contexto)
        oscuro -> EsquemaOscuro
        else -> EsquemaClaro
    }
    val esquema = if (modo == ModoTema.AMOLED) aAmoled(base) else base

    // Los iconos de la barra de estado deben leerse sobre el tema elegido,
    // aunque sea distinto del tema del sistema
    val actividad = contexto as? ComponentActivity
    DisposableEffect(oscuro) {
        val transparente = android.graphics.Color.TRANSPARENT
        val estilo = if (oscuro) {
            SystemBarStyle.dark(transparente)
        } else {
            SystemBarStyle.light(transparente, transparente)
        }
        actividad?.enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
        onDispose { }
    }

    MaterialTheme(colorScheme = esquema, content = content)
}