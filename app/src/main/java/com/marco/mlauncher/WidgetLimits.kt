package com.marco.mlauncher

import android.appwidget.AppWidgetProviderInfo
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * Limiti di ridimensionamento dei widget di Android: minimo e massimo in celle, e quali assi si possono
 * cambiare. Impostazione presa da ARK-launcher (https://github.com/jrs8205/ARK-launcher, Apache-2.0) e dal
 * launcher di Android (AOSP, Apache-2.0); adattata alle nostre celle.
 */
class WidgetRange(
    val minX: Int, val minY: Int, val maxX: Int, val maxY: Int,
    val horizontal: Boolean, val vertical: Boolean
) {
    val isResizable get() = horizontal || vertical
}

/** Margine che il widget lascia dentro la cella (lo stesso che usavamo già per le celle iniziali). */
private const val PAD_DP = 8f

private fun minCells(dp: Float, cellDp: Float) =
    ceil(((dp - PAD_DP).coerceAtLeast(1f)) / cellDp.coerceAtLeast(40f)).toInt().coerceAtLeast(1)

private fun maxCells(dp: Float, cellDp: Float, grid: Int) =
    if (dp <= 0f) grid
    else floor((dp + PAD_DP) / cellDp.coerceAtLeast(40f)).toInt().coerceIn(1, grid.coerceAtLeast(1))

/** Quanto può stare piccolo/grande [info] e su quali assi (resizeMode, minResize*, maxResize* del widget). */
fun widgetRange(info: AppWidgetProviderInfo, density: Float, cellWdp: Float, cellHdp: Float, cols: Int, rows: Int): WidgetRange {
    val minW = if (info.minResizeWidth > 0) min(info.minResizeWidth, info.minWidth) else info.minWidth
    val minH = if (info.minResizeHeight > 0) min(info.minResizeHeight, info.minHeight) else info.minHeight
    val minX = minCells(minW / density, cellWdp).coerceAtMost(cols.coerceAtLeast(1))
    val minY = minCells(minH / density, cellHdp).coerceAtMost(rows.coerceAtLeast(1))
    val maxX = max(minX, maxCells(info.maxResizeWidth / density, cellWdp, cols))
    val maxY = max(minY, maxCells(info.maxResizeHeight / density, cellHdp, rows))
    return WidgetRange(
        minX, minY, maxX, maxY,
        horizontal = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0,
        vertical = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
    )
}

/** Celle con cui piazzare [info] la prima volta: il suggerimento del widget (targetCell*) o, se più grande, il suo minimo. */
fun widgetDefaultSpans(info: AppWidgetProviderInfo, density: Float, cellWdp: Float, cellHdp: Float, maxX: Int, maxY: Int): Pair<Int, Int> {
    val sx = max(info.targetCellWidth, minCells(info.minWidth / density, cellWdp)).coerceIn(1, maxX.coerceAtLeast(1))
    val sy = max(info.targetCellHeight, minCells(info.minHeight / density, cellHdp)).coerceIn(1, maxY.coerceAtLeast(1))
    return sx to sy
}
