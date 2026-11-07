package com.marco.mlauncher

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// =====================================================================
//  Effetti dell'editor (applicati all'immagine quando premi Applica)
// =====================================================================

/** Effetti dell'editor, tutti da 0 (spento) a 1. */
data class WallFx(
    val blur: Float = 0f,
    val dim: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f,
    val fade: Float = 0f,
    val mosaic: Float = 0f
) {
    val none get() = blur == 0f && dim == 0f && vignette == 0f && grain == 0f && fade == 0f && mosaic == 0f
}

/** Piastrella di rumore per la grana (sempre la stessa: niente sfarfallio nell'anteprima). */
fun noiseTile(size: Int = 128): Bitmap {
    val r = java.util.Random(7)
    val px = IntArray(size * size) {
        val l = r.nextInt(256)
        android.graphics.Color.argb(255, l, l, l)
    }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}

/**
 * Sfocatura vera (tre passaggi di media mobile = quasi gaussiana) su una copia rimpicciolita,
 * poi ingrandita: essendo già liscia non mostra i "quadretti" del mosaico.
 */
private fun softBlur(src: Bitmap, amount: Float): Bitmap {
    if (amount <= 0f) return src
    val radiusFull = amount * src.width * 0.06f
    val d = (radiusFull / 6f).coerceIn(1f, 8f)
    val w = max(2, (src.width / d).toInt())
    val h = max(2, (src.height / d).toInt())
    val small = Bitmap.createScaledBitmap(src, w, h, true)
    val px = IntArray(w * h)
    small.getPixels(px, 0, w, 0, 0, w, h)
    val r = max(1, (radiusFull / d).toInt())
    repeat(3) { boxBlur(px, w, h, r) }
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(px, 0, w, 0, 0, w, h)
    return Bitmap.createScaledBitmap(out, src.width, src.height, true)
}

/** Media mobile orizzontale e verticale (bordi ripetuti). */
private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
    val tmp = IntArray(px.size)
    val div = 2 * r + 1
    fun pass(srcA: IntArray, dst: IntArray, len: Int, lines: Int, stride: Int, step: Int) {
        for (l in 0 until lines) {
            val base = l * stride
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (k in -r..r) {
                val c = srcA[base + k.coerceIn(0, len - 1) * step]
                sa += c ushr 24; sr += (c shr 16) and 255; sg += (c shr 8) and 255; sb += c and 255
            }
            for (i in 0 until len) {
                dst[base + i * step] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val out = srcA[base + (i - r).coerceIn(0, len - 1) * step]
                val inn = srcA[base + (i + r + 1).coerceIn(0, len - 1) * step]
                sa += (inn ushr 24) - (out ushr 24)
                sr += ((inn shr 16) and 255) - ((out shr 16) and 255)
                sg += ((inn shr 8) and 255) - ((out shr 8) and 255)
                sb += (inn and 255) - (out and 255)
            }
        }
    }
    pass(px, tmp, w, h, w, 1)      // righe
    pass(tmp, px, h, w, 1, w)      // colonne
}

