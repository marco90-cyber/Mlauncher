package com.marco.mlauncher

import android.graphics.Bitmap

/**
 * Sfocatura "a foto": invece di sfocare tutta la Home a ogni fotogramma (lavoro pesante per la scheda grafica),
 * si fa una foto della Home una volta sola, la si rimpicciolisce e la si sfoca qui; poi basta disegnarla
 * sopra con una dissolvenza. Essendo sfocata, ingrandita si vede uguale.
 *
 * [sigmaPx] è la sfocatura voluta a grandezza piena, in pixel.
 */
fun blurSnapshot(src: Bitmap, sigmaPx: Float, down: Int = 8): Bitmap? {
    return try {
        val soft = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
        val w = (soft.width / down).coerceAtLeast(1)
        val h = (soft.height / down).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(soft, w, h, true)
        if (soft !== src) soft.recycle()
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        // i colori si sfocano "premoltiplicati", così i bordi trasparenti non fanno aloni scuri
        for (i in px.indices) {
            val c = px[i]
            val a = c ushr 24
            if (a == 255) continue
            val r = ((c shr 16) and 255) * a / 255
            val g = ((c shr 8) and 255) * a / 255
            val b = (c and 255) * a / 255
            px[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        // tre passate di media su box ≈ sfocatura gaussiana
        val sigma = sigmaPx / down
        val r = ((kotlin.math.sqrt(4.0 * sigma * sigma / 3.0 + 1.0) - 1.0) / 2.0).toInt().coerceIn(1, 60)
        repeat(3) { boxBlur(px, w, h, r) }
        for (i in px.indices) {
            val c = px[i]
            val a = c ushr 24
            if (a == 255 || a == 0) continue
            val rr = (((c shr 16) and 255) * 255 / a).coerceAtMost(255)
            val gg = (((c shr 8) and 255) * 255 / a).coerceAtMost(255)
            val bb = ((c and 255) * 255 / a).coerceAtMost(255)
            px[i] = (a shl 24) or (rr shl 16) or (gg shl 8) or bb
        }
        val out = small.copy(Bitmap.Config.ARGB_8888, true)
        out.setPixels(px, 0, w, 0, 0, w, h)
        if (small !== out) small.recycle()
        out
    } catch (e: Exception) {
        AppLog.e("Sfocatura a foto non riuscita", e)
        null
    }
}

/** Media mobile orizzontale e verticale (bordi ripetuti). */
internal fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
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
