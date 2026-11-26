package com.zajeapps.musicplayer

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Todo lo que cambiarás cuando tengas tu cuenta de AdMob está aquí
object ConfigAnuncios {
    // Banner de prueba oficial de Google. Válido siempre, nunca genera ingresos.
    const val BANNER_PRUEBA = "ca-app-pub-3940256099942544/9214589741"

    // TU unidad de anuncio real de AdMob (formato ca-app-pub-XXXX/YYYY).
    // Mientras esté vacía, la versión final NO muestra anuncios (es lo más seguro).
    const val BANNER_REAL = ""

    // En la versión de pruebas, salta el consentimiento y pide anuncios directamente.
    // Ponlo en false cuando quieras probar el formulario de consentimiento con tu cuenta.
    // En la versión final se ignora siempre.
    const val SALTAR_CONSENTIMIENTO_EN_DEBUG = true

    // Solo para probar el formulario de consentimiento (se ignora en la versión final)
    const val FORZAR_EEA_EN_DEBUG = false
    const val DISPOSITIVO_PRUEBA_UMP = "" // ID que aparece en Logcat
}

object Anuncios {
    const val TAG = "ANUNCIOS"
    private const val PREFS = "anuncios"

    // true = el usuario no ve anuncios (compra, o simulación en pruebas)
    var sinAnuncios by mutableStateOf(false)
        private set

    // true = en tu región hay que ofrecer un botón de "opciones de privacidad"
    var opcionesPrivacidad by mutableStateOf(false)
        private set

    private var consentido by mutableStateOf(false)
    private var sdkListo by mutableStateOf(false)
    private var iniciando = false
    private var sdkIniciado = false

    // Los banners solo se muestran si se cumplen las tres condiciones
    val mostrar: Boolean
        get() = !sinAnuncios && consentido && sdkListo

    fun esDebug(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    // En desarrollo siempre el de prueba; en la versión final, el tuyo
    fun unidadBanner(context: Context): String =
        if (esDebug(context)) ConfigAnuncios.BANNER_PRUEBA else ConfigAnuncios.BANNER_REAL

    fun cargar(context: Context) {
        sinAnuncios = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("sin_anuncios", false)
    }

    fun ponerSinAnuncios(activity: Activity, valor: Boolean) {
        sinAnuncios = valor
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("sin_anuncios", valor).apply()
        if (!valor) iniciar(activity)
    }

    // Se llama al abrir la app: pide el consentimiento si hace falta y luego inicia el SDK
    fun iniciar(activity: Activity) {
        if (sinAnuncios) {
            Log.d(TAG, "Usuario sin anuncios: no se inicia nada")
            return
        }

        // Atajo solo para pruebas con anuncios de prueba
        if (esDebug(activity) && ConfigAnuncios.SALTAR_CONSENTIMIENTO_EN_DEBUG) {
            Log.d(TAG, "Debug: consentimiento omitido, se inicia el SDK directamente")
            consentido = true
            iniciarSdk(activity)
            return
        }

        if (iniciando) return
        iniciando = true

        val info = UserMessagingPlatform.getConsentInformation(activity)

        val parametros = ConsentRequestParameters.Builder().apply {
            if (esDebug(activity) &&
                (ConfigAnuncios.FORZAR_EEA_EN_DEBUG || ConfigAnuncios.DISPOSITIVO_PRUEBA_UMP.isNotEmpty())
            ) {
                val depuracion = ConsentDebugSettings.Builder(activity)
                if (ConfigAnuncios.FORZAR_EEA_EN_DEBUG) {
                    depuracion.setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                }
                if (ConfigAnuncios.DISPOSITIVO_PRUEBA_UMP.isNotEmpty()) {
                    depuracion.addTestDeviceHashedId(ConfigAnuncios.DISPOSITIVO_PRUEBA_UMP)
                }
                setConsentDebugSettings(depuracion.build())
            }
        }.build()

        info.requestConsentInfoUpdate(
            activity,
            parametros,
            {
                Log.d(TAG, "Consentimiento: información actualizada")
                // Muestra el formulario solo si la ley de tu región lo exige
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Formulario de consentimiento: ${formError.errorCode} - ${formError.message}")
                    }
                    iniciando = false
                    actualizar(activity, info)
                }
            },
            { error ->
                // Sin conexión o error: se usa lo que el usuario eligió en sesiones anteriores
                Log.w(TAG, "Error al pedir consentimiento: ${error.errorCode} - ${error.message}")
                iniciando = false
                actualizar(activity, info)
            }
        )

        // Quien ya dio su consentimiento antes no tiene que esperar a la red
        actualizar(activity, info)
    }

    private fun actualizar(context: Context, info: ConsentInformation) {
        opcionesPrivacidad = info.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        consentido = info.canRequestAds()
        Log.d(
            TAG,
            "Consentimiento: estado=${info.consentStatus} (0=desconocido, 1=no requerido, 2=requerido, 3=obtenido) " +
                    "canRequestAds=$consentido opcionesPrivacidad=$opcionesPrivacidad"
        )
        if (consentido) iniciarSdk(context)
    }

    private fun iniciarSdk(context: Context) {
        if (sdkIniciado) return
        sdkIniciado = true
        val app = context.applicationContext
        // La inicialización puede tardar: se hace fuera del hilo principal
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(app) {
                Log.d(TAG, "SDK de anuncios inicializado")
                sdkListo = true
            }
        }
    }

    // Botón "Privacidad de anuncios" de Ajustes: permite cambiar la elección
    fun mostrarOpcionesPrivacidad(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            actualizar(activity, UserMessagingPlatform.getConsentInformation(activity))
        }
    }
}