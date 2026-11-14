package com.marco.mlauncher

import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/*
 * Tema «Sensation»: ricrea a mano (solo forme e gradienti, nessuna immagine HTC) la Home dei telefoni HTC
 * con l'interfaccia Sense: la barra a tre tasti in basso, l'orologio analogico con l'anello scuro e il
 * quadrante d'argento, l'orologio a cartellini con il meteo, le caselle di spunta verdi delle impostazioni.
 */

// ---------------------------------------------------------------------
//  Colori e testi
// ---------------------------------------------------------------------

private val SenseText = Color(0xFFECEDEF)
private val SenseShadow = Shadow(Color.Black.copy(alpha = 0.75f), Offset(0f, 2f), 4f)

/** Stile dei testi bianchi appoggiati sullo sfondo (ombra scura sotto, come in Sense). */
private fun senseTextStyle(size: TextUnit, weight: FontWeight = FontWeight.Normal) =
    TextStyle(color = SenseText, fontSize = size, fontWeight = weight, shadow = SenseShadow)

// ---------------------------------------------------------------------
//  Barra a tre tasti (al posto della dock)
// ---------------------------------------------------------------------

/** App del tasto centrale: quella scelta o il Telefono predefinito. */
private class SenseCenter(val app: AppEntry?, val isPhone: Boolean, val label: String)

@Composable
private fun rememberSenseCenter(vm: LauncherViewModel, ctx: Context): SenseCenter {
    val id = vm.settings.senseCenter
    val apps = vm.apps
    val dialerPkg = remember {
        try {
            ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        } catch (e: Exception) {
            null
        }
    }
    return remember(id, apps, dialerPkg) {
        val chosen = if (id.isNotEmpty()) apps[id] else null
        val app = chosen ?: dialerPkg?.let { p -> apps.values.firstOrNull { it.pkg == p && !it.isAction } }
        val phone = app == null || app.pkg == dialerPkg
        SenseCenter(app, phone, if (phone || app == null) "Telefono" else app.label)
    }
}

private fun openDialer(ctx: Context) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        AppLog.quiet("apertura del Telefono", e)
    }
}

/**
 * Barra in basso del tema Sensation, come i telefoni HTC con Sense: a sinistra il tasto del cassetto,
 * al centro l'app scelta (il Telefono se non ne scegli un'altra: tienilo premuto per cambiarla), a destra
 * "+" per personalizzare la Home. Sopra, il bordo curvo d'argento con la linguetta: tirala su per il cassetto.
 */
