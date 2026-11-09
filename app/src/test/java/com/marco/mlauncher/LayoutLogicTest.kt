package com.marco.mlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Regole della disposizione della Home: posti liberi, verticale ↔ orizzontale, modifiche riportate. */
class LayoutLogicTest {
    private val port = Grid(cols = 4, rows = 6, slots = 4)
    private val land = Grid(cols = 8, rows = 3, slots = 5)

    private fun app(name: String, page: Int, x: Int, y: Int) = AppItem("pkg/$name", page, x, y)
    private fun widget(uid: String, page: Int, x: Int, y: Int, sx: Int, sy: Int, kind: String = "clock") =
        WidgetItem(uid, WidgetRef(kind), page, x, y, sx, sy)

    /** Tutti dentro la griglia, nessuno sopra un altro, pagine sufficienti. */
    private fun assertValid(l: Layout, g: Grid) {
        for (i in l.items) {
            if (i.page == -1) {
                assertTrue("dock: ${i.id} fuori dai posti", i.x in 0 until g.slots && i.y == 0)
            } else {
                assertTrue("${i.id} fuori dalla griglia: ${i.x},${i.y} ${i.spanX}x${i.spanY}",
                    i.x >= 0 && i.y >= 0 && i.x + i.spanX <= g.cols && i.y + i.spanY <= g.rows)
                assertTrue("${i.id} su una pagina che non esiste", i.page < l.pages)
            }
        }
        for (a in l.items) for (b in l.items) {
            if (a !== b) assertFalse("${a.id} sopra ${b.id}", LayoutLogic.overlaps(a, b.page, b.x, b.y, b.spanX, b.spanY))
        }
        assertEquals("elementi doppi", l.items.size, l.items.map { it.id }.toSet().size)
    }

    /** Una Home valida a caso (stesso seme = stessa Home). */
    private fun randomLayout(seed: Int, g: Grid, pages: Int = 3): Layout {
        val r = Random(seed)
        val list = ArrayList<HomeItem>()
        repeat(r.nextInt(0, g.slots + 1)) { n -> list.add(app("dock$n", -1, n, 0)) }
        repeat(r.nextInt(1, 4)) { n ->
            val sx = r.nextInt(2, g.cols + 1)
            val sy = r.nextInt(1, 3)
            val (p, x, y) = LayoutLogic.findFree(list, g, r.nextInt(0, pages), sx, sy)
            list.add(widget("w$n", p, x, y, minOf(sx, g.cols), minOf(sy, g.rows)))
        }
        repeat(r.nextInt(5, 30)) { n ->
            val (p, x, y) = LayoutLogic.findFree(list, g, r.nextInt(0, pages))
            list.add(if (n % 7 == 0) FolderItem("f$n", "Cartella $n", listOf("a/b", "c/d"), p, x, y) else app("a$n", p, x, y))
        }
        return Layout(list, maxOf(pages, (list.maxOfOrNull { it.page } ?: 0) + 1))
    }

    // ---------- posti liberi ----------

    @Test fun areaFree_respectsBoundsAndOverlap() {
        val list = listOf(app("a", 0, 1, 1), widget("w", 0, 2, 2, 2, 2))
        assertTrue(LayoutLogic.areaFree(list, port, 0, 0, 0, 1, 1))
        assertFalse("occupato", LayoutLogic.areaFree(list, port, 0, 1, 1, 1, 1))
        assertFalse("tocca il widget", LayoutLogic.areaFree(list, port, 0, 3, 3, 1, 1))
        assertFalse("fuori a destra", LayoutLogic.areaFree(list, port, 0, 3, 0, 2, 1))
        assertFalse("fuori in basso", LayoutLogic.areaFree(list, port, 0, 0, 5, 1, 2))
        assertFalse("negativo", LayoutLogic.areaFree(list, port, 0, -1, 0, 1, 1))
        assertTrue("altra pagina", LayoutLogic.areaFree(list, port, 1, 1, 1, 1, 1))
        assertTrue("ignora se stesso", LayoutLogic.areaFree(list, port, 0, 1, 1, 1, 1, ignoreId = "app:pkg/a"))
    }

    @Test fun areaFree_dockRules() {
        val list = listOf(app("d", -1, 0, 0))
        assertFalse(LayoutLogic.areaFree(list, port, -1, 0, 0, 1, 1))
        assertTrue(LayoutLogic.areaFree(list, port, -1, 3, 0, 1, 1))
        assertFalse("oltre i posti", LayoutLogic.areaFree(list, port, -1, 4, 0, 1, 1))
        assertFalse("la dock ha una riga sola", LayoutLogic.areaFree(list, port, -1, 1, 1, 1, 1))
    }

