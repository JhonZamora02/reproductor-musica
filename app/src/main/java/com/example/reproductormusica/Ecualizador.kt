package com.example.reproductormusica

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

// Una banda del ecualizador: su frecuencia central y su nivel en milibelios (100 mB = 1 dB)
data class Banda(val frecuenciaHz: Int, val nivelMb: Int)

data class EstadoEcualizador(
    val disponible: Boolean = false,
    val activado: Boolean = false,
    val minMb: Int = -1500,
    val maxMb: Int = 1500,
    val bandas: List<Banda> = emptyList(),
    val preset: String = Ecualizador.PERSONALIZADO,
    val gravesDisponible: Boolean = false,
    val graves: Int = 0,          // 0..1000
    val volumenDisponible: Boolean = false,
    val volumenExtra: Int = 0     // 0..600 mB (hasta +6 dB)
)

object Ecualizador {
    const val PERSONALIZADO = "Personalizado"
    private const val PREFS = "ecualizador"

    // Cada preset son 5 valores en dB (de graves a agudos).
    // Se adaptan al número real de bandas del dispositivo.
    val presets: Map<String, List<Int>> = linkedMapOf(
        "Plano" to listOf(0, 0, 0, 0, 0),
        "Graves" to listOf(6, 4, 0, 0, 0),
        "Agudos" to listOf(0, 0, 0, 4, 6),
        "Rock" to listOf(5, 3, -1, 3, 5),
        "Pop" to listOf(-2, 2, 4, 2, -2),
        "Vocal" to listOf(-3, 0, 4, 3, 0),
        "Clásica" to listOf(3, 2, -1, 2, 3)
    )

    private val _estado = MutableStateFlow(EstadoEcualizador())
    val estado: StateFlow<EstadoEcualizador> = _estado.asStateFlow()

    private var contexto: Context? = null
    private var player: ExoPlayer? = null
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var loud: LoudnessEnhancer? = null

