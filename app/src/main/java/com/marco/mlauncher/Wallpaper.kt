package com.marco.mlauncher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// =====================================================================
//  Sfondo gestito da MLauncher
// =====================================================================

/**
 * Come è disegnato lo sfondo sullo schermo: serve agli elementi in vetro per sapere
 * quale pezzo di immagine c'è dietro di loro.
 */
class WallRender(
    val bmp: ImageBitmap,
    val screenW: Float,
    val screenH: Float,
    val parallax: Boolean,
    private val scrollOf: () -> Float
) {
    val scale = max(screenW * (if (parallax) WALL_WIDE else 1f) / bmp.width, screenH / bmp.height)
    val dw = bmp.width * scale
    val dh = bmp.height * scale
    val offY = -(dh - screenH) / 2f
    fun offX(): Float = -(dw - screenW) * (if (parallax) scrollOf().coerceIn(0f, 1f) else 0.5f)
}

/** Sfondo corrente (null = sfondo di Android, che il Launcher non può leggere). */
val LocalWall = staticCompositionLocalOf<WallRender?> { null }

/** Sfondo per calcolare i colori dei testi: c'è anche dove il vetro non lo disegna (cassetto, ricerca). */
val LocalBackdropWall = staticCompositionLocalOf<WallRender?> { null }

/** Tema Liquid Glass attivo. */
val LocalLiquid = staticCompositionLocalOf { false }

/** Materiale dei widget di MLauncher (Stile → Materiale degli elementi). */
val LocalWidgetMat = staticCompositionLocalOf { Mat("standard", 60, true, 28) }

/** Testo scuro sopra il vetro (sfondo chiaro). */
val LocalGlassDarkText = staticCompositionLocalOf { false }

fun wallFile(ctx: android.content.Context) = File(ctx.filesDir, "wallpaper.jpg")

/** Immagine originale dello sfondo, per poterla ritagliare di nuovo. */
fun wallSrcFile(ctx: android.content.Context) = File(ctx.filesDir, "wallpaper_src.jpg")

/** Lo sfondo è più largo dello schermo per la parallasse. */
const val WALL_WIDE = 1.22f