    @Test fun findFree_fillsRowsThenNextPage() {
        val list = ArrayList<HomeItem>()
        assertEquals(Triple(0, 0, 0), LayoutLogic.findFree(list, port, 0))
        list.add(app("a", 0, 0, 0))
        assertEquals(Triple(0, 1, 0), LayoutLogic.findFree(list, port, 0))
        // pagina 0 piena → pagina 1
        for (y in 0 until port.rows) for (x in 0 until port.cols) if (x + y > 0) list.add(app("f$x$y", 0, x, y))
        assertEquals(Triple(1, 0, 0), LayoutLogic.findFree(list, port, 0))
        // un widget più largo della griglia viene cercato alla larghezza massima
        assertEquals(Triple(1, 0, 0), LayoutLogic.findFree(list, port, 0, 9, 2))
    }

    @Test fun nearestFree_picksClosestOrNull() {
        val list = listOf(app("a", 0, 2, 2))
        assertEquals(2 to 3, LayoutLogic.nearestFree(list, port, 0, 2, 3, 1, 1))
        // posto voluto occupato: quello accanto in orizzontale (in verticale conta doppio)
        val near = LayoutLogic.nearestFree(list, port, 0, 2, 2, 1, 1)
        assertNotNull(near)
        assertEquals(2, near!!.second)
        assertEquals(1, kotlin.math.abs(near.first - 2))
        val full = (0 until port.rows).flatMap { y -> (0 until port.cols).map { x -> app("f$x$y", 0, x, y) } }
        assertNull(LayoutLogic.nearestFree(full, port, 0, 0, 0, 1, 1))
    }

    @Test fun mapPos_keepsCornersAndProportion() {
        assertEquals(0, LayoutLogic.mapPos(0, 1, 4, 1, 8))
        assertEquals("ultima colonna resta ultima", 7, LayoutLogic.mapPos(3, 1, 4, 1, 8))
        assertEquals("ultima riga resta ultima", 2, LayoutLogic.mapPos(5, 1, 6, 1, 3))
        assertEquals("un widget a destra resta a destra", 6, LayoutLogic.mapPos(2, 2, 4, 2, 8))
        assertEquals("largo quanto la griglia", 0, LayoutLogic.mapPos(0, 4, 4, 4, 8))
        assertEquals("non esce mai", 0, LayoutLogic.mapPos(3, 1, 4, 4, 4))
        for (v in 0..3) assertTrue(LayoutLogic.mapPos(v, 1, 4, 1, 8) in 0..7)
    }

    // ---------- verticale → orizzontale ----------

    @Test fun project_sameGridIsIdentity() {
        val src = randomLayout(1, port)
        val out = LayoutLogic.project(src.items, src.pages, port, port)
        assertEquals(src.items.toSet(), out.items.toSet())
        assertEquals(src.pages, out.pages)
    }

    @Test fun project_keepsEverythingValid() {
        for (seed in 1..60) {
            val src = randomLayout(seed, port)
            assertValid(src, port)
            val out = LayoutLogic.project(src.items, src.pages, port, land)
            assertValid(out, land)
            assertEquals("seme $seed: nessun elemento perso o inventato", src.items.map { it.id }.toSet(), out.items.map { it.id }.toSet())
            assertTrue("le pagine non diminuiscono", out.pages >= src.pages)
        }
    }

    @Test fun project_backToPortraitIsValidToo() {
        for (seed in 1..30) {
            val src = randomLayout(seed, land)
            val out = LayoutLogic.project(src.items, src.pages, land, port)
            assertValid(out, port)
            assertEquals(src.items.map { it.id }.toSet(), out.items.map { it.id }.toSet())
        }
    }

    @Test fun project_cornersStayCorners() {
        val src = listOf(app("tl", 0, 0, 0), app("tr", 0, 3, 0), app("bl", 0, 0, 5), app("br", 0, 3, 5))
        val out = LayoutLogic.project(src, 1, port, land).items.associateBy { it.id }
        assertEquals(0 to 0, out["app:pkg/tl"]!!.let { it.x to it.y })
        assertEquals(7 to 0, out["app:pkg/tr"]!!.let { it.x to it.y })
        assertEquals(0 to 2, out["app:pkg/bl"]!!.let { it.x to it.y })
        assertEquals(7 to 2, out["app:pkg/br"]!!.let { it.x to it.y })
    }

