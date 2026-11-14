package com.marco.mlauncher

import android.graphics.ComposeShader
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import kotlin.math.min

/*
 * Forme delle icone in stile Material 3 Expressive (biscotto, quadrifoglio, fiore, sole…),
 * come nella schermata dei temi di Expressive Launcher (github.com/kotlalipopus/expressive-launcher, licenza MIT).
 * Sono poligoni arrotondati (androidx.graphics.shapes) portati in un quadrato 1×1 una volta sola.
 */

/** Forme Material Expressive per le icone: chiave → nome. */
val EXPRESSIVE_SHAPES = listOf(
    "cookie9" to "Biscotto",
    "cookie12" to "Biscotto 12",
    "clover" to "Quadrifoglio",
    "flower" to "Fiore",
    "sunny" to "Sole",
    "puffy" to "Nuvola",
    "pentagon" to "Pentagono",
    "gem" to "Gemma",
    "squircle" to "Squircle"
)

/** Tutte le forme delle icone, quelle classiche prima. */
val ICON_SHAPES = listOf(
    "original" to "Predefinita (come sono sempre state)",
    "ios" to "iOS (squircola)",
    "circle" to "Cerchio",
    "square" to "Quadrato arrotondato"
) + EXPRESSIVE_SHAPES

fun isExpressiveShape(key: String) = EXPRESSIVE_SHAPES.any { it.first == key }

private fun polygonOf(key: String): Pair<RoundedPolygon, Float>? = when (key) {
    "cookie9" -> RoundedPolygon.star(9, innerRadius = 0.8f, rounding = CornerRounding(0.5f)) to 0f
    "cookie12" -> RoundedPolygon.star(12, innerRadius = 0.8f, rounding = CornerRounding(0.5f)) to 0f
    "clover" -> RoundedPolygon.star(4, innerRadius = 0.45f, rounding = CornerRounding(0.9f), innerRounding = CornerRounding(0.15f)) to 45f
    "flower" -> RoundedPolygon.star(8, innerRadius = 0.68f, rounding = CornerRounding(0.4f), innerRounding = CornerRounding(0.12f)) to 0f
    "sunny" -> RoundedPolygon.star(8, innerRadius = 0.82f, rounding = CornerRounding(0.15f)) to 0f
    "puffy" -> RoundedPolygon.star(6, innerRadius = 0.86f, rounding = CornerRounding(0.9f), innerRounding = CornerRounding(0.3f)) to 0f
    "pentagon" -> RoundedPolygon(5, rounding = CornerRounding(0.3f)) to -90f
    "gem" -> RoundedPolygon(6, rounding = CornerRounding(0.35f)) to 90f
    else -> null
}

/** Contorno della forma in un quadrato 1×1 (centrato, senza deformarla). */
private fun unitPath(key: String): android.graphics.Path? {
    val (poly, rot) = polygonOf(key) ?: return null
    val path = poly.toPath()
    val m = Matrix()
    if (rot != 0f) {
        m.setRotate(rot)
        path.transform(m)
    }
    val b = RectF()
    path.computeBounds(b, true)
    m.setRectToRect(b, RectF(0f, 0f, 1f, 1f), Matrix.ScaleToFit.CENTER)
    path.transform(m)
    return path
}

/** Forma Compose costruita dal contorno unitario: si scala alla misura dell'icona. */
private class UnitPathShape(private val unit: android.graphics.Path) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = android.graphics.Path(unit)
        p.transform(Matrix().apply { setScale(size.width, size.height) })
        return Outline.Generic(p.asComposePath())
    }
}

private val shapeCache = HashMap<String, Shape>()

/** Forma Material Expressive per [key] (null se non è una di queste). Creata una volta sola. */
fun expressiveShape(key: String): Shape? {
    shapeCache[key]?.let { return it }
    val shape: Shape = when (key) {
        "squircle" -> RoundedCornerShape(34)
        else -> unitPath(key)?.let { UnitPathShape(it) } ?: return null
    }
    shapeCache[key] = shape
    return shape
}

/** Forma di un'icona dalla sua chiave, per le anteprime (le icone vere usano [iconShapeFor]). */
fun iconShapeOf(key: String): Shape = expressiveShape(key) ?: when (key) {
    "circle" -> CircleShape
    "ios" -> IosShape
    "square" -> RoundedCornerShape(22)
    else -> RoundedCornerShape(32)
}

/**
 * Bordo e riflessi del vetro liquido (come in iOS 26): i riflessi stanno solo negli angoli,
 * forte in alto a sinistra e più debole in basso a destra, e si spengono verso il centro dei lati.
 */
object Specular {
    private fun white(alpha: Float): Int = android.graphics.Color.argb((alpha.coerceIn(0f, 1f) * 255f).toInt(), 255, 255, 255)

    /** Shader per un bordo sottile: linea [rim] su tutto il contorno e riflessi agli angoli. */
    fun cornerRim(w: Float, h: Float, strength: Float, rim: Float): Shader {
        val s = strength.coerceIn(0f, 1.5f)
        val reach = min(w, h) * 0.55f
        val topLeft = RadialGradient(
            0f, 0f, reach,
            intArrayOf(white(1f * s), white(0.5f * s), white(0.12f * s), white(rim)),
            floatArrayOf(0f, 0.25f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        val bottomRight = RadialGradient(
            w, h, reach * 0.8f,
            intArrayOf(white(0.6f * s), white(0.25f * s), white(0f)),
            floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP
        )
        return ComposeShader(topLeft, bottomRight, PorterDuff.Mode.ADD)
    }

    /** Leggero bagliore interno in alto a sinistra: il vetro "prende" la luce. */
    fun innerGloss(w: Float, h: Float, strength: Float): Shader =
        RadialGradient(
            0f, 0f, min(w, h) * 1.1f,
            intArrayOf(white(0.16f * strength), white(0.04f * strength), white(0f)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
}