/** Decodifica un'immagine ridimensionandola alla misura utile per lo schermo. */
fun decodeScaled(open: () -> java.io.InputStream?, maxSide: Int): Bitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open()?.use { BitmapFactory.decodeStream(it, null, o) }
    if (o.outWidth <= 0 || o.outHeight <= 0) return null
    var sample = 1
    while (max(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val bm = open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
    val big = max(bm.width, bm.height)
    if (big <= maxSide) return bm
    val k = maxSide.toFloat() / big
    return Bitmap.createScaledBitmap(bm, (bm.width * k).roundToInt(), (bm.height * k).roundToInt(), true)
}

/** Sfondi inclusi, disegnati al volo (niente immagini nell'APK). */
val BUILTIN_WALLS = listOf(
    "aurora" to "Aurora",
    "ocean" to "Oceano",
    "sunset" to "Tramonto",
    "night" to "Notte",
    "mint" to "Menta",
    "candy" to "Pastello"
)

fun builtinWall(key: String, w: Int, h: Int): Bitmap {
    val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bm)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val (bg, blobs) = when (key) {
        "ocean" -> intArrayOf(0xFF03224C.toInt(), 0xFF0B5FA5.toInt()) to listOf(
            Triple(0.2f, 0.25f, 0xFF19C3E6.toInt()), Triple(0.85f, 0.55f, 0xFF2A6CFF.toInt()), Triple(0.35f, 0.9f, 0xFF00E0B0.toInt())
        )
        "sunset" -> intArrayOf(0xFF3B0A45.toInt(), 0xFFFF6A3D.toInt()) to listOf(
            Triple(0.25f, 0.3f, 0xFFFF2E7E.toInt()), Triple(0.8f, 0.65f, 0xFFFFB347.toInt()), Triple(0.4f, 0.95f, 0xFFFF7A00.toInt())
        )
        "night" -> intArrayOf(0xFF05060F.toInt(), 0xFF1A1440.toInt()) to listOf(
            Triple(0.3f, 0.2f, 0xFF4B2BFF.toInt()), Triple(0.75f, 0.6f, 0xFF8E2DE2.toInt()), Triple(0.2f, 0.85f, 0xFF0B3D91.toInt())
        )
        "mint" -> intArrayOf(0xFFE8FFF6.toInt(), 0xFF9BE7C4.toInt()) to listOf(
            Triple(0.2f, 0.3f, 0xFF4FD1A5.toInt()), Triple(0.85f, 0.5f, 0xFFB6F2FF.toInt()), Triple(0.5f, 0.9f, 0xFF2BB673.toInt())
        )
        "candy" -> intArrayOf(0xFFFFF1F7.toInt(), 0xFFE7E3FF.toInt()) to listOf(
            Triple(0.25f, 0.25f, 0xFFFFA8D2.toInt()), Triple(0.8f, 0.55f, 0xFFA8C8FF.toInt()), Triple(0.35f, 0.9f, 0xFFC9A8FF.toInt())
        )
        else -> intArrayOf(0xFF061423.toInt(), 0xFF0E3B3A.toInt()) to listOf(
            Triple(0.25f, 0.3f, 0xFF27F5A6.toInt()), Triple(0.8f, 0.45f, 0xFF3A7BFF.toInt()), Triple(0.45f, 0.85f, 0xFFB14BFF.toInt())
        )
    }
    p.shader = LinearGradient(0f, 0f, w * 0.3f, h.toFloat(), bg[0], bg[1], Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    for ((x, y, col) in blobs) {
        val r = max(w, h) * 0.55f
        p.shader = RadialGradient(w * x, h * y, r, col, col and 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawCircle(w * x, h * y, r, p)
    }
    return bm
}

/** Lo sfondo disegnato dietro la Home: parallasse, sfocatura e oscuramento. */
@Composable
fun WallpaperLayer(w: WallRender, dim: Float, blur: Dp) {
    Canvas(Modifier.fillMaxSize().then(if (blur > 0.dp) Modifier.blur(blur) else Modifier)) {
        drawImage(
            w.bmp,
            dstOffset = IntOffset(w.offX().roundToInt(), w.offY.roundToInt()),
            dstSize = IntSize(w.dw.roundToInt(), w.dh.roundToInt())
        )
        if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
    }
}

// =====================================================================
//  Liquid Glass
// =====================================================================

/** Forza dei riflessi del vetro liquido (Stile → Tema → Liquid Glass). */
val LocalGlassStrength = staticCompositionLocalOf { 0.7f }

/**
 * Vetro liquido (Android 13+, AGSL), come in iOS 26 — portato da Expressive Launcher
 * (github.com/kotlalipopus/expressive-launcher, licenza MIT). Attraverso il vetro lo sfondo si vede NITIDO:
 *  - il centro ingrandisce un poco quello che c'è sotto (zoom < 1);
 *  - verso il bordo l'immagine si piega come in una lente spessa (la prende da fuori dal pannello);
 *  - la piega separa i colori: rosso, verde e blu si spostano diversamente, bordo arcobaleno;
 *  - riflessi solo negli angoli: forte in alto a sinistra, più debole in basso a destra.
 * Senza uno sfondo di MLauncher (sfondo di Android) il vetro resta trasparente: lo spessore lo danno
 * il bordo, una leggera ombra dal lato lontano dalla luce e i riflessi.
 */
private const val LIQUID_AGSL = """
uniform shader image;
uniform float2 size;
uniform float radius;
uniform float band;
uniform float bendPx;
uniform float zoom;
uniform float chroma;
uniform float spec;
uniform float useImage;
uniform float4 tint;
uniform float dp;

half4 main(float2 fc) {
    float2 hb = size * 0.5;
    float2 p = fc - hb;
    float r = min(radius, min(hb.x, hb.y));
    float2 q = abs(p) - hb + r;
    float2 qm = max(q, 0.0);
    float d = min(max(q.x, q.y), 0.0) + length(qm) - r;
    // lontano dal bordo: vetro piatto, un solo campione
    if (d < -band - 2.0 * dp) {
        if (useImage > 0.5) {
            half4 c = image.eval(hb + p * zoom);
            float ca = float(c.a) * (1.0 - tint.a) + tint.a;
            return half4(half3(min(float3(c.rgb) * (1.0 - tint.a) + tint.rgb * tint.a, float3(ca))), half(ca));
        }
        return half4(half3(tint.rgb * tint.a), half(tint.a));
    }
    float2 sg = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
    float2 n;
    if (qm.x + qm.y > 0.0) n = normalize(qm) * sg;
    else n = q.x > q.y ? float2(sg.x, 0.0) : float2(0.0, sg.y);
    float t = clamp(1.0 + d / band, 0.0, 1.0);
    float bend = 1.0 - sqrt(max(1.0 - t * t, 0.0));
    float facing = dot(n, float2(-0.7071, -0.7071));
    float3 col;
    float a;
    if (useImage > 0.5) {
        float2 base = hb + p * zoom;
        float2 off = n * bend * bendPx;
        half4 mid = image.eval(base + off);
        float3 img = float3(
            image.eval(base + off * (1.0 + chroma)).r,
            mid.g,
            image.eval(base + off * (1.0 - chroma)).b);
        img *= 1.0 + 0.12 * bend * facing;
        float ia = mid.a;
        a = ia * (1.0 - tint.a) + tint.a;
        col = (img * (1.0 - tint.a) + tint.rgb * tint.a) / max(a, 0.0001);
    } else {
        float shade = bend * max(-facing, 0.0);
        a = clamp(tint.a + bend * 0.10 + shade * 0.10, 0.0, 1.0);
        col = mix(tint.rgb, float3(0.0), clamp(shade * 0.7, 0.0, 1.0));
    }
    float rim = clamp(1.0 + d / (1.6 * dp), 0.0, 1.0);
    float corner = pow(max(facing, 0.0), 5.0) + 0.65 * pow(max(-facing, 0.0), 5.0);
    float glow = spec * (rim * (0.06 + 1.3 * corner) + bend * 0.32 * pow(max(facing, 0.0), 3.0)
        + bend * 0.14 * pow(max(-facing, 0.0), 3.0));
    float outA = clamp(a + glow, 0.0, 1.0);
    float3 outC = min(col * a + float3(glow), float3(outA));
    return half4(half3(outC), half(outA));
}
"""

/** Posizione dell'elemento sullo schermo: cambia di rado e si legge solo mentre si disegna. */
private class GlassPos {
    var offset by mutableStateOf(Offset.Zero)
}

/**
 * Vetro liquido: dietro l'elemento si vede lo sfondo nitido, piegato verso il bordo come da una lente,
 * con bordo arcobaleno e riflessi agli angoli. Senza uno sfondo di MLauncher il vetro è trasparente con
 * bordo e riflessi; prima di Android 13 (niente AGSL) resta un vetro disegnato con velo, bagliore e bordo.
 */
fun Modifier.liquidGlass(corner: Dp, shape: Shape? = null, tint: Color = Color.Unspecified, opacity: Float = 1f): Modifier = composed {
    val wall = LocalWall.current
    val lightBack = LocalGlassDarkText.current
    val strength = LocalGlassStrength.current
    val pos = remember { GlassPos() }
    this
        .then(if (wall != null) Modifier.onGloballyPositioned { pos.offset = it.positionInRoot() } else Modifier)
        .drawWithCache {
            val w = size.width
            val h = size.height
            val outline = (shape ?: RoundedCornerShape(corner)).createOutline(size, layoutDirection, this)
            val rounded = (outline as? Outline.Rounded)?.roundRect
            val radius = rounded?.topLeftCornerRadius?.x
                ?: if (outline is Outline.Rectangle) 0f else corner.toPx().coerceAtMost(size.minDimension / 2f)
            val path = Path().apply { addOutline(outline) }.asAndroidPath()
            val rect = RectF(0f, 0f, w, h)
            fun android.graphics.Canvas.shape(paint: Paint) {
                if (rounded != null) drawRoundRect(rect, radius, radius, paint) else drawPath(path, paint)
            }
            // velo appena percettibile, solo per leggere icone e testi; con "Trasparenza" più bassa è più denso
            val base = if (tint != Color.Unspecified) tint else if (lightBack) Color.White else Color(0xFF0C0D10)
            val fill = base.copy(alpha = (opacity * 0.3f).coerceIn(0.03f, 0.5f))
            val stroke = 1.dp.toPx()
            // ombra morbida sotto il vetro
            val shadowH = 8.dp.toPx()
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, h, 0f, h + shadowH,
                    android.graphics.Color.argb(if (lightBack) 28 else 60, 0, 0, 0), 0, Shader.TileMode.CLAMP
                )
            }
            val shadowRect = RectF(radius * 0.5f, h - shadowH * 0.2f, w - radius * 0.5f, h + shadowH)
            val wallMatrix = android.graphics.Matrix()
            val wallShader = wall?.let { BitmapShader(it.bmp.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            val half = min(w, h) / 2f
            val liquid = if (Build.VERSION.SDK_INT >= 33 && w > 1f && h > 1f) runCatching {
                RuntimeShader(LIQUID_AGSL).apply {
                    setInputShader("image", wallShader ?: LinearGradient(0f, 0f, 1f, 0f, 0, 0, Shader.TileMode.CLAMP))
                    setFloatUniform("size", w, h)
                    setFloatUniform("radius", radius)
                    setFloatUniform("band", min(48.dp.toPx(), half * 0.95f).coerceAtLeast(1f))
                    setFloatUniform("bendPx", min(26.dp.toPx(), half * 0.85f))
                    setFloatUniform("zoom", 0.86f)
                    setFloatUniform("chroma", 0.5f)
                    setFloatUniform("spec", strength.coerceIn(0f, 1.5f))
                    setFloatUniform("useImage", if (wallShader != null) 1f else 0f)
                    setFloatUniform("tint", fill.red, fill.green, fill.blue, fill.alpha)
                    setFloatUniform("dp", 1.dp.toPx())
                }
            }.getOrNull() else null
            val liquidPaint = liquid?.let { Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = it } }
            // senza AGSL: sfondo, velo, bagliore e bordo con i riflessi agli angoli
            val imagePaint = if (liquidPaint == null) wallShader?.let { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = it } } else null
            val fillPaint = if (liquidPaint == null) Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fill.copy(alpha = (fill.alpha + if (wall == null) 0.10f else 0f).coerceAtMost(1f)).toArgb()
            } else null
            val glossPaint = if (liquidPaint == null) Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = Specular.innerGloss(w, h, strength) } else null
            val rimPaint = if (liquidPaint == null) Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                this.shader = Specular.cornerRim(w, h, strength, rim = if (lightBack) 0.1f else 0.05f)
            } else null
            val inner = RectF(stroke / 2f, stroke / 2f, w - stroke / 2f, h - stroke / 2f)
            var lastX = Float.NaN
            var lastY = Float.NaN
            var lastWall = Float.NaN
            onDrawWithContent {
                drawIntoCanvas { cv ->
                    val c = cv.nativeCanvas
                    c.drawRoundRect(shadowRect, radius, radius, shadowPaint)
                    if (wall != null && wallShader != null) {
                        val p = pos.offset
                        val ox = wall.offX()
                        // la matrice si aggiorna solo se l'elemento o lo sfondo si sono mossi
                        if (p.x != lastX || p.y != lastY || ox != lastWall) {
                            lastX = p.x
                            lastY = p.y
                            lastWall = ox
                            // pixel dello sfondo b ↔ punto dell'elemento: b · scala + posizione dello sfondo − posizione dell'elemento
                            wallMatrix.setScale(wall.scale, wall.scale)
                            wallMatrix.postTranslate(ox - p.x, wall.offY - p.y)
                            wallShader.setLocalMatrix(wallMatrix)
                            if (liquid != null && Build.VERSION.SDK_INT >= 33) liquid.setInputShader("image", wallShader)
                        }
                    }
                    if (liquidPaint != null) {
                        c.shape(liquidPaint)
                    } else {
                        imagePaint?.let { c.shape(it) }
                        fillPaint?.let { c.shape(it) }
                        glossPaint?.let { c.shape(it) }
                    }
                }
                drawContent()
                // il bordo sta sopra al contenuto: i riflessi prendono luce anche lì
                if (rimPaint != null) drawIntoCanvas { cv ->
                    if (rounded != null) cv.nativeCanvas.drawRoundRect(inner, radius, radius, rimPaint)
                    else cv.nativeCanvas.drawPath(path, rimPaint)
                }
            }
        }
}

