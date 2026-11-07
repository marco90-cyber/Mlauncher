package com.marco.mlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build

/**
 * Azioni di MLauncher (come le "Azioni Nova"): icone in Home che fanno qualcosa invece di aprire un'app.
 * Sono "app" virtuali del Launcher con classe "action:<chiave>[:numero]".
 */
const val ACTION_PREFIX = "action:"

val MLA_ACTIONS = listOf(
    "drawer" to "Cassetto app",
    "search" to "Ricerca",
    "notif" to "Notifiche",
    "qs" to "Impostazioni rapide",
    "recents" to "App recenti",
    "lock" to "Blocca schermo",
    "statusbar" to "Barra di stato",
    "dock" to "Mostra/nascondi dock",
    "home" to "Pagina principale",
    "overview" to "Anteprima pagine"
)

/** Pagine raggiungibili con "Vai a pagina". */
const val ACTION_MAX_PAGE = 9

val AppEntry.isAction: Boolean get() = cls.startsWith(ACTION_PREFIX)

/** Colore di fondo dell'icona di ogni azione. */
private fun actionColor(key: String): Int = when (key) {
    "drawer", "search" -> 0xFF3D5AFE.toInt()
    "notif", "qs" -> 0xFF00897B.toInt()
    "recents", "overview" -> 0xFF6D4CC2.toInt()
    "lock" -> 0xFF37474F.toInt()
    "statusbar", "dock" -> 0xFF0277BD.toInt()
    else -> 0xFFE65100.toInt()
}

/** Icona adattiva dell'azione (prende la forma scelta per le icone come le altre app). */
fun actionIcon(ctx: Context, spec: String): Drawable {
    val key = spec.substringBefore(":")
    val arg = spec.substringAfter(":", "")
    val size = 432
    val fg = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    drawGlyph(Canvas(fg), key, arg, size.toFloat())
    val res = ctx.resources
    val fgD = BitmapDrawable(res, fg)
    val bg = ColorDrawable(actionColor(key))
    return if (Build.VERSION.SDK_INT >= 33) AdaptiveIconDrawable(bg, fgD, BitmapDrawable(res, fg))
    else AdaptiveIconDrawable(bg, fgD)
}

/** Disegna il simbolo bianco al centro (zona sicura delle icone adattive). */
private fun drawGlyph(c: Canvas, key: String, arg: String, s: Float) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = Paint.Style.STROKE; strokeWidth = s * 0.035f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = Paint.Style.FILL }
    val cx = s / 2f
    val cy = s / 2f
    val u = s * 0.11f // unità: il simbolo sta in circa ±1.6u dal centro
    when (key) {
        "drawer" -> for (i in -1..1) for (j in -1..1) c.drawCircle(cx + i * u, cy + j * u, u * 0.32f, f)
        "search" -> {
            c.drawCircle(cx - u * 0.3f, cy - u * 0.3f, u * 0.9f, p)
            c.drawLine(cx + u * 0.35f, cy + u * 0.35f, cx + u * 1.25f, cy + u * 1.25f, p)
        }
        "notif" -> {
            // campanella
            val path = Path()
            path.moveTo(cx - u * 1.05f, cy + u * 0.7f)
            path.lineTo(cx - u * 0.8f, cy + u * 0.35f)
            path.lineTo(cx - u * 0.8f, cy - u * 0.25f)
            path.cubicTo(cx - u * 0.8f, cy - u * 1.15f, cx + u * 0.8f, cy - u * 1.15f, cx + u * 0.8f, cy - u * 0.25f)
            path.lineTo(cx + u * 0.8f, cy + u * 0.35f)
            path.lineTo(cx + u * 1.05f, cy + u * 0.7f)
            path.close()
            c.drawPath(path, f)
            c.drawCircle(cx, cy + u * 1.0f, u * 0.25f, f)
        }
        "qs" -> {
            // due interruttori
            for ((dy, on) in listOf(-0.55f to true, 0.55f to false)) {
                val r = RectF(cx - u * 1.2f, cy + dy * u - u * 0.38f, cx + u * 1.2f, cy + dy * u + u * 0.38f)
                c.drawRoundRect(r, u * 0.38f, u * 0.38f, p)
                c.drawCircle(if (on) cx + u * 0.82f else cx - u * 0.82f, cy + dy * u, u * 0.24f, f)
            }
        }
        "recents" -> {
            c.drawRoundRect(RectF(cx - u * 0.65f, cy - u * 1.2f, cx + u * 1.0f, cy + u * 0.75f), u * 0.25f, u * 0.25f, p)
            c.drawRoundRect(RectF(cx - u * 1.1f, cy - u * 0.75f, cx + u * 0.55f, cy + u * 1.2f), u * 0.25f, u * 0.25f, f)
        }
        "lock" -> {
            c.drawRoundRect(RectF(cx - u * 0.95f, cy - u * 0.15f, cx + u * 0.95f, cy + u * 1.2f), u * 0.25f, u * 0.25f, f)
            c.drawArc(RectF(cx - u * 0.6f, cy - u * 1.25f, cx + u * 0.6f, cy + u * 0.05f), 180f, 180f, false, p)
            c.drawLine(cx - u * 0.6f, cy - u * 0.6f, cx - u * 0.6f, cy - u * 0.15f, p)
            c.drawLine(cx + u * 0.6f, cy - u * 0.6f, cx + u * 0.6f, cy - u * 0.15f, p)
        }
        "statusbar" -> {
            c.drawRoundRect(RectF(cx - u * 0.85f, cy - u * 1.35f, cx + u * 0.85f, cy + u * 1.35f), u * 0.3f, u * 0.3f, p)
            c.drawRoundRect(RectF(cx - u * 0.85f, cy - u * 1.35f, cx + u * 0.85f, cy - u * 0.8f), u * 0.2f, u * 0.2f, f)
        }
        "dock" -> {
            c.drawRoundRect(RectF(cx - u * 0.85f, cy - u * 1.35f, cx + u * 0.85f, cy + u * 1.35f), u * 0.3f, u * 0.3f, p)
            c.drawRoundRect(RectF(cx - u * 0.6f, cy + u * 0.6f, cx + u * 0.6f, cy + u * 1.0f), u * 0.2f, u * 0.2f, f)
        }
        "home" -> {
            val path = Path()
            path.moveTo(cx, cy - u * 1.2f)
            path.lineTo(cx + u * 1.25f, cy - u * 0.1f)
            path.lineTo(cx + u * 0.85f, cy - u * 0.1f)
            path.lineTo(cx + u * 0.85f, cy + u * 1.1f)
            path.lineTo(cx - u * 0.85f, cy + u * 1.1f)
            path.lineTo(cx - u * 0.85f, cy - u * 0.1f)
            path.lineTo(cx - u * 1.25f, cy - u * 0.1f)
            path.close()
            c.drawPath(path, f)
        }
        "overview" -> for (i in -1..1) {
            val x = cx + i * u * 0.95f
            c.drawRoundRect(RectF(x - u * 0.38f, cy - u * 0.9f, x + u * 0.38f, cy + u * 0.9f), u * 0.15f, u * 0.15f, if (i == 0) f else p)
        }
        "page" -> {
            c.drawRoundRect(RectF(cx - u * 1.0f, cy - u * 1.3f, cx + u * 1.0f, cy + u * 1.3f), u * 0.3f, u * 0.3f, p)
            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE; textSize = u * 1.5f; textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            c.drawText(arg, cx, cy + u * 0.52f, t)
        }
    }
}