@Composable
fun SenseBar(vm: LauncherViewModel, host: Host, vertical: Boolean = false) {
    val st = vm.settings
    val view = LocalView.current
    val ctx = LocalContext.current
    val center = rememberSenseCenter(vm, ctx)
    var picker by remember { mutableStateOf(false) }
    var centerBounds by remember { mutableStateOf<Rect?>(null) }
    val openDrawer: () -> Unit = {
        Haptics.tick(view)
        vm.openFolderId = null
        vm.overlay = Overlay.DRAWER
    }
    val personalize: () -> Unit = {
        Haptics.tick(view)
        // "+" apre il menu dei widget, come "Aggiungi alla Home" di Sense
        if (!vm.blockedByLock()) {
            vm.minusPick = false
            vm.stackTarget = null
            vm.overlay = Overlay.WIDGETS
        }
    }
    val launchCenter: () -> Unit = {
        val a = center.app
        if (a != null) host.launch(a, centerBounds) else openDialer(ctx)
    }
    val choose: () -> Unit = {
        Haptics.press(view)
        picker = true
    }
    if (picker) {
        SenseAppPicker(vm, st.senseCenter, onDismiss = { picker = false }) { id ->
            vm.setSetting("senseCenter", id)
            picker = false
        }
    }
    val size = st.dockSize.coerceIn(0.8f, 1.3f)
    if (vertical) {
        val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            Modifier
                .fillMaxHeight()
                .padding(bottom = nav)
                .width(70.dp * size)
                .drawBehind { drawSenseBarBackVertical() }
                .padding(horizontal = 7.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SenseKey(Modifier.weight(1f).fillMaxWidth(), onClick = openDrawer) { SenseDrawerGlyph() }
            SenseKey(
                Modifier.weight(1.6f).fillMaxWidth().onGloballyPositioned { centerBounds = it.boundsInWindow() },
                onClick = launchCenter, onLongClick = choose
            ) { SenseCenterGlyph(center, 26.dp) }
            SenseKey(Modifier.weight(1f).fillMaxWidth(), onClick = personalize) {
                Icon(Icons.Rounded.Add, "Aggiungi widget", tint = SenseText, modifier = Modifier.size(26.dp))
            }
        }
        return
    }
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // bordo superiore ben curvo: ai lati scende di [arc] rispetto al centro
    val arc = 22.dp
    // distanza dei tasti dal bordo curvo (i tasti seguono la stessa curva)
    val keysTop = 12.dp
    val keyH = 54.dp * size
    val swipePx = with(LocalDensity.current) { 36.dp.toPx() }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .height(keysTop + arc + keyH + 8.dp + nav)
            .drawBehind { drawSenseBarBack(arc.toPx()) }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { _, dy -> total += dy },
                    onDragEnd = { if (total < -swipePx && !vm.editMode) openDrawer() }
                )
            }
    ) {
        val barW = maxWidth
        val side = 7.dp
        val gap = 5.dp
        val unit = (barW - side * 2 - gap * 2) / 4.3f
        val widths = listOf(unit, unit * 2.3f, unit)
        val lefts = listOf(side, side + unit + gap, side + unit * 3.3f + gap * 2)
        val boxH = arc + keyH
        // quanto scende la curva al centro di ogni tasto: lì sotto si centra il contenuto
        fun dropAt(left: Dp, w: Dp): Dp {
            val t = 1f - 2f * ((left + w / 2) / barW)
            return arc * (t * t)
        }
        // linguetta d'argento al centro del bordo curvo: si tocca (o si tira su) per il cassetto
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-4).dp)
                .size(50.dp, 10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFF2F3F5), Color(0xFFB9BCC1), Color(0xFF7E8187))))
                .border(1.dp, Color(0xFF44474C), RoundedCornerShape(5.dp))
                .clickable(remember { MutableInteractionSource() }, null) { openDrawer() },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(22.dp, 2.dp).clip(CircleShape).background(Color(0xFF6A6D72)))
        }
        for (i in 0..2) {
            val shape = remember(barW, i) { SenseKeyShape(barW, lefts[i], arc, 14.dp) }
            val mod = Modifier
                .offset(x = lefts[i], y = keysTop)
                .size(widths[i], boxH)
                .then(if (i == 1) Modifier.onGloballyPositioned { centerBounds = it.boundsInWindow() } else Modifier)
            val drop = dropAt(lefts[i], widths[i])
            when (i) {
                0 -> SenseKey(mod, onClick = openDrawer, shape = shape, contentTop = drop) { SenseDrawerGlyph() }
                1 -> SenseKey(mod, onClick = launchCenter, onLongClick = choose, shape = shape, contentTop = drop) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                        SenseCenterGlyph(center, 22.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            center.label,
                            style = senseTextStyle(17.sp, FontWeight.Medium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                else -> SenseKey(mod, onClick = personalize, shape = shape, contentTop = drop) {
                    Icon(Icons.Rounded.Add, "Aggiungi widget", tint = SenseText, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

/**
 * Forma di un tasto della barra: il lato di sopra segue la curva della barra (parabola che sale al centro),
 * gli angoli sono arrotondati. [left] è la posizione del tasto nella barra larga [barW].
 */
private class SenseKeyShape(val barW: Dp, val left: Dp, val arc: Dp, val corner: Dp) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val bw = with(density) { barW.toPx() }
        val l = with(density) { left.toPx() }
        val a = with(density) { arc.toPx() }
        val w = size.width
        val h = size.height
        val r = minOf(with(density) { corner.toPx() }, w / 2f, h / 3f)
        fun k(x: Float): Float {
            val t = 1f - 2f * (l + x) / bw
            return a * t * t
        }
        val q = r * 0.45f
        val p = Path().apply {
            moveTo(0f, k(0f) + r)
            cubicTo(0f, k(0f) + q, q, k(q), r, k(r))
            val steps = 20
            for (i in 1..steps) {
                val x = r + (w - 2f * r) * i / steps
                lineTo(x, k(x))
            }
            cubicTo(w - q, k(w - q), w, k(w) + q, w, k(w) + r)
            lineTo(w, h - r)
            cubicTo(w, h - q, w - q, h, w - r, h)
            lineTo(r, h)
            cubicTo(q, h, 0f, h - q, 0f, h - r)
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(p)
    }
}

/** Fondo della barra: grafite lucida, bordo superiore curvo con il filo d'argento. */
private fun DrawScope.drawSenseBarBack(arc: Float) {
    val w = size.width
    val h = size.height
    // curva verso l'alto al centro (come una quadratica, scritta con una cubica)
    val edge = Path().apply {
        moveTo(0f, arc)
        cubicTo(w / 3f, -arc / 3f, w * 2f / 3f, -arc / 3f, w, arc)
    }
    val body = Path().apply {
        moveTo(0f, arc)
        cubicTo(w / 3f, -arc / 3f, w * 2f / 3f, -arc / 3f, w, arc)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(
        body,
        Brush.verticalGradient(
            0f to Color(0xFF55585D), 0.35f to Color(0xFF2C2E32), 0.7f to Color(0xFF151618), 1f to Color(0xFF0A0A0B)
        )
    )
    // smusso chiaro che segue la curva, come il metallo spazzolato dei telefoni HTC
    clipPath(body) {
        drawPath(edge, Color(0xFF8E9196).copy(alpha = 0.55f), style = Stroke(16.dp.toPx()))
        drawPath(edge, Color(0xFFB4B7BC).copy(alpha = 0.55f), style = Stroke(6.dp.toPx()))
    }
    drawPath(edge, Color(0xFFE2E4E7), style = Stroke(1.5.dp.toPx()))
    translate(top = 1.6.dp.toPx()) { drawPath(edge, Color.Black.copy(alpha = 0.45f), style = Stroke(1.dp.toPx())) }
}

/** Fondo della barra in orizzontale: la stessa grafite, in verticale, col filo d'argento verso la Home. */
private fun DrawScope.drawSenseBarBackVertical() {
    val r = 18.dp.toPx()
    drawRoundRect(
        Brush.horizontalGradient(listOf(Color(0xFF5B5E63), Color(0xFF2A2C30), Color(0xFF141516))),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
    )
    drawRoundRect(
        Color(0xFFB9BCC1),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        style = Stroke(1.dp.toPx())
    )
}

/** Tasto della barra: rilievo grafite bombato con la luce in alto; premuto diventa verde lime, come in Sense. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SenseKey(
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp),
    contentTop: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .clip(shape)
            .background(
                if (pressed) Brush.verticalGradient(listOf(Color(0xFFD2F29A), Color(0xFF9BD14E), Color(0xFF5E9A1C), Color(0xFF3E6E0C)))
                else Brush.verticalGradient(listOf(Color(0xFF6E7176), Color(0xFF45474C), Color(0xFF2A2C2F), Color(0xFF1B1C1E), Color(0xFF121314)))
            )
            // riflesso bombato: un ovale di luce nella parte alta, ombra verso il basso
            .drawBehind {
                drawOval(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.04f)),
                        startY = 0f, endY = size.height * 0.55f
                    ),
                    topLeft = Offset(size.width * 0.06f, -size.height * 0.30f),
                    size = Size(size.width * 0.88f, size.height * 0.85f)
                )
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)),
                        startY = size.height * 0.65f, endY = size.height
                    )
                )
            }
            .border(1.2.dp, Brush.verticalGradient(listOf(Color(0xFFB4B7BC), Color(0xFF4A4C50), Color(0xFF050506))), shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.padding(top = contentTop), contentAlignment = Alignment.Center) { content() }
    }
}

/** Tasto del cassetto: cerchio con la freccia in su. */
@Composable
private fun SenseDrawerGlyph() {
    Box(
        Modifier.size(26.dp).border(1.6.dp, SenseText, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.KeyboardArrowUp, "Tutte le app", tint = SenseText, modifier = Modifier.size(20.dp))
    }
}

/** Disegno del tasto centrale: la cornetta per il Telefono, altrimenti l'icona dell'app. */
@Composable
private fun SenseCenterGlyph(c: SenseCenter, size: Dp) {
    val a = c.app
    if (c.isPhone || a == null) Icon(Icons.Rounded.Call, null, tint = SenseText, modifier = Modifier.size(size))
    else AppIcon(a, size, false)
}

/** Scelta dell'app del tasto centrale (pressione lunga sul tasto o impostazioni del tema). */
@Composable
fun SenseAppPicker(vm: LauncherViewModel, current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    val list = remember(vm.apps) { vm.apps.values.filter { !it.isAction }.sortedBy { it.label.lowercase() } }
    val shown = list.filter { q.isBlank() || it.label.contains(q.trim(), ignoreCase = true) }
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
        title = { Text("Tasto centrale della barra") },
        text = {
            Column {
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    singleLine = true,
                    placeholder = { Text("Cerca un'app") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    if (q.isBlank()) item(key = "phone") {
                        PickRow(null, "Telefono (predefinito)", current.isEmpty()) { onPick("") }
                    }
                    items(shown, key = { it.id }) { a -> PickRow(a, a.label, a.id == current) { onPick(a.id) } }
                }
                if (shown.isEmpty() && q.isNotBlank()) {
                    Text("Nessuna app trovata", color = cs.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                }
            }
        }
    )
}

@Composable
private fun PickRow(app: AppEntry?, label: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app != null) AppIcon(app, 34.dp, false)
        else Box(Modifier.size(34.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Call, null, tint = cs.onPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (selected) Icon(Icons.Rounded.Check, null, tint = cs.primary)
    }
}

/** Nome dell'app del tasto centrale (per le impostazioni). */
@Composable
fun senseCenterLabel(vm: LauncherViewModel): String {
    val c = rememberSenseCenter(vm, LocalContext.current)
    return if (vm.settings.senseCenter.isEmpty()) "Telefono (predefinito)" else c.label
}

// ---------------------------------------------------------------------
//  Orologio analogico Sense
// ---------------------------------------------------------------------

/**
 * Orologio analogico come quello dei primi telefoni HTC con Sense: anello scuro con i numeri grandi,
 * quadrante d'argento con le tacche, finestrelle del giorno e della data, lancette chiare e, sotto,
 * il nome della città su una pillola.
 */
@Composable
fun SenseClockWidget(vm: LauncherViewModel, host: Host, onLongPress: () -> Unit, modifier: Modifier, sample: Boolean = false) {
    val seconds = vm.settings.senseSeconds && !sample
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(seconds) {
        while (true) {
            now = System.currentTimeMillis()
            delay(if (seconds) 1000L - now % 1000L + 5L else 60_000L - now % 60_000L + 30L)
        }
    }
    val measurer = rememberTextMeasurer()
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val minute = cal.get(Calendar.MINUTE) + (if (seconds) cal.get(Calendar.SECOND) / 60f else 0f)
    val hour = cal.get(Calendar.HOUR) + minute / 60f
    val sec = if (seconds) cal.get(Calendar.SECOND).toFloat() else null
    val loc = Locale.getDefault()
    val date = Date(now)
    val day = SimpleDateFormat("EEE", loc).format(date).replace(".", "").uppercase(loc)
    val dayDate = SimpleDateFormat("d MMM", loc).format(date).replace(".", "").uppercase(loc)
    val place = (if (sample) "Roma" else vm.weather?.place)?.takeIf { it.isNotBlank() }
        ?: TimeZone.getDefault().id.substringAfterLast('/').replace('_', ' ')
    Column(
        modifier.senseTap(onClick = { if (!sample) host.openClockApp() }, onLongClick = onLongPress),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp)) {
            drawSenseClock(measurer, hour, minute, sec, day, dayDate)
        }
        Spacer(Modifier.height(if (sample) 2.dp else 6.dp))
        SensePill(place, if (sample) 9.sp else 13.sp)
        Spacer(Modifier.height(2.dp))
    }
}

/** Etichetta su pillola scura (città sotto l'orologio). */
@Composable
private fun SensePill(text: String, size: TextUnit) {
    Text(
        text,
        style = TextStyle(color = SenseText, fontSize = size, fontWeight = FontWeight.Medium),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF55585D), Color(0xFF2A2C30), Color(0xFF18191B))))
            .border(1.dp, Color(0xFF7A7D82), CircleShape)
            .padding(horizontal = 14.dp, vertical = 2.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.senseTap(onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    this.combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
        onLongClick = onLongClick
    )

private fun polar(c: Offset, r: Float, deg: Float): Offset {
    val a = Math.toRadians(deg - 90.0)
    return Offset(c.x + (r * Math.cos(a)).toFloat(), c.y + (r * Math.sin(a)).toFloat())
}

private fun DrawScope.drawSenseClock(tm: TextMeasurer, hour: Float, minute: Float, sec: Float?, day: String, date: String) {
    val r = size.minDimension / 2f * 0.97f
    val c = center
    // ombra sotto l'orologio
    drawCircle(Color.Black.copy(alpha = 0.35f), r, c + Offset(0f, r * 0.04f))
    // anello scuro, più chiaro in alto a sinistra
    drawCircle(
        Brush.linearGradient(
            listOf(Color(0xFF73777D), Color(0xFF35383D), Color(0xFF1A1C1F), Color(0xFF2C2F33)),
            start = c - Offset(r, r), end = c + Offset(r, r)
        ),
        r, c
    )
    drawCircle(Color(0xFFC3C6CB), r - 0.8.dp.toPx(), c, style = Stroke(1.4.dp.toPx()))
    // riflesso sull'anello
    drawArc(
        Color.White.copy(alpha = 0.16f), startAngle = 195f, sweepAngle = 120f, useCenter = false,
        topLeft = c - Offset(r * 0.92f, r * 0.92f), size = Size(r * 1.84f, r * 1.84f),
        style = Stroke(r * 0.06f, cap = StrokeCap.Round)
    )
    // numeri
    for (i in 1..12) {
        val big = i % 3 == 0
        val style = TextStyle(
            color = Color(0xFFEFF1F3),
            fontSize = (r * (if (big) 0.23f else 0.165f)).toSp(),
            fontWeight = if (big) FontWeight.Medium else FontWeight.Normal,
            shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, r * 0.012f), r * 0.02f)
        )
        val layout = tm.measure(i.toString(), style)
        val p = polar(c, r * 0.805f, i * 30f)
        drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
    }
    // quadrante d'argento
    val ri = r * 0.62f
    drawCircle(Color.Black.copy(alpha = 0.5f), ri + r * 0.025f, c)
    drawCircle(
        Brush.radialGradient(
            listOf(Color.White, Color(0xFFEDEFF1), Color(0xFFC4C8CD)),
            center = c - Offset(ri * 0.3f, ri * 0.35f), radius = ri * 1.6f
        ),
        ri, c
    )
    drawCircle(Color(0xFF8E9298), ri, c, style = Stroke(1.dp.toPx()))
    // tacche dei minuti e delle ore
    for (i in 0 until 60) {
        val h = i % 5 == 0
        drawLine(
            if (h) Color(0xFF3B3E43) else Color(0xFF9A9EA4),
            polar(c, ri * 0.95f, i * 6f), polar(c, ri * (if (h) 0.80f else 0.89f), i * 6f),
            strokeWidth = if (h) r * 0.024f else r * 0.009f
        )
    }
    // giorno a sinistra, data a destra
    val ts = TextStyle(color = Color(0xFF5A5E64), fontSize = (r * 0.088f).toSp(), fontWeight = FontWeight.Medium)
    val dl = tm.measure(day, ts)
    drawText(dl, topLeft = Offset(c.x - ri * 0.50f - dl.size.width / 2f, c.y - dl.size.height / 2f))
    val dt = tm.measure(date, ts)
    drawText(dt, topLeft = Offset(c.x + ri * 0.50f - dt.size.width / 2f, c.y - dt.size.height / 2f))
    // lancette
    senseHand(c, hour * 30f, ri * 0.58f, r * 0.10f, r * 0.078f)
    senseHand(c, minute * 6f, r * 0.74f, r * 0.12f, r * 0.058f)
    if (sec != null) {
        val tail = polar(c, r * 0.16f, sec * 6f + 180f)
        val tip = polar(c, r * 0.78f, sec * 6f)
        drawLine(Color.Black.copy(alpha = 0.3f), tail + Offset(0f, r * 0.015f), tip + Offset(0f, r * 0.015f), strokeWidth = r * 0.016f, cap = StrokeCap.Round)
        drawLine(Color(0xFFF2F3F5), tail, tip, strokeWidth = r * 0.014f, cap = StrokeCap.Round)
    }
    // perno
    drawCircle(Color(0xFF2E3135), r * 0.055f, c)
    drawCircle(
        Brush.radialGradient(listOf(Color.White, Color(0xFF9A9EA4)), center = c - Offset(r * 0.015f, r * 0.015f), radius = r * 0.05f),
        r * 0.042f, c
    )
}

/** Lancetta affusolata, chiara con il bordo grigio e un'ombra leggera. */
private fun DrawScope.senseHand(c: Offset, deg: Float, len: Float, back: Float, width: Float) {
    rotate(deg, c) {
        val p = Path().apply {
            moveTo(c.x - width / 2f, c.y + back)
            lineTo(c.x - width * 0.30f, c.y - len)
            lineTo(c.x, c.y - len - width * 0.55f)
            lineTo(c.x + width * 0.30f, c.y - len)
            lineTo(c.x + width / 2f, c.y + back)
            close()
        }
        translate(top = width * 0.25f) { drawPath(p, Color.Black.copy(alpha = 0.28f)) }
        drawPath(p, Brush.horizontalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFD5D8DC)), startX = c.x - width / 2f, endX = c.x + width / 2f))
        drawPath(p, Color(0xFF5A5E64), style = Stroke(0.9.dp.toPx()))
    }
}

