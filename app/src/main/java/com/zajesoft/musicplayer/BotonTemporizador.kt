package com.zajesoft.musicplayer

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

@Composable
fun BotonTemporizador() {
    val estado by Temporizador.estado.collectAsState()
    var dialogo by remember { mutableStateOf(false) }
    var ahora by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    val lifecycle = (LocalContext.current as ComponentActivity).lifecycle

    // El reloj de la cuenta atrás corre solo con un temporizador activo y la app visible
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
            "Apagado en ${formatearDuracion((estado.finMs - ahora).coerceAtLeast(0L))}"
        estado.alFinalDeCancion -> "Apagado al terminar la canción"
        else -> "Temporizador"
    }

    TextButton(onClick = { dialogo = true }) { Text(texto) }

    if (dialogo) {
        var personalizado by remember { mutableStateOf("") }
        val minutosPersonalizados = personalizado.toIntOrNull()

        AlertDialog(
            onDismissRequest = { dialogo = false },
            title = { Text("Temporizador de apagado") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    listOf(15, 30, 60).forEach { minutos ->
                        TextButton(
                            onClick = {
                                Temporizador.iniciarEnMinutos(minutos)
                                dialogo = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("$minutos minutos") }
                    }
                    TextButton(
                        onClick = {
                            Temporizador.iniciarAlFinalDeCancion()
                            dialogo = false
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
                                dialogo = false
                            }
                        },
                        enabled = minutosPersonalizados != null && minutosPersonalizados > 0,
                        modifier = Modifier.padding(top = 8.dp)
                    ) { Text("Iniciar") }

                    if (estado.activo) {
                        TextButton(
                            onClick = {
                                Temporizador.cancelar()
                                dialogo = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Cancelar temporizador") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { dialogo = false }) { Text("Cerrar") }
            }
        )
    }
}