    @Test fun project_shrinksWidgetsTooTall_andKeepsPage() {
        val src = listOf(widget("big", 1, 0, 0, 4, 5), app("a", 1, 0, 5))
        val out = LayoutLogic.project(src, 2, port, land)
        assertValid(out, land)
        val w = out.items.first { it.id == "widget:big" }
        assertEquals("alto al massimo quanto la griglia", 3, w.spanY)
        assertEquals(4, w.spanX)
        assertEquals("resta sulla sua pagina", 1, w.page)
    }

    @Test fun project_dockOverflowGoesToPages() {
        val wide = Grid(4, 6, 5)
        val narrow = Grid(4, 6, 3)
        val src = (0 until 5).map { app("d$it", -1, it, 0) }
        val out = LayoutLogic.project(src, 1, wide, narrow)
        assertValid(out, narrow)
        assertEquals(3, out.items.count { it.page == -1 })
        assertEquals("le due in più finiscono in pagina", 2, out.items.count { it.page >= 0 })
    }

    @Test fun project_fullPageOverflowsToNextPages() {
        // 24 icone in verticale (4×6) non stanno in una pagina orizzontale da 6 (3×2)
        val tiny = Grid(3, 2, 4)
        val src = (0 until port.rows).flatMap { y -> (0 until port.cols).map { x -> app("a$x$y", 0, x, y) } }
        val out = LayoutLogic.project(src, 1, port, tiny)
        assertValid(out, tiny)
        assertEquals(24, out.items.size)
        assertEquals(4, out.pages)
    }

    // ---------- modifiche in orizzontale riportate in verticale ----------

    private fun derived(master: Layout) = LayoutLogic.project(master.items, master.pages, port, land)

    private fun merge(master: Layout, start: Layout, now: List<HomeItem>, nowPages: Int = start.pages) =
        LayoutLogic.mergeBack(master.items, master.pages, now, nowPages, start.items.associateBy { it.id }, start.pages, land, port)

    @Test fun mergeBack_nothingChangedReturnsNull() {
        for (seed in 1..20) {
            val master = randomLayout(seed, port)
            val start = derived(master)
            assertNull("seme $seed", merge(master, start, start.items))
        }
    }

    @Test fun mergeBack_moveGoesToCorrespondingSpot_othersUntouched() {
        val master = Layout(listOf(app("a", 0, 0, 0), app("b", 0, 1, 0), app("c", 0, 2, 3)), 1)
        val start = derived(master)
        // in orizzontale porto "a" nell'angolo in basso a destra
        val now = start.items.map { if (it.id == "app:pkg/a") it.at(0, 7, 2) else it }
        val res = merge(master, start, now)!!
        assertValid(res, port)
        val by = res.items.associateBy { it.id }
        assertEquals("angolo in basso a destra anche in verticale", 3 to 5, by["app:pkg/a"]!!.let { it.x to it.y })
        assertEquals(master.items[1], by["app:pkg/b"])
        assertEquals(master.items[2], by["app:pkg/c"])
    }

    @Test fun mergeBack_occupiedSpotUsesNearestFree() {
        val master = Layout(listOf(app("a", 0, 0, 0), app("corner", 0, 3, 5)), 1)
        val start = derived(master)
        // sposto "corner" altrove in orizzontale e metto "a" nell'angolo: in verticale l'angolo è libero solo
        // se "corner" è stato spostato prima; in ogni caso nessuno finisce sopra un altro
        val now = start.items.map {
            when (it.id) {
                "app:pkg/a" -> it.at(0, 7, 2)
                "app:pkg/corner" -> it.at(0, 6, 2)
                else -> it
            }
        }
        val res = merge(master, start, now)!!
        assertValid(res, port)
        assertEquals(2, res.items.size)
    }

    @Test fun mergeBack_renameKeepsPortraitPlace() {
        val f = FolderItem("f1", "Vecchio", listOf("a/b"), 0, 2, 4)
        val master = Layout(listOf(f, app("a", 0, 0, 0)), 1)
        val start = derived(master)
        val now = start.items.map { if (it is FolderItem) it.copy(name = "Nuovo", appIds = listOf("a/b", "c/d")) else it }
        val res = merge(master, start, now)!!
        val g = res.items.first { it is FolderItem } as FolderItem
        assertEquals("Nuovo", g.name)
        assertEquals(listOf("a/b", "c/d"), g.appIds)
        assertEquals("il posto in verticale non cambia", Triple(0, 2, 4), Triple(g.page, g.x, g.y))
    }