/**
 * Con il tema Liquid Glass i widget interni perdono il loro riquadro colorato e stanno su vetro liquido,
 * con testi chiari o scuri in base allo sfondo. Note e widget di altre app restano come sono.
 */
@Composable
fun GlassWidgetFrame(kind: String, content: @Composable () -> Unit) {
    val m = LocalWidgetMat.current
    // gli orologi Sense hanno la loro grafica (anello scuro, cartellini), senza pannello sotto
    if (m.plain || kind == "ext" || kind == "notes" || kind == "senseClock" || kind == "senseFlip") {
        content()
        return
    }
    val liquid = m.style == "liquid"
    val fg = if (liquid) (if (LocalGlassDarkText.current) Color(0xFF15171C) else Color.White) else matContentColor(m)
    val cs = androidx.compose.material3.MaterialTheme.colorScheme
    val glass = cs.copy(
        primaryContainer = Color.Transparent, onPrimaryContainer = fg,
        secondaryContainer = Color.Transparent, onSecondaryContainer = fg,
        tertiaryContainer = Color.Transparent, onTertiaryContainer = fg,
        surfaceContainerHigh = Color.Transparent, onSurface = fg, onSurfaceVariant = fg.copy(alpha = 0.8f)
    )
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().then(
            when {
                kind == "search" -> Modifier
                liquid -> Modifier.liquidGlass(28.dp)
                else -> Modifier.matLook(m, minOf(m.corner, 32).dp, minOpacity = 40)
            }
        )
    ) {
        androidx.compose.material3.MaterialTheme(colorScheme = glass) { content() }
    }
}

