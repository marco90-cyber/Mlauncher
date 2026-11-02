package com.marco.mlauncher

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/** Filtri pronti. */
private val FILTERS = listOf(
    "none" to "Originale",
    "vivid" to "Vivace",
    "warm" to "Caldo",
    "cool" to "Freddo",
    "fade" to "Sbiadito",
    "mono" to "Bianco e nero",
    "sepia" to "Seppia",
    "night" to "Notte"
)

/**
 * Matrice colore 4x5 (formato di Android) da regolazioni e filtro.
 * [bright] e [contrast] e [sat] e [warm] vanno da -1 a 1 (0 = invariato).
 */
fun wallColorMatrix(bright: Float, contrast: Float, sat: Float, warm: Float, filter: String): FloatArray? {
    if (bright == 0f && contrast == 0f && sat == 0f && warm == 0f && filter == "none") return null
    val m = android.graphics.ColorMatrix()
    // filtro
    when (filter) {
        "vivid" -> m.setSaturation(1.45f)
        "mono" -> m.setSaturation(0f)
        "fade" -> m.setSaturation(0.6f)
        "sepia" -> m.set(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }
    fun post(a: FloatArray) = m.postConcat(android.graphics.ColorMatrix(a))
    when (filter) {
        "warm" -> post(floatArrayOf(1.12f, 0f, 0f, 0f, 8f, 0f, 1.02f, 0f, 0f, 0f, 0f, 0f, 0.86f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
        "cool" -> post(floatArrayOf(0.9f, 0f, 0f, 0f, 0f, 0f, 1.0f, 0f, 0f, 4f, 0f, 0f, 1.14f, 0f, 10f, 0f, 0f, 0f, 1f, 0f))
        "fade" -> post(floatArrayOf(0.85f, 0f, 0f, 0f, 28f, 0f, 0.85f, 0f, 0f, 28f, 0f, 0f, 0.85f, 0f, 30f, 0f, 0f, 0f, 1f, 0f))
        "night" -> post(floatArrayOf(0.6f, 0f, 0f, 0f, -10f, 0f, 0.65f, 0f, 0f, -6f, 0f, 0f, 0.85f, 0f, 6f, 0f, 0f, 0f, 1f, 0f))
    }
    // saturazione
    if (sat != 0f) m.postConcat(android.graphics.ColorMatrix().apply { setSaturation(1f + sat) })
    // contrasto intorno al grigio medio
    if (contrast != 0f) {
        val c = 1f + contrast * 0.6f
        val t = 128f * (1f - c)
        post(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f))
    }
    // luminosità
    if (bright != 0f) {
        val b = bright * 80f
        post(floatArrayOf(1f, 0f, 0f, 0f, b, 0f, 1f, 0f, 0f, b, 0f, 0f, 1f, 0f, b, 0f, 0f, 0f, 1f, 0f))
    }
    // calore: più rosso e meno blu (o il contrario)
    if (warm != 0f) {
        val w = warm * 0.15f
        post(floatArrayOf(1f + w, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f - w, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
    }
    return m.array.copyOf()
}

private fun rotated(b: Bitmap, deg: Float, flip: Boolean): Bitmap {
    val mx = android.graphics.Matrix()
    if (flip) mx.postScale(-1f, 1f)
    if (deg != 0f) mx.postRotate(deg)
    return Bitmap.createBitmap(b, 0, 0, b.width, b.height, mx, true)
}

/** Editor dello sfondo: ritaglio con dita (sposta e pizzica), rotazione, specchio, regolazioni, filtri ed effetti. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WallpaperEditor(vm: LauncherViewModel, source: Bitmap) {
    val cs = MaterialTheme.colorScheme
    var bmp by remember(source) { mutableStateOf(source) }
    val img: ImageBitmap = remember(bmp) { bmp.asImageBitmap() }
    var zoom by remember(bmp) { mutableFloatStateOf(1f) }
    var pan by remember(bmp) { mutableStateOf(Offset.Zero) }
    var bright by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(0f) }
    var sat by remember { mutableFloatStateOf(0f) }
    var warm by remember { mutableFloatStateOf(0f) }
    var filter by remember { mutableStateOf("none") }
    var fx by remember { mutableStateOf(WallFx()) }
    var tab by remember { mutableIntStateOf(0) }
    val matrix = wallColorMatrix(bright, contrast, sat, warm, filter)
    val (tw, th) = vm.wallTarget()
    val ratio = tw.toFloat() / th
    val noise = remember { noiseTile().asImageBitmap() }
    BackHandler { vm.closeWallEditor() }

    Surface(color = cs.surface, contentColor = cs.onSurface, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.closeWallEditor() }) { Text("Annulla") }
                Text("Modifica sfondo", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton(onClick = {}) { Text(" ", color = Color.Transparent) }
            }
            // area di ritaglio
            // clipToBounds: l'immagine ingrandita non deve coprire schede e pulsanti sotto
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().clipToBounds()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(cs.surfaceContainerLowest)
            ) {
                val aw = constraints.maxWidth.toFloat()
                val ah = constraints.maxHeight.toFloat()
                // cornice con la proporzione dello sfondo (un po' più larga dello schermo per la parallasse)
                val fw: Float
                val fh: Float
                if (aw / ah > ratio) { fh = ah; fw = ah * ratio } else { fw = aw; fh = aw / ratio }
                val fl = (aw - fw) / 2f
                val ft = (ah - fh) / 2f
                val base = max(fw / bmp.width, fh / bmp.height)
                val sc = base * zoom
                val iw = bmp.width * sc
                val ih = bmp.height * sc
                // l'immagine deve sempre coprire la cornice
                fun clampPan(p: Offset): Offset {
                    // max(0) evita intervalli vuoti per gli arrotondamenti quando l'immagine combacia con la cornice
                    val maxX = max(0f, (iw - fw) / 2f)
                    val maxY = max(0f, (ih - fh) / 2f)
                    return Offset(p.x.coerceIn(-maxX, maxX), p.y.coerceIn(-maxY, maxY))
                }
                val clampNow = androidx.compose.runtime.rememberUpdatedState<(Offset) -> Offset> { o -> clampPan(o) }
                val p = clampPan(pan)
                val il = fl + (fw - iw) / 2f + p.x
                val itop = ft + (fh - ih) / 2f + p.y
                // mosaico in anteprima: la stessa immagine rimpicciolita e ingrandita senza filtro
                val mosaicStep = (fx.mosaic * 20).roundToInt()
                val mosaicImg = remember(bmp, mosaicStep, (zoom * 4).roundToInt()) {
                    if (mosaicStep == 0) null else {
                        val cropW = bmp.width / zoom
                        val block = max(2f, (mosaicStep / 20f) * cropW / 24f)
                        Bitmap.createScaledBitmap(bmp, max(1, (bmp.width / block).toInt()), max(1, (bmp.height / block).toInt()), true).asImageBitmap()
                    }
                }
                val density = LocalDensity.current
                // immagine (con sfocatura in anteprima)
                Canvas(
                    Modifier.fillMaxSize()
                        .then(if (fx.blur > 0f) Modifier.blur((fx.blur * 16).dp * (fw / with(density) { 360.dp.toPx() }).coerceIn(0.4f, 1.5f)) else Modifier)
                ) {
                    drawImage(
                        mosaicImg ?: img,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize((mosaicImg ?: img).width, (mosaicImg ?: img).height),
                        dstOffset = IntOffset(il.roundToInt(), itop.roundToInt()),
                        dstSize = IntSize(iw.roundToInt(), ih.roundToInt()),
                        colorFilter = matrix?.let { ColorFilter.colorMatrix(ColorMatrix(it)) },
                        filterQuality = if (mosaicImg != null) androidx.compose.ui.graphics.FilterQuality.None else androidx.compose.ui.graphics.FilterQuality.Low
                    )
                }
                // effetti, cornice e gesti
                Canvas(
                    Modifier.fillMaxSize().pointerInput(bmp) {
                        detectTransformGestures { _, panChange, zoomChange, _ ->
                            zoom = (zoom * zoomChange).coerceIn(1f, 5f)
                            pan = clampNow.value(pan + panChange)
                        }
                    }
                ) {
                    val fr = androidx.compose.ui.geometry.Rect(fl, ft, fl + fw, ft + fh)
                    clipRect(fr.left, fr.top, fr.right, fr.bottom) {
                        if (fx.vignette > 0f) drawRect(
                            Brush.radialGradient(
                                0f to Color.Transparent, 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = fx.vignette * 0.8f),
                                center = fr.center, radius = kotlin.math.hypot(fw, fh) / 2f
                            ),
                            fr.topLeft, fr.size
                        )
                        if (fx.fade > 0f) drawRect(
                            Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = fx.fade * 0.75f), startY = ft + fh * 0.5f, endY = ft + fh),
                            Offset(fl, ft + fh * 0.5f), Size(fw, fh * 0.5f)
                        )
                        if (fx.dim > 0f) drawRect(Color.Black.copy(alpha = fx.dim * 0.7f), fr.topLeft, fr.size)
                        if (fx.grain > 0f) drawRect(
                            ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated)),
                            fr.topLeft, fr.size, alpha = fx.grain * 0.22f, blendMode = BlendMode.Overlay
                        )
                    }
                    // fuori dalla cornice: scuro (si vede bene cosa si taglia anche con il tema chiaro)
                    val outside = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                        addRect(fr)
                    }
                    drawPath(outside, Color.Black.copy(alpha = 0.55f))
                    // cornice e griglia dei terzi
                    drawRect(Color.White, Offset(fl, ft), Size(fw, fh), style = Stroke(2.dp.toPx()))
                    for (i in 1..2) {
                        drawLine(Color.White.copy(alpha = 0.35f), Offset(fl + fw * i / 3f, ft), Offset(fl + fw * i / 3f, ft + fh), 1f)
                        drawLine(Color.White.copy(alpha = 0.35f), Offset(fl, ft + fh * i / 3f), Offset(fl + fw, ft + fh * i / 3f), 1f)
                    }
                    // parte visibile senza parallasse (lo schermo vero)
                    val sw = fw / WALL_WIDE
                    drawRect(Color.White.copy(alpha = 0.5f), Offset(fl + (fw - sw) / 2f, ft), Size(sw, fh), style = Stroke(1.dp.toPx()))
                }
                if (tab == 0) Text(
                    "Sposta e pizzica per ritagliare",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 12.dp, vertical = 4.dp)
                )
                // per il salvataggio: rettangolo della cornice in pixel dell'immagine
                SaveSlot.crop = RectF(
                    ((fl - il) / sc).coerceIn(0f, bmp.width.toFloat()),
                    ((ft - itop) / sc).coerceIn(0f, bmp.height.toFloat()),
                    ((fl + fw - il) / sc).coerceIn(0f, bmp.width.toFloat()),
                    ((ft + fh - itop) / sc).coerceIn(0f, bmp.height.toFloat())
                )
            }
            // schede, come nelle impostazioni
            androidx.compose.material3.PrimaryTabRow(selectedTabIndex = tab, containerColor = cs.surface) {
                listOf("Ritaglia", "Regola", "Filtri", "Effetti").forEachIndexed { i, l ->
                    androidx.compose.material3.Tab(selected = tab == i, onClick = { tab = i }, text = { Text(l) })
                }
            }
            Column(
                Modifier.fillMaxWidth().height(196.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                when (tab) {
                    0 -> Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { bmp = rotated(bmp, -90f, false) }, modifier = Modifier.weight(1f)) { Text("\u21ba Ruota") }
                        OutlinedButton(onClick = { bmp = rotated(bmp, 90f, false) }, modifier = Modifier.weight(1f)) { Text("Ruota \u21bb") }
                        OutlinedButton(onClick = { bmp = rotated(bmp, 0f, true) }, modifier = Modifier.weight(1f)) { Text("Specchia") }
                    }
                    1 -> {
                        EditSlider("Luminosità", bright) { bright = it }
                        EditSlider("Contrasto", contrast) { contrast = it }
                        EditSlider("Saturazione", sat) { sat = it }
                        EditSlider("Calore", warm) { warm = it }
                    }
                    2 -> Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val thumb = remember(bmp) {
                            val k = 120f / max(bmp.width, bmp.height)
                            Bitmap.createScaledBitmap(bmp, max(1, (bmp.width * k).toInt()), max(1, (bmp.height * k).toInt()), true).asImageBitmap()
                        }
                        FILTERS.forEach { (k, l) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { filter = k }) {
                                androidx.compose.foundation.Image(
                                    thumb, contentDescription = l,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    colorFilter = wallColorMatrix(0f, 0f, 0f, 0f, k)?.let { ColorFilter.colorMatrix(ColorMatrix(it)) },
                                    modifier = Modifier.size(64.dp, 88.dp).clip(RoundedCornerShape(12.dp))
                                        .border(if (filter == k) 3.dp else 0.dp, if (filter == k) cs.primary else Color.Transparent, RoundedCornerShape(12.dp))
                                )
                                Text(l, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                    else -> {
                        FxSlider("Sfoca", fx.blur) { fx = fx.copy(blur = it) }
                        FxSlider("Scurisci", fx.dim) { fx = fx.copy(dim = it) }
                        FxSlider("Vignettatura", fx.vignette) { fx = fx.copy(vignette = it) }
                        FxSlider("Sfumatura in basso", fx.fade) { fx = fx.copy(fade = it) }
                        FxSlider("Grana", fx.grain) { fx = fx.copy(grain = it) }
                        FxSlider("Mosaico", fx.mosaic) { fx = fx.copy(mosaic = it) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    zoom = 1f; pan = Offset.Zero; bright = 0f; contrast = 0f; sat = 0f; warm = 0f; filter = "none"; fx = WallFx()
                }, modifier = Modifier.weight(1f)) { Text("Ripristina") }
                Button(onClick = { vm.applyWallEdit(bmp, SaveSlot.crop, matrix, fx) }, modifier = Modifier.weight(1f)) { Text("Applica") }
            }
        }
    }
}

@Composable
private fun FxSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(112.dp))
        Slider(value = value, onValueChange = onChange, valueRange = 0f..1f, modifier = Modifier.weight(1f).height(32.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (value == 0f) "No" else "${(value * 100).roundToInt()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(30.dp))
    }
}

/** Ultimo ritaglio calcolato (aggiornato a ogni disegno dell'editor). */
private object SaveSlot {
    var crop = RectF()
}

@Composable
private fun EditSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(92.dp))
        Slider(value = value, onValueChange = onChange, valueRange = -1f..1f, modifier = Modifier.weight(1f).height(32.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (value == 0f) "0" else "${(value * 100).roundToInt()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(30.dp))
    }
}
