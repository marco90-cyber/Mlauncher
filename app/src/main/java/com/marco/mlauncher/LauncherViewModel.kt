package com.marco.mlauncher

import android.Manifest
import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import android.util.Xml
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Conteggio notifiche per pacchetto, aggiornato dal servizio notifiche. */
object BadgeStore {
    val counts = mutableStateMapOf<String, Int>()
}

class ShortcutEntry(val info: ShortcutInfo, val label: String, val icon: ImageBitmap?)

const val SETTINGS_CLS = "mlauncher.settings"

class IconPackData(val res: Resources, val packName: String, val map: Map<String, String>)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx: Context = application.applicationContext
    private val pm: PackageManager = ctx.packageManager
    private val sp: SharedPreferences = ctx.getSharedPreferences("mlauncher_state", Context.MODE_PRIVATE)
    val settings = LauncherSettings(ctx.getSharedPreferences("mlauncher_settings", Context.MODE_PRIVATE))

    val widgetMgr: AppWidgetManager = AppWidgetManager.getInstance(ctx)
    val widgetHost = MWidgetHost(ctx)

    var apps by mutableStateOf<Map<String, AppEntry>>(emptyMap())
        private set
    var sortedApps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    val items = mutableStateListOf<HomeItem>()
    val hidden = mutableStateListOf<String>()
    var pageCount by mutableIntStateOf(1)
        private set
    var loaded by mutableStateOf(false)
        private set

    var overlay by mutableStateOf(Overlay.NONE)
    /** pagina aperta nelle impostazioni (null = elenco delle categorie) */
    var settingsPage by mutableStateOf<String?>(null)
    var editMode by mutableStateOf(false)
    var openFolderId by mutableStateOf<String?>(null)
    var homeSignal by mutableIntStateOf(0)
        private set
    var currentPage by mutableIntStateOf(0)
    var isDefaultHome by mutableStateOf(false)
    var listenerEnabled by mutableStateOf(false)
    var accessibilityEnabled by mutableStateOf(false)
    val exploding = mutableStateListOf<String>()

    var weather by mutableStateOf<WeatherData?>(null)
        private set
    var hasLocation by mutableStateOf(false)
    var iconPacks by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set

    // dimensione approssimata di una cella (dp), aggiornata dalla Home
    var cellWdp: Float = 80f
    var cellHdp: Float = 100f

    private var pendingUninstall: AppEntry? = null
    private var hasLayout = false
    private val knownIds = HashSet<String>()
    private var weatherBusy = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_PACKAGE_REMOVED &&
                i.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            ) return
            val pkg = i.data?.schemeSpecificPart ?: return
            refresh(pkg)
        }
    }

    // ---------- caricamento app ----------

    private val iconPx = 160

    private fun drawableToBitmap(d: Drawable, inset: Int): Bitmap {
        val bmp = Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val copy = d.constantState?.newDrawable()?.mutate() ?: d
        copy.setBounds(-inset, -inset, iconPx + inset, iconPx + inset)
        copy.draw(canvas)
        return bmp
    }

    /** Icona senza maschera: i livelli adattivi a tutto campo, oppure l'icona classica su un riquadro chiaro. */
    private fun renderRaw(d: Drawable): ImageBitmap? = try {
        val bmp = Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (d is AdaptiveIconDrawable) {
            val pad = iconPx / 4
            d.background?.let { b ->
                val c = b.constantState?.newDrawable()?.mutate() ?: b
                c.setBounds(-pad, -pad, iconPx + pad, iconPx + pad)
                c.draw(canvas)
            }
            d.foreground?.let { f ->
                val c = f.constantState?.newDrawable()?.mutate() ?: f
                c.setBounds(-pad, -pad, iconPx + pad, iconPx + pad)
                c.draw(canvas)
            }
        } else {
            canvas.drawColor(0xFFF2F2F7.toInt())
            val inset = (iconPx * 0.14f).toInt()
            val c = d.constantState?.newDrawable()?.mutate() ?: d
            c.setBounds(inset, inset, iconPx - inset, iconPx - inset)
            c.draw(canvas)
        }
        bmp.asImageBitmap()
    } catch (e: Throwable) {
        null
    }

    private fun renderIcons(d: Drawable): Pair<ImageBitmap, ImageBitmap?> {
        val icon = drawableToBitmap(d, 0).asImageBitmap()
        var mono: ImageBitmap? = null
        if (Build.VERSION.SDK_INT >= 33 && d is AdaptiveIconDrawable) {
            val m = d.monochrome
            if (m != null) {
                mono = drawableToBitmap(m, iconPx / 4).asImageBitmap()
            }
        }
        return Pair(icon, mono)
    }

    private fun findIconPacks(): List<Pair<String, String>> {
        val actions = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme")
        val set = LinkedHashMap<String, String>()
        for (a in actions) {
            try {
                for (ri in pm.queryIntentActivities(Intent(a), 0)) {
                    val p = ri.activityInfo.packageName
                    if (p !in set) set[p] = ri.loadLabel(pm).toString()
                }
            } catch (e: Exception) {
                // niente
            }
        }
        return set.map { it.key to it.value }
    }

    private fun loadIconPack(name: String): IconPackData? {
        if (name.isEmpty()) return null
        return try {
            val res = pm.getResourcesForApplication(name)
            val map = HashMap<String, String>()
            var parser: XmlPullParser? = null
            val rid = res.getIdentifier("appfilter", "xml", name)
            if (rid != 0) {
                parser = res.getXml(rid)
            } else {
                try {
                    val stream = res.assets.open("appfilter.xml")
                    parser = Xml.newPullParser().apply { setInput(stream, null) }
                } catch (e: Exception) {
                    parser = null
                }
            }
            if (parser == null) return null
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val dr = parser.getAttributeValue(null, "drawable")
                    if (comp != null && dr != null && comp.startsWith("ComponentInfo{") && comp.endsWith("}")) {
                        map[comp.substring(14, comp.length - 1)] = dr
                    }
                }
                ev = parser.next()
            }
            IconPackData(res, name, map)
        } catch (e: Exception) {
            null
        }
    }

    private fun queryApps(pkg: String?, pack: IconPackData?): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        if (pkg != null) intent.setPackage(pkg)
        val list = pm.queryIntentActivities(intent, 0)
        val result = ArrayList<AppEntry>()
        for (ri in list) {
            val ai = ri.activityInfo ?: continue
            if (ai.packageName == ctx.packageName) continue // l'icona di MLauncher serve solo agli altri launcher
            try {
                val label = ri.loadLabel(pm).toString()
                var packDrawable: Drawable? = null
                if (pack != null) {
                    val dn = pack.map[ai.packageName + "/" + ai.name]
                    if (dn != null) {
                        val rid = pack.res.getIdentifier(dn, "drawable", pack.packName)
                        if (rid != 0) packDrawable = pack.res.getDrawable(rid, null)
                    }
                }
                val icon: ImageBitmap
                val mono: ImageBitmap?
                var raw: ImageBitmap? = null
                if (packDrawable != null) {
                    icon = drawableToBitmap(packDrawable, 0).asImageBitmap()
                    mono = null
                } else {
                    val dr = ri.loadIcon(pm)
                    val r = renderIcons(dr)
                    icon = r.first
                    mono = r.second
                    raw = renderRaw(dr)
                }
                val removable = (ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                    ai.packageName != ctx.packageName
                result.add(AppEntry(ai.packageName, ai.name, label, icon, mono, removable, raw))
            } catch (e: Exception) {
                // app non leggibile: la salto
            }
        }
        if (pkg == null || pkg == ctx.packageName) {
            try {
                val sd = ctx.getDrawable(R.drawable.ic_home_settings)!!
                val r = renderIcons(sd)
                result.add(AppEntry(ctx.packageName, SETTINGS_CLS, "Impostazioni Home", r.first, r.second, false, renderRaw(sd)))
            } catch (e: Exception) {
                // senza icona dell'app
            }
        }
        return result
    }

    fun refresh(pkg: String?) {
        viewModelScope.launch {
            val packName = settings.iconPack
            val fresh = withContext(Dispatchers.Default) {
                val pack = loadIconPack(packName)
                if (pkg == null) iconPacks = findIconPacks()
                queryApps(pkg, pack)
            }
            val map = HashMap(apps)
            if (pkg == null) {
                map.clear()
            } else {
                val old = map.values.filter { it.pkg == pkg }
                old.forEach { map.remove(it.id) }
                // durante una disinstallazione tengo l'icona per l'animazione
                val pend = pendingUninstall
                if (fresh.isEmpty() && pend != null && pend.pkg == pkg) {
                    old.forEach { map[it.id] = it }
                }
            }
            fresh.forEach { map[it.id] = it }
            apps = map
            sortedApps = map.values.sortedBy { it.label.lowercase() }
            reconcile()
            loaded = true
        }
    }

    fun reloadIcons() = refresh(null)

    // ---------- layout: geometria ----------

    private fun overlaps(a: HomeItem, page: Int, x: Int, y: Int, sx: Int, sy: Int) =
        a.page == page && a.x < x + sx && x < a.x + a.spanX && a.y < y + sy && y < a.y + a.spanY

    private fun areaFree(page: Int, x: Int, y: Int, sx: Int, sy: Int, ignoreId: String? = null): Boolean {
        if (x < 0 || y < 0) return false
        if (page == -1) {
            if (y != 0 || sy != 1 || x + sx > settings.dockSlots) return false
        } else if (x + sx > settings.cols || y + sy > settings.rows) {
            return false
        }
        return items.none { it.id != ignoreId && overlaps(it, page, x, y, sx, sy) }
    }

    private fun findFree(startPage: Int, sx: Int = 1, sy: Int = 1): Triple<Int, Int, Int> {
        val cols = settings.cols
        val rows = settings.rows
        val w = minOf(sx, cols)
        val h = minOf(sy, rows)
        var p = startPage
        while (true) {
            for (y in 0..(rows - h)) for (x in 0..(cols - w)) {
                if (areaFree(p, x, y, w, h)) return Triple(p, x, y)
            }
            p++
        }
    }

    private fun ensurePages(upTo: Int) {
        if (upTo + 1 > pageCount) pageCount = upTo + 1
    }

    private fun addApp(appId: String, startPage: Int = 0) {
        val (p, x, y) = findFree(startPage)
        items.add(AppItem(appId, p, x, y))
        ensurePages(p)
    }

    private fun replace(old: HomeItem, new: HomeItem) {
        val idx = items.indexOfFirst { it.id == old.id }
        if (idx >= 0) items[idx] = new
    }

    private fun placedAppIds(): Set<String> {
        val set = HashSet<String>()
        for (it in items) {
            when (it) {
                is AppItem -> set.add(it.appId)
                is FolderItem -> set.addAll(it.appIds)
                else -> {}
            }
        }
        return set
    }

    // ---------- layout: widget ----------

    private fun addInternalWidget(kind: String, sx: Int, sy: Int, startPage: Int) {
        val (p, x, y) = findFree(startPage, sx, sy)
        val w = minOf(sx, settings.cols)
        val h = minOf(sy, settings.rows)
        items.add(WidgetItem(UUID.randomUUID().toString(), WidgetRef(kind), p, x, y, w, h))
        ensurePages(p)
    }

    /** Mette orologio e meteo in cima alla prima pagina, spostando le icone che occupano il posto. */
    private fun placeDefaultWidgetsOnTop() {
        items.removeAll { it is WidgetItem && it.ref.kind != "ext" }
        val cols = settings.cols
        val w = if (cols >= 4) 2 else 1
        val slots = listOf(Triple("clock", 0, 0), Triple("weather", minOf(w, cols - w), 0))
        val moved = ArrayList<HomeItem>()
        for ((_, x, y) in slots) {
            for (it in items.toList()) {
                if (it.page == 0 && overlaps(it, 0, x, y, w, 2)) {
                    items.remove(it)
                    moved.add(it)
                }
            }
        }
        for ((kind, x, y) in slots) {
            items.add(WidgetItem(UUID.randomUUID().toString(), WidgetRef(kind), 0, x, y, w, 2))
        }
        for (m in moved) {
            val (p, x, y) = findFree(0, m.spanX, m.spanY)
            items.add(m.at(p, x, y))
            ensurePages(p)
        }
    }

    fun moveInFolder(folderId: String, appId: String, index: Int) {
        val f = items.firstOrNull { it is FolderItem && it.folderId == folderId } as? FolderItem ?: return
        val l = f.appIds.toMutableList()
        l.remove(appId)
        l.add(index.coerceIn(0, l.size), appId)
        replace(f, f.copy(appIds = l))
        saveLayout()
    }

    /** Ricarica app, icone, meteo e layout. */
    fun restartHome() {
        editMode = false
        openFolderId = null
        overlay = Overlay.NONE
        refresh(null)
        refreshWeather(true)
        homeSignal += 1
    }

    fun addStackPreset(sx: Int, sy: Int, kinds: List<String>) {
        val (p, x, y) = findFree(currentPage, minOf(sx, settings.cols), minOf(sy, settings.rows))
        items.add(
            StackItem(UUID.randomUUID().toString(), kinds.map { WidgetRef(it) }, p, x, y,
                minOf(sx, settings.cols), minOf(sy, settings.rows))
        )
        ensurePages(p)
        overlay = Overlay.NONE
        saveLayout()
    }

    fun addWidgetFromPicker(kind: String, sx: Int, sy: Int) {
        val t = stackTarget
        if (t != null && addRefToStack(t, WidgetRef(kind))) return
        addInternalWidget(kind, sx, sy, currentPage)
        overlay = Overlay.NONE
        saveLayout()
    }

    fun addStackPreset() {
        val (p, x, y) = findFree(currentPage, 2, 2)
        items.add(
            StackItem(
                UUID.randomUUID().toString(),
                listOf(WidgetRef("clock"), WidgetRef("weather")),
                p, x, y, 2, 2
            )
        )
        ensurePages(p)
        overlay = Overlay.NONE
        saveLayout()
    }

    fun addExternalWidget(widgetId: Int, info: AppWidgetProviderInfo) {
        val t = stackTarget
        if (t != null && addRefToStack(t, WidgetRef("ext", widgetId, info.provider.flattenToString()))) return
        val density = ctx.resources.displayMetrics.density
        val wDp = info.minWidth / density
        val hDp = info.minHeight / density
        val sx = ceil(((wDp - 8f).coerceAtLeast(1f)) / cellWdp.coerceAtLeast(40f)).toInt()
            .coerceIn(1, minOf(4, settings.cols))
        val sy = ceil(((hDp - 8f).coerceAtLeast(1f)) / cellHdp.coerceAtLeast(40f)).toInt()
            .coerceIn(1, minOf(4, settings.rows))
        val (p, x, y) = findFree(currentPage, sx, sy)
        items.add(
            WidgetItem(
                UUID.randomUUID().toString(),
                WidgetRef("ext", widgetId, info.provider.flattenToString()),
                p, x, y, sx, sy
            )
        )
        ensurePages(p)
        overlay = Overlay.NONE
        saveLayout()
    }

    fun discardWidgetId(id: Int) {
        try {
            widgetHost.deleteAppWidgetId(id)
        } catch (e: Exception) {
            // già rimosso
        }
    }

    private fun refsOf(i: HomeItem): List<WidgetRef> = when (i) {
        is WidgetItem -> listOf(i.ref)
        is StackItem -> i.refs
        else -> emptyList()
    }

    fun removeWidgetItem(item: HomeItem) {
        refsOf(item).filter { it.kind == "ext" }.forEach { discardWidgetId(it.widgetId) }
        items.removeAll { it.id == item.id }
        saveLayout()
    }

    fun removeFromStack(stack: StackItem, ref: WidgetRef) {
        val rest = stack.refs - ref
        if (ref.kind == "ext") discardWidgetId(ref.widgetId)
        replace(stack, stack.copy(refs = rest))
        saveLayout()
    }

    /** Stack al quale aggiungere il prossimo widget scelto (null = nuovo widget). */
    var stackTarget by mutableStateOf<String?>(null)

    private fun addRefToStack(uid: String, ref: WidgetRef): Boolean {
        val st = items.firstOrNull { it is StackItem && it.uid == uid } as? StackItem ?: return false
        replace(st, st.copy(refs = st.refs + ref))
        stackTarget = null
        overlay = Overlay.NONE
        saveLayout()
        return true
    }

    fun addEmptyStack(sx: Int, sy: Int) = addStackPreset(sx, sy, emptyList())

    fun resizeItem(item: HomeItem, sx: Int, sy: Int) {
        val cur = items.firstOrNull { it.id == item.id } ?: return
        if (sx == cur.spanX && sy == cur.spanY) return
        if (!areaFree(cur.page, cur.x, cur.y, sx, sy, cur.id)) return
        replace(cur, cur.sized(sx, sy))
        saveLayout()
    }

    fun setFolderBig(folderId: String, big: Boolean) {
        val f = items.firstOrNull { it is FolderItem && it.folderId == folderId } as? FolderItem ?: return
        if (f.page < 0) return
        if (!big) {
            replace(f, f.copy(big = false))
        } else if (areaFree(f.page, f.x, f.y, 2, 2, f.id)) {
            replace(f, f.copy(big = true))
        } else {
            items.removeAll { it.id == f.id }
            val (p, x, y) = findFree(f.page, 2, 2)
            items.add(f.copy(big = true, page = p, x = x, y = y))
            ensurePages(p)
        }
        saveLayout()
    }

    fun dissolveFolder(folderId: String) {
        val f = items.firstOrNull { it is FolderItem && it.folderId == folderId } as? FolderItem ?: return
        items.removeAll { it.id == f.id }
        f.appIds.forEach { addApp(it, maxOf(f.page, 0)) }
        if (openFolderId == folderId) openFolderId = null
        saveLayout()
    }

    // ---------- layout: iniziale e coerenza ----------

    private fun resolvePkg(intent: Intent): String? {
        val p = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        return if (p == null || p == "android") null else p
    }

    private fun buildDefault() {
        items.clear()
        pageCount = 1
        val byPkg = apps.values.groupBy { it.pkg }
        val dockPkgs = listOfNotNull(
            resolvePkg(Intent(Intent.ACTION_DIAL)),
            resolvePkg(Intent(Intent.ACTION_VIEW, Uri.parse("sms:"))),
            resolvePkg(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))),
            resolvePkg(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
        ).distinct()
        var dx = 0
        for (pkg in dockPkgs) {
            val a = byPkg[pkg]?.firstOrNull() ?: continue
            if (dx >= settings.dockSlots) break
            items.add(AppItem(a.id, -1, dx, 0))
            dx++
        }
        // orologio e meteo di default
        addInternalWidget("clock", 2, 2, 0)
        addInternalWidget("weather", 2, 2, 0)
        sp.edit().putBoolean("defaultWidgets", true).apply()

        if (settings.homeMode == "all") {
            val placed = placedAppIds()
            sortedApps.filter { it.id !in placed && it.id !in hidden }.forEach { addApp(it.id) }
        } else {
            // pochi elementi in Home, come sui launcher Samsung: il resto sta nel cassetto
            val preferred = listOf(
                "com.google.android.apps.photos", "com.google.android.gm",
                "com.google.android.apps.maps", "com.android.vending"
            )
            val placed = placedAppIds()
            for (pkg in preferred) {
                val a = byPkg[pkg]?.firstOrNull() ?: continue
                if (a.id in placed) continue
                addApp(a.id)
            }
        }
        apps.values.firstOrNull { it.pkg == ctx.packageName && it.cls == SETTINGS_CLS }?.let {
            if (it.id !in placedAppIds()) addApp(it.id)
        }
        sp.edit().putBoolean("settingsIcon", true).apply()
        knownIds.clear()
        knownIds.addAll(apps.keys)
    }

    private fun reconcile() {
        val ids = apps.keys
        if (ids.isEmpty()) return
        if (!hasLayout) {
            buildDefault()
            hasLayout = true
            saveLayout()
            return
        }
        for (item in items.toList()) {
            when (item) {
                is AppItem -> if (item.appId !in ids) items.remove(item)
                is FolderItem -> {
                    val keep = item.appIds.filter { it in ids }
                    if (keep.size != item.appIds.size) {
                        if (keep.size <= 1) {
                            items.remove(item)
                            keep.firstOrNull()?.let { items.add(AppItem(it, item.page, item.x, item.y)) }
                        } else {
                            replace(item, item.copy(appIds = keep))
                        }
                    }
                }
                else -> {}
            }
        }
        // prima volta dopo l'introduzione dei widget: aggiungo orologio e meteo
        if (!sp.getBoolean("defaultWidgetsTop", false)) {
            placeDefaultWidgetsOnTop()
            sp.edit().putBoolean("defaultWidgets", true).putBoolean("defaultWidgetsTop", true).apply()
        }
        if (!sp.getBoolean("settingsIcon", false)) {
            apps.values.firstOrNull { it.pkg == ctx.packageName && it.cls == SETTINGS_CLS }?.let {
                if (it.id !in placedAppIds()) addApp(it.id)
                sp.edit().putBoolean("settingsIcon", true).apply()
            }
        }
        val placed = placedAppIds()
        if (settings.homeMode == "all") {
            sortedApps.filter { it.id !in placed && it.id !in hidden }.forEach { addApp(it.id) }
        } else if (settings.autoAddNew && knownIds.isNotEmpty()) {
            ids.filter { it !in knownIds && it !in placed && it !in hidden }.forEach { addApp(it) }
        }
        knownIds.addAll(ids)
        saveLayout()
    }

    // ---------- salvataggio ----------

    private fun encodeRef(r: WidgetRef) = JSONObject().put("k", r.kind).put("w", r.widgetId).put("p", r.provider)

    private fun decodeRef(o: JSONObject) = WidgetRef(o.getString("k"), o.optInt("w", -1), o.optString("p", ""))

    private fun encodeItems(): JSONArray {
        val arr = JSONArray()
        for (it in items) {
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

    private fun decodeItems(arr: JSONArray, skipExt: Boolean): List<HomeItem> {
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
                    val sx = o.getInt("sx")
                    val sy = o.getInt("sy")
                    out.add(StackItem(o.getString("uid"), refs, page, x, y, sx, sy))
                }
            }
        }
        return out
    }

    private fun loadLayout(): Boolean {
        val s = sp.getString("layout", null) ?: return false
        try {
            items.addAll(decodeItems(JSONArray(s), false))
        } catch (e: Exception) {
            items.clear()
            return false
        }
        pageCount = sp.getInt("pages", 1).coerceAtLeast(1)
        sp.getStringSet("hidden", null)?.let { hidden.addAll(it) }
        return true
    }

    private fun saveLayout() {
        sp.edit()
            .putString("layout", encodeItems().toString())
            .putInt("pages", pageCount)
            .putStringSet("known", HashSet(knownIds))
            .putStringSet("hidden", HashSet(hidden))
            .apply()
    }

    // ---------- backup ----------

    fun buildBackup(): String {
        val o = JSONObject()
        o.put("version", 1)
        o.put("layout", encodeItems())
        o.put("pages", pageCount)
        o.put("hidden", JSONArray(hidden.toList()))
        o.put("settings", settings.dump())
        return o.toString(2)
    }

    fun restoreBackup(text: String): Boolean {
        return try {
            val o = JSONObject(text)
            val list = decodeItems(o.getJSONArray("layout"), true)
            settings.load(o.getJSONObject("settings"))
            items.clear()
            items.addAll(list)
            pageCount = o.optInt("pages", 1).coerceAtLeast(1)
            hidden.clear()
            val h = o.optJSONArray("hidden")
            if (h != null) for (i in 0 until h.length()) hidden.add(h.getString(i))
            hasLayout = true
            saveLayout()
            refresh(null)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---------- operazioni dalla UI ----------

    fun dropItem(dragged: HomeItem, page: Int, x: Int, y: Int) {
        // elementi "fantasma" (app che non esistono più) non devono bloccare le celle
        items.removeAll { g ->
            (g is AppItem && g.appId !in apps && g.id != dragged.id && g.appId !in exploding) ||
                (g is FolderItem && g.appIds.none { it in apps })
        }
        val item = items.firstOrNull { it.id == dragged.id } ?: return
        val sx = item.spanX
        val sy = item.spanY
        val isWidget = item is WidgetItem || item is StackItem
        if (page == -1 && (isWidget || sx > 1 || sy > 1)) return
        val hits = items.filter { it.id != item.id && overlaps(it, page, x, y, sx, sy) }
        if (hits.isEmpty()) {
            if (areaFree(page, x, y, sx, sy, item.id)) {
                replace(item, item.at(page, x, y))
                if (page >= 0) ensurePages(page)
            }
        } else if (hits.size == 1) {
            val target = hits[0]
            val small = sx == 1 && sy == 1 && target.spanX == 1 && target.spanY == 1
            if (small && page >= 0 && item is AppItem && target is AppItem) {
                val f = FolderItem(UUID.randomUUID().toString(), "Cartella", listOf(target.appId, item.appId), page, target.x, target.y)
                items.remove(item)
                items.remove(target)
                items.add(f)
            } else if (small && item is AppItem && target is FolderItem) {
                replace(target, target.copy(appIds = target.appIds + item.appId))
                items.remove(item)
            } else if (small) {
                val op = item.page
                val ox = item.x
                val oy = item.y
                replace(target, target.at(op, ox, oy))
                replace(item, item.at(page, target.x, target.y))
            } else if (isWidget && (target is WidgetItem || target is StackItem) &&
                target.spanX == sx && target.spanY == sy
            ) {
                val refs = refsOf(target) + refsOf(item)
                val uid = if (target is StackItem) target.uid else UUID.randomUUID().toString()
                items.removeAll { it.id == item.id || it.id == target.id }
                items.add(StackItem(uid, refs, target.page, target.x, target.y, sx, sy))
            } else if (sx > 1 || sy > 1) {
                displaceAndPlace(item, hits, page, x, y)
            }
        } else if (sx > 1 || sy > 1) {
            displaceAndPlace(item, hits, page, x, y)
        }
        saveLayout()
    }

    var highlightId by mutableStateOf<String?>(null)
        private set
    var locatePage by mutableIntStateOf(-1)
        private set
    var locateSignal by mutableIntStateOf(0)
        private set

    /** Porta l'utente alla pagina della Home dove si trova l'app e la evidenzia. */
    fun locateApp(appId: String): Boolean {
        val direct = items.firstOrNull { it is AppItem && it.appId == appId }
        val folder = items.firstOrNull { it is FolderItem && appId in it.appIds } as? FolderItem
        val target = direct ?: folder ?: return false
        overlay = Overlay.NONE
        editMode = false
        locatePage = target.page
        locateSignal += 1
        highlightId = target.id
        if (folder != null && direct == null) openFolderId = folder.folderId
        val mine = target.id
        viewModelScope.launch {
            kotlinx.coroutines.delay(3500)
            if (highlightId == mine) highlightId = null
        }
        return true
    }

    /** Riporta la Home all'impostazione pulita: widget in alto, poche app, dock. */
    fun resetHome() {
        items.filterIsInstance<WidgetItem>().filter { it.ref.kind == "ext" }.forEach { discardWidgetId(it.ref.widgetId) }
        items.filterIsInstance<StackItem>().forEach { st -> st.refs.filter { it.kind == "ext" }.forEach { discardWidgetId(it.widgetId) } }
        editMode = false
        openFolderId = null
        buildDefault()
        saveLayout()
        homeSignal += 1
    }

    /** Mette un elemento grande dove vuole e sposta in posti liberi quelli che occupavano l'area. */
    private fun displaceAndPlace(item: HomeItem, hits: List<HomeItem>, page: Int, x: Int, y: Int) {
        if (page < 0 || x + item.spanX > settings.cols || y + item.spanY > settings.rows) return
        replace(item, item.at(page, x, y))
        for (h in hits) {
            items.removeAll { it.id == h.id }
            val (p, nx, ny) = findFree(page, h.spanX, h.spanY)
            items.add(h.at(p, nx, ny))
            ensurePages(p)
        }
    }

    /** Crea una cartella con l'app trascinata e quelle raccolte. */
    fun createFolderFrom(main: AppItem, appIds: List<String>) {
        val cur = items.firstOrNull { it.id == main.id } ?: return
        val ids = (listOf(main.appId) + appIds).distinct().filter { it in apps }
        if (ids.size < 2) return
        val keepPage = cur.page
        val keepX = cur.x
        val keepY = cur.y
        items.removeAll { it is AppItem && it.appId in ids }
        val (p, x, y) = if (keepPage >= 0) Triple(keepPage, keepX, keepY) else findFree(currentPage)
        items.add(FolderItem(UUID.randomUUID().toString(), "Cartella", ids, p, x, y))
        ensurePages(p)
        saveLayout()
    }

    /** Sposta le app raccolte nei primi posti liberi della pagina indicata. */
    fun gatherApps(appIds: List<String>, page: Int) {
        for (id in appIds) {
            if (items.none { it is AppItem && it.appId == id }) continue
            items.removeAll { it is AppItem && it.appId == id }
            addApp(id, maxOf(page, 0))
        }
        saveLayout()
    }

    fun renameFolder(folderId: String, name: String) {
        val f = items.firstOrNull { it is FolderItem && it.folderId == folderId } as? FolderItem ?: return
        val n = name.trim().ifEmpty { "Cartella" }
        if (n != f.name) {
            replace(f, f.copy(name = n))
            saveLayout()
        }
    }

    fun removeFromFolder(folderId: String, appId: String) {
        val f = items.firstOrNull { it is FolderItem && it.folderId == folderId } as? FolderItem ?: return
        val rest = f.appIds - appId
        if (rest.size <= 1) {
            items.remove(f)
            rest.firstOrNull()?.let { items.add(AppItem(it, f.page, f.x, f.y)) }
            if (openFolderId == folderId) openFolderId = null
        } else {
            replace(f, f.copy(appIds = rest))
        }
        addApp(appId, maxOf(f.page, 0))
        saveLayout()
    }

    fun addToHome(appId: String) {
        if (appId in placedAppIds()) return
        addApp(appId, currentPage)
        saveLayout()
    }

    fun hideApp(appId: String) {
        if (appId !in hidden) hidden.add(appId)
        for (it in items.toList()) {
            when (it) {
                is AppItem -> if (it.appId == appId) items.remove(it)
                is FolderItem -> if (appId in it.appIds) {
                    val rest = it.appIds - appId
                    if (rest.size <= 1) {
                        items.remove(it)
                        rest.firstOrNull()?.let { r -> items.add(AppItem(r, it.page, it.x, it.y)) }
                    } else {
                        replace(it, it.copy(appIds = rest))
                    }
                }
                else -> {}
            }
        }
        saveLayout()
    }

    fun unhideApp(appId: String) {
        hidden.remove(appId)
        saveLayout()
    }

    fun addPage() {
        pageCount += 1
        saveLayout()
    }

    fun exitEdit() {
        editMode = false
        trimPages()
    }

    private fun trimPages() {
        val used = items.filter { it.page >= 0 }.map { it.page }.toSortedSet()
        val map = HashMap<Int, Int>()
        used.forEachIndexed { index, p -> map[p] = index }
        for (it in items.toList()) {
            if (it.page >= 0) replace(it, it.at(map[it.page] ?: 0, it.x, it.y))
        }
        pageCount = maxOf(used.size, 1)
        saveLayout()
    }

    fun onHomePressed() {
        overlay = Overlay.NONE
        openFolderId = null
        if (editMode) exitEdit()
        homeSignal += 1
    }

    fun setSetting(key: String, value: Any) {
        settings.set(key, value)
        when (key) {
            "homeMode" -> {
                if (value == "all") {
                    val placed = placedAppIds()
                    sortedApps.filter { it.id !in placed && it.id !in hidden }.forEach { addApp(it.id) }
                    saveLayout()
                }
            }
            "cols", "rows" -> reflow()
            "dockSlots" -> {
                val n = settings.dockSlots
                val out = items.filter { it.page == -1 && it.x >= n }
                items.removeAll(out)
                for (o in out) if (o is AppItem) addApp(o.appId, 0) else if (o is FolderItem) {
                    val (p, x, y) = findFree(0)
                    items.add(o.at(p, x, y))
                    ensurePages(p)
                }
                saveLayout()
            }
            "iconPack" -> reloadIcons()
        }
    }

    private fun reflow() {
        val cols = settings.cols
        val rows = settings.rows
        for (it in items.toList()) {
            if (it.page >= 0 && (it.x + it.spanX > cols || it.y + it.spanY > rows)) {
                items.remove(it)
                val n = it.sized(minOf(it.spanX, cols), minOf(it.spanY, rows))
                val nx = minOf(it.x, cols - n.spanX)
                val ny = minOf(it.y, rows - n.spanY)
                if (areaFree(n.page, nx, ny, n.spanX, n.spanY)) {
                    items.add(n.at(n.page, nx, ny))
                } else {
                    val (p, fx, fy) = findFree(it.page, n.spanX, n.spanY)
                    items.add(n.at(p, fx, fy))
                    ensurePages(p)
                }
            }
        }
        saveLayout()
    }

    // ---------- scorciatoie delle app ----------

    fun loadShortcuts(pkg: String): List<ShortcutEntry> {
        return try {
            val la = ctx.getSystemService(LauncherApps::class.java)
            val q = LauncherApps.ShortcutQuery()
                .setPackage(pkg)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            val list = la.getShortcuts(q, Process.myUserHandle()) ?: emptyList()
            list.filter { it.isEnabled }.take(4).map {
                val icon = la.getShortcutIconDrawable(it, 0)?.let { d -> drawableToBitmap(d, 0).asImageBitmap() }
                ShortcutEntry(it, it.shortLabel?.toString() ?: "", icon)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun launchShortcut(e: ShortcutEntry) {
        try {
            ctx.getSystemService(LauncherApps::class.java).startShortcut(e.info, null, null)
        } catch (ex: Exception) {
            // niente
        }
    }

    // ---------- disinstallazione ----------

    fun beginUninstall(app: AppEntry) {
        pendingUninstall = app
    }

    fun uninstallCancelled() {
        pendingUninstall = null
    }

    fun uninstallSucceeded() {
        val app = pendingUninstall ?: return
        val onHome = items.any { it is AppItem && it.appId == app.id }
        if (onHome && settings.uninstallAnim != "none") {
            exploding.add(app.id)
        } else {
            finishExplode(app.id)
        }
    }

    fun finishExplode(appId: String) {
        exploding.remove(appId)
        for (it in items.toList()) {
            when (it) {
                is AppItem -> if (it.appId == appId) items.remove(it)
                is FolderItem -> if (appId in it.appIds) {
                    val rest = it.appIds - appId
                    if (rest.size <= 1) {
                        items.remove(it)
                        rest.firstOrNull()?.let { r -> items.add(AppItem(r, it.page, it.x, it.y)) }
                    } else {
                        replace(it, it.copy(appIds = rest))
                    }
                }
                else -> {}
            }
        }
        val map = HashMap(apps)
        map.remove(appId)
        apps = map
        sortedApps = map.values.sortedBy { it.label.lowercase() }
        pendingUninstall = null
        saveLayout()
    }

    // ---------- meteo ----------

    private suspend fun findLocation(): Location? {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        for (p in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)) {
            try {
                val l = lm.getLastKnownLocation(p)
                if (l != null) return l
            } catch (e: Exception) {
                // provider non disponibile o senza permesso
            }
        }
        return suspendCancellableCoroutine { cont ->
            try {
                lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, ctx.mainExecutor) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    fun checkLocationPermission() {
        hasLocation = ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun refreshWeather(force: Boolean = false) {
        checkLocationPermission()
        if (!hasLocation || weatherBusy) return
        val w = weather
        if (!force && w != null && System.currentTimeMillis() - w.time < 20 * 60 * 1000L) return
        weatherBusy = true
        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) { fetchWeather() }
                if (data != null) {
                    weather = data
                    sp.edit().putString(
                        "weather",
                        JSONObject().put("t", data.temp).put("c", data.code).put("d", data.isDay)
                            .put("h", data.hi).put("l", data.lo).put("p", data.place).put("time", data.time).toString()
                    ).apply()
                }
            } finally {
                weatherBusy = false
            }
        }
    }

    private suspend fun fetchWeather(): WeatherData? {
        return try {
            val loc = findLocation() ?: return null
            val url = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=${loc.latitude}&longitude=${loc.longitude}" +
                    "&current=temperature_2m,weather_code,is_day" +
                    "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val txt = conn.inputStream.bufferedReader().use { it.readText() }
            val j = JSONObject(txt)
            val cur = j.getJSONObject("current")
            val daily = j.getJSONObject("daily")
            var place = ""
            try {
                @Suppress("DEPRECATION")
                val list = Geocoder(ctx, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)
                val a = list?.firstOrNull()
                place = a?.locality ?: a?.subAdminArea ?: a?.adminArea ?: ""
            } catch (e: Exception) {
                // senza nome del luogo
            }
            WeatherData(
                temp = cur.getDouble("temperature_2m").roundToInt(),
                code = cur.getInt("weather_code"),
                isDay = cur.optInt("is_day", 1) == 1,
                hi = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                lo = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
                place = place,
                time = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun loadWeatherCache() {
        val s = sp.getString("weather", null) ?: return
        try {
            val o = JSONObject(s)
            weather = WeatherData(
                o.getInt("t"), o.getInt("c"), o.optBoolean("d", true),
                o.getInt("h"), o.getInt("l"), o.optString("p", ""), o.optLong("time", 0L)
            )
        } catch (e: Exception) {
            // cache non valida
        }
    }

    override fun onCleared() {
        try {
            ctx.unregisterReceiver(receiver)
        } catch (e: Exception) {
            // già rimosso
        }
        super.onCleared()
    }

    init {
        sp.getStringSet("known", null)?.let { knownIds.addAll(it) }
        hasLayout = loadLayout()
        loadWeatherCache()
        checkLocationPermission()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        refresh(null)
    }
}
