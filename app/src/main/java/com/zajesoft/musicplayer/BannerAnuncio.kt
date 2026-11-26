package com.zajesoft.musicplayer

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

@Composable
fun BannerAnuncio(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val unidad = Anuncios.unidadBanner(context)

    LaunchedEffect(Anuncios.mostrar, unidad) {
        Log.d(Anuncios.TAG, "Banner: mostrar=${Anuncios.mostrar} unidad='$unidad'")
    }

    if (Anuncios.mostrar && unidad.isNotBlank()) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface) // fondo opaco, lo exige AdMob
        ) {
            val anchoDp = maxWidth.value.toInt()
            val cargado = remember(anchoDp) { mutableStateOf(false) }

            val adView = remember(anchoDp) {
                AdView(context).apply {
                    adUnitId = unidad
                    setAdSize(
                        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, anchoDp)
                    )
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            Log.d(Anuncios.TAG, "Banner cargado")
                            cargado.value = true
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            Log.w(Anuncios.TAG, "Banner falló: código ${error.code} - ${error.message}")
                            cargado.value = false
                        }
                    }
                    Log.d(Anuncios.TAG, "Banner: pidiendo anuncio (ancho ${anchoDp}dp)")
                    loadAd(AdRequest.Builder().build())
                }
            }

            // Pausa el banner con la app en segundo plano y lo destruye al salir
            val lifecycle = (context as ComponentActivity).lifecycle
            DisposableEffect(adView) {
                val observador = LifecycleEventObserver { _, evento ->
                    when (evento) {
                        Lifecycle.Event.ON_PAUSE -> adView.pause()
                        Lifecycle.Event.ON_RESUME -> adView.resume()
                        else -> {}
                    }
                }
                lifecycle.addObserver(observador)
                onDispose {
                    lifecycle.removeObserver(observador)
                    adView.destroy()
                }
            }

            // El banner se mide con su tamaño normal, pero sin anuncio cargado
            // (por ejemplo, sin internet) no ocupa espacio en la pantalla
            AndroidView(
                factory = { adView },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (cargado.value) 6.dp else 0.dp)
                    .clipToBounds()
                    .layout { medible, restricciones ->
                        val colocable = medible.measure(restricciones)
                        val alto = if (cargado.value) colocable.height else 0
                        layout(colocable.width, alto) { colocable.place(0, 0) }
                    }
            )
        }
    }
}