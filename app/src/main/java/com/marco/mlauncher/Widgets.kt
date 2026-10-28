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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
        "weather" -> WeatherWidget(vm, host, onLongPress, modifier, sample)
        else -> ExtWidget(ref, vm, onLongPress, modifier)
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
            Row(
                Modifier.fillMaxSize().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val fs = (h * 0.52f).asSp()
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = cs.onPrimaryContainer)) { append(hh) }
                        withStyle(SpanStyle(color = cs.primary)) { append(":$mm") }
                    },
                    fontSize = fs,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        day,
                        color = cs.onPrimaryContainer,
                        fontSize = (h * 0.14f).coerceAtLeast(12.dp).asSp(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            val side = if (w < h) w else h
            val fs = (side * 0.40f).asSp()
            Column(
                Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    hh,
                    color = cs.onPrimaryContainer,
                    fontSize = fs,
                    lineHeight = fs * 0.9f,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    mm,
                    color = cs.primary,
                    fontSize = fs,
                    lineHeight = fs * 0.9f,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (h > 150.dp) {
                    Text(
                        day,
                        color = cs.onPrimaryContainer,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
    LaunchedEffect(vm.hasLocation, sample) {
        if (!sample && vm.hasLocation) {
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
                    if (!vm.hasLocation) host.requestLocation() else vm.refreshWeather(true)
                },
                onLongClick = onLongPress
            )
    ) {
        val bw = maxWidth
        val bh = maxHeight
        if (w == null) {
            Box(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (!vm.hasLocation) "Tocca per usare la tua posizione" else "Carico il meteo…",
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
