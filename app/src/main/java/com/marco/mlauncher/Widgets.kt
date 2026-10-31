package com.marco.mlauncher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.TextView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// ---------------------------------------------------------------------
//  Host dei widget Android veri, con riconoscimento del tocco prolungato
// ---------------------------------------------------------------------

class MWidgetHost(ctx: Context) : AppWidgetHost(ctx, 1024) {
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView = LongPressHostView(context)
}

class LongPressHostView(ctx: Context) : AppWidgetHostView(ctx) {
    var onLongPress: (() -> Unit)? = null
    private var fired = false
    private var downX = 0f
    private var downY = 0f
    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val runnable = Runnable {
        fired = true
        onLongPress?.invoke()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fired = false
                downX = ev.x
                downY = ev.y
                handler.postDelayed(runnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) handler.removeCallbacks(runnable)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(runnable)
        }
        return fired
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean = fired || super.onTouchEvent(ev)
}

// ---------------------------------------------------------------------
//  Contenuto di un widget
// ---------------------------------------------------------------------

@Composable
fun WidgetContent(
    ref: WidgetRef,
    vm: LauncherViewModel,
    host: Host,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    sample: Boolean = false
) {
    when (ref.kind) {
        "clock" -> ClockWidget(host, onLongPress, modifier)
        "analog" -> AnalogClockWidget(host, onLongPress, modifier)
        "battery" -> BatteryWidget(host, onLongPress, modifier)
        "search" -> SearchWidget(vm, onLongPress, modifier, sample)
        "notes" -> NotesWidget(ref, vm, onLongPress, modifier, sample)
        "weather" -> WeatherWidget(vm, host, onLongPress, modifier, sample)
        else -> ExtWidget(ref, vm, onLongPress, modifier)
    }
}