// ---------------------------------------------------------------------
//  Orologio a cartellini con il meteo
// ---------------------------------------------------------------------

/**
 * L'orologio più famoso di Sense: ore e minuti su due cartellini bianchi che girano a ogni cambio
 * (con l'animazione del cartellino che cade), sotto la città, il tempo, l'icona, la data e la temperatura.
 */
@Composable
fun SenseFlipWidget(vm: LauncherViewModel, host: Host, onLongPress: () -> Unit, modifier: Modifier, sample: Boolean = false) {
    val ctx = LocalContext.current
    var showDetail by remember { mutableStateOf(false) }
    var showCity by remember { mutableStateOf(false) }
    if (showDetail) WeatherDetailDialog(vm, onCity = { showDetail = false; showCity = true }) { showDetail = false }
    if (showCity) CityPickerDialog(vm, host) { showCity = false }
    LaunchedEffect(vm.weatherReady, sample) {
        if (!sample && vm.weatherReady) {
            vm.refreshWeather()
            while (true) {
                delay(30 * 60_000L)
                vm.refreshWeather(true)
            }
        }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L + 30L)
        }
    }
    val is24 = android.text.format.DateFormat.is24HourFormat(ctx)
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val hh = if (is24) "%02d".format(cal.get(Calendar.HOUR_OF_DAY))
    else cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }.toString()
    val mm = "%02d".format(cal.get(Calendar.MINUTE))
    val ampm = if (is24) null else if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
    val loc = Locale.getDefault()
    val dateStr = SimpleDateFormat("EEE d MMM", loc).format(Date(now)).replace(".", "")
        .replaceFirstChar { it.uppercase(loc) }
    val w: WeatherData? = if (sample) WeatherData(12, 1, true, 16, 10, "Roma", 0L) else vm.weather
    BoxWithConstraints(modifier) {
        val small = sample || maxHeight < 110.dp
        val clockH = maxHeight * 0.60f
        val iconSize = minOf(maxHeight - clockH + 14.dp, maxWidth * 0.24f)
        Column(Modifier.fillMaxSize()) {
            // piastra scura dietro i cartellini
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(clockH)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.45f))))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                    .senseTap(onClick = { if (!sample) host.openClockApp() }, onLongClick = onLongPress)
                    .padding(horizontal = if (small) 4.dp else 8.dp, vertical = if (small) 3.dp else 6.dp)
            ) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(if (small) 4.dp else 8.dp)) {
                    FlipCard(hh, Modifier.weight(1f).fillMaxHeight(), ampm)
                    FlipCard(mm, Modifier.weight(1f).fillMaxHeight())
                }
            }
            // meteo
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .senseTap(
                        onClick = {
                            if (sample) Unit
                            else if (!vm.weatherReady) showCity = true
                            else {
                                vm.refreshWeather()
                                showDetail = true
                            }
                        },
                        onLongClick = onLongPress
                    )
                    .padding(horizontal = 6.dp)
            ) {
                if (w == null) {
                    Text(
                        if (!vm.weatherReady) "Tocca per scegliere la città" else "Carico il meteo…",
                        style = senseTextStyle(12.sp),
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                w.place, style = senseTextStyle(if (small) 9.sp else 15.sp, FontWeight.SemiBold),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                weatherText(w.code), style = senseTextStyle(if (small) 8.sp else 12.sp),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        // l'icona si appoggia sul bordo della piastra, come nell'originale
                        WeatherIcon(w.code, w.isDay, Modifier.offset(y = (-10).dp).size(iconSize))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(dateStr, style = senseTextStyle(if (small) 8.sp else 12.sp), maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${w.temp}°", style = senseTextStyle(if (small) 13.sp else 26.sp, FontWeight.Medium), maxLines = 1)
                                if (!small) {
                                    Spacer(Modifier.width(6.dp))
                                    Column {
                                        Text("Max ${w.hi}°", style = senseTextStyle(10.sp), maxLines = 1)
                                        Text("Min ${w.lo}°", style = senseTextStyle(10.sp), maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Carattere stretto e marcato delle cifre dei cartellini. */
private val FlipFont = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Typeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD))
)

/**
 * Cartellino con due metà: quando il numero cambia la metà di sopra (col numero vecchio) cade in avanti
 * fino a metà corsa, poi la metà di sotto col numero nuovo finisce di scendere.
 */
@Composable
private fun FlipCard(value: String, modifier: Modifier, tag: String? = null) {
    var shown by remember { mutableStateOf(value) }
    var prev by remember { mutableStateOf(value) }
    val anim = remember { Animatable(1f) }
    LaunchedEffect(value) {
        if (value != shown) {
            prev = shown
            shown = value
            anim.snapTo(0f)
            anim.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        }
    }
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val h = maxHeight
        val fs = with(density) { minOf(h * 0.80f, maxWidth * 0.62f).toSp() }
        val p = anim.value
        val cam = 14f * density.density
        FlipHalf(shown, true, h, fs, Modifier.align(Alignment.TopCenter))
        FlipHalf(if (p < 0.5f) prev else shown, false, h, fs, Modifier.align(Alignment.BottomCenter))
        if (p < 0.5f) {
            FlipHalf(
                prev, true, h, fs,
                Modifier.align(Alignment.TopCenter).graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    rotationX = -180f * p
                    cameraDistance = cam
                }
            )
        } else if (p < 1f) {
            FlipHalf(
                shown, false, h, fs,
                Modifier.align(Alignment.BottomCenter).graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    rotationX = 180f * (1f - p)
                    cameraDistance = cam
                }
            )
        }
        // fessura tra le due metà e cerniere ai lati
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(1.5.dp).background(Color(0xFF2B2B2B)))
        val hinge = Brush.verticalGradient(listOf(Color(0xFFB0B3B8), Color(0xFF3A3C40)))
        Box(Modifier.align(Alignment.CenterStart).offset(x = (-1).dp).size(4.dp, h * 0.16f).clip(RoundedCornerShape(2.dp)).background(hinge))
        Box(Modifier.align(Alignment.CenterEnd).offset(x = 1.dp).size(4.dp, h * 0.16f).clip(RoundedCornerShape(2.dp)).background(hinge))
        if (tag != null) {
            Text(
                tag,
                color = Color(0xFF4A4A4A),
                fontSize = with(density) { (h * 0.11f).toSp() },
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 5.dp, bottom = 2.dp)
            )
        }
    }
}

