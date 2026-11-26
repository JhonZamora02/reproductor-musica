package com.zajeapps.musicplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Letra inicial de un título, sin tildes: "Ángel" -> "A". Símbolos y números -> "#"
fun letraDe(titulo: String): String {
    val c = normalizar(titulo.trimStart()).firstOrNull() ?: return "#"
    return if (c in 'a'..'z') c.uppercaseChar().toString() else "#"
}

// Deslizador rápido: se superpone a una lista (póngalo dentro de un Box, encima de la lista)
@Composable
fun BarraRapida(
    listState: LazyListState,
    total: Int,
    etiqueta: (Int) -> String,
    modifier: Modifier = Modifier
) {
    // Con pocas canciones no hace falta
    if (total >= 30) {
        BarraRapidaInterna(listState, total, etiqueta, modifier)
    }
}

@Composable
private fun BarraRapidaInterna(
    listState: LazyListState,
    total: Int,
    etiqueta: (Int) -> String,
    modifier: Modifier
) {
    val scope = rememberCoroutineScope()
    val densidad = LocalDensity.current

    var arrastrando by remember { mutableStateOf(false) }
    var fraccion by remember { mutableStateOf(0f) } // 0..1 mientras se arrastra
    var visible by remember { mutableStateOf(false) }

    // Aparece al desplazarse y se oculta 1,5 s después de parar
    val enMovimiento = listState.isScrollInProgress
    LaunchedEffect(enMovimiento, arrastrando) {
        if (enMovimiento || arrastrando) {
            visible = true
        } else {
            delay(1500)
            visible = false
        }
    }

    val totalActual by rememberUpdatedState(total)
    val fraccionLista by remember(listState) {
        derivedStateOf {
            val visibles = listState.layoutInfo.visibleItemsInfo.size
            val maximo = (totalActual - visibles).coerceAtLeast(1)
            (listState.firstVisibleItemIndex.toFloat() / maximo).coerceIn(0f, 1f)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val altoPista = with(densidad) { maxHeight.toPx() }
        val altoPulgar = with(densidad) { 56.dp.toPx() }
        val recorrido = (altoPista - altoPulgar).coerceAtLeast(1f)
        val posicion = (if (arrastrando) fraccion else fraccionLista) * recorrido

        if (visible || arrastrando) {

            // Burbuja con la letra, solo mientras se arrastra
            if (arrastrando) {
                val indice = (fraccion * (total - 1)).roundToInt().coerceIn(0, total - 1)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset {
                            IntOffset(
                                (-64).dp.roundToPx(),
                                (posicion + altoPulgar / 2 - 28.dp.toPx()).roundToInt()
                            )
                        }
                        .size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            etiqueta(indice),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Agarrador
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, posicion.roundToInt()) }
                    .width(36.dp)
                    .height(56.dp)
                    .pointerInput(recorrido, total) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                arrastrando = true
                                fraccion = fraccionLista
                            },
                            onDragEnd = { arrastrando = false },
                            onDragCancel = { arrastrando = false },
                            onVerticalDrag = { cambio, dy ->
                                cambio.consume()
                                fraccion = (fraccion + dy / recorrido).coerceIn(0f, 1f)
                                val i = (fraccion * (total - 1)).roundToInt()
                                scope.launch { listState.scrollToItem(i) }
                            }
                        )
                    }
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                        .width(if (arrastrando) 10.dp else 6.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                )
            }
        }
    }
}