// ---------------------------------------------------------------------
//  Barra di ricerca (widget)
// ---------------------------------------------------------------------

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SearchWidget(vm: LauncherViewModel, onLongPress: () -> Unit, modifier: Modifier, sample: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val h = minOf(maxHeight, if (sample) 30.dp else 50.dp)
        val compact = maxWidth < 120.dp
        Row(
            Modifier
                .fillMaxWidth()
                .height(h)
                .clip(CircleShape)
                .background(cs.surfaceContainerHigh.copy(alpha = 0.9f))
                .combinedClickable(
                    onClick = {
                        if (!sample) {
                            vm.searchFocus = true
                            vm.overlay = Overlay.DRAWER
                        }
                    },
                    onLongClick = onLongPress
                )
                .padding(horizontal = if (sample) 10.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Default.Search,
                contentDescription = "Cerca",
                tint = cs.primary,
                modifier = Modifier.size(if (sample) 16.dp else 22.dp)
            )
            if (!compact) {
                Spacer(Modifier.width(if (sample) 6.dp else 12.dp))
                Text(
                    "Cerca app e altro",
                    color = cs.onSurfaceVariant,
                    fontSize = if (sample) 10.sp else 15.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun Dp.asSp(): TextUnit = with(LocalDensity.current) { this@asSp.toSp() }

// ---------------------------------------------------------------------
//  Orologio
// ---------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClockWidget(host: Host, onLongPress: () -> Unit, modifier: Modifier) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - (now % 60_000L) + 30L)
        }
    }
    val is24 = DateFormat.is24HourFormat(ctx)
    val date = Date(now)
    val loc = Locale.getDefault()
    val hh = SimpleDateFormat(if (is24) "HH" else "hh", loc).format(date)
    val mm = SimpleDateFormat("mm", loc).format(date)
    val day = SimpleDateFormat("EEEE d MMMM", loc).format(date).replaceFirstChar { it.uppercase() }

    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(cs.primaryContainer)
            .combinedClickable(onClick = { host.openClockApp() }, onLongClick = onLongPress)
    ) {
        val w = maxWidth
        val h = maxHeight
        if (w >= h * 1.5f) {
            // spazio disponibile: 62% all'ora, il resto alla data; con poco spazio uso giorno e mese abbreviati
            val availW = w - 36.dp
            val short = availW < 300.dp
            val dayName = SimpleDateFormat(if (short) "EEE" else "EEEE", loc).format(date)
                .replaceFirstChar { it.uppercase() }
            val dayDate = SimpleDateFormat(if (short) "d MMM" else "d MMMM", loc).format(date)
            val fsDp = minOf(h * 0.50f, availW * 0.60f / 2.75f)
            val dateDp = minOf(h * 0.16f, availW * 0.34f / (if (short) 3.6f else 5.6f)).coerceAtLeast(11.dp)
            Row(
                Modifier.fillMaxSize().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = cs.onPrimaryContainer)) { append(hh) }
                        withStyle(SpanStyle(color = cs.primary)) { append(":$mm") }
                    },
                    fontSize = fsDp.asSp(),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        dayName,
                        color = cs.onPrimaryContainer,
                        fontSize = dateDp.asSp(),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        dayDate,
                        color = cs.onPrimaryContainer.copy(alpha = 0.8f),
                        fontSize = dateDp.asSp(),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        } else {
            val showDate = h >= 120.dp
            val fsDp = if (showDate) minOf(w * 0.34f, h * 0.30f) else minOf(w * 0.40f, h * 0.38f)
            val fs = fsDp.asSp()
            val dayShort = SimpleDateFormat("EEE d MMM", loc).format(date).replaceFirstChar { it.uppercase() }
            Column(
                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    hh,
                    color = cs.onPrimaryContainer,
                    fontSize = fs,
                    lineHeight = fs * 0.92f,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    mm,
                    color = cs.primary,
                    fontSize = fs,
                    lineHeight = fs * 0.92f,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                if (showDate) {
                    Text(
                        dayShort,
                        color = cs.onPrimaryContainer,
                        fontSize = minOf(h * 0.085f, w * 0.085f).coerceAtLeast(11.dp).asSp(),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
//  Meteo
// ---------------------------------------------------------------------

fun weatherText(code: Int): String = when (code) {
    0 -> "Sereno"
    1 -> "Quasi sereno"
    2 -> "Poco nuvoloso"
    3 -> "Nuvoloso"
    45, 48 -> "Nebbia"
    51, 53, 55 -> "Pioggerella"
    56, 57, 66, 67 -> "Pioggia gelata"
    61 -> "Pioggia debole"
    63 -> "Pioggia"
    65 -> "Pioggia forte"
    71, 73, 75, 77 -> "Neve"
    80, 81, 82 -> "Rovesci"
    85, 86 -> "Rovesci di neve"
    95, 96, 99 -> "Temporale"
    else -> "Meteo"
}

private fun weatherKind(code: Int): Int = when (code) {
    0 -> 0
    1, 2 -> 1
    3 -> 2
    45, 48 -> 3
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> 4
    71, 73, 75, 77, 85, 86 -> 5
    95, 96, 99 -> 6
    else -> 2
}

private val sunColor = Color(0xFFFFB300)
private val moonColor = Color(0xFFFFE082)
private val cloudColor = Color(0xFF90A4AE)
private val rainColor = Color(0xFF42A5F5)
private val snowColor = Color(0xFF81D4FA)
private val boltColor = Color(0xFFFFC107)

private fun DrawScope.drawSun(c: Offset, r: Float, color: Color) {
    drawCircle(color, r * 0.62f, c)
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        val dx = Math.cos(a).toFloat()
        val dy = Math.sin(a).toFloat()
        drawLine(
            color,
            Offset(c.x + dx * r * 0.88f, c.y + dy * r * 0.88f),
            Offset(c.x + dx * r * 1.2f, c.y + dy * r * 1.2f),
            strokeWidth = r * 0.16f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawMoon(c: Offset, r: Float, color: Color) {
    val a = Path().apply { addOval(Rect(Offset(c.x - r * 0.8f, c.y - r * 0.8f), Size(r * 1.6f, r * 1.6f))) }
    val b = Path().apply {
        addOval(Rect(Offset(c.x - r * 0.35f, c.y - r * 1.0f), Size(r * 1.5f, r * 1.5f)))
    }
    drawPath(Path.combine(PathOperation.Difference, a, b), color)
}

private fun DrawScope.drawCloud(x: Float, y: Float, w: Float, color: Color) {
    val h = w * 0.6f
    drawCircle(color, h * 0.38f, Offset(x + w * 0.30f, y + h * 0.55f))
    drawCircle(color, h * 0.48f, Offset(x + w * 0.56f, y + h * 0.45f))
    drawRoundRect(color, Offset(x + w * 0.08f, y + h * 0.5f), Size(w * 0.84f, h * 0.5f), CornerRadius(h * 0.25f))
}

@Composable
fun WeatherIcon(code: Int, isDay: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        when (weatherKind(code)) {
            0 -> if (isDay) drawSun(Offset(s / 2, s / 2), s * 0.3f, sunColor)
            else drawMoon(Offset(s / 2, s / 2), s * 0.3f, moonColor)
            1 -> {
                if (isDay) drawSun(Offset(s * 0.62f, s * 0.36f), s * 0.2f, sunColor)
                else drawMoon(Offset(s * 0.6f, s * 0.36f), s * 0.2f, moonColor)
                drawCloud(s * 0.06f, s * 0.34f, s * 0.86f, cloudColor)
            }
            2 -> drawCloud(s * 0.04f, s * 0.22f, s * 0.92f, cloudColor)
            3 -> {
                drawCloud(s * 0.1f, s * 0.08f, s * 0.8f, cloudColor)
                for (i in 0 until 3) {
                    val y = s * (0.68f + i * 0.11f)
                    drawLine(cloudColor, Offset(s * 0.2f, y), Offset(s * 0.8f, y), strokeWidth = s * 0.05f, cap = StrokeCap.Round)
                }
            }
            4 -> {
                drawCloud(s * 0.06f, s * 0.08f, s * 0.88f, cloudColor)
                for (i in 0 until 3) {
                    val x = s * (0.3f + i * 0.2f)
                    drawLine(rainColor, Offset(x, s * 0.7f), Offset(x - s * 0.06f, s * 0.9f), strokeWidth = s * 0.06f, cap = StrokeCap.Round)
                }
            }
            5 -> {
                drawCloud(s * 0.06f, s * 0.08f, s * 0.88f, cloudColor)
                for (i in 0 until 3) {
                    drawCircle(snowColor, s * 0.045f, Offset(s * (0.3f + i * 0.2f), s * (0.76f + (i % 2) * 0.09f)))
                }
            }
            else -> {
                drawCloud(s * 0.06f, s * 0.06f, s * 0.88f, Color(0xFF607D8B))
                val bolt = Path().apply {
                    moveTo(s * 0.54f, s * 0.58f)
                    lineTo(s * 0.40f, s * 0.80f)
                    lineTo(s * 0.50f, s * 0.80f)
                    lineTo(s * 0.44f, s * 0.97f)
                    lineTo(s * 0.64f, s * 0.72f)
                    lineTo(s * 0.53f, s * 0.72f)
                    close()
                }
                drawPath(bolt, boltColor)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeatherWidget(
    vm: LauncherViewModel,
    host: Host,
    onLongPress: () -> Unit,
    modifier: Modifier,
    sample: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
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
    val w: WeatherData? = if (sample) WeatherData(22, 0, true, 25, 15, "Roma", 0L) else vm.weather
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(cs.tertiaryContainer)
            .combinedClickable(
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
    ) {
        val bw = maxWidth
        val bh = maxHeight
        if (w == null) {
            Box(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (!vm.weatherReady) "Tocca per scegliere la città o usare la posizione" else "Carico il meteo…",
                    color = cs.onTertiaryContainer,
                    fontSize = 13.sp,
                    maxLines = 3
                )
            }
        } else if (bw >= bh * 1.5f) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WeatherIcon(w.code, w.isDay, Modifier.size(bh * 0.62f))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "${w.temp}°",
                        color = cs.onTertiaryContainer,
                        fontSize = (bh * 0.40f).asSp(),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(weatherText(w.code), color = cs.onTertiaryContainer, fontSize = 14.sp, maxLines = 1)
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    if (w.place.isNotEmpty()) {
                        Text(w.place, color = cs.onTertiaryContainer, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("Max ${w.hi}°  Min ${w.lo}°", color = cs.onTertiaryContainer.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1)
                }
            }
        } else {
            val side = if (bw < bh) bw else bh
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    WeatherIcon(w.code, w.isDay, Modifier.size(side * 0.36f))
                    Spacer(Modifier.weight(1f))
                    if (w.place.isNotEmpty() && bh > 100.dp) {
                        Text(w.place, color = cs.onTertiaryContainer, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${w.temp}°",
                    color = cs.onTertiaryContainer,
                    fontSize = (side * 0.30f).asSp(),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(weatherText(w.code), color = cs.onTertiaryContainer, fontSize = 13.sp, maxLines = 1)
                if (bh > 120.dp) {
                    Text("${w.hi}° / ${w.lo}°", color = cs.onTertiaryContainer.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
//  Widget Android veri
// ---------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExtWidget(ref: WidgetRef, vm: LauncherViewModel, onLongPress: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val info = remember(ref.widgetId) {
        try {
            vm.widgetMgr.getAppWidgetInfo(ref.widgetId)
        } catch (e: Exception) {
            null
        }
    }
    if (info == null) {
        Box(
            modifier
                .clip(RoundedCornerShape(24.dp))
                .background(cs.errorContainer)
                .combinedClickable(onClick = {}, onLongClick = onLongPress),
            contentAlignment = Alignment.Center
        ) {
            Text("Widget non disponibile", color = cs.onErrorContainer, fontSize = 12.sp)
        }
        return
    }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(24.dp))) {
        val wDp = maxWidth.value.toInt()
        val hDp = maxHeight.value.toInt()
        val lp = rememberUpdatedState(onLongPress)
        AndroidView(
            factory = { c ->
                try {
                    val v = vm.widgetHost.createView(c, ref.widgetId, info)
                    (v as? LongPressHostView)?.onLongPress = { lp.value() }
                    v
                } catch (e: Exception) {
                    TextView(c).apply { text = "Widget non disponibile" }
                }
            },
            update = { v ->
                (v as? AppWidgetHostView)?.updateAppWidgetSize(null, wDp, hDp, wDp, hDp)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ---------------------------------------------------------------------
//  Stack di widget (scorrimento orizzontale)
// ---------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StackContent(
    item: StackItem,
    vm: LauncherViewModel,
    host: Host,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val pager = rememberPagerState { item.refs.size }
    val auto = vm.settings.stackAutoRotate
    if (item.refs.isEmpty()) {
        Box(
            modifier
                .clip(RoundedCornerShape(28.dp))
                .background(cs.surfaceContainerHigh.copy(alpha = 0.7f))
                .combinedClickable(
                    onClick = {
                        vm.stackTarget = item.uid
                        vm.overlay = Overlay.WIDGETS
                    },
                    onLongClick = onLongPress
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("+", color = cs.primary, fontSize = 34.sp)
                Text("Stack vuoto", color = cs.onSurfaceVariant, fontSize = 12.sp)
                Text("Tocca per aggiungere widget", color = cs.onSurfaceVariant, fontSize = 10.sp)
            }
        }
        return
    }
    LaunchedEffect(auto, item.refs.size, vm.editMode) {
        while (auto && !vm.editMode && item.refs.size > 1) {
            delay(7000)
            pager.animateScrollToPage((pager.currentPage + 1) % item.refs.size)
        }
    }
    Box(modifier) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            key = { i -> item.refs[i].let { r -> "${r.kind}-${r.widgetId}-$i" } }
        ) { i ->
            WidgetContent(item.refs[i], vm, host, onLongPress, Modifier.fillMaxSize())
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(item.refs.size) { i ->
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(cs.onSurface.copy(alpha = if (pager.currentPage == i) 0.8f else 0.3f))
                )
            }
        }
    }
}


// ---------------------------------------------------------------------
//  Orologio analogico (Material You)
// ---------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnalogClockWidget(host: Host, onLongPress: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - (now % 60_000L) + 30L)
        }
    }
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
    val hour = cal.get(java.util.Calendar.HOUR) + cal.get(java.util.Calendar.MINUTE) / 60f
    val minute = cal.get(java.util.Calendar.MINUTE).toFloat()
    val date = Date(now)
    val loc = Locale.getDefault()
    val dayName = SimpleDateFormat("EEEE", loc).format(date).replaceFirstChar { it.uppercase() }
    val dayDate = SimpleDateFormat("d MMMM", loc).format(date)

    val bg = cs.tertiaryContainer
    val face = cs.surface
    val tick = cs.onSurfaceVariant
    val hourHand = cs.onSurface
    val minHand = cs.primary
    val centerDot = cs.tertiary

    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(bg)
            .combinedClickable(onClick = { host.openClockApp() }, onLongClick = onLongPress)
    ) {
        val wide = maxWidth >= maxHeight * 1.5f
        val fs = minOf(maxHeight * 0.16f, maxWidth * 0.06f).coerceAtLeast(13.dp).asSp()
        Row(
            Modifier.fillMaxSize().padding(if (wide) 12.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (wide) Arrangement.SpaceEvenly else Arrangement.Center
        ) {
            Canvas(Modifier.fillMaxHeight().aspectRatio(1f)) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(face, radius = r, center = c)
                for (i in 0 until 12) {
                    val ang = Math.toRadians(i * 30.0 - 90.0)
                    val big = i % 3 == 0
                    val r1 = r * 0.90f
                    val r2 = r * (if (big) 0.76f else 0.83f)
                    drawLine(
                        tick,
                        Offset(c.x + (r1 * Math.cos(ang)).toFloat(), c.y + (r1 * Math.sin(ang)).toFloat()),
                        Offset(c.x + (r2 * Math.cos(ang)).toFloat(), c.y + (r2 * Math.sin(ang)).toFloat()),
                        strokeWidth = r * (if (big) 0.055f else 0.035f),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
                val ha = Math.toRadians(hour * 30.0 - 90.0)
                val ma = Math.toRadians(minute * 6.0 - 90.0)
                drawLine(
                    hourHand, c,
                    Offset(c.x + (r * 0.46f * Math.cos(ha)).toFloat(), c.y + (r * 0.46f * Math.sin(ha)).toFloat()),
                    strokeWidth = r * 0.12f, cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                drawLine(
                    minHand, c,
                    Offset(c.x + (r * 0.68f * Math.cos(ma)).toFloat(), c.y + (r * 0.68f * Math.sin(ma)).toFloat()),
                    strokeWidth = r * 0.085f, cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                drawCircle(centerDot, radius = r * 0.07f, center = c)
            }
            if (wide) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(dayName, color = cs.onTertiaryContainer, fontSize = fs, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                    Text(dayDate, color = cs.onTertiaryContainer.copy(alpha = 0.8f), fontSize = fs, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}


// ---------------------------------------------------------------------
//  Batteria (telefono + accessori Bluetooth)
// ---------------------------------------------------------------------

class Accessory(val name: String, val level: Int, val icon: String)

/** Livelli segnalati dai dispositivi tramite broadcast (indirizzo -> %). */
object AccessoryCache {
    val levels = HashMap<String, Int>()
}

private const val ACTION_BT_BATTERY = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
private const val EXTRA_BT_BATTERY = "android.bluetooth.device.extra.BATTERY_LEVEL"

private fun hasBtPermission(ctx: android.content.Context) =
    ctx.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

@android.annotation.SuppressLint("MissingPermission")
private fun readAccessories(ctx: android.content.Context): List<Accessory> {
    if (!hasBtPermission(ctx)) return emptyList()
    val out = ArrayList<Accessory>()
    try {
        val adapter = ctx.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
            ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        for (d in adapter.bondedDevices ?: emptySet()) {
            var level = -1
            try {
                level = android.bluetooth.BluetoothDevice::class.java
                    .getMethod("getBatteryLevel").invoke(d) as Int
            } catch (e: Throwable) {
                level = AccessoryCache.levels[d.address] ?: -1
            }
            if (level !in 0..100) continue
            val name = try { d.alias ?: d.name } catch (e: Exception) { null } ?: continue
            val icon = when (d.bluetoothClass?.majorDeviceClass) {
                0x0400 -> "🎧"
                0x0500 -> "⌨"
                0x0700 -> "⌚"
                0x0200 -> "📱"
                else -> "🔹"
            }
            out.add(Accessory(name, level, icon))
        }
    } catch (e: Throwable) {
        // Bluetooth non disponibile
    }
    return out
}

@Composable
private fun PhoneRing(level: Int, ring: Color, track: Color, modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val sw = size.minDimension * 0.13f
            val sz = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw)
            val tl = Offset(sw / 2, sw / 2)
            val st = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawArc(track, -90f, 360f, false, topLeft = tl, size = sz, style = st)
            drawArc(ring, -90f, 360f * level / 100f, false, topLeft = tl, size = sz, style = st)
        }
        content()
    }
}

@Composable
private fun AccessoryRow(a: Accessory, textColor: Color, fs: TextUnit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(a.icon, fontSize = fs)
        Spacer(Modifier.width(6.dp))
        Text(
            a.name,
            color = textColor,
            fontSize = fs,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(6.dp))
        Text("${a.level}%", color = textColor, fontSize = fs, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BatteryWidget(host: Host, onLongPress: () -> Unit, modifier: Modifier) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var level by remember { mutableIntStateOf(100) }
    var charging by remember { mutableStateOf(false) }
    var accessories by remember { mutableStateOf<List<Accessory>>(emptyList()) }
    var hasPerm by remember { mutableStateOf(hasBtPermission(ctx)) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        fun read(i: android.content.Intent?) {
            if (i == null) return
            val l = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val sc = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
            if (l >= 0 && sc > 0) level = l * 100 / sc
            val st = i.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
            charging = st == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                st == android.os.BatteryManager.BATTERY_STATUS_FULL
        }
        val rec = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: android.content.Intent) = read(i)
        }
        read(ctx.registerReceiver(rec, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)))
        // accessori: aggiornamenti dei livelli e connessioni/disconnessioni
        val btRec = object : android.content.BroadcastReceiver() {
            @android.annotation.SuppressLint("MissingPermission")
            override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                try {
                    if (i.action == ACTION_BT_BATTERY) {
                        val dev = i.getParcelableExtra<android.bluetooth.BluetoothDevice>(
                            android.bluetooth.BluetoothDevice.EXTRA_DEVICE
                        )
                        val lv = i.getIntExtra(EXTRA_BT_BATTERY, -1)
                        if (dev != null && lv in 0..100) AccessoryCache.levels[dev.address] = lv
                    }
                } catch (e: Throwable) { }
                accessories = readAccessories(ctx)
            }
        }
        val f = android.content.IntentFilter().apply {
            addAction(ACTION_BT_BATTERY)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        try {
            androidx.core.content.ContextCompat.registerReceiver(
                ctx, btRec, f, androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Throwable) { }
        onDispose {
            try { ctx.unregisterReceiver(rec) } catch (e: Exception) { }
            try { ctx.unregisterReceiver(btRec) } catch (e: Exception) { }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            hasPerm = hasBtPermission(ctx)
            accessories = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { readAccessories(ctx) }
            delay(30_000L)
        }
    }

    val low = level <= 15 && !charging
    val ring = if (low) cs.error else cs.primary
    val track = cs.onSecondaryContainer.copy(alpha = 0.15f)
    val status = when {
        charging && level >= 100 -> "Carica completa"
        charging -> "In carica"
        low -> "Batteria scarica"
        else -> "Batteria"
    }

    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(cs.secondaryContainer)
            .combinedClickable(
                onClick = { if (!hasPerm) host.requestBluetooth() },
                onLongClick = onLongPress
            )
    ) {
        val wide = maxWidth >= maxHeight * 1.5f
        val small = maxWidth < 100.dp || maxHeight < 100.dp
        val tall = !wide && !small && maxHeight >= 150.dp
        val pad = if (small) 8.dp else 14.dp
        val pctFs = when {
            wide -> minOf(maxHeight * 0.26f, maxWidth * 0.10f)
            tall -> minOf(maxWidth * 0.20f, maxHeight * 0.14f)
            else -> minOf(maxWidth * 0.26f, maxHeight * 0.22f)
        }
        val pctSmall = minOf(maxWidth * 0.22f, maxHeight * 0.22f)
        val statusFs = minOf(maxHeight * 0.12f, maxWidth * 0.045f).coerceAtLeast(12.dp).asSp()
        val accFs = minOf(maxHeight * 0.10f, maxWidth * 0.05f).coerceIn(11.dp, 15.dp).asSp()
        val ringTextFs = (if (small) pctSmall else pctFs).asSp()
        val onC = cs.onSecondaryContainer
        val bars = maxWidth >= 220.dp && maxHeight >= 150.dp
        val barFs = minOf(maxHeight * 0.12f, maxWidth * 0.06f).coerceIn(14.dp, 20.dp).asSp()
        val ringH = if (accessories.isEmpty()) maxHeight * 0.70f else maxHeight * 0.52f

        if (bars) {
            val rowsMax = (((maxHeight - 28.dp) / 46.dp).toInt()).coerceAtLeast(1)
            val rows = listOf(Accessory("Telefono", level, if (charging) "⚡" else "📱")) + accessories
            Column(
                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                rows.take(rowsMax).forEachIndexed { idx, r ->
                    Column(Modifier.fillMaxWidth()) {
                        AccessoryRow(r, onC, if (idx == 0) barFs else accFs)
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier.fillMaxWidth().height(if (idx == 0) 12.dp else 8.dp)
                                .clip(CircleShape).background(track)
                        ) {
                            Box(
                                Modifier.fillMaxHeight().fillMaxWidth(r.level / 100f).clip(CircleShape)
                                    .background(if (idx == 0) ring else cs.tertiary)
                            )
                        }
                    }
                }
                if (accessories.isEmpty() && !hasPerm && rowsMax > 1) {
                    Text(
                        "Tocca per mostrare la batteria degli accessori",
                        color = onC.copy(alpha = 0.7f),
                        fontSize = accFs
                    )
                }
            }
        } else if (wide) {
            Row(
                Modifier.fillMaxSize().padding(pad),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PhoneRing(level, ring, track, Modifier.fillMaxHeight().aspectRatio(1f)) {}
                Column(Modifier.weight(if (accessories.isEmpty()) 1f else 0.9f)) {
                    Text("$level%", color = onC, fontSize = pctFs.asSp(), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    Text(status + if (charging) " ⚡" else "", color = onC.copy(alpha = 0.8f), fontSize = statusFs, maxLines = 1, softWrap = false)
                }
                if (accessories.isNotEmpty()) {
                    Column(Modifier.weight(1.1f)) {
                        accessories.take(3).forEach { AccessoryRow(it, onC, accFs) }
                    }
                } else if (!hasPerm) {
                    Text(
                        "Tocca per mostrare la batteria degli accessori",
                        color = onC.copy(alpha = 0.7f),
                        fontSize = accFs,
                        modifier = Modifier.weight(1.1f)
                    )
                }
            }
        } else if (tall) {
            Column(
                Modifier.fillMaxSize().padding(pad),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                PhoneRing(level, ring, track, Modifier.height(ringH).aspectRatio(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$level%", color = onC, fontSize = ringTextFs, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        if (charging) Text("⚡", color = ring, fontSize = (pctFs * 0.7f).asSp())
                    }
                }
                if (accessories.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    accessories.take(2).forEach { AccessoryRow(it, onC, accFs) }
                }
            }
        } else {
            Row(
                Modifier.fillMaxSize().padding(pad),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                PhoneRing(level, ring, track, Modifier.fillMaxHeight().aspectRatio(1f)) {
                    Text("$level%", color = onC, fontSize = ringTextFs, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}