/** Applica gli effetti a un'immagine già ritagliata (larghezza e altezza finali). */
fun applyWallFx(src: Bitmap, fx: WallFx): Bitmap {
    if (fx.none) return src
    var out = src.copy(Bitmap.Config.ARGB_8888, true)
    val w = out.width
    val h = out.height
    if (fx.mosaic > 0f) {
        val block = max(2, (fx.mosaic * w / 24f).toInt())
        val small = Bitmap.createScaledBitmap(out, max(1, w / block), max(1, h / block), true)
        out = Bitmap.createScaledBitmap(small, w, h, false).copy(Bitmap.Config.ARGB_8888, true)
    }
    if (fx.blur > 0f) out = softBlur(out, fx.blur).copy(Bitmap.Config.ARGB_8888, true)
    val c = Canvas(out)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    if (fx.vignette > 0f) {
        val a = (fx.vignette * 0.8f * 255).toInt()
        p.shader = RadialGradient(
            w / 2f, h / 2f, hypot(w.toFloat(), h.toFloat()) / 2f,
            intArrayOf(0, 0, android.graphics.Color.argb(a, 0, 0, 0)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    }
    if (fx.fade > 0f) {
        val a = (fx.fade * 0.75f * 255).toInt()
        p.shader = LinearGradient(0f, h * 0.5f, 0f, h.toFloat(), 0, android.graphics.Color.argb(a, 0, 0, 0), Shader.TileMode.CLAMP)
        c.drawRect(0f, h * 0.5f, w.toFloat(), h.toFloat(), p)
    }
    p.shader = null
    if (fx.dim > 0f) c.drawColor(android.graphics.Color.argb((fx.dim * 0.7f * 255).toInt(), 0, 0, 0))
    if (fx.grain > 0f) {
        val gp = Paint()
        gp.shader = BitmapShader(noiseTile(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        gp.alpha = (fx.grain * 0.22f * 255).toInt()
        gp.blendMode = android.graphics.BlendMode.OVERLAY
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), gp)
    }
    return out
}

// =====================================================================
//  "Crea il tuo sfondo": sfondi disegnati da MLauncher, con regole salvate
// =====================================================================

val GEN_STYLES = listOf(
    "aurora" to "Aurora",
    "mesh" to "Mesh",
    "waves" to "Onde",
    "hills" to "Colline",
    "linear" to "Gradiente",
    "radial" to "Radiale",
    "bubbles" to "Bolle",
    "rings" to "Cerchi",
    "rays" to "Raggi",
    "poly" to "Poligoni",
    "diagonal" to "Strisce"
)

/** Tavolozze pronte (4 colori ciascuna). */
val GEN_PALETTES = listOf(
    "Aurora" to listOf(0xFF27F5A6, 0xFF3A7BFF, 0xFFB14BFF, 0xFF0E3B3A),
    "Tramonto" to listOf(0xFFFF2E7E, 0xFFFFB347, 0xFFFF7A00, 0xFF3B0A45),
    "Oceano" to listOf(0xFF19C3E6, 0xFF2A6CFF, 0xFF00E0B0, 0xFF03224C),
    "Bosco" to listOf(0xFF2E7D32, 0xFF81C784, 0xFFC5E1A5, 0xFF1B3A1F),
    "Lavanda" to listOf(0xFFB39DDB, 0xFFF8BBD0, 0xFF9FA8DA, 0xFF4A3F6B),
    "Pesca" to listOf(0xFFFFCCBC, 0xFFFFAB91, 0xFFFFE0B2, 0xFFE57373),
    "Neon" to listOf(0xFFFF00E5, 0xFF00F0FF, 0xFF7C4DFF, 0xFF0A0A1F),
    "Sabbia" to listOf(0xFFD7CCC8, 0xFFBCAAA4, 0xFFFFE0B2, 0xFF6D4C41),
    "Ghiaccio" to listOf(0xFFE1F5FE, 0xFF81D4FA, 0xFFB3E5FC, 0xFF37474F),
    "Fuoco" to listOf(0xFFFFEB3B, 0xFFFF9800, 0xFFF44336, 0xFF3E0A0A),
    "Menta" to listOf(0xFF4FD1A5, 0xFFB6F2FF, 0xFF2BB673, 0xFFE8FFF6),
    "Notte" to listOf(0xFF4B2BFF, 0xFF8E2DE2, 0xFF0B3D91, 0xFF05060F)
).map { (n, l) -> n to l.map { it.toInt() } }

/** Regole di uno sfondo creato: si ridisegna sempre alla risoluzione del telefono. */
data class GenWall(
    val id: String,
    val name: String,
    val style: String = "aurora",
    /** da 1 a 4 colori */
    val colors: List<Int> = listOf(0xFF27F5A6.toInt(), 0xFF3A7BFF.toInt(), 0xFFB14BFF.toInt()),
    /** da dove vengono i colori: "mine", "system", "theme", "wall" */
    val source: String = "mine",
    val seed: Int = 1,
    val soft: Float = 0.5f,
    val grain: Float = 0f,
    val dark: Boolean = true,
    /** direzione (gradiente, strisce, raggi), da 0 a 1 = mezzo giro */
    val angle: Float = 0.3f,
    /** quantità di elementi (bolle, onde, cerchi, raggi, poligoni, colline, strisce) */
    val density: Float = 0.5f,
    /** da -1 a 1 */
    val bright: Float = 0f,
    val sat: Float = 0f,
    val vignette: Float = 0f,
    val blur: Float = 0f
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("n", name).put("st", style)
        .put("cols", JSONArray(colors)).put("src", source).put("seed", seed)
        .put("soft", soft.toDouble()).put("gr", grain.toDouble()).put("dk", dark)
        .put("ang", angle.toDouble()).put("den", density.toDouble()).put("br", bright.toDouble())
        .put("sat", sat.toDouble()).put("vig", vignette.toDouble()).put("bl", blur.toDouble())

    companion object {
        fun fromJson(o: JSONObject): GenWall {
            val arr = o.optJSONArray("cols")
            // le prime versioni avevano sempre 3 colori in c1, c2, c3
            val cols = if (arr != null && arr.length() > 0) (0 until arr.length()).map { arr.getInt(it) }
            else listOf(o.optInt("c1"), o.optInt("c2"), o.optInt("c3"))
            return GenWall(
                o.getString("id"), o.optString("n", "Il mio sfondo"), o.optString("st", "aurora"),
                cols.take(4), o.optString("src", "mine"),
                o.optInt("seed", 1), o.optDouble("soft", 0.5).toFloat(), o.optDouble("gr", 0.0).toFloat(), o.optBoolean("dk", true),
                o.optDouble("ang", 0.3).toFloat(), o.optDouble("den", 0.5).toFloat(), o.optDouble("br", 0.0).toFloat(),
                o.optDouble("sat", 0.0).toFloat(), o.optDouble("vig", 0.0).toFloat(), o.optDouble("bl", 0.0).toFloat()
            )
        }

        fun listFrom(json: String): List<GenWall> = try {
            val a = JSONArray(json)
            (0 until a.length()).map { fromJson(a.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }

        fun listTo(list: List<GenWall>): String = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
    }
}

/** Colori di uno degli sfondi inclusi, per partire da lì con "Personalizza". */
fun builtinGen(key: String, label: String): GenWall {
    val (c, dark) = when (key) {
        "ocean" -> listOf(0xFF19C3E6, 0xFF2A6CFF, 0xFF00E0B0) to true
        "sunset" -> listOf(0xFFFF2E7E, 0xFFFFB347, 0xFFFF7A00) to true
        "night" -> listOf(0xFF4B2BFF, 0xFF8E2DE2, 0xFF0B3D91) to true
        "mint" -> listOf(0xFF4FD1A5, 0xFFB6F2FF, 0xFF2BB673) to false
        "candy" -> listOf(0xFFFFA8D2, 0xFFA8C8FF, 0xFFC9A8FF) to false
        else -> listOf(0xFF27F5A6, 0xFF3A7BFF, 0xFFB14BFF) to true
    }
    return GenWall(java.util.UUID.randomUUID().toString(), "$label (mio)", "aurora", c.map { it.toInt() }, "mine", 1, 0.55f, 0f, dark)
}

/** Uno sfondo tutto a caso: stile, tavolozza e regolazioni. */
fun randomGen(id: String, name: String): GenWall {
    val r = java.util.Random()
    val pal = GEN_PALETTES[r.nextInt(GEN_PALETTES.size)].second
    val n = 2 + r.nextInt(3)
    return GenWall(
        id, name, GEN_STYLES[r.nextInt(GEN_STYLES.size)].first, pal.shuffled(r).take(n), "mine",
        1 + r.nextInt(1_000_000), 0.3f + r.nextFloat() * 0.6f, if (r.nextBoolean()) 0.25f else 0f, r.nextBoolean(),
        r.nextFloat(), 0.3f + r.nextFloat() * 0.5f
    )
}

private fun withL(c: Int, l: Float, satMul: Float = 1f): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(c, hsl)
    hsl[1] = (hsl[1] * satMul).coerceIn(0f, 1f)
    hsl[2] = l.coerceIn(0f, 1f)
    return ColorUtils.HSLToColor(hsl)
}

private fun shiftL(c: Int, d: Float): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(c, hsl)
    hsl[2] = (hsl[2] + d).coerceIn(0.03f, 0.97f)
    return ColorUtils.HSLToColor(hsl)
}

private fun alpha(c: Int, a: Float) = (c and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

/** Disegna lo sfondo creato. */
fun genWall(g: GenWall, w: Int, h: Int): Bitmap {
    val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bm)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = java.util.Random(g.seed.toLong() * 7919L + g.style.hashCode())
    // con un solo colore uso tre sue sfumature
    val src = g.colors.ifEmpty { listOf(0xFF3A7BFF.toInt()) }
    val cols = if (src.size == 1) listOf(src[0], shiftL(src[0], 0.18f), shiftL(src[0], -0.18f)) else src
    fun pick(i: Int) = cols[((i % cols.size) + cols.size) % cols.size]
    // base chiara o scura con la tinta dei primi colori
    val b1 = if (g.dark) withL(pick(0), 0.07f, 0.8f) else withL(pick(0), 0.94f, 0.6f)
    val b2 = if (g.dark) withL(pick(1), 0.16f, 0.8f) else withL(pick(1), 0.86f, 0.6f)
    val big = max(w, h).toFloat()
    val soft = g.soft.coerceIn(0f, 1f)
    val dens = g.density.coerceIn(0f, 1f)
    val ang = g.angle * PI.toFloat()
    fun base() {
        p.shader = LinearGradient(0f, 0f, w * 0.35f, h.toFloat(), b1, b2, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    }
    fun col(i: Int) = if (g.dark) pick(i) else withL(pick(i), 0.72f)
    fun gradColors(lights: List<Float>) = IntArray(cols.size) { i ->
        val l = lights[i % lights.size]
        withL(cols[i], l)
    }
    val full = android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat())
    when (g.style) {
        "linear" -> {
            val dx = kotlin.math.cos(ang) * big / 2f
            val dy = kotlin.math.sin(ang) * big / 2f
            val list = if (g.dark) gradColors(listOf(0.22f, 0.32f, 0.18f, 0.12f)) else gradColors(listOf(0.82f, 0.74f, 0.86f, 0.9f))
            val stops = FloatArray(list.size) { it / max(1f, list.size - 1f) }
            p.shader = LinearGradient(w / 2f - dx, h / 2f - dy, w / 2f + dx, h / 2f + dy, if (list.size == 1) intArrayOf(list[0], list[0]) else list,
                if (list.size == 1) floatArrayOf(0f, 1f) else stops, Shader.TileMode.CLAMP)
            c.drawRect(full, p)
        }
        "radial" -> {
            val cx = w * (0.2f + r.nextFloat() * 0.6f)
            val cy = h * (0.1f + r.nextFloat() * 0.35f)
            val list = if (g.dark) gradColors(listOf(0.55f, 0.25f, 0.12f, 0.06f)) else gradColors(listOf(0.95f, 0.82f, 0.7f, 0.6f))
            val stops = FloatArray(list.size) { it / max(1f, list.size - 1f) }
            p.shader = RadialGradient(cx, cy, big * (0.7f + soft * 0.6f), if (list.size == 1) intArrayOf(list[0], list[0]) else list,
                if (list.size == 1) floatArrayOf(0f, 1f) else stops, Shader.TileMode.CLAMP)
            c.drawRect(full, p)
        }
        "mesh" -> {
            // quattro angoli che si fondono
            base()
            val pts = listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f).shuffled(r)
            pts.forEachIndexed { i, (px, py) ->
                val cx = w * (px + (r.nextFloat() - 0.5f) * 0.3f)
                val cy = h * (py + (r.nextFloat() - 0.5f) * 0.3f)
                val cc = col(i)
                p.shader = RadialGradient(cx, cy, big * (0.6f + soft * 0.5f), cc, cc and 0x00FFFFFF, Shader.TileMode.CLAMP)
                c.drawRect(full, p)
            }
        }
        "waves" -> {
            base()
            val n = 3 + (dens * 6).toInt()
            for (i in 0 until n) {
                val y0 = h * (0.3f + i * (0.6f / n))
                val amp = h * (0.03f + r.nextFloat() * 0.05f)
                val len = w * (0.8f + r.nextFloat() * 0.9f)
                val ph = r.nextFloat() * 6.28f
                val path = Path()
                path.moveTo(0f, h.toFloat())
                var x = 0f
                while (x <= w) {
                    path.lineTo(x, y0 + amp * sin(x / len * 6.28f + ph))
                    x += w / 60f
                }
                path.lineTo(w.toFloat(), h.toFloat())
                path.close()
                val cc = col(i)
                p.shader = LinearGradient(0f, y0 - amp, 0f, h.toFloat(), alpha(cc, 0.5f + 0.2f * (1f - soft)), alpha(cc, 0.12f), Shader.TileMode.CLAMP)
                c.drawPath(path, p)
            }
        }
        "hills" -> {
            // cielo sfumato e colline una davanti all'altra, dalla più chiara alla più scura
            p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                if (g.dark) withL(pick(0), 0.12f) else withL(pick(0), 0.9f),
                if (g.dark) withL(pick(1), 0.3f) else withL(pick(1), 0.75f), Shader.TileMode.CLAMP)
            c.drawRect(full, p)
            p.shader = null
            val n = 3 + (dens * 4).toInt()
            for (i in 0 until n) {
                val t = i / max(1f, n - 1f)
                val y0 = h * (0.45f + t * 0.4f)
                val path = Path()
                path.moveTo(0f, h.toFloat())
                val f1 = 1f + r.nextFloat() * 2f
                val f2 = 2f + r.nextFloat() * 3f
                val ph = r.nextFloat() * 6.28f
                var x = 0f
                while (x <= w) {
                    val u = x / w * 6.28f
                    path.lineTo(x, y0 - h * 0.06f * (sin(u * f1 + ph) * 0.7f + sin(u * f2 + ph * 2) * 0.3f))
                    x += w / 80f
                }
                path.lineTo(w.toFloat(), h.toFloat())
                path.close()
                val l = if (g.dark) 0.45f - t * 0.35f else 0.75f - t * 0.45f
                p.color = withL(pick(i + 1), l)
                c.drawPath(path, p)
            }
        }
        "bubbles" -> {
            base()
            val n = 6 + (dens * 30).toInt()
            repeat(n) { i ->
                val cx = w * r.nextFloat()
                val cy = h * r.nextFloat()
                val rad = big * (0.03f + r.nextFloat() * (0.18f - dens * 0.08f))
                val cc = col(i)
                val a = 0.25f + r.nextFloat() * 0.45f
                p.shader = RadialGradient(cx, cy, rad, intArrayOf(alpha(cc, a), alpha(cc, a * (1f - soft * 0.7f)), alpha(cc, 0f)),
                    floatArrayOf(0f, 0.7f + 0.25f * (1f - soft), 1f), Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, rad, p)
            }
        }
        "rings" -> {
            base()
            val cx = w * (0.2f + r.nextFloat() * 0.6f)
            val cy = h * (0.2f + r.nextFloat() * 0.6f)
            val n = 5 + (dens * 20).toInt()
            val step = big * 1.1f / n
            p.shader = null
            for (i in n downTo 1) {
                p.color = alpha(col(i), 0.35f + 0.4f * (i % 2))
                if (soft > 0.05f) {
                    val cc = col(i)
                    p.shader = RadialGradient(cx, cy, step * i, intArrayOf(alpha(cc, 0.75f), alpha(cc, 0.75f), alpha(cc, 0.75f * (1f - soft))),
                        floatArrayOf(0f, 0.85f, 1f), Shader.TileMode.CLAMP)
                }
                c.drawCircle(cx, cy, step * i, p)
            }
        }
        "rays" -> {
            base()
            val cx = w * (0.3f + r.nextFloat() * 0.4f)
            val cy = h * (0.15f + r.nextFloat() * 0.3f)
            val n = 8 + (dens * 28).toInt()
            val len = big * 1.6f
            val wedge = (2 * PI / n).toFloat()
            p.shader = null
            for (i in 0 until n) {
                val a0 = ang * 2f + i * wedge
                val a1 = a0 + wedge * 0.5f
                val path = Path()
                path.moveTo(cx, cy)
                path.lineTo(cx + kotlin.math.cos(a0) * len, cy + kotlin.math.sin(a0) * len)
                path.lineTo(cx + kotlin.math.cos(a1) * len, cy + kotlin.math.sin(a1) * len)
                path.close()
                p.color = alpha(col(i), 0.35f)
                c.drawPath(path, p)
            }
            // bagliore al centro
            val cc = if (g.dark) withL(pick(0), 0.7f) else Color.White.toArgb()
            p.shader = RadialGradient(cx, cy, big * (0.25f + soft * 0.4f), alpha(cc, 0.7f), alpha(cc, 0f), Shader.TileMode.CLAMP)
            c.drawRect(full, p)
        }
        "poly" -> {
            // poligoni (triangoli) con i colori che sfumano dall'alto in basso
            val cell = big / (5f + dens * 14f)
            val cx = (w / cell).toInt() + 2
            val cy = (h / cell).toInt() + 2
            val px = Array(cy) { FloatArray(cx) }
            val py = Array(cy) { FloatArray(cx) }
            for (j in 0 until cy) for (i in 0 until cx) {
                px[j][i] = (i - 0.5f) * cell + (r.nextFloat() - 0.5f) * cell * 0.8f
                py[j][i] = (j - 0.5f) * cell + (r.nextFloat() - 0.5f) * cell * 0.8f
            }
            p.shader = null
            fun colorAt(y: Float): Int {
                val t = (y / h).coerceIn(0f, 1f) * (cols.size - 1)
                val k = t.toInt().coerceAtMost(cols.size - 2).coerceAtLeast(0)
                val a = if (cols.size == 1) cols[0] else ColorUtils.blendARGB(cols[k], cols[min(k + 1, cols.size - 1)], t - k)
                return if (g.dark) withL(a, 0.18f + r.nextFloat() * 0.18f) else withL(a, 0.62f + r.nextFloat() * 0.2f)
            }
            for (j in 0 until cy - 1) for (i in 0 until cx - 1) {
                val t1 = Path().apply { moveTo(px[j][i], py[j][i]); lineTo(px[j][i + 1], py[j][i + 1]); lineTo(px[j + 1][i], py[j + 1][i]); close() }
                val t2 = Path().apply { moveTo(px[j][i + 1], py[j][i + 1]); lineTo(px[j + 1][i + 1], py[j + 1][i + 1]); lineTo(px[j + 1][i], py[j + 1][i]); close() }
                p.color = colorAt(py[j][i]); c.drawPath(t1, p)
                p.color = colorAt(py[j + 1][i + 1]); c.drawPath(t2, p)
            }
        }
        "diagonal" -> {
            base()
            val bands = 3 + (dens * 10).toInt()
            val list = IntArray(bands * 2) { i -> alpha(col(i / 2), if (i % 2 == 0) 0.55f else soft * 0.45f) }
            val stops = FloatArray(bands * 2) { i -> i / (bands * 2f - 1f) }
            val dx = kotlin.math.cos(ang) * big
            val dy = kotlin.math.sin(ang) * big
            p.shader = LinearGradient(0f, 0f, dx, dy, list, stops, Shader.TileMode.MIRROR)
            c.drawRect(full, p)
        }
        else -> {
            // aurora: grandi macchie sfumate
            base()
            val n = max(3, cols.size) + (dens * 3).toInt()
            repeat(n) { i ->
                val cx = w * (0.1f + r.nextFloat() * 0.8f)
                val cy = h * (0.05f + (i + r.nextFloat()) / n * 0.9f)
                val rad = big * (0.3f + soft * 0.35f)
                val cc = col(i)
                p.shader = RadialGradient(cx, cy, rad, cc, cc and 0x00FFFFFF, Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, rad, p)
            }
        }
    }
    p.shader = null
    // luminosità e saturazione
    var out = bm
    if (g.bright != 0f || g.sat != 0f) {
        val m = wallColorMatrix(g.bright * 0.6f, 0f, g.sat, 0f, "none")
        if (m != null) {
            val o2 = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(o2).drawBitmap(bm, 0f, 0f, Paint().apply { colorFilter = android.graphics.ColorMatrixColorFilter(m) })
            out = o2
        }
    }
    // effetti finali: sfocatura, vignettatura, grana
    return applyWallFx(out, WallFx(blur = g.blur, vignette = g.vignette, grain = g.grain))
}