    @Test fun mergeBack_removeAndAdd() {
        val master = Layout(listOf(app("a", 0, 0, 0), app("b", 0, 1, 0)), 1)
        val start = derived(master)
        val now = start.items.filter { it.id != "app:pkg/b" } + app("nuova", 0, 4, 1)
        val res = merge(master, start, now)!!
        assertValid(res, port)
        assertEquals(setOf("app:pkg/a", "app:pkg/nuova"), res.items.map { it.id }.toSet())
        assertEquals(master.items[0], res.items.first { it.id == "app:pkg/a" })
    }

    @Test fun mergeBack_resizeOnlyChangesWhatWasResized() {
        // widget 4×2 in verticale; in orizzontale lo allargo a 6: la larghezza cambia (al massimo 4), l'altezza resta
        val master = Layout(listOf(widget("w", 0, 0, 0, 4, 2)), 1)
        val start = derived(master)
        val w0 = start.items.first()
        val now = listOf(w0.sized(6, w0.spanY))
        val res = merge(master, start, now)!!
        assertValid(res, port)
        val w = res.items.first()
        assertEquals(4, w.spanX)
        assertEquals(2, w.spanY)
    }

    @Test fun mergeBack_shrunkWidgetKeepsPortraitSizeWhenOnlyMoved() {
        // widget 4×5 in verticale: in orizzontale (3 righe) è rimpicciolito; se lo sposto soltanto, in verticale resta 4×5
        val master = Layout(listOf(widget("w", 0, 0, 0, 4, 5)), 1)
        val start = derived(master)
        val w0 = start.items.first()
        assertEquals(3, w0.spanY)
        val res = merge(master, start, listOf(w0.at(0, 4, 0)))!!
        assertValid(res, port)
        val w = res.items.first()
        assertEquals(4 to 5, w.spanX to w.spanY)
    }

    @Test fun mergeBack_newPageAndItemOnIt() {
        val master = Layout(listOf(app("a", 0, 0, 0)), 1)
        val start = derived(master)
        val now = start.items + app("b", 1, 0, 0)
        val res = merge(master, start, now, nowPages = 2)!!
        assertValid(res, port)
        assertEquals(2, res.pages)
        assertEquals(1, res.items.first { it.id == "app:pkg/b" }.page)
    }

    @Test fun mergeBack_dockMoveStaysInDock() {
        val master = Layout(listOf(app("d0", -1, 0, 0), app("d1", -1, 1, 0)), 1)
        val start = derived(master)
        val now = start.items.map { if (it.id == "app:pkg/d1") it.at(-1, 3, 0) else it }
        val res = merge(master, start, now)!!
        assertValid(res, port)
        val d1 = res.items.first { it.id == "app:pkg/d1" }
        assertEquals(-1 to 3, d1.page to d1.x)
    }

    @Test fun mergeBack_dockSlotMissingInPortraitGoesToPage() {
        // la dock orizzontale ha 5 posti, quella verticale 4: il quinto posto in verticale non esiste
        val master = Layout(listOf(app("d0", -1, 0, 0)), 1)
        val start = derived(master)
        val now = start.items + app("quinta", -1, 4, 0)
        val res = merge(master, start, now)!!
        assertValid(res, port)
        assertTrue(res.items.first { it.id == "app:pkg/quinta" }.page >= 0)
    }

    @Test fun mergeBack_randomEditsAlwaysGiveValidLayout() {
        for (seed in 1..60) {
            val r = Random(seed * 31)
            val master = randomLayout(seed, port)
            val start = derived(master)
            val now = ArrayList(start.items)
            // qualche modifica a caso in orizzontale: sposta, togli, aggiungi
            repeat(r.nextInt(1, 5)) { n ->
                when (r.nextInt(3)) {
                    0 -> if (now.isNotEmpty()) {
                        val it = now.removeAt(r.nextInt(now.size))
                        val (p, x, y) = LayoutLogic.findFree(now, land, r.nextInt(0, start.pages), it.spanX, it.spanY)
                        now.add(it.at(p, x, y))
                    }
                    1 -> if (now.isNotEmpty()) now.removeAt(r.nextInt(now.size))
                    else -> {
                        val (p, x, y) = LayoutLogic.findFree(now, land, 0)
                        now.add(app("new$n", p, x, y))
                    }
                }
            }
            val pages = maxOf(start.pages, (now.maxOfOrNull { it.page } ?: 0) + 1)
            val res = merge(master, start, now, pages) ?: continue
            assertValid(res, port)
            assertEquals("seme $seed: in verticale ci sono gli stessi elementi dell'orizzontale",
                now.map { it.id }.toSet(), res.items.map { it.id }.toSet())
            // e ruotando di nuovo la Home orizzontale resta valida
            assertValid(LayoutLogic.project(res.items, res.pages, port, land), land)
        }
    }
}
