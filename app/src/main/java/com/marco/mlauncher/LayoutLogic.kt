package com.marco.mlauncher

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** Griglia della Home: colonne e righe delle pagine, posti della dock. */
data class Grid(val cols: Int, val rows: Int, val slots: Int)

/** Una disposizione completa: elementi e numero di pagine. */
class Layout(val items: List<HomeItem>, val pages: Int)

/**
 * Regole della disposizione della Home, senza niente di Android: così sono controllate dai test automatici
 * a ogni build (posti liberi, passaggio verticale ↔ orizzontale, modifiche riportate in verticale).
 */
object LayoutLogic {

    fun overlaps(a: HomeItem, page: Int, x: Int, y: Int, sx: Int, sy: Int): Boolean =
        a.page == page && a.x < x + sx && x < a.x + a.spanX && a.y < y + sy && y < a.y + a.spanY

    /** Il rettangolo sta nella griglia (o nella dock, pagina −1) e non tocca nessun altro elemento. */
    fun areaFree(list: List<HomeItem>, g: Grid, page: Int, x: Int, y: Int, sx: Int, sy: Int, ignoreId: String? = null): Boolean {
        if (x < 0 || y < 0) return false
        if (page == -1) {
            if (y != 0 || sy != 1 || x + sx > g.slots) return false
        } else if (x + sx > g.cols || y + sy > g.rows) {
            return false
        }
        return list.none { it.id != ignoreId && overlaps(it, page, x, y, sx, sy) }
    }

    /** Primo posto libero da [startPage] in poi (riga per riga); se serve va sulle pagine successive. */
    fun findFree(list: List<HomeItem>, g: Grid, startPage: Int, sx: Int = 1, sy: Int = 1): Triple<Int, Int, Int> {
        val w = minOf(sx, g.cols)
        val h = minOf(sy, g.rows)
        var p = startPage
        while (true) {
            for (y in 0..(g.rows - h)) for (x in 0..(g.cols - w)) {
                if (areaFree(list, g, p, x, y, w, h)) return Triple(p, x, y)
            }
            p++
        }
    }

    /** Posto libero più vicino a (tx,ty) nella pagina; null se la pagina è piena. */
    fun nearestFree(list: List<HomeItem>, g: Grid, page: Int, tx: Int, ty: Int, sx: Int, sy: Int, ignoreId: String? = null): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var bd = Int.MAX_VALUE
        for (y in 0..(g.rows - sy)) for (x in 0..(g.cols - sx)) {
            if (!areaFree(list, g, page, x, y, sx, sy, ignoreId)) continue
            val d = (x - tx) * (x - tx) + 2 * (y - ty) * (y - ty)
            if (d < bd) {
                bd = d
                best = x to y
            }
        }
        return best
    }

    /** Posizione corrispondente tra due griglie: in alto a destra resta in alto a destra. */
    fun mapPos(v: Int, span: Int, fromN: Int, toSpan: Int, toN: Int): Int {
        val f = if (fromN - span > 0) v.toFloat() / (fromN - span) else 0f
        return (f * (toN - toSpan)).roundToInt().coerceIn(0, maxOf(0, toN - toSpan))
    }

    /**
     * Ricava la disposizione per un'altra griglia (es. l'orizzontale dalla verticale): ogni elemento va nel
     * posto corrispondente sulla stessa pagina; chi non ci sta finisce nel primo posto libero dopo.
     */
    fun project(src: List<HomeItem>, srcPages: Int, from: Grid, to: Grid): Layout {
        val out = ArrayList<HomeItem>()
        var pages = srcPages.coerceAtLeast(1)
        val overflow = ArrayList<HomeItem>()
        for (d in src.filter { it.page == -1 }.sortedBy { it.x }) {
            if (d.x < to.slots && areaFree(out, to, -1, d.x, 0, 1, 1)) out.add(d) else overflow.add(d)
        }
        // prima i più grandi, che hanno meno posti possibili
        val grid = src.filter { it.page >= 0 }.sortedWith(
            compareByDescending<HomeItem> { it.spanX * it.spanY }.thenBy { it.page }.thenBy { it.y }.thenBy { it.x }
        )
        for (it in grid) {
            val n = it.sized(minOf(it.spanX, to.cols), minOf(it.spanY, to.rows))
            val tx = mapPos(it.x, it.spanX, from.cols, n.spanX, to.cols)
            val ty = mapPos(it.y, it.spanY, from.rows, n.spanY, to.rows)
            val spot = nearestFree(out, to, n.page, tx, ty, n.spanX, n.spanY)
            if (spot != null) {
                out.add(n.at(n.page, spot.first, spot.second))
                if (n.page + 1 > pages) pages = n.page + 1
            } else {
                overflow.add(n)
            }
        }
        for (o in overflow) {
            val (p, x, y) = findFree(out, to, maxOf(o.page, 0), o.spanX, o.spanY)
            out.add(o.at(p, x, y))
            if (p + 1 > pages) pages = p + 1
        }
        return Layout(out, pages)
    }

    /**
     * Riporta nella disposizione principale (verticale) quello che è cambiato in quella ricavata (orizzontale).
     * [snap] è la disposizione ricavata com'era all'inizio: dal confronto con [now] si capisce cosa è stato
     * spostato, aggiunto, tolto o solo modificato. Restituisce null se non è cambiato niente.
     */
    fun mergeBack(
        master: List<HomeItem>, masterPages: Int,
        now: List<HomeItem>, nowPages: Int,
        snap: Map<String, HomeItem>, snapPages: Int,
        derived: Grid, main: Grid
    ): Layout? {
        val out = ArrayList(master)
        var pages = masterPages.coerceAtLeast(1)
        val nowIds = now.map { it.id }.toSet()
        var changed = false
        // tolti nella disposizione ricavata
        if (out.removeAll { it.id in snap && it.id !in nowIds }) changed = true
        // pagine aggiunte o tolte
        val dp = nowPages - snapPages
        if (dp != 0) {
            pages = maxOf(1, pages + dp)
            changed = true
        }
        for (l in now) {
            val s = snap[l.id]
            if (s == l) continue
            changed = true
            val m = out.firstOrNull { it.id == l.id }
            val moved = s == null || m == null || s.page != l.page || s.x != l.x || s.y != l.y ||
                s.spanX != l.spanX || s.spanY != l.spanY
            if (m != null && !moved) {
                // cambiato solo il contenuto (nome della cartella, app dentro…): il posto resta quello principale
                out[out.indexOf(m)] = l.at(m.page, m.x, m.y).sized(m.spanX, m.spanY)
                continue
            }
            if (m != null) out.remove(m)
            // grandezza: se non è stata cambiata resta quella principale
            val sx = if (m != null && s != null && s.spanX == l.spanX) m.spanX else minOf(l.spanX, main.cols)
            val sy = if (m != null && s != null && s.spanY == l.spanY) m.spanY else minOf(l.spanY, main.rows)
            val placed: HomeItem = if (l.page == -1 && areaFree(out, main, -1, l.x, 0, 1, 1, l.id)) {
                l.at(-1, l.x, 0)
            } else {
                val tx = if (l.page == -1) 0 else mapPos(l.x, l.spanX, derived.cols, sx, main.cols)
                val ty = if (l.page == -1) main.rows - 1 else mapPos(l.y, l.spanY, derived.rows, sy, main.rows)
                // posto corrispondente, o il più vicino libero; pagina piena → la prima libera dopo
                var p = maxOf(l.page, 0)
                var spot = nearestFree(out, main, p, tx, ty, sx, sy, l.id)
                while (spot == null && p < pages + 20) {
                    p++
                    spot = nearestFree(out, main, p, tx, ty, sx, sy, l.id)
                }
                if (spot == null) {
                    p = pages
                    spot = 0 to 0
                }
                l.at(p, spot.first, spot.second).sized(sx, sy)
            }
            out.add(placed)
        }
        if (!changed) return null
        // le pagine bastano sempre per tutti gli elementi
        val last = out.maxOfOrNull { it.page } ?: 0
        if (last + 1 > pages) pages = last + 1
        return Layout(out, pages)
    }
}