/** Colori pronti per la scelta. */
private val PALETTE = listOf(
    0xFFEF5350, 0xFFFF7043, 0xFFFFA726, 0xFFFFCA28, 0xFFD4E157, 0xFF9CCC65,
    0xFF66BB6A, 0xFF26A69A, 0xFF26C6DA, 0xFF29B6F6, 0xFF42A5F5, 0xFF5C6BC0,
    0xFF7E57C2, 0xFFAB47BC, 0xFFEC407A, 0xFFF48FB1, 0xFFB39DDB, 0xFF90CAF9,
    0xFF80DEEA, 0xFFA5D6A7, 0xFFFFE082, 0xFFFFAB91, 0xFFBCAAA4, 0xFF90A4AE,
    0xFF1B5E20, 0xFF0D47A1, 0xFF311B92, 0xFF880E4F, 0xFFBF360C, 0xFF263238
).map { it.toInt() }

@Composable
private fun GenLabel(t: String) {
    Text(t, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

@Composable
private fun GenSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float> = 0f..1f, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(96.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f).height(30.dp))
    }
}

/** Editor di "Crea il tuo sfondo": anteprima in alto, schede con i comandi sotto. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WallGenerator(vm: LauncherViewModel, start: GenWall) {
    val cs = MaterialTheme.colorScheme
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var g by remember(start.id) { mutableStateOf(start) }
    var pickIdx by remember { mutableStateOf(-1) }
    var tab by remember { mutableStateOf(0) }
    BackHandler { vm.genEdit = null }
    val (tw, th) = vm.wallTarget()
    val ratio = tw.toFloat() / th
    val pw = 320
    val ph = (pw / ratio).toInt().coerceAtLeast(320)
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(g) {
        preview = withContext(Dispatchers.Default) { genWall(g, pw, ph).asImageBitmap() }
    }
    // miniature degli stili con i colori scelti
    var thumbs by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    val thumbKey = g.copy(id = "", name = "", style = "", soft = 0.5f, grain = 0f, vignette = 0f, blur = 0f)
    LaunchedEffect(thumbKey) {
        thumbs = withContext(Dispatchers.Default) {
            GEN_STYLES.associate { (k, _) -> k to genWall(thumbKey.copy(style = k), 64, 128).asImageBitmap() }
        }
    }
    // colori dal sistema, dal tema o dallo sfondo attuale
    val sys = remember {
        listOf(
            ctx.getColor(android.R.color.system_accent1_400),
            ctx.getColor(android.R.color.system_accent2_400),
            ctx.getColor(android.R.color.system_accent3_400),
            ctx.getColor(android.R.color.system_accent1_700)
        )
    }
    val theme = listOf(cs.primary.toArgb(), cs.secondary.toArgb(), cs.tertiary.toArgb(), cs.primaryContainer.toArgb())
    val wallCols = remember(vm.wallBitmap) {
        try {
            val b = vm.wallBitmap?.asAndroidBitmap() ?: return@remember null
            val wc = android.app.WallpaperColors.fromBitmap(b)
            val p0 = wc.primaryColor.toArgb()
            listOf(p0, (wc.secondaryColor ?: wc.primaryColor).toArgb(), (wc.tertiaryColor ?: wc.primaryColor).toArgb(), shiftL(p0, -0.25f))
        } catch (e: Throwable) {
            null
        }
    }
    fun withCount(n: Int, from: List<Int>): List<Int> = List(n) { i -> from.getOrNull(i) ?: shiftL(from[i % from.size], if (i % 2 == 0) 0.2f else -0.2f) }
    fun setSource(src: String, list: List<Int>) { g = g.copy(source = src, colors = withCount(g.colors.size, list)) }

    Surface(color = cs.surface, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.genEdit = null }) { Text("Annulla") }
                Text("Crea il tuo sfondo", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton(onClick = {}) { Text(" ", color = Color.Transparent) }
            }
            // anteprima con le proporzioni dello sfondo, e accanto Varia / A caso
            androidx.compose.foundation.layout.BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                val boxW = minOf(maxWidth * 0.62f, maxHeight * ratio)
                val boxH = boxW / ratio
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(boxW, boxH).clip(RoundedCornerShape(22.dp))
                            .border(1.dp, cs.outlineVariant, RoundedCornerShape(22.dp))
                            .background(cs.surfaceContainer)
                    ) {
                        preview?.let {
                            androidx.compose.foundation.Image(
                                it, contentDescription = "Anteprima",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { g = g.copy(seed = (1..1_000_000).random()) }) { Text("Varia") }
                        OutlinedButton(onClick = { g = randomGen(g.id, g.name) }) { Text("A caso") }
                    }
                }
            }
            androidx.compose.material3.PrimaryTabRow(selectedTabIndex = tab, containerColor = cs.surface) {
                listOf("Stile", "Colori", "Forma", "Luce", "Effetti").forEachIndexed { i, l ->
                    androidx.compose.material3.Tab(selected = tab == i, onClick = { tab = i }, text = { Text(l, maxLines = 1) })
                }
            }
            Column(
                Modifier.fillMaxWidth().height(232.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                when (tab) {
                    0 -> Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GEN_STYLES.forEach { (k, l) ->
                            val on = g.style == k
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { g = g.copy(style = k) }) {
                                Box(
                                    Modifier.size(64.dp, 128.dp).clip(RoundedCornerShape(14.dp))
                                        .border(if (on) 3.dp else 1.dp, if (on) cs.primary else cs.outlineVariant, RoundedCornerShape(14.dp))
                                        .background(cs.surfaceContainer)
                                ) {
                                    thumbs[k]?.let { androidx.compose.foundation.Image(it, contentDescription = l, modifier = Modifier.fillMaxSize()) }
                                }
                                Text(l, style = MaterialTheme.typography.labelMedium, color = if (on) cs.primary else cs.onSurface, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                    1 -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Quanti", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(64.dp))
                            (1..4).forEach { n ->
                                FilterChip(
                                    selected = g.colors.size == n,
                                    onClick = { g = g.copy(colors = withCount(n, g.colors)) },
                                    label = { Text("$n") },
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                            }
                        }
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            g.colors.forEachIndexed { i, col ->
                                Box(
                                    Modifier.size(42.dp).clip(CircleShape).background(Color(col))
                                        .border(1.dp, cs.outline, CircleShape)
                                        .clickable { pickIdx = i }
                                )
                            }
                            Text("Tocca per cambiarlo", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = g.source == "mine", onClick = { g = g.copy(source = "mine") }, label = { Text("Miei") })
                            FilterChip(selected = g.source == "system", onClick = { setSource("system", sys) }, label = { Text("Sistema") })
                            FilterChip(selected = g.source == "theme", onClick = { setSource("theme", theme) }, label = { Text("Tema") })
                            if (wallCols != null) FilterChip(selected = g.source == "wall", onClick = { setSource("wall", wallCols) }, label = { Text("Dallo sfondo") })
                        }
                        Text("Tavolozze pronte", style = MaterialTheme.typography.labelLarge, color = cs.primary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        GEN_PALETTES.chunked(4).forEach { row ->
                            Row(Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (n, pal) ->
                                    Column(Modifier.weight(1f).clickable { g = g.copy(source = "mine", colors = withCount(max(g.colors.size, 2), pal)) }) {
                                        Row(Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(12.dp))) {
                                            pal.forEach { pc -> Box(Modifier.weight(1f).fillMaxHeight().background(Color(pc))) }
                                        }
                                        Text(n, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1)
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    2 -> {
                        GenSlider("Morbidezza", g.soft) { g = g.copy(soft = it) }
                        GenSlider("Quantità", g.density) { g = g.copy(density = it) }
                        GenSlider("Direzione", g.angle) { g = g.copy(angle = it) }
                        Text(
                            "Quantità: quante bolle, onde, cerchi, raggi, poligoni, colline o strisce. Direzione: gradiente, strisce e raggi.",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    3 -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                            FilterChip(selected = !g.dark, onClick = { g = g.copy(dark = false) }, label = { Text("Chiaro") })
                            FilterChip(selected = g.dark, onClick = { g = g.copy(dark = true) }, label = { Text("Scuro") })
                        }
                        GenSlider("Luminosità", g.bright, -1f..1f) { g = g.copy(bright = it) }
                        GenSlider("Saturazione", g.sat, -1f..1f) { g = g.copy(sat = it) }
                    }
                    else -> {
                        GenSlider("Grana", g.grain) { g = g.copy(grain = it) }
                        GenSlider("Vignettatura", g.vignette) { g = g.copy(vignette = it) }
                        GenSlider("Sfocatura", g.blur) { g = g.copy(blur = it) }
                    }
                }
            }
            OutlinedTextField(
                value = g.name, onValueChange = { g = g.copy(name = it) },
                singleLine = true, label = { Text("Nome") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { vm.saveMyWall(g, apply = false) }, modifier = Modifier.weight(1f)) { Text("Salva") }
                Button(onClick = { vm.saveMyWall(g, apply = true) }, modifier = Modifier.weight(1f)) { Text("Salva e applica") }
            }
        }
    }

    if (pickIdx >= 0) {
        ColorPickDialog(g.colors.getOrElse(pickIdx) { g.colors.first() }, onDismiss = { pickIdx = -1 }) { picked ->
            g = g.copy(colors = g.colors.toMutableList().also { if (pickIdx < it.size) it[pickIdx] = picked }, source = "mine")
            pickIdx = -1
        }
    }
}

/** Scelta di un colore: tavolozza pronta, tonalità, intensità e luminosità, o codice esadecimale. */
@Composable
private fun ColorPickDialog(start: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val hsl0 = remember { FloatArray(3).also { ColorUtils.colorToHSL(start, it) } }
    var hue by remember { mutableStateOf(hsl0[0]) }
    var sat by remember { mutableStateOf(hsl0[1]) }
    var light by remember { mutableStateOf(hsl0[2]) }
    val current = ColorUtils.HSLToColor(floatArrayOf(hue, sat, light))
    var hex by remember { mutableStateOf("") }
    fun setFrom(c: Int) {
        val h = FloatArray(3)
        ColorUtils.colorToHSL(c, h)
        hue = h[0]; sat = h[1]; light = h[2]
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scegli un colore") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PALETTE.chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { c ->
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                                    .border(if (c == current) 3.dp else 0.dp, cs.primary, CircleShape)
                                    .clickable { setFrom(c) }
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(current)))
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = hex.ifEmpty { String.format("#%06X", current and 0xFFFFFF) },
                        onValueChange = { t ->
                            hex = t
                            val clean = t.trim().removePrefix("#")
                            if (clean.length == 6) clean.toLongOrNull(16)?.let { setFrom((0xFF000000 or it).toInt()); hex = "" }
                        },
                        singleLine = true,
                        label = { Text("Codice") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Text("Tonalità", style = MaterialTheme.typography.labelMedium)
                // striscia arcobaleno sotto il cursore della tonalità
                Box(
                    Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            (0..6).map { Color(ColorUtils.HSLToColor(floatArrayOf(it * 60f, 0.8f, 0.55f))) }
                        )
                    )
                )
                Slider(value = hue, onValueChange = { hue = it; hex = "" }, valueRange = 0f..360f, modifier = Modifier.height(30.dp))
                Text("Intensità", style = MaterialTheme.typography.labelMedium)
                Slider(value = sat, onValueChange = { sat = it; hex = "" }, modifier = Modifier.height(30.dp))
                Text("Luminosità", style = MaterialTheme.typography.labelMedium)
                Slider(value = light, onValueChange = { light = it; hex = "" }, valueRange = 0.05f..0.95f, modifier = Modifier.height(30.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onPick(current) }) { Text("Scegli") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}