/**
 * Colore medio dello sfondo nella zona [rect] dello schermo (tutto lo schermo se null).
 * Serve a scegliere testi leggibili sopra pannelli trasparenti: si guarda lo sfondo vero,
 * non un grigio immaginato. Senza sfondo di MLauncher si usa il consiglio di Android (chiaro/scuro).
 */
fun wallToneAt(wall: WallRender?, rect: androidx.compose.ui.geometry.Rect?, darkTextHint: Boolean): Color {
    val fallback = if (darkTextHint) Color(0xFFD8D8DC) else Color(0xFF2A2C33)
    if (wall == null) return fallback
    return try {
        val bm = wall.bmp.asAndroidBitmap()
        if (bm.config == Bitmap.Config.HARDWARE) return fallback
        val r = rect?.takeIf { it.width > 1f && it.height > 1f } ?: androidx.compose.ui.geometry.Rect(0f, 0f, wall.screenW, wall.screenH)
        var rs = 0f; var gs = 0f; var bs = 0f; var n = 0
        val steps = 12
        for (i in 0 until steps) for (j in 0 until steps) {
            val x = r.left + r.width * (i + 0.5f) / steps
            val y = r.top + r.height * (j + 0.5f) / steps
            val px = ((x - wall.offX()) / wall.scale).toInt().coerceIn(0, bm.width - 1)
            val py = ((y - wall.offY) / wall.scale).toInt().coerceIn(0, bm.height - 1)
            val c = bm.getPixel(px, py)
            rs += ((c shr 16) and 255) / 255f
            gs += ((c shr 8) and 255) / 255f
            bs += (c and 255) / 255f
            n++
        }
        Color(rs / n, gs / n, bs / n, 1f)
    } catch (e: Exception) {
        fallback
    }
}