    // Si Android cambia el ID de sesión de audio, hay que recrear los efectos
    private val listener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            crearEfectos(audioSessionId)
        }
    }

    // Lo llama el servicio al crearse
    fun vincular(context: Context, p: ExoPlayer) {
        desvincular()
        contexto = context.applicationContext
        player = p
        p.addListener(listener)
        if (p.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            crearEfectos(p.audioSessionId)
        }
    }

    // Lo llama el servicio al destruirse
    fun desvincular() {
        player?.removeListener(listener)
        player = null
        liberarEfectos()
        contexto = null
        _estado.value = EstadoEcualizador()
    }

    private fun seguro(bloque: () -> Unit) {
        try {
            bloque()
        } catch (e: Exception) {
            // Algunos dispositivos rechazan ciertos ajustes: lo ignoramos
        }
    }

    private fun liberarEfectos() {
        seguro { eq?.release() }
        seguro { bass?.release() }
        seguro { loud?.release() }
        eq = null
        bass = null
        loud = null
    }

    private fun crearEfectos(sessionId: Int) {
        liberarEfectos()
        val ctx = contexto ?: return
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val activado = prefs.getBoolean("activado", false)
        val graves = prefs.getInt("graves", 0)
        val volumen = prefs.getInt("volumen", 0)
        val preset = prefs.getString("preset", PERSONALIZADO) ?: PERSONALIZADO

        // --- Ecualizador (si falla, no hay nada que mostrar) ---
        val nuevoEq = try { Equalizer(0, sessionId) } catch (e: Exception) { null }
        if (nuevoEq == null) {
            _estado.value = EstadoEcualizador()
            return
        }

        val bandas: List<Banda>
        val minMb: Int
        val maxMb: Int
        try {
            val n = nuevoEq.numberOfBands.toInt()
            val rango = nuevoEq.bandLevelRange
            minMb = rango[0].toInt()
            maxMb = rango[1].toInt()

            val guardados = prefs.getString("bandas", null)
                ?.split(",")?.mapNotNull { it.toIntOrNull() }
            val niveles = if (guardados != null && guardados.size == n) guardados else List(n) { 0 }

            bandas = List(n) { i ->
                val nivel = niveles[i].coerceIn(minMb, maxMb)
                nuevoEq.setBandLevel(i.toShort(), nivel.toShort())
                Banda(nuevoEq.getCenterFreq(i.toShort()) / 1000, nivel)
            }
            nuevoEq.setEnabled(activado)
        } catch (e: Exception) {
            seguro { nuevoEq.release() }
            _estado.value = EstadoEcualizador()
            return
        }
        eq = nuevoEq

        // --- Refuerzo de graves (opcional) ---
        val nuevoBass = try { BassBoost(0, sessionId) } catch (e: Exception) { null }
        val gravesOk = nuevoBass != null && nuevoBass.strengthSupported
        if (nuevoBass != null) {
            if (gravesOk) {
                seguro {
                    nuevoBass.setStrength(graves.toShort())
                    nuevoBass.setEnabled(activado)
                }
                bass = nuevoBass
            } else {
                seguro { nuevoBass.release() }
            }
        }

        // --- Volumen extra (opcional) ---
        val nuevoLoud = try { LoudnessEnhancer(sessionId) } catch (e: Exception) { null }
        if (nuevoLoud != null) {
            seguro {
                nuevoLoud.setTargetGain(volumen)
                nuevoLoud.setEnabled(activado)
            }
            loud = nuevoLoud
        }

        _estado.value = EstadoEcualizador(
            disponible = true,
            activado = activado,
            minMb = minMb,
            maxMb = maxMb,
            bandas = bandas,
            preset = preset,
            gravesDisponible = gravesOk,
            graves = if (gravesOk) graves else 0,
            volumenDisponible = nuevoLoud != null,
            volumenExtra = if (nuevoLoud != null) volumen else 0
        )
    }

    // ---------- Acciones que usa la pantalla ----------

    fun activar(valor: Boolean) {
        seguro { eq?.setEnabled(valor) }
        seguro { bass?.setEnabled(valor) }
        seguro { loud?.setEnabled(valor) }
        _estado.update { it.copy(activado = valor) }
        guardar()
    }

    // Se llama mientras se arrastra: aplica el cambio en vivo, sin guardar todavía
    fun ponerBanda(indice: Int, nivelMb: Int) {
        val e = _estado.value
        if (indice !in e.bandas.indices) return
        val nivel = nivelMb.coerceIn(e.minMb, e.maxMb)
        seguro { eq?.setBandLevel(indice.toShort(), nivel.toShort()) }
        _estado.update { s ->
            s.copy(
                preset = PERSONALIZADO,
                bandas = s.bandas.mapIndexed { i, b ->
                    if (i == indice) b.copy(nivelMb = nivel) else b
                }
            )
        }
    }

    fun aplicarPreset(nombre: String) {
        val curva = presets[nombre] ?: return
        val e = _estado.value
        val n = e.bandas.size
        if (n == 0) return

        val niveles = List(n) { i ->
            val j = if (n == 1) 0 else (i * 4f / (n - 1)).roundToInt()
            (curva[j] * 100).coerceIn(e.minMb, e.maxMb)
        }
        niveles.forEachIndexed { i, nivel ->
            seguro { eq?.setBandLevel(i.toShort(), nivel.toShort()) }
        }
        _estado.update { s ->
            s.copy(
                preset = nombre,
                bandas = s.bandas.mapIndexed { i, b -> b.copy(nivelMb = niveles[i]) }
            )
        }
        guardar()
    }

    fun ponerGraves(valor: Int) {
        val v = valor.coerceIn(0, 1000)
        seguro { bass?.setStrength(v.toShort()) }
        _estado.update { it.copy(graves = v) }
    }

    fun ponerVolumenExtra(valor: Int) {
        val v = valor.coerceIn(0, 600)
        seguro { loud?.setTargetGain(v) }
        _estado.update { it.copy(volumenExtra = v) }
    }

    fun restablecer() {
        aplicarPreset("Plano")
        ponerGraves(0)
        ponerVolumenExtra(0)
        guardar()
    }

    // Se llama al soltar un control
    fun guardar() {
        val ctx = contexto ?: return
        val e = _estado.value
        if (!e.disponible) return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("activado", e.activado)
            .putString("preset", e.preset)
            .putString("bandas", e.bandas.joinToString(",") { it.nivelMb.toString() })
            .putInt("graves", e.graves)
            .putInt("volumen", e.volumenExtra)
            .apply()
    }
}