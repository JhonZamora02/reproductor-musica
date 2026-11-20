package com.zajesoft.musicplayer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private fun etiquetaFrecuencia(hz: Int): String =
    if (hz >= 1000) "%.1f kHz".format(hz / 1000f) else "$hz Hz"

private fun etiquetaDb(mb: Int): String = "%+.1f dB".format(mb / 100f)

@Composable
fun PantallaEcualizador() {
    val estado by Ecualizador.estado.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Sonido", style = MaterialTheme.typography.titleMedium)

        if (!estado.disponible) {
            Text(
                "Este dispositivo no ofrece ecualizador para esta reproducción.",
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            // Interruptor general
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Activar ecualizador")
                Switch(checked = estado.activado, onCheckedChange = { Ecualizador.activar(it) })
            }

            // Presets
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Ecualizador.presets.keys.forEach { nombre ->
                    FilterChip(
                        selected = estado.preset == nombre,
                        onClick = { Ecualizador.aplicarPreset(nombre) },
                        label = { Text(nombre) },
                        enabled = estado.activado
                    )
                }
            }

            // Una barra por cada banda real del dispositivo
            estado.bandas.forEachIndexed { indice, banda ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(etiquetaFrecuencia(banda.frecuenciaHz), style = MaterialTheme.typography.bodyMedium)
                    Text(etiquetaDb(banda.nivelMb), style = MaterialTheme.typography.bodySmall)
                }
                Slider(
                    value = banda.nivelMb.toFloat(),
                    onValueChange = { Ecualizador.ponerBanda(indice, it.roundToInt()) },
                    onValueChangeFinished = { Ecualizador.guardar() },
                    valueRange = estado.minMb.toFloat()..estado.maxMb.toFloat(),
                    enabled = estado.activado
                )
            }

            if (estado.gravesDisponible) {
                Text(
                    "Refuerzo de graves: ${estado.graves / 10}%",
                    modifier = Modifier.padding(top = 16.dp)
                )
                Slider(
                    value = estado.graves.toFloat(),
                    onValueChange = { Ecualizador.ponerGraves(it.roundToInt()) },
                    onValueChangeFinished = { Ecualizador.guardar() },
                    valueRange = 0f..1000f,
                    enabled = estado.activado
                )
            }

            if (estado.volumenDisponible) {
                Text(
                    "Volumen extra: +${"%.1f".format(estado.volumenExtra / 100f)} dB",
                    modifier = Modifier.padding(top = 8.dp)
                )
                Slider(
                    value = estado.volumenExtra.toFloat(),
                    onValueChange = { Ecualizador.ponerVolumenExtra(it.roundToInt()) },
                    onValueChangeFinished = { Ecualizador.guardar() },
                    valueRange = 0f..600f,
                    enabled = estado.activado
                )
                Text(
                    "Con valores altos el sonido puede distorsionarse.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            TextButton(
                onClick = { Ecualizador.restablecer() },
                modifier = Modifier.padding(top = 8.dp)
            ) { Text("Restablecer") }
        }
    }
}