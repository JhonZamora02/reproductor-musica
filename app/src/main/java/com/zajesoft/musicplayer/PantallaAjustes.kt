package com.zajesoft.musicplayer

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
private fun Seccion(titulo: String) {
    Text(
        titulo,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun FilaOpcion(texto: String, seleccionada: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = seleccionada, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = seleccionada, onClick = null)
        Text(texto, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
fun PantallaAjustes() {
    val context = LocalContext.current

    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Ajustes", style = MaterialTheme.typography.titleMedium)

        Seccion("Tema")
        ModoTema.entries.forEach { modo ->
            FilaOpcion(
                texto = modo.etiqueta,
                seleccionada = Ajustes.modoTema == modo,
                onClick = { Ajustes.ponerModoTema(context, modo) }
            )
        }

        // Los colores dinámicos solo existen desde Android 12
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Colores del sistema")
                    Text(
                        "Usa los colores de tu fondo de pantalla",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = Ajustes.colorDinamico,
                    onCheckedChange = { Ajustes.ponerColorDinamico(context, it) }
                )
            }
        }

        Seccion("Biblioteca")
        Text(
            "Ignorar audios cortos (tonos, notas de voz)",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        listOf(
            0 to "No ignorar ninguno",
            30 to "Menos de 30 segundos",
            60 to "Menos de 60 segundos"
        ).forEach { (segundos, etiqueta) ->
            FilaOpcion(
                texto = etiqueta,
                seleccionada = Ajustes.duracionMinimaSeg == segundos,
                onClick = { Ajustes.ponerDuracionMinima(context, segundos) }
            )
        }

        Seccion("Acerca de")
        Text("Reproductor de música offline")
        Text("ZAJE Soft", style = MaterialTheme.typography.bodySmall)
        if (version.isNotEmpty()) {
            Text("Versión $version", style = MaterialTheme.typography.bodySmall)
        }
    }
}