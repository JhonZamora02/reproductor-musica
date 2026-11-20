package com.zajesoft.musicplayer


import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private fun icono(nombre: String, ruta: String): ImageVector =
    ImageVector.Builder(
        name = nombre,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = addPathNodes(ruta),
        fill = SolidColor(Color.Black)
    ).build()

// Iconos de Material Design como rutas vectoriales (ligeros y sin dependencias)
object Iconos {
    val Play by lazy { icono("Play", "M8,5v14l11,-7z") }
    val Pausa by lazy { icono("Pausa", "M6,19h4V5H6v14zm8,-14v14h4V5h-4z") }
    val Siguiente by lazy { icono("Siguiente", "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z") }
    val Anterior by lazy { icono("Anterior", "M6,6h2v12H6z M9.5,12L18,18V6z") }
    val Aleatorio by lazy {
        icono(
            "Aleatorio",
            "M10.59,9.17L5.41,4 4,5.41l5.17,5.17 1.42,-1.41zM14.5,4l2.04,2.04L4,18.59 5.41,20 17.96,7.46 20,9.5V4h-5.5zM14.83,13.41l-1.41,1.41 3.13,3.13L14.5,20H20v-5.5l-2.04,2.04 -3.13,-3.13z"
        )
    }
    val Repetir by lazy {
        icono("Repetir", "M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zM17,17H7v-3l-4,4 4,4v-3h12v-6h-2v4z")
    }
    val RepetirUna by lazy {
        icono(
            "RepetirUna",
            "M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zM17,17H7v-3l-4,4 4,4v-3h12v-6h-2v4zM13,15V9h-1l-2,1v1h1.5v4H13z"
        )
    }
    val Nota by lazy {
        icono("Nota", "M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z")
    }
    val Lista by lazy {
        icono("Lista", "M3,13h2v-2H3v2zM3,17h2v-2H3v2zM3,9h2V7H3v2zM7,13h14v-2H7v2zM7,17h14v-2H7v2zM7,7v2h14V7H7z")
    }
    val Ecualizador by lazy { icono("Ecualizador", "M10,20h4V4h-4v16zM4,20h4v-8H4v8zM16,9v11h4V9h-4z") }
    val Buscar by lazy {
        icono(
            "Buscar",
            "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z"
        )
    }
    val Cerrar by lazy {
        icono("Cerrar", "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z")
    }
    val Actualizar by lazy {
        icono(
            "Actualizar",
            "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z"
        )
    }
    val Agregar by lazy { icono("Agregar", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z") }

    val Opciones by lazy {
        icono(
            "Opciones",
            "M3,17v2h6v-2H3zM3,5v2h10V5H3zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2H3v2h4v2h2V9H7zM21,13v-2H11v2h10zM15,9h2V7h4V5h-4V3h-2v6z"
        )
    }
    val Subir by lazy { icono("Subir", "M7.41,15.41L12,10.83l4.59,4.58L18,14l-6,-6 -6,6z") }
    val Bajar by lazy { icono("Bajar", "M7.41,8.59L12,13.17l4.59,-4.58L18,10l-6,6 -6,-6 1.41,-1.41z") }
}