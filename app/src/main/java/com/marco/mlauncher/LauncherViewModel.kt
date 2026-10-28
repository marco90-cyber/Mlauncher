package com.marco.mlauncher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx: Context = application.applicationContext
    private val pm: PackageManager = ctx.packageManager
    private val sp: SharedPreferences = ctx.getSharedPreferences("mlauncher_state", Context.MODE_PRIVATE)
    val settings = LauncherSettings(ctx.getSharedPreferences("mlauncher_settings", Context.MODE_PRIVATE))

    var apps by mutableStateOf<Map<String, AppEntry>>(emptyMap())
        private set
    var sortedApps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    val items = mutableStateListOf<HomeItem>()
    var pageCount by mutableIntStateOf(1)
        private set
    var loaded by mutableStateOf(false)
        private set

    var overlay by mutableStateOf(Overlay.NONE)
    var editMode by mutableStateOf(false)
    var openFolderId by mutableStateOf<String?>(null)
    var homeSignal by mutableIntStateOf(0)
        private set
    var isDefaultHome by mutableStateOf(false)
    val exploding = mutableStateListOf<String>()

    private var pendingUninstall: AppEntry? = null
    private var hasLayout = false
    private val knownIds = HashSet<String>()

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

    private fun queryApps(pkg: String?): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        if (pkg != null) intent.setPackage(pkg)
        val list = pm.queryIntentActivities(intent, 0)
        val result = ArrayList<AppEntry>()
        for (ri in list) {
            val ai = ri.activityInfo ?: continue
            try {
                val label = ri.loadLabel(pm).toString()
                val (icon, mono) = renderIcons(ri.loadIcon(pm))
                val removable = (ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                    ai.packageName != ctx.packageName
                result.add(AppEntry(ai.packageName, ai.name, label, icon, mono, removable))
            } catch (e: Exception) {
                // app non leggibile: la salto
            }
        }
        return result
    }

    fun refresh(pkg: String?) {
        viewModelScope.launch {
            val fresh = withContext(Dispatchers.Default) { queryApps(pkg) }
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

    // ---------- layout ----------

    private fun occupied(page: Int, x: Int, y: Int) =
        items.any { it.page == page && it.x == x && it.y == y }

    private fun findFree(startPage: Int): Triple<Int, Int, Int> {
        val cols = settings.cols
        val rows = settings.rows
        var p = startPage
        while (true) {
            for (y in 0 until rows) for (x in 0 until cols) {
                if (!occupied(p, x, y)) return Triple(p, x, y)
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
            }
        }
        return set
    }

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
            if (dx >= 5) break
            items.add(AppItem(a.id, -1, dx, 0))
            dx++
        }
        if (settings.homeMode == "all") {
            val placed = placedAppIds()
            sortedApps.filter { it.id !in placed }.forEach { addApp(it.id) }
        } else {
            val preferred = listOf(
                "com.android.chrome", "com.google.android.apps.photos", "com.google.android.gm",
                "com.google.android.apps.maps", "com.android.vending", "com.google.android.youtube",
                "com.google.android.calendar", "com.google.android.deskclock", "com.android.settings",
                "com.google.android.apps.messaging", "com.google.android.apps.nbu.files",
                "com.google.android.calculator"
            )
            val placed = placedAppIds()
            for (pkg in preferred) {
                val a = byPkg[pkg]?.firstOrNull() ?: continue
                if (a.id in placed) continue
                addApp(a.id)
            }
        }
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
            }
        }
        val placed = placedAppIds()
        if (settings.homeMode == "all") {
            sortedApps.filter { it.id !in placed }.forEach { addApp(it.id) }
        } else if (settings.autoAddNew && knownIds.isNotEmpty()) {
            ids.filter { it !in knownIds && it !in placed }.forEach { addApp(it) }
        }
        knownIds.addAll(ids)
        saveLayout()
    }

    private fun loadLayout(): Boolean {
        val s = sp.getString("layout", null) ?: return false
        try {
            val arr = JSONArray(s)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val page = o.getInt("page")
                val x = o.getInt("x")
                val y = o.getInt("y")
                if (o.getString("t") == "app") {
                    items.add(AppItem(o.getString("app"), page, x, y))
                } else {
                    val a = o.getJSONArray("apps")
                    val list = (0 until a.length()).map { a.getString(it) }
                    items.add(FolderItem(o.getString("fid"), o.getString("name"), list, page, x, y))
                }
            }
        } catch (e: Exception) {
            items.clear()
            return false
        }
        pageCount = sp.getInt("pages", 1).coerceAtLeast(1)
        return true
    }

    private fun saveLayout() {
        val arr = JSONArray()
        for (it in items) {
            val o = JSONObject().put("page", it.page).put("x", it.x).put("y", it.y)
            when (it) {
                is AppItem -> o.put("t", "app").put("app", it.appId)
                is FolderItem -> o.put("t", "folder").put("fid", it.folderId)
                    .put("name", it.name).put("apps", JSONArray(it.appIds))
            }
            arr.put(o)
        }
        sp.edit()
            .putString("layout", arr.toString())
            .putInt("pages", pageCount)
            .putStringSet("known", HashSet(knownIds))
            .apply()
    }

    // ---------- operazioni dalla UI ----------

    fun dropItem(dragged: HomeItem, page: Int, x: Int, y: Int) {
        val item = items.firstOrNull { it.id == dragged.id } ?: return
        val target = items.firstOrNull { it.id != item.id && it.page == page && it.x == x && it.y == y }
        if (target == null) {
            replace(item, item.at(page, x, y))
            if (page >= 0) ensurePages(page)
        } else if (page >= 0 && item is AppItem && target is AppItem) {
            val f = FolderItem(UUID.randomUUID().toString(), "Cartella", listOf(target.appId, item.appId), page, x, y)
            items.remove(item)
            items.remove(target)
            items.add(f)
        } else if (item is AppItem && target is FolderItem) {
            replace(target, target.copy(appIds = target.appIds + item.appId))
            items.remove(item)
        } else {
            val op = item.page
            val ox = item.x
            val oy = item.y
            replace(target, target.at(op, ox, oy))
            replace(item, item.at(page, x, y))
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
        addApp(appId)
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
                    sortedApps.filter { it.id !in placed }.forEach { addApp(it.id) }
                    saveLayout()
                }
            }
            "cols", "rows" -> reflow()
        }
    }

    private fun reflow() {
        val cols = settings.cols
        val rows = settings.rows
        for (it in items.toList()) {
            if (it.page >= 0 && (it.x >= cols || it.y >= rows)) {
                items.remove(it)
                val (p, x, y) = findFree(it.page)
                items.add(it.at(p, x, y))
                ensurePages(p)
            }
        }
        saveLayout()
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
            }
        }
        val map = HashMap(apps)
        map.remove(appId)
        apps = map
        sortedApps = map.values.sortedBy { it.label.lowercase() }
        pendingUninstall = null
        saveLayout()
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
