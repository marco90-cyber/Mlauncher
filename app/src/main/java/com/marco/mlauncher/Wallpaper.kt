package com.marco.mlauncher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.max
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

/** Tema Liquid Glass attivo. */
val LocalLiquid = staticCompositionLocalOf { false }

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

/** Lente sui bordi con leggera dispersione dei colori (AGSL, Android 13+). */
private const val LENS_AGSL = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float depth;

float sdRound(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 fc) {
    float2 c = size * 0.5;
    float2 p = fc - c;
    float d = sdRound(p, c, radius);
    float band = min(min(size.x, size.y) * 0.5, radius * 1.4 + 10.0);
    float t = clamp(1.0 + d / band, 0.0, 1.0);
    float2 g = float2(
        sdRound(p + float2(1.0, 0.0), c, radius) - sdRound(p - float2(1.0, 0.0), c, radius),
        sdRound(p + float2(0.0, 1.0), c, radius) - sdRound(p - float2(0.0, 1.0), c, radius)
    );
    float2 n = g / max(length(g), 0.0001);
    float2 off = -n * depth * t * t;
    half4 col;
    col.r = content.eval(fc + off).r;
    col.g = content.eval(fc + off * 0.9).g;
    col.b = content.eval(fc + off * 0.8).b;
    col.a = 1.0;
    return col;
}
"""

/**
 * Vetro liquido: dietro l'elemento si vede lo sfondo sfocato e piegato ai bordi come da una lente,
 * con riflesso in alto e bordo luminoso. Senza uno sfondo di MLauncher (o prima di Android 12)
 * resta un vetro imitato con riflessi e bordo.
 */
fun Modifier.liquidGlass(corner: Dp, shape: Shape? = null, tint: Color = Color.Unspecified, opacity: Float = 1f): Modifier = composed {
    val wall = LocalWall.current
    val dark = LocalGlassDarkText.current
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    val layer = rememberGraphicsLayer()
    val shader = remember {
        if (Build.VERSION.SDK_INT >= 33) try { android.graphics.RuntimeShader(LENS_AGSL) } catch (e: Exception) { null } else null
    }
    val cornerPx = with(density) { corner.toPx() }
    val blurPx = with(density) { 14.dp.toPx() }
    val depthPx = with(density) { 10.dp.toPx() }
    val rim = with(density) { 1.2.dp.toPx() }
    this
        .onGloballyPositioned { pos = it.positionInRoot() }
        .drawWithContent {
            val r = cornerPx.coerceAtMost(size.minDimension / 2f)
            val outline: Path = Path().apply {
                if (shape != null) {
                    when (val o = shape.createOutline(size, dir, density)) {
                        is androidx.compose.ui.graphics.Outline.Rectangle -> addRect(o.rect)
                        is androidx.compose.ui.graphics.Outline.Rounded -> addRoundRect(o.roundRect)
                        is androidx.compose.ui.graphics.Outline.Generic -> addPath(o.path)
                    }
                } else {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(r)))
                }
            }
            clipPath(outline) {
                if (wall != null && size.width > 1f && size.height > 1f) {
                    // il pezzo di sfondo che sta dietro l'elemento
                    val sx = ((pos.x - wall.offX()) / wall.scale)
                    val sy = ((pos.y - wall.offY) / wall.scale)
                    val sw = (size.width / wall.scale)
                    val sh = (size.height / wall.scale)
                    val left = sx.roundToInt().coerceIn(0, max(0, wall.bmp.width - 1))
                    val top = sy.roundToInt().coerceIn(0, max(0, wall.bmp.height - 1))
                    val w = sw.roundToInt().coerceIn(1, wall.bmp.width - left)
                    val h = sh.roundToInt().coerceIn(1, wall.bmp.height - top)
                    val isz = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    var effect: android.graphics.RenderEffect = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                    if (shader != null && Build.VERSION.SDK_INT >= 33) {
                        shader.setFloatUniform("size", size.width, size.height)
                        shader.setFloatUniform("radius", r)
                        shader.setFloatUniform("depth", depthPx)
                        effect = android.graphics.RenderEffect.createChainEffect(
                            android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content"), effect
                        )
                    }
                    layer.renderEffect = effect.asComposeRenderEffect()
                    layer.record(size = isz) {
                        drawImage(wall.bmp, srcOffset = IntOffset(left, top), srcSize = IntSize(w, h), dstSize = isz)
                    }
                    drawLayer(layer)
                    // leggera velatura per leggere bene icone e testi
                    drawRect((if (dark) Color.White else Color.Black).copy(alpha = 0.06f + 0.10f * (1f - opacity)))
                } else {
                    drawRect(Color.White.copy(alpha = 0.10f + 0.12f * opacity))
                }
                if (tint != Color.Unspecified) drawRect(tint.copy(alpha = 0.18f * opacity))
                // riflesso in alto
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.28f),
                        0.45f to Color.White.copy(alpha = 0.04f),
                        1f to Color.White.copy(alpha = 0.10f)
                    )
                )
            }
            // bordo luminoso, più forte in alto a sinistra e in basso a destra
            drawPath(
                outline,
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.85f),
                    0.35f to Color.White.copy(alpha = 0.12f),
                    0.7f to Color.White.copy(alpha = 0.08f),
                    1f to Color.White.copy(alpha = 0.55f),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                ),
                style = Stroke(rim)
            )
            drawContent()
        }
}

/**
 * Con il tema Liquid Glass i widget interni perdono il loro riquadro colorato e stanno su vetro liquido,
 * con testi chiari o scuri in base allo sfondo. Note e widget di altre app restano come sono.
 */
@Composable
fun GlassWidgetFrame(kind: String, content: @Composable () -> Unit) {
    if (!LocalLiquid.current || kind == "ext" || kind == "notes") {
        content()
        return
    }
    val fg = if (LocalGlassDarkText.current) Color(0xFF15171C) else Color.White
    val cs = androidx.compose.material3.MaterialTheme.colorScheme
    val glass = cs.copy(
        primaryContainer = Color.Transparent, onPrimaryContainer = fg,
        secondaryContainer = Color.Transparent, onSecondaryContainer = fg,
        tertiaryContainer = Color.Transparent, onTertiaryContainer = fg,
        surfaceContainerHigh = Color.Transparent, onSurface = fg, onSurfaceVariant = fg.copy(alpha = 0.8f)
    )
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().then(if (kind == "search") Modifier else Modifier.liquidGlass(28.dp))
    ) {
        androidx.compose.material3.MaterialTheme(colorScheme = glass) { content() }
    }
}
