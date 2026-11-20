package com.example.reproductormusica

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ModoTema(val etiqueta: String) {
    SISTEMA("Seguir al sistema"),
    CLARO("Claro"),
    OSCURO("Oscuro"),
    AMOLED("AMOLED (negro puro)")
}

// Ajustes de la app. Al ser estado de Compose, la pantalla se actualiza sola al cambiarlos.
object Ajustes {
    private const val PREFS = "ajustes"

    var modoTema by mutableStateOf(ModoTema.AMOLED)
        private set

    var colorDinamico by mutableStateOf(false)
        private set

    // 0 = no ignorar nada
    var duracionMinimaSeg by mutableStateOf(30)
        private set

    // Se llama una vez al abrir la app
    fun cargar(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        modoTema = try {
            ModoTema.valueOf(p.getString("tema", null) ?: ModoTema.AMOLED.name)
        } catch (e: IllegalArgumentException) {
            ModoTema.AMOLED
        }
        colorDinamico = p.getBoolean("dinamico", false)
        duracionMinimaSeg = p.getInt("duracion_min", 30)
    }

    fun ponerModoTema(context: Context, modo: ModoTema) {
        modoTema = modo
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("tema", modo.name).apply()
    }

    fun ponerColorDinamico(context: Context, valor: Boolean) {
        colorDinamico = valor
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("dinamico", valor).apply()
    }

    fun ponerDuracionMinima(context: Context, segundos: Int) {
        duracionMinimaSeg = segundos
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt("duracion_min", segundos).apply()
    }
}