@Composable
private fun FlipHalf(text: String, top: Boolean, h: Dp, fs: TextUnit, modifier: Modifier) {
    val shape = if (top) RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp) else RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(h / 2)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    if (top) listOf(Color(0xFFFFFFFF), Color(0xFFF0F0F0), Color(0xFFDADADA))
                    else listOf(Color(0xFFCFCFCF), Color(0xFFE9E9E9), Color(0xFFF7F7F7))
                )
            )
    ) {
        // il numero intero, alto quanto il cartellino: se ne vede solo la metà giusta
        Box(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(if (top) Alignment.Top else Alignment.Bottom, unbounded = true)
                .height(h),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = Color(0xFF1C1C1C),
                fontSize = fs,
                fontFamily = FlipFont,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                style = TextStyle(lineHeight = fs)
            )
        }
    }
}

// ---------------------------------------------------------------------
//  Impostazioni nello stile Sense
// ---------------------------------------------------------------------

/** Barra del titolo delle impostazioni: grafite lucida con il titolo bianco, come le app HTC. */
@Composable
fun SenseTitleBar(title: String, onBack: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF5E6166), Color(0xFF2E3034), Color(0xFF151618))))
                .statusBarsPadding()
                .height(54.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = Color.White)
            }
            Text(title, style = senseTextStyle(19.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF8E9196)))
    }
}

