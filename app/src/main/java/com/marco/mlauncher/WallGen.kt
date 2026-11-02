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

/** Sfocatura veloce: rimpicciolisce e ingrandisce di nuovo con il filtro (simile a una gaussiana). */
private fun softBlur(src: Bitmap, amount: Float): Bitmap {
    if (amount <= 0f) return src
    val f = 1f + amount * 28f
    val w = max(2, (src.width / f).toInt())
    val h = max(2, (src.height / f).toInt())
    var small = Bitmap.createScaledBitmap(src, w, h, true)
    // un secondo passaggio ammorbidisce i bordi dei "quadretti"
    small = Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(small, max(2, w / 2), max(2, h / 2), true), w, h, true)
    return Bitmap.createScaledBitmap(small, src.width, src.height, true)
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
    "waves" to "Onde",
    "linear" to "Gradiente",
    "radial" to "Radiale",
    "bubbles" to "Bolle",
    "diagonal" to "Diagonale"
)

/** Regole di uno sfondo creato: si ridisegna sempre alla risoluzione del telefono. */
data class GenWall(
    val id: String,
    val name: String,
    val style: String = "aurora",
    val c1: Int = 0xFF27F5A6.toInt(),
    val c2: Int = 0xFF3A7BFF.toInt(),
    val c3: Int = 0xFFB14BFF.toInt(),
    /** da dove vengono i colori: "mine", "system", "theme" */
    val source: String = "mine",
    val seed: Int = 1,
    val soft: Float = 0.5f,
    val grain: Float = 0f,
    val dark: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("n", name).put("st", style)
        .put("c1", c1).put("c2", c2).put("c3", c3).put("src", source).put("seed", seed)
        .put("soft", soft.toDouble()).put("gr", grain.toDouble()).put("dk", dark)

    companion object {
        fun fromJson(o: JSONObject) = GenWall(
            o.getString("id"), o.optString("n", "Il mio sfondo"), o.optString("st", "aurora"),
            o.optInt("c1"), o.optInt("c2"), o.optInt("c3"), o.optString("src", "mine"),
            o.optInt("seed", 1), o.optDouble("soft", 0.5).toFloat(), o.optDouble("gr", 0.0).toFloat(), o.optBoolean("dk", true)
        )

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
        "ocean" -> intArrayOf(0xFF19C3E6.toInt(), 0xFF2A6CFF.toInt(), 0xFF00E0B0.toInt()) to true
        "sunset" -> intArrayOf(0xFFFF2E7E.toInt(), 0xFFFFB347.toInt(), 0xFFFF7A00.toInt()) to true
        "night" -> intArrayOf(0xFF4B2BFF.toInt(), 0xFF8E2DE2.toInt(), 0xFF0B3D91.toInt()) to true
        "mint" -> intArrayOf(0xFF4FD1A5.toInt(), 0xFFB6F2FF.toInt(), 0xFF2BB673.toInt()) to false
        "candy" -> intArrayOf(0xFFFFA8D2.toInt(), 0xFFA8C8FF.toInt(), 0xFFC9A8FF.toInt()) to false
        else -> intArrayOf(0xFF27F5A6.toInt(), 0xFF3A7BFF.toInt(), 0xFFB14BFF.toInt()) to true
    }
    return GenWall(java.util.UUID.randomUUID().toString(), "$label (mio)", "aurora", c[0], c[1], c[2], "mine", 1, 0.55f, 0f, dark)
}

private fun withL(c: Int, l: Float, satMul: Float = 1f): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(c, hsl)
    hsl[1] = (hsl[1] * satMul).coerceIn(0f, 1f)
    hsl[2] = l
    return ColorUtils.HSLToColor(hsl)
}