/** Colore medio dello sfondo dietro [rect] (tutto lo schermo se null). */
@Composable
fun backdropTone(rect: androidx.compose.ui.geometry.Rect? = null): Color {
    val wall = LocalWall.current ?: LocalBackdropWall.current
    val hint = LocalGlassDarkText.current
    return remember(wall, rect, hint) { wallToneAt(wall, rect, hint) }
}

/**
 * Opacità del velo [veil] (a tutto schermo, sopra la Home sfocata) perché [text] si legga ovunque:
 * si prova su sei fasce dello sfondo e si tiene la più esigente. Non scende mai sotto [start].
 */
@Composable
fun readableVeilAlpha(text: Color, veil: Color, start: Float): Float {
    val wall = LocalWall.current ?: LocalBackdropWall.current
    val hint = LocalGlassDarkText.current
    return remember(wall, text, veil, start, hint) {
        if (wall == null) veilAlphaFor(text, veil, wallToneAt(null, null, hint), start)
        else (0 until 6).maxOf { i ->
            val band = androidx.compose.ui.geometry.Rect(0f, wall.screenH * i / 6f, wall.screenW, wall.screenH * (i + 1) / 6f)
            veilAlphaFor(text, veil, wallToneAt(wall, band, hint), start)
        }
    }
}