/** Gruppo di voci come le liste di Sense: striscia grigia col titolo, voci a tutta larghezza separate da un filo. */
@Composable
fun SenseGroup(title: String?, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val dark = cs.surface.luminance() < 0.5f
    Column(Modifier.fillMaxWidth()) {
        if (title != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            if (dark) listOf(Color(0xFF4C4F54), Color(0xFF2B2D31)) else listOf(Color(0xFFE2E4E7), Color(0xFFB9BCC1))
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 5.dp)
            ) {
                Text(
                    title,
                    color = if (dark) Color(0xFFF0F0F0) else Color(0xFF1A1A1A),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().background(cs.outlineVariant),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) { content() }
    }
}

/** Casella di spunta di Sense al posto dell'interruttore: quadratino in rilievo, spunta verde lime. */
@Composable
fun SenseCheck(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val dark = cs.surface.luminance() < 0.5f
    val shape = RoundedCornerShape(4.dp)
    var m = modifier.padding(horizontal = 6.dp)
    if (onCheckedChange != null) m = m.toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
    Box(
        m.size(26.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    if (dark) listOf(Color(0xFF55585D), Color(0xFF2A2C30)) else listOf(Color.White, Color(0xFFD3D6DA))
                )
            )
            .border(1.dp, if (dark) Color(0xFF8A8D92) else Color(0xFF7C8086), shape),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Canvas(Modifier.size(22.dp)) {
                val s = size.minDimension
                val p = Path().apply {
                    moveTo(s * 0.16f, s * 0.52f)
                    lineTo(s * 0.42f, s * 0.78f)
                    lineTo(s * 0.88f, s * 0.20f)
                }
                drawPath(p, Color.Black.copy(alpha = 0.45f), style = Stroke(s * 0.20f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                drawPath(p, SENSE_GREEN, style = Stroke(s * 0.13f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
    }
}

/** La casella in grigio quando la voce dipende da un'altra spenta. */
@Composable
fun SenseCheckDisabled(checked: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.alpha(0.38f)) { SenseCheck(checked, null) }
}
