package com.zajeapps.musicplayer

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

// Texto pequeño con la cuenta atrás. Si no hay temporizador activo, no muestra nada.
@Composable
fun TextoTemporizador(modifier: Modifier = Modifier) {
    val estado by Temporizador.estado.collectAsState()
    var ahora by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    val lifecycle = (LocalContext.current as ComponentActivity).lifecycle

    // El reloj corre solo con un temporizador activo y la app visible
    LaunchedEffect(estado.finMs) {
        if (estado.finMs == 0L) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                ahora = SystemClock.elapsedRealtime()
                delay(1000)
            }
        }
    }

    val texto = when {
        estado.finMs > 0L ->
            "Se apagará en ${formatearDuracion((estado.finMs - ahora).coerceAtLeast(0L))}"
        estado.alFinalDeCancion -> "Se apagará al terminar la canción"
        else -> null
    }
    if (texto != null) {
        Text(
            texto,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier
        )
    }
}

@Composable
fun DialogoTemporizador(onCerrar: () -> Unit) {
    val estado by Temporizador.estado.collectAsState()
    var personalizado by remember { mutableStateOf("") }
    val minutosPersonalizados = personalizado.toIntOrNull()

    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Temporizador de apagado") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                listOf(15, 30, 60).forEach { minutos ->
                    TextButton(
                        onClick = {
                            Temporizador.iniciarEnMinutos(minutos)
                            onCerrar()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("$minutos minutos") }
                }
                TextButton(
                    onClick = {
                        Temporizador.iniciarAlFinalDeCancion()
                        onCerrar()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Al terminar la canción") }

                OutlinedTextField(
                    value = personalizado,
                    onValueChange = { nuevo ->
                        if (nuevo.length <= 3 && nuevo.all { it.isDigit() }) {
                            personalizado = nuevo
                        }
                    },
                    singleLine = true,
                    label = { Text("Minutos (personalizado)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.padding(top = 8.dp)
                )
                Button(
                    onClick = {
                        if (minutosPersonalizados != null && minutosPersonalizados > 0) {
                            Temporizador.iniciarEnMinutos(minutosPersonalizados)
                            onCerrar()
                        }
                    },
                    enabled = minutosPersonalizados != null && minutosPersonalizados > 0,
                    modifier = Modifier.padding(top = 8.dp)
                ) { Text("Iniciar") }

                if (estado.activo) {
                    TextButton(
                        onClick = {
                            Temporizador.cancelar()
                            onCerrar()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Cancelar temporizador") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } }
    )
}