/** Salvataggio della disposizione in JSON (file delle preferenze e backup) e rilettura. */
object LayoutCodec {
    private fun encodeRef(r: WidgetRef) = JSONObject().put("k", r.kind).put("w", r.widgetId).put("p", r.provider)

    private fun decodeRef(o: JSONObject) = WidgetRef(o.getString("k"), o.optInt("w", -1), o.optString("p", ""))

    fun encode(list: List<HomeItem>): JSONArray {
        val arr = JSONArray()
        for (it in list) {
            val o = JSONObject().put("page", it.page).put("x", it.x).put("y", it.y)
            when (it) {
                is AppItem -> o.put("t", "app").put("app", it.appId)
                is FolderItem -> o.put("t", "folder").put("fid", it.folderId)
                    .put("name", it.name).put("apps", JSONArray(it.appIds)).put("big", it.big)
                is WidgetItem -> o.put("t", "widget").put("uid", it.uid).put("ref", encodeRef(it.ref))
                    .put("sx", it.spanX).put("sy", it.spanY)
                is StackItem -> {
                    val refs = JSONArray()
                    it.refs.forEach { r -> refs.put(encodeRef(r)) }
                    o.put("t", "stack").put("uid", it.uid).put("refs", refs)
                        .put("sx", it.spanX).put("sy", it.spanY)
                }
            }
            arr.put(o)
        }
        return arr
    }

    /** [skipExt]: lascia fuori i widget delle altre app (non si possono ricreare da un backup su un altro telefono). */
    fun decode(arr: JSONArray, skipExt: Boolean): List<HomeItem> {
        val out = ArrayList<HomeItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val page = o.getInt("page")
            val x = o.getInt("x")
            val y = o.getInt("y")
            when (o.getString("t")) {
                "app" -> out.add(AppItem(o.getString("app"), page, x, y))
                "folder" -> {
                    val a = o.getJSONArray("apps")
                    val list = (0 until a.length()).map { a.getString(it) }
                    out.add(FolderItem(o.getString("fid"), o.getString("name"), list, page, x, y, o.optBoolean("big", false)))
                }
                "widget" -> {
                    val ref = decodeRef(o.getJSONObject("ref"))
                    if (!(skipExt && ref.kind == "ext")) {
                        out.add(WidgetItem(o.getString("uid"), ref, page, x, y, o.getInt("sx"), o.getInt("sy")))
                    }
                }
                "stack" -> {
                    val ra = o.getJSONArray("refs")
                    var refs = (0 until ra.length()).map { decodeRef(ra.getJSONObject(it)) }
                    if (skipExt) refs = refs.filter { it.kind != "ext" }
                    out.add(StackItem(o.getString("uid"), refs, page, x, y, o.getInt("sx"), o.getInt("sy")))
                }
            }
        }
        return out
    }
}

/** Nome dei file di un'app nella cache delle icone: stabile, non cambia se si aggiungono o tolgono altre app. */
fun iconCacheName(pkg: String, cls: String): String {
    val key = "$pkg/$cls"
    return "a" + Integer.toHexString(key.hashCode()) + "_" + key.length
}