private fun alpha(c: Int, a: Float) = (c and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

/** Disegna lo sfondo creato. */
fun genWall(g: GenWall, w: Int, h: Int): Bitmap {
    val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bm)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = java.util.Random(g.seed.toLong() * 7919L + g.style.hashCode())
    val cols = intArrayOf(g.c1, g.c2, g.c3)
    // base chiara o scura con la tinta del primo colore
    val b1 = if (g.dark) withL(g.c1, 0.07f, 0.8f) else withL(g.c1, 0.94f, 0.6f)
    val b2 = if (g.dark) withL(g.c2, 0.16f, 0.8f) else withL(g.c2, 0.86f, 0.6f)
    val big = max(w, h).toFloat()
    val soft = g.soft.coerceIn(0f, 1f)
    fun base() {
        p.shader = LinearGradient(0f, 0f, w * 0.35f, h.toFloat(), b1, b2, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    }
    fun col(i: Int) = if (g.dark) cols[i % 3] else withL(cols[i % 3], 0.72f)
    when (g.style) {
        "linear" -> {
            val ang = r.nextFloat() * PI.toFloat()
            val dx = kotlin.math.cos(ang) * big / 2f
            val dy = kotlin.math.sin(ang) * big / 2f
            val list = if (g.dark) intArrayOf(withL(g.c1, 0.22f), withL(g.c2, 0.32f), withL(g.c3, 0.18f))
            else intArrayOf(withL(g.c1, 0.82f), withL(g.c2, 0.74f), withL(g.c3, 0.86f))
            p.shader = LinearGradient(w / 2f - dx, h / 2f - dy, w / 2f + dx, h / 2f + dy, list, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
        "radial" -> {
            val cx = w * (0.2f + r.nextFloat() * 0.6f)
            val cy = h * (0.1f + r.nextFloat() * 0.35f)
            val list = if (g.dark) intArrayOf(withL(g.c1, 0.55f), withL(g.c2, 0.25f), withL(g.c3, 0.08f))
            else intArrayOf(withL(g.c1, 0.95f), withL(g.c2, 0.80f), withL(g.c3, 0.66f))
            p.shader = RadialGradient(cx, cy, big * (0.7f + soft * 0.6f), list, floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
        "waves" -> {
            base()
            p.shader = null
            for (i in 0 until 4) {
                val y0 = h * (0.35f + i * 0.15f)
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
                p.shader = LinearGradient(0f, y0 - amp, 0f, h.toFloat(), alpha(cc, 0.55f + 0.2f * (1f - soft)), alpha(cc, 0.15f), Shader.TileMode.CLAMP)
                c.drawPath(path, p)
            }
        }
        "bubbles" -> {
            base()
            val n = 10 + r.nextInt(10)
            repeat(n) { i ->
                val cx = w * r.nextFloat()
                val cy = h * r.nextFloat()
                val rad = big * (0.04f + r.nextFloat() * 0.16f)
                val cc = col(i)
                val a = 0.25f + r.nextFloat() * 0.45f
                // morbidezza: bordo sfumato
                p.shader = RadialGradient(cx, cy, rad, intArrayOf(alpha(cc, a), alpha(cc, a * (1f - soft * 0.7f)), alpha(cc, 0f)),
                    floatArrayOf(0f, 0.7f + 0.25f * (1f - soft), 1f), Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, rad, p)
            }
        }
        "diagonal" -> {
            base()
            val bands = 5 + r.nextInt(4)
            val list = IntArray(bands * 2) { i -> alpha(col(i / 2), if (i % 2 == 0) 0.55f else 0.0f + soft * 0.35f) }
            val stops = FloatArray(bands * 2) { i -> i / (bands * 2f - 1f) }
            p.shader = LinearGradient(0f, 0f, w.toFloat(), h * 0.6f, list, stops, Shader.TileMode.MIRROR)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
        else -> {
            // aurora: grandi macchie sfumate
            base()
            val n = 3 + r.nextInt(2)
            repeat(n) { i ->
                val cx = w * (0.1f + r.nextFloat() * 0.8f)
                val cy = h * (0.05f + (i + r.nextFloat()) / n * 0.9f)
                val rad = big * (0.35f + soft * 0.35f)
                val cc = col(i)
                p.shader = RadialGradient(cx, cy, rad, cc, cc and 0x00FFFFFF, Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, rad, p)
            }
        }
    }
    p.shader = null
    if (g.grain > 0f) {
        val gp = Paint()
        gp.shader = BitmapShader(noiseTile(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        gp.alpha = (g.grain * 0.22f * 255).toInt()
        gp.blendMode = android.graphics.BlendMode.OVERLAY
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), gp)
    }
    return bm
}

/** Colori pronti per la scelta. */
private val PALETTE = listOf(
    0xFFEF5350, 0xFFFF7043, 0xFFFFA726, 0xFFFFCA28, 0xFFD4E157, 0xFF9CCC65,
    0xFF66BB6A, 0xFF26A69A, 0xFF26C6DA, 0xFF29B6F6, 0xFF42A5F5, 0xFF5C6BC0,
    0xFF7E57C2, 0xFFAB47BC, 0xFFEC407A, 0xFFF48FB1, 0xFFB39DDB, 0xFF90CAF9,
    0xFF80DEEA, 0xFFA5D6A7, 0xFFFFE082, 0xFFFFAB91, 0xFFBCAAA4, 0xFF90A4AE,
    0xFF1B5E20, 0xFF0D47A1, 0xFF311B92, 0xFF880E4F, 0xFFBF360C, 0xFF263238
).map { it.toInt() }

/** Editor di "Crea il tuo sfondo". */
@Composable
fun WallGenerator(vm: LauncherViewModel, start: GenWall) {
    val cs = MaterialTheme.colorScheme
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var g by remember(start.id) { mutableStateOf(start) }
    var pickIdx by remember { mutableStateOf(-1) }
    BackHandler { vm.genEdit = null }
    val (tw, th) = vm.wallTarget()
    val pw = 300
    val ph = (pw * th.toFloat() / tw).toInt().coerceAtLeast(300)
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(g) {
        preview = withContext(Dispatchers.Default) { genWall(g, pw, ph).asImageBitmap() }
    }
    // colori dal sistema o dal tema
    val sys = remember {
        intArrayOf(
            ctx.getColor(android.R.color.system_accent1_400),
            ctx.getColor(android.R.color.system_accent2_400),
            ctx.getColor(android.R.color.system_accent3_400)
        )
    }
    val theme = intArrayOf(cs.primary.toArgb(), cs.secondary.toArgb(), cs.tertiary.toArgb())

    Surface(color = cs.surface, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.genEdit = null }) { Text("Annulla") }
                Text("Crea il tuo sfondo", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton(onClick = { g = g.copy(seed = (1..1_000_000).random()) }) { Text("Varia") }
            }
            Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                // anteprima
                Box(
                    Modifier.fillMaxHeight().aspectRatio(tw.toFloat() / th).clip(RoundedCornerShape(22.dp))
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
                Spacer(Modifier.width(12.dp))
                // comandi
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Stile", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                    GEN_STYLES.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { (k, l) ->
                                FilterChip(selected = g.style == k, onClick = { g = g.copy(style = k) }, label = { Text(l) }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Text("Colori", style = MaterialTheme.typography.labelLarge, color = cs.primary, modifier = Modifier.padding(top = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = g.source == "mine", onClick = { g = g.copy(source = "mine") }, label = { Text("Miei") })
                        FilterChip(selected = g.source == "system", onClick = { g = g.copy(source = "system", c1 = sys[0], c2 = sys[1], c3 = sys[2]) }, label = { Text("Sistema") })
                    }
                    FilterChip(selected = g.source == "theme", onClick = { g = g.copy(source = "theme", c1 = theme[0], c2 = theme[1], c3 = theme[2]) }, label = { Text("Dal tema") })
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(g.c1, g.c2, g.c3).forEachIndexed { i, col ->
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(Color(col))
                                    .border(1.dp, cs.outline, CircleShape)
                                    .clickable { pickIdx = i }
                            )
                        }
                    }
                    Text("Morbidezza", style = MaterialTheme.typography.labelLarge, color = cs.primary, modifier = Modifier.padding(top = 6.dp))
                    Slider(value = g.soft, onValueChange = { g = g.copy(soft = it) })
                    Text("Grana", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                    Slider(value = g.grain, onValueChange = { g = g.copy(grain = it) })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = !g.dark, onClick = { g = g.copy(dark = false) }, label = { Text("Chiaro") })
                        FilterChip(selected = g.dark, onClick = { g = g.copy(dark = true) }, label = { Text("Scuro") })
                    }
                    OutlinedTextField(
                        value = g.name, onValueChange = { g = g.copy(name = it) },
                        singleLine = true, label = { Text("Nome") },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { vm.saveMyWall(g, apply = false) }, modifier = Modifier.weight(1f)) { Text("Salva") }
                Button(onClick = { vm.saveMyWall(g, apply = true) }, modifier = Modifier.weight(1f)) { Text("Salva e applica") }
            }
        }
    }

    if (pickIdx >= 0) {
        val cur = when (pickIdx) { 0 -> g.c1; 1 -> g.c2; else -> g.c3 }
        ColorPickDialog(cur, onDismiss = { pickIdx = -1 }) { picked ->
            g = when (pickIdx) {
                0 -> g.copy(c1 = picked, source = "mine")
                1 -> g.copy(c2 = picked, source = "mine")
                else -> g.copy(c3 = picked, source = "mine")
            }
            pickIdx = -1
        }
    }
}

/** Scelta di un colore: tavolozza pronta più tonalità e luminosità regolabili. */
@Composable
private fun ColorPickDialog(start: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val hsl0 = remember { FloatArray(3).also { ColorUtils.colorToHSL(start, it) } }
    var hue by remember { mutableStateOf(hsl0[0]) }
    var sat by remember { mutableStateOf(hsl0[1]) }
    var light by remember { mutableStateOf(hsl0[2]) }
    val current = ColorUtils.HSLToColor(floatArrayOf(hue, sat, light))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scegli un colore") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PALETTE.chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { c ->
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                                    .border(if (c == current) 3.dp else 0.dp, cs.primary, CircleShape)
                                    .clickable {
                                        val h = FloatArray(3)
                                        ColorUtils.colorToHSL(c, h)
                                        hue = h[0]; sat = h[1]; light = h[2]
                                    }
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(current)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Tonalità", style = MaterialTheme.typography.labelMedium)
                        Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f, modifier = Modifier.height(30.dp))
                        Text("Intensità", style = MaterialTheme.typography.labelMedium)
                        Slider(value = sat, onValueChange = { sat = it }, modifier = Modifier.height(30.dp))
                        Text("Luminosità", style = MaterialTheme.typography.labelMedium)
                        Slider(value = light, onValueChange = { light = it }, valueRange = 0.05f..0.95f, modifier = Modifier.height(30.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(current) }) { Text("Scegli") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}
