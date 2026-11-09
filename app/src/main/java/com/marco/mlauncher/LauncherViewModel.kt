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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import androidx.compose.runtime.mutableFloatStateOf
import android.graphics.BitmapFactory
import kotlin.math.max
import kotlinx.coroutines.sync.withLock
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
    /** icona trascinata dal cassetto verso la Home */
    var drawerDrag by mutableStateOf<DrawerDrag?>(null)
    var drawerDropSignal by mutableIntStateOf(0)
        private set
    /** il cassetto deve aprirsi con la tastiera sulla ricerca (tocco sul widget di ricerca) */
    var searchFocus by mutableStateOf(false)
    /** pagina aperta nelle impostazioni (null = elenco delle categorie) */
    var settingsPage by mutableStateOf<String?>(null)
    /** scheda delle impostazioni: 0 Impostazioni, 1 Stile */
    var settingsTab by mutableIntStateOf(0)

    /** Indietro nelle impostazioni: dai sottomenu dei materiali si torna a "Materiale degli elementi". */
    fun settingsBack() {
        val p = settingsPage
        settingsPage = if (p != null && p.startsWith("mat_")) "material" else null
    }

    /** Un elemento smette (o riprende) di seguire il materiale generale: la prima volta parte da quello generale. */
    fun setElFollow(e: String, follow: Boolean) {
        val s = settings
        if (!follow && s.elStyle(e) == "standard" && e != "dock") {
            setSetting("el_${e}_style", s.matStyle)
            setSetting("el_${e}_opacity", s.matOpacity)
            setSetting("el_${e}_glass", s.matGlass)
            setSetting("el_${e}_corner", s.matCorner)
        }
        setSetting("el_${e}_follow", follow)
    }
    /** schermate di benvenuto visibili */
    var showWelcome by mutableStateOf(false)

    fun finishWelcome() {
        showWelcome = false
        settings.previewLand = null
        sp.edit().putBoolean("welcomeDone", true).apply()
        AppLog.i("Benvenuto completato")
    }
    /** testo cercato nelle impostazioni e voce da evidenziare dopo averla aperta */
    var settingsQuery by mutableStateOf("")
    var settingsHighlight by mutableStateOf<String?>(null)
    private var editModeState by mutableStateOf(false)
    /** modifica Home; con la Home bloccata non si entra (e si avvisa) */
    var editMode: Boolean
        get() = editModeState
        set(v) {
            if (v && settings.lockHome) {
                lockedSignal += 1
                return
            }
            editModeState = v
            if (!v) selectedWidget = null
        }
    /** widget selezionato in modifica: mostra i pallini per ridimensionarlo */
    var selectedWidget by mutableStateOf<String?>(null)
    private var selectedAt = 0L

    fun selectWidget(id: String) {
        selectedWidget = id
        selectedAt = android.os.SystemClock.uptimeMillis()
    }

    /** la selezione è appena avvenuta: il tocco che l'ha fatta non la deve anche togliere */
    fun selectionSettled() = android.os.SystemClock.uptimeMillis() - selectedAt > 400
    /** cresce ogni volta che si prova a cambiare la Home mentre è bloccata */
    var lockedSignal by mutableIntStateOf(0)
        private set

    /** lo sfondo è chiaro: Android consiglia testo scuro sopra */
    var wallDarkText by mutableStateOf(false)

    // ---------- sfondo gestito da MLauncher ----------

    /** immagine dello sfondo di MLauncher (null = si usa quello di Android) */
    var wallBitmap by mutableStateOf<ImageBitmap?>(null)
        private set
    /** colore principale dello sfondo di MLauncher, per i colori "Dallo sfondo" */
    var wallSeed by mutableStateOf<Int?>(null)
        private set
    private var customDarkText = false
    /** posizione delle pagine (0 = prima, 1 = ultima) per la parallasse */
    var homeScroll by mutableFloatStateOf(0f)
    var wallBusy by mutableStateOf(false)
        private set
    /** lo sfondo è sempre quello di MLauncher: attivo appena l'immagine è caricata */
    val customWallActive: Boolean get() = wallBitmap != null
    /** misura reale della finestra (per ritagliare lo sfondo nella proporzione giusta) */
    var screenPx by mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)
    /** immagine aperta nell'editor dello sfondo (null = editor chiuso) */
    var wallEditSrc by mutableStateOf<Bitmap?>(null)
        private set

    private fun maxWallSide(): Int {
        val m = ctx.resources.displayMetrics
        return (max(m.widthPixels, m.heightPixels) * 1.35f).toInt().coerceIn(1280, 3200)
    }

    /** Carica lo sfondo salvato e ne ricava colore principale e testo chiaro/scuro. */
    fun loadWallpaper() {
        val f = wallFile(ctx)
        if (!f.exists()) {
            wallBitmap = null
            wallSeed = null
            // primo avvio: provo a copiare lo sfondo di Android, altrimenti uno di quelli inclusi
            setWallpaperFrom(keepSource = true) { systemWallpaperBitmap() ?: defaultBuiltin() }
            return
        }
        viewModelScope.launch {
            val bm = withContext(Dispatchers.IO) { try { BitmapFactory.decodeFile(f.path) } catch (e: Throwable) { null } }
            if (bm == null) return@launch
            val colors = withContext(Dispatchers.Default) {
                try { android.app.WallpaperColors.fromBitmap(bm) } catch (e: Throwable) { null }
            }
            wallSeed = colors?.primaryColor?.toArgb()
            customDarkText = colors != null && (colors.colorHints and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
            wallBitmap = bm.asImageBitmap()
            refreshDarkText()
        }
    }

    /** Salva come sfondo di MLauncher un'immagine scelta (galleria, sfondo di Android o incluso). */
    fun setWallpaperFrom(keepSource: Boolean = true, load: () -> Bitmap?) {
        if (wallBusy) return
        wallBusy = true
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val bm = load() ?: return@withContext false
                    java.io.FileOutputStream(wallFile(ctx)).use { bm.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                    // l'originale serve per poterlo ritagliare di nuovo
                    if (keepSource) java.io.FileOutputStream(wallSrcFile(ctx)).use { bm.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                    true
                } catch (e: Throwable) {
                    AppLog.e("Sfondo non salvato", e)
                    false
                }
            }
            wallBusy = false
            if (ok) {
                settings.set("wallMode", "custom")
                loadWallpaper()
                AppLog.i("Nuovo sfondo di MLauncher")
            } else {
                wallError += 1
            }
        }
    }
    var wallError by mutableIntStateOf(0)
        private set

    /** Foto scelta dalla galleria: si apre l'editor per ritagliarla e regolarla. */
    fun pickedWallpaper(uri: Uri) = openWallEditor {
        decodeScaled({ ctx.contentResolver.openInputStream(uri) }, 2600)
    }

    private fun defaultBuiltin(): Bitmap {
        val (w, h) = wallTarget()
        return builtinWall("aurora", w, h)
    }

    /** Sfondo incluso: si applica subito (si può poi modificare). */
    fun builtinWallpaper(key: String) = setWallpaperFrom {
        val (w, h) = wallTarget()
        builtinWall(key, w, h)
    }

    private fun systemWallpaperBitmap(): Bitmap? {
        val d = try {
            android.app.WallpaperManager.getInstance(ctx).drawable
        } catch (e: Throwable) {
            null
        } ?: return null
        return try {
            drawableToBitmap(d, 0).let { bm ->
                val big = max(bm.width, bm.height)
                if (big <= 2600) bm else Bitmap.createScaledBitmap(bm, bm.width * 2600 / big, bm.height * 2600 / big, true)
            }
        } catch (e: Throwable) {
            null
        }
    }

    /** Prova a copiare lo sfondo di Android (non sempre il sistema lo permette) e apre l'editor. */
    fun importSystemWallpaper() = openWallEditor { systemWallpaperBitmap() }

    /** Riapre l'editor sull'immagine originale dello sfondo attuale. */
    fun editCurrentWallpaper() = openWallEditor {
        val f = wallSrcFile(ctx).takeIf { it.exists() } ?: wallFile(ctx)
        BitmapFactory.decodeFile(f.path)
    }

    private fun openWallEditor(load: () -> Bitmap?) {
        if (wallBusy) return
        wallBusy = true
        viewModelScope.launch {
            val bm = withContext(Dispatchers.IO) { try { load() } catch (e: Throwable) { null } }
            wallBusy = false
            if (bm == null) wallError += 1 else wallEditSrc = bm
        }
    }

    fun closeWallEditor() {
        wallEditSrc = null
    }

    /** Misura dello sfondo salvato: alto come lo schermo, largo abbastanza per la parallasse. */
    fun wallTarget(): Pair<Int, Int> {
        val sw = if (screenPx.width > 0) screenPx.width else ctx.resources.displayMetrics.widthPixels
        val sh = if (screenPx.height > 0) screenPx.height else ctx.resources.displayMetrics.heightPixels
        val sw2 = minOf(sw, sh) // lo sfondo si prepara per il verticale
        val sh2 = max(sw, sh)
        return (sw2 * WALL_WIDE).toInt() to sh2
    }

    /** Salva il risultato dell'editor: ritaglio [crop] (in pixel dell'immagine) e regolazioni colore [matrix]. */
    fun applyWallEdit(src: Bitmap, crop: android.graphics.RectF, matrix: FloatArray?, fx: WallFx = WallFx()) {
        wallEditSrc = null
        setWallpaperFrom(keepSource = false) {
            val (w, h) = wallTarget()
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(out)
            val p = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
            if (matrix != null) p.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
            c.drawBitmap(
                src,
                android.graphics.Rect(crop.left.toInt(), crop.top.toInt(), crop.right.toInt(), crop.bottom.toInt()),
                android.graphics.Rect(0, 0, w, h),
                p
            )
            // l'originale (già ruotato/specchiato nell'editor) resta per i prossimi ritagli
            try {
                java.io.FileOutputStream(wallSrcFile(ctx)).use { src.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            } catch (e: Throwable) { }
            // effetti (sfoca, scurisci, vignettatura, grana, sfumatura, mosaico) dentro l'immagine
            applyWallFx(out, fx)
        }
    }

    // ---------- "Crea il tuo sfondo" ----------

    /** sfondo creato in modifica (null = editor chiuso) */
    var genEdit by mutableStateOf<GenWall?>(null)
    val myWalls: List<GenWall> get() = GenWall.listFrom(settings.myWalls)

    fun newMyWall() {
        genEdit = GenWall(UUID.randomUUID().toString(), "Il mio sfondo ${myWalls.size + 1}", seed = (1..1_000_000).random())
    }

    fun saveMyWall(g: GenWall, apply: Boolean) {
        val list = myWalls.toMutableList()
        val i = list.indexOfFirst { it.id == g.id }
        val named = g.copy(name = g.name.trim().ifEmpty { "Il mio sfondo" })
        if (i >= 0) list[i] = named else list.add(0, named)
        settings.set("myWalls", GenWall.listTo(list))
        genEdit = null
        if (apply) applyMyWall(named)
    }

    fun deleteMyWall(id: String) {
        settings.set("myWalls", GenWall.listTo(myWalls.filter { it.id != id }))
    }

    fun applyMyWall(g: GenWall) = setWallpaperFrom {
        val (w, h) = wallTarget()
        genWall(g, w, h)
    }

    /**
     * Mette lo sfondo di MLauncher anche in Android: [which] "home" (schermata Home di sistema),
     * "lock" (schermata di blocco) o "both".
     */
    fun wallpaperToAndroid(which: String): Boolean = try {
        val bm = BitmapFactory.decodeFile(wallFile(ctx).path)
        val flags = when (which) {
            "home" -> android.app.WallpaperManager.FLAG_SYSTEM
            "lock" -> android.app.WallpaperManager.FLAG_LOCK
            else -> android.app.WallpaperManager.FLAG_SYSTEM or android.app.WallpaperManager.FLAG_LOCK
        }
        android.app.WallpaperManager.getInstance(ctx).setBitmap(bm, null, true, flags)
        AppLog.i("Sfondo messo anche in Android ($which)")
        true
    } catch (e: Throwable) {
        AppLog.e("Sfondo di Android non impostato", e)
        false
    }

    /** Mette l'immagine attuale anche sulla schermata di blocco di Android. */
    fun wallpaperToLockScreen(): Boolean = try {
        val f = wallFile(ctx)
        val bm = BitmapFactory.decodeFile(f.path)
        android.app.WallpaperManager.getInstance(ctx).setBitmap(bm, null, true, android.app.WallpaperManager.FLAG_LOCK)
        true
    } catch (e: Throwable) {
        AppLog.e("Sfondo della schermata di blocco non impostato", e)
        false
    }

    private fun refreshDarkText() {
        if (customWallActive) wallDarkText = customDarkText else readSystemHint()
    }
    val labelDark: Boolean
        get() = when (settings.labelColor) {
            "dark" -> true
            "white" -> false
            else -> wallDarkText
        }

    fun readWallpaperHint() {
        if (customWallActive) {
            wallDarkText = customDarkText
            return
        }
        readSystemHint()
    }

    private fun readSystemHint() {
        try {
            val c = android.app.WallpaperManager.getInstance(ctx).getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)
            wallDarkText = c != null && (c.colorHints and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
        } catch (e: Exception) {
            // colori dello sfondo non disponibili
        }
    }

    // ---------- animazione di ritorno alla Home ----------

    /** avanzamento dell'animazione di ritorno (1 = ferma) */
    var returnP by mutableFloatStateOf(1f)
    var returnSignal by mutableIntStateOf(0)
        private set
    /** ultima app aperta dalla Home (per il rimbalzo e l'onda) */
    var returnAppId by mutableStateOf<String?>(null)
        private set
    /** cella da cui parte l'onda (colonna, riga) */
    var returnOrigin by mutableStateOf(0f to 0f)
        private set
    private var launchedId: String? = null

    fun noteLaunched(appId: String) {
        launchedId = appId
    }

    /** [fromApp] true = tornato da un'app, false = sblocco del telefono. */
    fun playReturn(fromApp: Boolean, force: Boolean = false) {
        val s = settings
        if (!force && (if (fromApp) !s.returnOnApp else !s.returnOnUnlock)) return
        if (s.returnAnim == "none") return
        if (overlay != Overlay.NONE || openFolderId != null || editMode) return
        val id = if (fromApp) launchedId else null
        returnAppId = id
        val hit = if (id == null) null else items.firstOrNull { i ->
            (i.page == currentPage || i.page == -1) && ((i is AppItem && i.appId == id) || (i is FolderItem && id in i.appIds))
        }
        returnOrigin = when {
            hit == null -> (gridCols / 2f) to (gridRows / 2f)
            hit.page == -1 -> (hit.x.toFloat() * gridCols / dockCount.coerceAtLeast(1)) to gridRows.toFloat()
            else -> (hit.x + hit.spanX / 2f) to (hit.y + hit.spanY / 2f)
        }
        returnP = 0f
        returnSignal += 1
    }

    /** true se la Home è bloccata (e avvisa l'utente) */
    fun blockedByLock(): Boolean {
        if (!settings.lockHome) return false
        lockedSignal += 1
        return true
    }
    var openFolderId by mutableStateOf<String?>(null)
    /** posizione della cartella toccata: la cartella aperta nasce da lì */
    var folderAnchor by mutableStateOf(androidx.compose.ui.geometry.Rect.Zero)
    var homeSignal by mutableIntStateOf(0)
        private set
    /** richiesta di andare a una pagina (azione "Pagina N") */
    var pageReq by mutableIntStateOf(-1)
    var pageReqN by mutableIntStateOf(0)
    /** anteprima di tutte le pagine (azione "Anteprima pagine") */
    var showOverview by mutableStateOf(false)

    fun goToPage(p: Int) {
        pageReq = p
        pageReqN += 1
    }

    /** Aggiunge in Home l'icona di un'azione di MLauncher. */
    fun addActionToHome(spec: String) {
        if (blockedByLock()) return
        val id = "${ctx.packageName}/$ACTION_PREFIX$spec"
        if (id !in apps) return
        addApp(id, maxOf(currentPage, 0))
        saveLayout()
        overlay = Overlay.NONE
        AppLog.i("Azione $spec aggiunta in Home")
    }
    var currentPage by mutableIntStateOf(0)
    var isDefaultHome by mutableStateOf(false)
    var listenerEnabled by mutableStateOf(false)
    var accessibilityEnabled by mutableStateOf(false)
    val exploding = mutableStateListOf<String>()

    var weather by mutableStateOf<WeatherData?>(null)
        private set
    var weatherDetail by mutableStateOf<WeatherDetail?>(null)
    /** città scelta a mano per il meteo (null = posizione del telefono) */
    var weatherCity by mutableStateOf<City?>(null)
        private set
    val weatherReady: Boolean get() = hasLocation || weatherCity != null
    var hasLocation by mutableStateOf(false)
    var iconPacks by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set

    // dimensione approssimata di una cella (dp), aggiornata dalla Home
    var cellWdp: Float = 80f
    var cellHdp: Float = 100f

    private var pendingUninstall: AppEntry? = null
    private var hasLayout = false
    /** orientamento del layout attualmente caricato in [items] */
    var layoutLand by mutableStateOf(false)
        private set
    /** dimensioni del layout caricato: la Home usa queste, così icone e griglia sono sempre coerenti */
    val gridCols: Int get() = settings.colsOf(layoutLand)
    val gridRows: Int get() = settings.rowsOf(layoutLand)
    val dockCount: Int get() = settings.dockSlotsOf(layoutLand)
    private val curCols get() = gridCols
    private val curRows get() = gridRows
    private val curSlots get() = dockCount
    private fun lk(k: String) = if (layoutLand) k + "_l" else k
    private fun otherKey(k: String) = if (layoutLand) k else k + "_l"
    private val knownIds = HashSet<String>()
    private var weatherBusy = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_PACKAGE_REMOVED &&
                i.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            ) return
            val pkg = i.data?.schemeSpecificPart ?: return
            queuePkg(pkg)
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
        val t0 = System.currentTimeMillis()
        val r = loadIconPackRaw(name)
        Perf.time("Lettura del pacchetto di icone", System.currentTimeMillis() - t0, slowMs = 800, detail = name)
        return r
    }

    private fun loadIconPackRaw(name: String): IconPackData? {
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
            // azioni di MLauncher (non compaiono nel cassetto: solo se le aggiungi tu)
            val specs = MLA_ACTIONS.map { it.first to it.second } + (1..ACTION_MAX_PAGE).map { "page:$it" to "Pagina $it" }
            for ((spec, label) in specs) {
                try {
                    val d = actionIcon(ctx, spec)
                    val r = renderIcons(d)
                    result.add(AppEntry(ctx.packageName, ACTION_PREFIX + spec, label, r.first, r.second, false, renderRaw(d)))
                } catch (e: Exception) {
                    AppLog.e("Icona dell'azione $spec non creata", e)
                }
            }
        }
        return result
    }

    // ---------- cache delle icone: la Home compare subito anche dopo che il sistema ha chiuso il Launcher ----------

    private val iconDir get() = java.io.File(ctx.cacheDir, "icons")
    private val cacheLock = kotlinx.coroutines.sync.Mutex()

    private var cacheJob: kotlinx.coroutines.Job? = null

    /** Salva icone e nomi su disco per l'avvio veloce; più richieste ravvicinate diventano un solo salvataggio. */
    private fun saveAppCache(list: List<AppEntry>, pack: String) {
        cacheJob?.cancel()
        cacheJob = viewModelScope.launch(Dispatchers.IO) {
            delay(1500)
            cacheLock.withLock {
                val t0 = System.currentTimeMillis()
                try {
                    val dir = iconDir
                    dir.deleteRecursively()
                    dir.mkdirs()
                    val arr = JSONArray()
                    fun put(b: ImageBitmap?, name: String): Boolean {
                        if (b == null) return false
                        return try {
                            java.io.FileOutputStream(java.io.File(dir, name)).use { b.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
                        } catch (e: Exception) {
                            false
                        }
                    }
                    list.forEachIndexed { i, a ->
                        if (!put(a.icon, "${i}i.png")) return@forEachIndexed
                        arr.put(
                            JSONObject().put("p", a.pkg).put("c", a.cls).put("l", a.label).put("r", a.removable).put("n", i)
                                .put("m", put(a.mono, "${i}m.png")).put("w", put(a.raw, "${i}w.png"))
                        )
                    }
                    java.io.File(dir, "index.json").writeText(JSONObject().put("pack", pack).put("apps", arr).toString())
                    AppLog.i("Cache delle icone salvata: ${list.size} app in ${System.currentTimeMillis() - t0} ms")
                } catch (e: Exception) {
                    AppLog.e("Salvataggio cache icone non riuscito", e)
                }
            }
        }
    }

    private suspend fun loadAppCache(pack: String): List<AppEntry>? = withContext(Dispatchers.IO) {
        cacheLock.withLock {
            try {
                val dir = iconDir
                val idx = java.io.File(dir, "index.json")
                if (!idx.exists()) return@withLock null
                val j = JSONObject(idx.readText())
                if (j.optString("pack") != pack) return@withLock null
                val arr = j.getJSONArray("apps")
                fun img(name: String): ImageBitmap? =
                    android.graphics.BitmapFactory.decodeFile(java.io.File(dir, name).path)?.asImageBitmap()
                val out = ArrayList<AppEntry>()
                for (k in 0 until arr.length()) {
                    val o = arr.getJSONObject(k)
                    val n = o.getInt("n")
                    val icon = img("${n}i.png") ?: continue
                    out.add(
                        AppEntry(
                            o.getString("p"), o.getString("c"), o.getString("l"), icon,
                            if (o.optBoolean("m")) img("${n}m.png") else null,
                            o.optBoolean("r"),
                            if (o.optBoolean("w")) img("${n}w.png") else null
                        )
                    )
                }
                out
            } catch (e: Exception) {
                AppLog.e("Cache icone non leggibile", e)
                null
            }
        }
    }

    private fun loadFromCache() {
        val t0 = System.currentTimeMillis()
        val pack = settings.iconPack
        viewModelScope.launch {
            val list = loadAppCache(pack) ?: return@launch
            if (loaded || list.isEmpty()) return@launch
            apps = list.associateBy { it.id }
            sortedApps = list.filter { !it.isAction }.sortedBy { it.label.lowercase() }
            loaded = true
            AppLog.i("Home mostrata dalla cache: ${list.size} app in ${System.currentTimeMillis() - t0} ms")
            Perf.time("Icone dalla cache all'avvio", System.currentTimeMillis() - t0)
        }
    }

    // Aggiornamenti di pacchetti in arrivo tutti insieme (es. tornando alla Home dopo gli aggiornamenti del
    // Play Store): li raccolgo per un attimo e li elaboro in un colpo solo, invece di ricaricare tutto N volte.
    private val pendingPkgs = LinkedHashSet<String>()
    private var pkgJob: kotlinx.coroutines.Job? = null

    private fun queuePkg(pkg: String) {
        pendingPkgs.add(pkg)
        pkgJob?.cancel()
        pkgJob = viewModelScope.launch {
            delay(350)
            val list = pendingPkgs.toList()
            pendingPkgs.clear()
            refreshPkgs(list)
        }
    }

    private fun refreshPkgs(list: List<String>) {
        if (list.isEmpty()) return
        val t0 = System.currentTimeMillis()
        viewModelScope.launch {
            val packName = settings.iconPack
            val fresh = withContext(Dispatchers.Default) {
                try { smart.prepare(); smartReady = true } catch (e: Exception) { AppLog.e("Categorie non pronte", e) }
                val pack = loadIconPack(packName)
                list.associateWith { queryApps(it, pack) }
            }
            val t1 = System.currentTimeMillis()
            val map = HashMap(apps)
            for ((pkg, got) in fresh) {
                val old = map.values.filter { it.pkg == pkg }
                old.forEach { map.remove(it.id) }
                // durante una disinstallazione tengo l'icona per l'animazione
                val pend = pendingUninstall
                if (got.isEmpty() && pend != null && pend.pkg == pkg) old.forEach { map[it.id] = it }
                got.forEach { map[it.id] = it }
            }
            apps = map
            sortedApps = map.values.filter { !it.isAction }.sortedBy { it.label.lowercase() }
            reconcile()
            loaded = true
            AppLog.i(
                (if (list.size == 1) "Aggiornato il pacchetto ${list[0]}" else "Aggiornati ${list.size} pacchetti: ${list.joinToString(", ")}") +
                    " (${t1 - t0} ms in background + ${System.currentTimeMillis() - t1} ms sulla Home)"
            )
            saveAppCache(map.values.toList(), packName)
        }
    }

    fun refresh(pkg: String?) {
        if (pkg != null) {
            queuePkg(pkg)
            return
        }
        val t0 = System.currentTimeMillis()
        viewModelScope.launch {
            val packName = settings.iconPack
            val fresh = withContext(Dispatchers.Default) {
                // ruoli delle app (browser, galleria…) possono cambiare con installazioni e rimozioni
                try { smart.prepare(); smartReady = true } catch (e: Exception) { AppLog.e("Categorie non pronte", e) }
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
            sortedApps = map.values.filter { !it.isAction }.sortedBy { it.label.lowercase() }
            reconcile()
            loaded = true
            if (pkg == null) {
                AppLog.i("App caricate: ${map.size} in ${System.currentTimeMillis() - t0} ms")
                Perf.time("Caricamento completo di app e icone", System.currentTimeMillis() - t0)
            }
            else AppLog.i("Aggiornato il pacchetto $pkg")
            saveAppCache(map.values.toList(), packName)
        }
    }

    fun reloadIcons() = refresh(null)

    // ---------- layout: geometria ----------

    private fun overlaps(a: HomeItem, page: Int, x: Int, y: Int, sx: Int, sy: Int) =
        a.page == page && a.x < x + sx && x < a.x + a.spanX && a.y < y + sy && y < a.y + a.spanY

    private fun areaFree(page: Int, x: Int, y: Int, sx: Int, sy: Int, ignoreId: String? = null): Boolean {
        if (x < 0 || y < 0) return false
        if (page == -1) {
            if (y != 0 || sy != 1 || x + sx > curSlots) return false
        } else if (x + sx > curCols || y + sy > curRows) {
            return false
        }
        return items.none { it.id != ignoreId && overlaps(it, page, x, y, sx, sy) }
    }

    private fun findFree(startPage: Int, sx: Int = 1, sy: Int = 1): Triple<Int, Int, Int> {
        val cols = curCols
        val rows = curRows
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
        val w = minOf(sx, curCols)
        val h = minOf(sy, curRows)
        items.add(WidgetItem(UUID.randomUUID().toString(), newRef(kind), p, x, y, w, h))
        ensurePages(p)
    }

    /** Riferimento per un nuovo widget interno: le note hanno un proprio id per il testo. */
    // ---------- pagina −1 ----------

    /** apertura della pagina −1: 0 chiusa, 1 aperta (segue il dito) */
    var minusP by mutableFloatStateOf(0f)
    /** il prossimo widget scelto va nella pagina −1 */
    var minusPick by mutableStateOf(false)
    var minusCloseSignal by mutableIntStateOf(0)
        private set
    val minusWidgets = mutableStateListOf<MinusW>()
    /** nome del proprietario del telefono (se trovato) */
    var autoName by mutableStateOf<String?>(null)
        private set

    fun loadMinus() {
        minusWidgets.clear()
        try {
            val a = JSONArray(settings.minusWidgets)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                // le prime versioni salvavano solo la grandezza (1-3): larghe tutta la pagina
                val old = o.optInt("s", 2)
                minusWidgets.add(
                    MinusW(
                        o.getString("u"),
                        WidgetRef(o.getString("k"), o.optInt("w", -1), o.optString("p", "")),
                        o.optInt("x", 4),
                        o.optInt("y", old)
                    )
                )
            }
        } catch (e: Exception) {
            AppLog.e("Pagina -1 non letta", e)
        }
    }

    private fun saveMinus() {
        val a = JSONArray()
        minusWidgets.forEach { m ->
            a.put(JSONObject().put("u", m.uid).put("k", m.ref.kind).put("w", m.ref.widgetId).put("p", m.ref.provider).put("x", m.sx).put("y", m.sy))
        }
        settings.set("minusWidgets", a.toString())
    }

    /** la pagina −1 è in modifica: bordo, maniglia per ridimensionare, frecce e "togli" */
    var minusEdit by mutableStateOf(false)

    private fun addToMinus(ref: WidgetRef, sx: Int, sy: Int) {
        minusWidgets.add(MinusW(UUID.randomUUID().toString(), ref, sx.coerceIn(1, MINUS_COLS), sy.coerceIn(1, MINUS_ROWS)))
        saveMinus()
        minusPick = false
        stackTarget = null
        overlay = Overlay.NONE
        AppLog.i("Widget ${ref.kind} aggiunto alla pagina -1")
    }

    fun moveMinus(uid: String, dir: Int) {
        val i = minusWidgets.indexOfFirst { it.uid == uid }
        val j = i + dir
        if (i < 0 || j !in minusWidgets.indices) return
        val m = minusWidgets.removeAt(i)
        minusWidgets.add(j, m)
        saveMinus()
    }

    fun resizeMinus(uid: String, sx: Int, sy: Int) {
        val i = minusWidgets.indexOfFirst { it.uid == uid }
        if (i < 0) return
        val m = minusWidgets[i]
        val nx = sx.coerceIn(1, MINUS_COLS)
        val ny = sy.coerceIn(1, MINUS_ROWS)
        if (m.sx == nx && m.sy == ny) return
        minusWidgets[i] = m.copy(sx = nx, sy = ny)
        saveMinus()
    }

    fun removeMinus(uid: String) {
        val m = minusWidgets.firstOrNull { it.uid == uid } ?: return
        if (m.ref.kind == "ext") discardWidgetId(m.ref.widgetId)
        minusWidgets.remove(m)
        saveMinus()
    }

    fun openMinusPicker() {
        if (blockedByLock()) return
        minusPick = true
        stackTarget = null
        overlay = Overlay.WIDGETS
    }

    fun closeMinus() {
        minusEdit = false
        if (minusP > 0f) minusCloseSignal += 1
    }

    /** Nome per il saluto: quello scritto nelle impostazioni, altrimenti quello del telefono. */
    fun greetName(): String = settings.minusName.trim().ifEmpty { autoName ?: "" }

    fun loadAutoName() {
        viewModelScope.launch {
            autoName = withContext(Dispatchers.IO) {
                val generic = setOf("owner", "proprietario", "utente", "user", "me", "io", "proprietaria")
                var n: String? = try {
                    ctx.getSystemService(android.os.UserManager::class.java)?.userName
                } catch (e: Exception) {
                    null
                }
                if (n.isNullOrBlank() || n.trim().lowercase() in generic) n = null
                if (n == null && ctx.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    n = try {
                        ctx.contentResolver.query(
                            android.provider.ContactsContract.Profile.CONTENT_URI,
                            arrayOf(android.provider.ContactsContract.Profile.DISPLAY_NAME), null, null, null
                        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    } catch (e: Exception) {
                        null
                    }
                }
                n?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() }
            }
        }
    }

    private fun newRef(kind: String) = if (kind == "notes") WidgetRef("notes", -1, UUID.randomUUID().toString()) else WidgetRef(kind)

    // ---------- backup automatico ----------

    var lastAutoBackup by mutableStateOf(0L)
        private set
    var autoBackupBusy by mutableStateOf(false)
        private set
    var autoBackupError by mutableStateOf<String?>(null)
        private set

    /** Fa il backup nella cartella scelta se è passato il tempo impostato (o subito con [force]). */
    fun runAutoBackup(force: Boolean = false) {
        lastAutoBackup = sp.getLong("lastAutoBackup", 0L)
        val dir = settings.backupDir
        if (dir.isEmpty() || autoBackupBusy) return
        if (!force) {
            if (!settings.autoBackup) return
            if (System.currentTimeMillis() - lastAutoBackup < settings.backupEvery * 24L * 3600_000L) return
        }
        val json = buildBackup()
        val keep = settings.backupKeep
        autoBackupBusy = true
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) { writeBackupTo(Uri.parse(dir), json, keep) }
            autoBackupBusy = false
            autoBackupError = err
            if (err == null) {
                lastAutoBackup = System.currentTimeMillis()
                sp.edit().putLong("lastAutoBackup", lastAutoBackup).apply()
                AppLog.i("Backup automatico salvato")
            } else {
                AppLog.w("Backup automatico non riuscito: $err")
            }
        }
    }

    /** Scrive un nuovo file nella cartella e cancella i backup più vecchi oltre [keep]. Restituisce l'errore o null. */
    private fun writeBackupTo(tree: Uri, json: String, keep: Int): String? {
        val cr = ctx.contentResolver
        return try {
            val rootId = android.provider.DocumentsContract.getTreeDocumentId(tree)
            val parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
            val name = "MLauncher-backup-" + java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.ITALY).format(java.util.Date()) + ".json"
            val doc = android.provider.DocumentsContract.createDocument(cr, parent, "application/json", name)
                ?: return "impossibile creare il file"
            cr.openOutputStream(doc, "wt")?.use { it.write(json.toByteArray()) } ?: return "impossibile scrivere"
            // pulizia: tengo solo gli ultimi [keep]
            val children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
            val found = ArrayList<Pair<String, String>>()
            cr.query(
                children,
                arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val n = c.getString(1) ?: continue
                    if (n.startsWith("MLauncher-backup-") && n.endsWith(".json")) found.add(id to n)
                }
            }
            found.sortedByDescending { it.second }.drop(keep).forEach { (id, _) ->
                try {
                    android.provider.DocumentsContract.deleteDocument(cr, android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, id))
                } catch (e: Exception) { }
            }
            null
        } catch (e: SecurityException) {
            "permesso sulla cartella perso, sceglila di nuovo"
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
    }

    // ---------- note veloci ----------

    /** testo, colore e grandezza di ogni nota (chiave = id della nota) */
    val notes = mutableStateMapOf<String, NoteData>()

    private fun loadNotes() {
        for ((k, v) in sp.all) {
            if (!k.startsWith("note_") || v !is String) continue
            try {
                val o = JSONObject(v)
                notes[k.removePrefix("note_")] = NoteData(o.optString("t"), o.optString("c", "yellow"), o.optInt("s", 15))
            } catch (e: Exception) { }
        }
    }

    fun saveNote(id: String, n: NoteData) {
        if (id.isEmpty()) return
        notes[id] = n
        sp.edit().putString("note_$id", JSONObject().put("t", n.text).put("c", n.color).put("s", n.size).toString()).apply()
    }

    /** Mette orologio e meteo in cima alla prima pagina, spostando le icone che occupano il posto. */
    private fun placeDefaultWidgetsOnTop() {
        items.removeAll { it is WidgetItem && it.ref.kind != "ext" }
        val cols = curCols
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

    /** Mette la barra di ricerca sulla prima riga della prima pagina, spostando giù ciò che c'era. */
    private fun placeSearchOnTop() {
        val cols = curCols
        val moved = items.filter { it.page == 0 && overlaps(it, 0, 0, 0, cols, 1) }.sortedBy { it.x }
        items.removeAll(moved)
        items.add(WidgetItem(UUID.randomUUID().toString(), WidgetRef("search"), 0, 0, 0, cols, 1))
        val later = ArrayList<HomeItem>()
        for (m in moved) {
            if (areaFree(0, m.x, m.y + 1, m.spanX, m.spanY)) items.add(m.at(0, m.x, m.y + 1)) else later.add(m)
        }
        for (m in later) {
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

    fun sortFolder(folderId: String) {
        val f = items.firstOrNull { it is FolderItem && it.folderId == folderId } as? FolderItem ?: return
        replace(f, f.copy(appIds = f.appIds.sortedBy { (apps[it]?.label ?: it).lowercase() }))
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
        val (p, x, y) = findFree(currentPage, minOf(sx, curCols), minOf(sy, curRows))
        items.add(
            StackItem(UUID.randomUUID().toString(), kinds.map { WidgetRef(it) }, p, x, y,
                minOf(sx, curCols), minOf(sy, curRows))
        )
        ensurePages(p)
        overlay = Overlay.NONE
        saveLayout()
    }

    fun addWidgetFromPicker(kind: String, sx: Int, sy: Int) {
        if (minusPick) {
            addToMinus(newRef(kind), sx, sy)
            return
        }
        val t = stackTarget
        if (t != null && addRefToStack(t, newRef(kind))) return
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
        if (minusPick) {
            val d = ctx.resources.displayMetrics.density
            val mx = ceil(((info.minWidth / d - 8f).coerceAtLeast(1f)) / cellWdp.coerceAtLeast(40f)).toInt()
            val my = ceil(((info.minHeight / d - 8f).coerceAtLeast(1f)) / cellHdp.coerceAtLeast(40f)).toInt()
            addToMinus(WidgetRef("ext", widgetId, info.provider.flattenToString()), mx, my)
            return
        }
        val t = stackTarget
        if (t != null && addRefToStack(t, WidgetRef("ext", widgetId, info.provider.flattenToString()))) return
        val density = ctx.resources.displayMetrics.density
        val wDp = info.minWidth / density
        val hDp = info.minHeight / density
        val sx = ceil(((wDp - 8f).coerceAtLeast(1f)) / cellWdp.coerceAtLeast(40f)).toInt()
            .coerceIn(1, minOf(4, curCols))
        val sy = ceil(((hDp - 8f).coerceAtLeast(1f)) / cellHdp.coerceAtLeast(40f)).toInt()
            .coerceIn(1, minOf(4, curRows))
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

    var drawerScroll = 0
    fun useCount(appId: String) = sp.getInt("use_$appId", 0)
    fun installTime(pkg: String): Long =
        try { pm.getPackageInfo(pkg, 0).firstInstallTime } catch (e: Exception) { 0L }

    /** app installate da poco e mai aperte: puntino nel cassetto */
    val newApps = mutableStateListOf<String>()

    /** Grandezza delle icone della Home (dp), uguale a quella calcolata in GridItem: la usano anche le cartelle. */
    fun homeIconDp(): Float {
        val cw = cellWdp
        val ch = cellHdp
        if (cw <= 0f || ch <= 0f) return 56f * settings.iconScale
        val base = if (settings.showLabels) minOf(cw * 0.62f, ch * 0.50f) else minOf(cw * 0.82f, ch * 0.72f)
        return minOf(base * settings.iconScale, 96f)
    }

    /** la schermata di ricerca (Home con tutte le app) ha l'aspetto della pagina −1 */
    val searchMinusLook: Boolean get() = settings.homeMode == "all" && settings.searchLook == "minus"
    val searchStyleEff: String get() = if (settings.searchSame) settings.minusStyle else settings.searchStyle
    val searchBlurEff: Int get() = if (settings.searchSame) settings.minusBlur else settings.searchBlur
    val searchDimEff: Int get() = if (settings.searchSame) settings.minusDim else settings.searchDim

    fun searchCount(appId: String) = sp.getInt("srch_$appId", 0)

    /** app aperta dai risultati della ricerca: finisce tra le "Cercate spesso" */
    fun recordSearch(appId: String) {
        val k = "srch_$appId"
        sp.edit().putInt(k, sp.getInt(k, 0) + 1).apply()
    }

    fun recordLaunch(appId: String) {
        if (newApps.remove(appId)) sp.edit().putStringSet("newApps", HashSet(newApps)).apply()
        val k = "use_$appId"
        sp.edit().putInt(k, sp.getInt(k, 0) + 1).apply()
    }

    private fun hueKey(a: AppEntry): Float {
        return try {
            val bm = a.icon.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
            val sm = Bitmap.createScaledBitmap(bm, 8, 8, true)
            var sx = 0f; var sy = 0f; var w = 0f; var br = 0f
            val hsv = FloatArray(3)
            for (y in 0 until 8) for (x in 0 until 8) {
                val c = sm.getPixel(x, y)
                if (android.graphics.Color.alpha(c) < 40) continue
                android.graphics.Color.colorToHSV(c, hsv)
                br += hsv[2]
                val wt = hsv[1] * hsv[2]
                sx += kotlin.math.cos(Math.toRadians(hsv[0].toDouble())).toFloat() * wt
                sy += kotlin.math.sin(Math.toRadians(hsv[0].toDouble())).toFloat() * wt
                w += wt
            }
            if (w < 2f) 400f + br else {
                val h = Math.toDegrees(kotlin.math.atan2(sy, sx).toDouble()).toFloat()
                if (h < 0) h + 360f else h
            }
        } catch (e: Exception) { 500f }
    }

    private fun categoryName(a: AppEntry): String? {
        val info = try { pm.getApplicationInfo(a.pkg, 0) } catch (e: Exception) { return null }
        if (a.pkg.startsWith("com.google.")) return "Google"
        // niente cartella generica "Sistema": le app di sistema senza genere restano sciolte
        if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0 && info.category == ApplicationInfo.CATEGORY_UNDEFINED) return null
        return when (info.category) {
            ApplicationInfo.CATEGORY_GAME -> "Giochi"
            ApplicationInfo.CATEGORY_AUDIO -> "Musica"
            ApplicationInfo.CATEGORY_VIDEO -> "Video"
            ApplicationInfo.CATEGORY_IMAGE -> "Foto"
            ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            ApplicationInfo.CATEGORY_NEWS -> "Notizie"
            ApplicationInfo.CATEGORY_MAPS -> "Mappe"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Produttività"
            ApplicationInfo.CATEGORY_ACCESSIBILITY -> "Accessibilità"
            else -> null
        }
    }

    private val smart = SmartFolders(ctx)
    @Volatile private var smartReady = false

    private fun ensureSmart() {
        if (!smartReady) {
            smart.prepare()
            smartReady = true
        }
    }

    /** Cartella intelligente di un'app secondo la modalità scelta (null = resta sciolta). */
    private fun smartGroupOf(a: AppEntry): String? = when (settings.smartMode) {
        "genre" -> categoryName(a)
        "color" -> try {
            colorFolderOf(a.icon.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false))
        } catch (e: Exception) {
            null
        }
        "letter" -> {
            val c = a.label.trim().firstOrNull()?.uppercaseChar() ?: '#'
            if (c.isLetter()) c.toString() else if (c.isDigit()) "0-9" else null
        }
        else -> {
            ensureSmart()
            smart.folderOf(a)
        }
    }

    /** Dispone le app non ancora in Home dalla seconda pagina in poi: cartelle intelligenti e poi le app sciolte. */
    fun arrangeHomeApps() {
        items.removeAll { it.page >= 1 && (it is AppItem || it is FolderItem) }
        val placed = placedAppIds()
        var list = apps.values.filter { it.id !in placed && it.id !in hidden }
        val byName = compareBy<AppEntry> { it.label.lowercase() }
        // cartelle intelligenti: niente cartelle generiche, chi non rientra (o resterebbe da solo) va sciolto
        if (settings.autoFolders) {
            val t0 = System.currentTimeMillis()
            val groups = HashMap<String, MutableList<AppEntry>>()
            for (a in list) smartGroupOf(a)?.let { groups.getOrPut(it) { ArrayList() }.add(a) }
            val inFolder = HashSet<String>()
            val order: Comparator<String> = if (settings.smartMode == "color") {
                val rank = listOf("Rosso", "Arancione", "Giallo", "Verde", "Azzurro", "Blu", "Viola", "Rosa", "Bianco e nero")
                compareBy { rank.indexOf(it) }
            } else compareBy { it.lowercase() }
            var nFolders = 0
            for ((name, g) in groups.toSortedMap(order)) {
                if (g.size < 2) continue
                val (p, x, y) = findFree(1)
                items.add(FolderItem(UUID.randomUUID().toString(), name, g.sortedWith(byName).map { it.id }, p, x, y))
                ensurePages(p)
                g.forEach { inFolder.add(it.id) }
                nFolders++
            }
            list = list.filter { it.id !in inFolder }
            AppLog.i("Cartelle intelligenti (${settings.smartMode}): $nFolders cartelle, ${list.size} app sciolte, ${System.currentTimeMillis() - t0} ms")
        }
        when (settings.homeOrder) {
            "name_desc" -> list.sortedWith(byName.reversed()).forEach { addApp(it.id, 1) }
            "usage" -> list.sortedWith(
                compareByDescending<AppEntry> { sp.getInt("use_${it.id}", 0) }.then(byName)
            ).forEach { addApp(it.id, 1) }
            "recent" -> list.sortedByDescending {
                try { pm.getPackageInfo(it.pkg, 0).firstInstallTime } catch (e: Exception) { 0L }
            }.forEach { addApp(it.id, 1) }
            "color" -> list.sortedBy { hueKey(it) }.forEach { addApp(it.id, 1) }
            else -> list.sortedWith(byName).forEach { addApp(it.id, 1) }
        }
        saveLayout()
    }

    /** App appena installata con "tutte le app in Home": entra nella sua cartella intelligente, se esiste. */
    private fun placeNewApp(a: AppEntry) {
        if (settings.autoFolders) {
            val g = smartGroupOf(a)
            val f = if (g == null) null else items.firstOrNull { it is FolderItem && it.page >= 1 && it.name == g } as? FolderItem
            if (f != null) {
                replace(f, f.copy(appIds = f.appIds + a.id))
                AppLog.i("${a.label} aggiunta alla cartella $g")
                return
            }
        }
        addApp(a.id, 1)
    }

    /** Cancella layout, impostazioni, app nascoste: tutto come al primo avvio. */
    fun resetLauncher() {
        if (!layoutLand) dropLegacyLand()
        items.filterIsInstance<WidgetItem>().filter { it.ref.kind == "ext" }.forEach { discardWidgetId(it.ref.widgetId) }
        items.filterIsInstance<StackItem>().forEach { st -> st.refs.filter { it.kind == "ext" }.forEach { discardWidgetId(it.widgetId) } }
        sp.edit().clear().commit()
        ctx.getSharedPreferences("mlauncher_settings", Context.MODE_PRIVATE).edit().clear().commit()
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
            byPkg["com.google.android.contacts"]?.firstOrNull()?.pkg
                ?: byPkg["com.android.contacts"]?.firstOrNull()?.pkg,
            resolvePkg(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))
        ).distinct()
        var dx = 0
        for (pkg in dockPkgs) {
            val a = byPkg[pkg]?.firstOrNull() ?: continue
            if (dx >= curSlots) break
            items.add(AppItem(a.id, -1, dx, 0))
            dx++
        }
        // barra di ricerca in cima, sotto orologio e meteo
        items.add(WidgetItem(UUID.randomUUID().toString(), WidgetRef("search"), 0, 0, 0, curCols, 1))
        addInternalWidget("clock", 2, 2, 0)
        addInternalWidget("weather", 2, 2, 0)
        sp.edit().putBoolean("defaultWidgets", true).apply()

        run {
            // poche icone in Home, il resto sta nel cassetto
            val rows = curRows
            val cols = curCols
            fun app(pkg: String) = byPkg[pkg]?.firstOrNull()
            val gmail = app("com.google.android.gm")
            val folderApps = listOf(
                "com.google.android.googlequicksearchbox", "com.google.android.gm",
                "com.google.android.apps.docs", "com.google.android.apps.youtube.music"
            ).mapNotNull { app(it)?.id }
            val row = ArrayList<HomeItem>()
            if (folderApps.size >= 2) row.add(FolderItem(UUID.randomUUID().toString(), "Google", folderApps, 0, 0, rows - 1))
            apps.values.firstOrNull { it.pkg == ctx.packageName && it.cls == SETTINGS_CLS }
                ?.let { row.add(AppItem(it.id, 0, 0, rows - 1)) }
            app("com.google.android.apps.photos")?.let { row.add(AppItem(it.id, 0, 0, rows - 1)) }
            app("com.android.vending")?.let { row.add(AppItem(it.id, 0, 0, rows - 1)) }
            row.take(cols).forEachIndexed { i, it -> items.add(it.at(0, i, rows - 1)) }
            gmail?.let { items.add(AppItem(it.id, 0, cols - 1, rows - 2)) }
        }
        if (settings.homeMode == "all") {
            // la prima pagina resta pulita: le altre app partono dalla seconda
            arrangeHomeApps()
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
        migrateSearchBar()
        if (!sp.getBoolean("settingsIcon", false)) {
            apps.values.firstOrNull { it.pkg == ctx.packageName && it.cls == SETTINGS_CLS }?.let {
                if (it.id !in placedAppIds()) addApp(it.id)
                sp.edit().putBoolean("settingsIcon", true).apply()
            }
        }
        val placed = placedAppIds()
        if (settings.homeMode == "all") {
            sortedApps.filter { it.id !in placed && it.id !in hidden }.forEach { placeNewApp(it) }
        } else if (settings.autoAddNew && knownIds.isNotEmpty()) {
            ids.filter { it !in knownIds && it !in placed && it !in hidden }.forEach { addApp(it) }
        }
        if (knownIds.isNotEmpty()) {
            val fresh = ids.filter { it !in knownIds && it !in newApps }
            if (fresh.isNotEmpty()) {
                newApps.addAll(fresh)
                sp.edit().putStringSet("newApps", HashSet(newApps)).apply()
            }
        }
        knownIds.addAll(ids)
        saveLayout()
    }

    // ---------- salvataggio ----------

    private fun encodeRef(r: WidgetRef) = JSONObject().put("k", r.kind).put("w", r.widgetId).put("p", r.provider)

    private fun decodeRef(o: JSONObject) = WidgetRef(o.getString("k"), o.optInt("w", -1), o.optString("p", ""))

    private fun encodeItems(list: List<HomeItem> = items): JSONArray {
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

    internal fun decodeItems(arr: JSONArray, skipExt: Boolean): List<HomeItem> {
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
        val s = sp.getString(lk("layout"), null) ?: return false
        try {
            items.addAll(decodeItems(JSONArray(s), false))
        } catch (e: Exception) {
            items.clear()
            return false
        }
        pageCount = sp.getInt(lk("pages"), 1).coerceAtLeast(1)
        sp.getStringSet("hidden", null)?.let { hidden.addAll(it) }
        return true
    }

    private fun saveLayout() {
        if (dryRun) return
        if (layoutLand) {
            // in orizzontale si modifica la disposizione unica (quella verticale), nel posto corrispondente
            writeBackToMaster()
            sp.edit()
                .putStringSet("known", HashSet(knownIds))
                .putStringSet("hidden", HashSet(hidden))
                .apply()
            return
        }
        sp.edit()
            .putString("layout", encodeItems().toString())
            .putInt("pages", pageCount)
            .putStringSet("known", HashSet(knownIds))
            .putStringSet("hidden", HashSet(hidden))
            .apply()
    }

    // ---------- disposizione unica: l'orizzontale è ricavata dalla verticale ----------

    /** Com'era la Home orizzontale appena ricavata (o all'ultimo salvataggio): serve a capire cosa è cambiato. */
    private var landSnap: Map<String, HomeItem> = emptyMap()
    private var landSnapPages = 1

    private var landSnapTaken = false

    private fun takeLandSnapshot() {
        landSnap = items.associateBy { it.id }
        landSnapPages = pageCount
        landSnapTaken = true
    }

    /** Vecchia disposizione orizzontale separata (versioni precedenti): i suoi widget vengono liberati. */
    private fun dropLegacyLand() {
        if (!sp.contains("layout_l")) return
        discardExtOf("layout_l")
        sp.edit().remove("layout_l").remove("pages_l").apply()
        AppLog.i("Orientamento: la Home orizzontale ora segue quella verticale")
    }

    /** Posizione corrispondente tra due griglie: in alto a destra resta in alto a destra. */
    private fun mapPos(v: Int, span: Int, fromN: Int, toSpan: Int, toN: Int): Int {
        val f = if (fromN - span > 0) v.toFloat() / (fromN - span) else 0f
        return (f * (toN - toSpan)).roundToInt().coerceIn(0, maxOf(0, toN - toSpan))
    }

    private fun freeIn(list: List<HomeItem>, page: Int, x: Int, y: Int, sx: Int, sy: Int, cols: Int, rows: Int, slots: Int, except: String?): Boolean {
        if (page == -1) {
            if (x < 0 || x >= slots) return false
            return list.none { it.id != except && it.page == -1 && it.x == x }
        }
        if (x < 0 || y < 0 || x + sx > cols || y + sy > rows) return false
        return list.none {
            it.id != except && it.page == page &&
                x < it.x + it.spanX && it.x < x + sx && y < it.y + it.spanY && it.y < y + sy
        }
    }

    /** Posto libero più vicino a (tx,ty) nella pagina; se la pagina è piena, la prima libera dopo. */
    private fun spotIn(list: List<HomeItem>, page: Int, tx: Int, ty: Int, sx: Int, sy: Int, cols: Int, rows: Int, slots: Int, except: String?, pages: Int): Triple<Int, Int, Int> {
        var p = maxOf(page, 0)
        while (p < pages + 20) {
            var best: Pair<Int, Int>? = null
            var bd = Int.MAX_VALUE
            for (y in 0..(rows - sy)) for (x in 0..(cols - sx)) {
                if (!freeIn(list, p, x, y, sx, sy, cols, rows, slots, except)) continue
                val d = (x - tx) * (x - tx) + 2 * (y - ty) * (y - ty)
                if (d < bd) { bd = d; best = x to y }
            }
            if (best != null) return Triple(p, best.first, best.second)
            p++
        }
        return Triple(pages, 0, 0)
    }

    /** Quello che è cambiato in orizzontale finisce anche nella disposizione verticale. */
    private fun writeBackToMaster() {
        // senza un punto di partenza non si sa cosa è cambiato: non tocco la disposizione verticale
        if (!landSnapTaken) {
            takeLandSnapshot()
            return
        }
        val str = sp.getString("layout", null)
        val master = ArrayList<HomeItem>()
        if (str != null) try { master.addAll(decodeItems(JSONArray(str), false)) } catch (e: Exception) { return }
        var mPages = sp.getInt("pages", 1).coerceAtLeast(1)
        val pc = settings.colsOf(false)
        val pr = settings.rowsOf(false)
        val ps = settings.dockSlotsOf(false)
        val lc = curCols
        val lr = curRows
        val now = items.associateBy { it.id }
        var changed = false
        // tolti in orizzontale
        if (master.removeAll { it.id in landSnap && it.id !in now }) changed = true
        // pagine aggiunte o tolte
        val dp = pageCount - landSnapPages
        if (dp != 0) { mPages = maxOf(1, mPages + dp); changed = true }
        for (l in items) {
            val snap = landSnap[l.id]
            if (snap == l) continue
            changed = true
            val m = master.firstOrNull { it.id == l.id }
            val moved = snap == null || m == null || snap.page != l.page || snap.x != l.x || snap.y != l.y ||
                snap.spanX != l.spanX || snap.spanY != l.spanY
            if (m != null && !moved) {
                // cambiato solo il contenuto (nome della cartella, app dentro…): il posto resta quello verticale
                master[master.indexOf(m)] = l.at(m.page, m.x, m.y).sized(m.spanX, m.spanY)
                continue
            }
            if (m != null) master.remove(m)
            val sx = if (m != null && snap != null && snap.spanX == l.spanX) m.spanX else minOf(l.spanX, pc)
            val sy = if (m != null && snap != null && snap.spanY == l.spanY) m.spanY else minOf(l.spanY, pr)
            val placed: HomeItem = if (l.page == -1 && freeIn(master, -1, l.x, 0, 1, 1, pc, pr, ps, l.id)) {
                l.at(-1, l.x, 0)
            } else {
                val tx = if (l.page == -1) 0 else mapPos(l.x, l.spanX, lc, sx, pc)
                val ty = if (l.page == -1) pr - 1 else mapPos(l.y, l.spanY, lr, sy, pr)
                val (p, x, y) = spotIn(master, maxOf(l.page, 0), tx, ty, sx, sy, pc, pr, ps, l.id, mPages)
                if (p + 1 > mPages) mPages = p + 1
                l.at(p, x, y).sized(sx, sy)
            }
            master.add(placed)
        }
        if (changed) {
            sp.edit().putString("layout", encodeItems(master).toString()).putInt("pages", mPages).apply()
        }
        takeLandSnapshot()
    }

    // ---------- backup ----------

    fun buildBackup(): String {
        val o = JSONObject()
        o.put("version", 1)
        saveLayout()
        o.put("layout", JSONArray(sp.getString("layout", "[]")))
        o.put("pages", sp.getInt("pages", 1))
        o.put("hidden", JSONArray(hidden.toList()))
        o.put("notes", JSONObject().apply { notes.forEach { (k, n) -> put(k, JSONObject().put("t", n.text).put("c", n.color).put("s", n.size)) } })
        o.put("settings", settings.dump())
        return o.toString(2)
    }

    /** Solo la disposizione: icone, cartelle, widget e pagine (verticale e orizzontale) e pagina −1. Niente impostazioni. */
    fun buildLayoutBackup(): String {
        val o = JSONObject()
        o.put("version", 1)
        o.put("type", "layout")
        saveLayout()
        o.put("layout", JSONArray(sp.getString("layout", "[]")))
        o.put("pages", sp.getInt("pages", 1))
        o.put("minus", JSONArray(settings.minusWidgets))
        o.put("notes", JSONObject().apply { notes.forEach { (k, n) -> put(k, JSONObject().put("t", n.text).put("c", n.color).put("s", n.size)) } })
        return o.toString(2)
    }

    /**
     * Widget di altre app da un backup: se l'id esiste già su questo telefono resta,
     * altrimenti provo a ricollegarlo (riesce se hai permesso a MLauncher di aggiungere widget);
     * se non è possibile, quel widget viene saltato.
     */
    private fun reviveRef(ref: WidgetRef): WidgetRef? {
        if (ref.kind != "ext") return ref
        try {
            val info = widgetMgr.getAppWidgetInfo(ref.widgetId)
            if (info != null && info.provider.flattenToString() == ref.provider) return ref
        } catch (e: Exception) { }
        val cn = android.content.ComponentName.unflattenFromString(ref.provider) ?: return null
        return try {
            val id = widgetHost.allocateAppWidgetId()
            if (widgetMgr.bindAppWidgetIdIfAllowed(id, cn)) ref.copy(widgetId = id)
            else {
                discardWidgetId(id)
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun reviveItems(list: List<HomeItem>): Pair<List<HomeItem>, Int> {
        var lost = 0
        val out = list.mapNotNull { i ->
            when (i) {
                is WidgetItem -> reviveRef(i.ref)?.let { i.copy(ref = it) } ?: run { lost++; null }
                is StackItem -> {
                    val refs = i.refs.mapNotNull { r -> reviveRef(r) ?: run { lost++; null } }
                    i.copy(refs = refs)
                }
                else -> i
            }
        }
        return out to lost
    }

    /**
     * Ripristina solo la disposizione (va bene anche un backup completo: le impostazioni restano quelle attuali).
     * Restituisce quanti widget di altre app non è stato possibile rimettere, oppure -1 se il file non è valido.
     */
    fun restoreLayout(text: String): Int {
        return try {
            val o = JSONObject(text)
            val (list, lostP) = reviveItems(decodeItems(o.getJSONArray("layout"), false))
            var lost = lostP
            layoutLand = false
            items.clear()
            items.addAll(list)
            pageCount = o.optInt("pages", 1).coerceAtLeast(1)
            val e = sp.edit().remove("layout_l").remove("pages_l")
            e.apply()
            o.optJSONArray("minus")?.let { a ->
                // pagina −1: stessi controlli sui widget delle altre app
                val keep = JSONArray()
                for (i in 0 until a.length()) {
                    val m = a.getJSONObject(i)
                    val r = reviveRef(WidgetRef(m.getString("k"), m.optInt("w", -1), m.optString("p", "")))
                    if (r == null) lost++ else keep.put(JSONObject(m.toString()).put("w", r.widgetId))
                }
                settings.set("minusWidgets", keep.toString())
                loadMinus()
            }
            o.optJSONObject("notes")?.let { no ->
                for (k in no.keys()) no.optJSONObject(k)?.let { n ->
                    saveNote(k, NoteData(n.optString("t"), n.optString("c", "yellow"), n.optInt("s", 15)))
                }
            }
            hasLayout = true
            saveLayout()
            if (settings.actualLand) swapTo(true)
            refresh(null)
            AppLog.i("Disposizione ripristinata" + if (lost > 0) " ($lost widget non disponibili)" else "")
            lost
        } catch (e: Exception) {
            AppLog.e("Ripristino disposizione non riuscito", e)
            -1
        }
    }

    fun restoreBackup(text: String): Boolean {
        return try {
            val o = JSONObject(text)
            val list = decodeItems(o.getJSONArray("layout"), true)
            settings.load(o.getJSONObject("settings"))
            loadMinus()
            layoutLand = false
            items.clear()
            items.addAll(list)
            pageCount = o.optInt("pages", 1).coerceAtLeast(1)
            val e = sp.edit().remove("layout_l").remove("pages_l")
            e.apply()
            o.optJSONObject("notes")?.let { no ->
                for (k in no.keys()) no.optJSONObject(k)?.let { n ->
                    saveNote(k, NoteData(n.optString("t"), n.optString("c", "yellow"), n.optInt("s", 15)))
                }
            }
            hidden.clear()
            val h = o.optJSONArray("hidden")
            if (h != null) for (i in 0 until h.length()) hidden.add(h.getString(i))
            hasLayout = true
            saveLayout()
            if (settings.actualLand) swapTo(true)
            refresh(null)
            if (showWelcome) finishWelcome()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---------- operazioni dalla UI ----------

    /**
     * Inserisce [item] (1x1) in una cella occupata spingendo le icone vicine, una dopo l'altra,
     * fino al primo posto libero (come Launcher3). Prima prova verso il posto da cui arriva l'icona.
     * I widget non si spostano: le icone li scavalcano.
     */
    private fun insertShift(item: HomeItem, page: Int, x: Int, y: Int): Boolean {
        if (item.spanX != 1 || item.spanY != 1) return false
        val cols = if (page == -1) curSlots else curCols
        val rows = if (page == -1) 1 else curRows
        val total = cols * rows
        val t = y * cols + x
        if (t !in 0 until total) return false
        val inList = items.any { it.id == item.id }
        val from = if (inList && item.page == page) item.y * cols + item.x else -1
        val dirs = if (from in 0 until t) listOf(-1, 1) else listOf(1, -1)
        for (dir in dirs) {
            val slots = ArrayList<Int>()
            val chain = ArrayList<HomeItem>()
            var c = t
            var ok = false
            while (c in 0 until total) {
                val cx = c % cols
                val cy = c / cols
                val occ = items.firstOrNull { it.id != item.id && overlaps(it, page, cx, cy, 1, 1) }
                if (occ == null) {
                    slots.add(c)
                    ok = true
                    break
                }
                if (occ.spanX == 1 && occ.spanY == 1) {
                    slots.add(c)
                    chain.add(occ)
                } else if (c == t) {
                    return false
                }
                c += dir
            }
            if (!ok || chain.isEmpty()) continue
            chain.forEachIndexed { i, o ->
                val sl = slots[i + 1]
                replace(o, o.at(page, sl % cols, sl / cols))
            }
            val cur = items.firstOrNull { it.id == item.id }
            if (cur != null) replace(cur, cur.at(page, x, y)) else items.add(item.at(page, x, y))
            if (page >= 0) ensurePages(page)
            return true
        }
        return false
    }

    /** [merge] false = l'icona va tra le altre (spostandole); true = sopra un'app/cartella crea o riempie la cartella. */
    fun dropItem(dragged: HomeItem, page: Int, x: Int, y: Int, merge: Boolean = true) {
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
            if (small && !merge && insertShift(item, page, x, y)) {
                // spostata tra le altre
            } else if (small && merge && page >= 0 && item is AppItem && target is AppItem) {
                val f = FolderItem(UUID.randomUUID().toString(), "Cartella", listOf(target.appId, item.appId), page, target.x, target.y)
                items.remove(item)
                items.remove(target)
                items.add(f)
            } else if (small && merge && item is AppItem && target is FolderItem) {
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
    /** app o cartella su cui si sta per creare/riempire una cartella (cerchio dietro l'icona) */
    var folderHoverId by mutableStateOf<String?>(null)
    /** fotografia del widget che si sta trascinando (il fantasma mostra il widget vero) */
    var dragSnapshot by mutableStateOf<Pair<String, androidx.compose.ui.graphics.ImageBitmap>?>(null)
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
        if (!layoutLand) dropLegacyLand()
        editMode = false
        openFolderId = null
        // la Home predefinita nasce sempre in verticale; l'orizzontale si ricava da quella
        val land = layoutLand
        items.clear()
        layoutLand = false
        buildDefault()
        saveLayout()
        sp.edit().remove("layout_l").remove("pages_l").apply()
        if (land) {
            items.clear()
            layoutLand = true
            buildFromOther()
            takeLandSnapshot()
        }
        homeSignal += 1
    }

    /** Libera gli id dei widget esterni salvati nel layout [key] (quello non attivo). */
    private fun discardExtOf(key: String) {
        val str = sp.getString(key, null) ?: return
        try {
            decodeItems(JSONArray(str), false).forEach { i -> refsOf(i).filter { it.kind == "ext" }.forEach { discardWidgetId(it.widgetId) } }
        } catch (e: Exception) {
            // layout illeggibile
        }
    }

    /** Dopo un riordino globale l'altro orientamento viene rigenerato da questo alla prossima rotazione. */
    private fun dropOtherLayout() {
        // c'è una sola disposizione: resta solo da togliere quella vecchia separata, se c'è
        if (!layoutLand) dropLegacyLand()
    }

    /** Mette un elemento grande dove vuole e sposta in posti liberi quelli che occupavano l'area. */
    private fun displaceAndPlace(item: HomeItem, hits: List<HomeItem>, page: Int, x: Int, y: Int) {
        if (page < 0 || x + item.spanX > curCols || y + item.spanY > curRows) return
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

    /**
     * Rilascio di più app raccolte insieme: sopra una cartella entrano tutte, sopra un'app
     * nasce una cartella con tutte. Restituisce false se il punto non è una cartella/app.
     */
    fun dropGroup(main: AppItem, extra: List<String>, page: Int, x: Int, y: Int): Boolean {
        val ids = (listOf(main.appId) + extra).distinct().filter { it in apps }
        // vale anche per la dock (page = -1): sopra una cartella della dock entrano tutte
        val target = items.firstOrNull { it.id != main.id && it.page == page && overlaps(it, page, x, y, 1, 1) } ?: return false
        when (target) {
            is FolderItem -> {
                val add = ids.filter { it !in target.appIds }
                items.removeAll { it is AppItem && it.appId in ids }
                replace(target, target.copy(appIds = target.appIds + add))
                AppLog.i("${add.size} app aggiunte alla cartella ${target.name}")
            }
            is AppItem -> {
                if (target.appId in ids) return false
                val all = listOf(target.appId) + ids
                items.removeAll { it is AppItem && (it.appId in ids || it.id == target.id) }
                items.add(FolderItem(UUID.randomUUID().toString(), "Cartella", all, page, target.x, target.y))
            }
            else -> return false
        }
        saveLayout()
        return true
    }

    /**
     * "Scuoti per riordinare": compatta la pagina dall'alto in basso, da sinistra a destra,
     * mantenendo l'ordine di lettura degli elementi.
     */
    fun tidyPage(page: Int) {
        if (page < 0) return
        val onPage = items.filter { it.page == page }.sortedWith(compareBy<HomeItem> { it.y }.thenBy { it.x })
        if (onPage.isEmpty()) return
        items.removeAll(onPage.toSet())
        val overflow = ArrayList<HomeItem>()
        for (it in onPage) {
            var spot: Pair<Int, Int>? = null
            loop@ for (yy in 0..(curRows - it.spanY)) for (xx in 0..(curCols - it.spanX)) {
                if (areaFree(page, xx, yy, it.spanX, it.spanY)) {
                    spot = xx to yy
                    break@loop
                }
            }
            if (spot != null) items.add(it.at(page, spot.first, spot.second)) else overflow.add(it)
        }
        for (o in overflow) {
            val (p, x, y) = findFree(page, o.spanX, o.spanY)
            items.add(o.at(p, x, y))
            ensurePages(p)
        }
        saveLayout()
        AppLog.i("Pagina ${page + 1} riordinata scuotendo il telefono")
    }

    // ---------- trascinamento con anteprima (come Launcher3) ----------

    /** dove andrebbero gli elementi spostati se si lasciasse ora (id -> pagina, colonna, riga) */
    val reorderPreview = mutableStateMapOf<String, Triple<Int, Int, Int>>()
    private var dryRun = false

    /** Elemento che occupa la cella (diverso da [exceptId]). */
    fun hitAt(page: Int, x: Int, y: Int, exceptId: String?): HomeItem? =
        items.firstOrNull { it.id != exceptId && overlaps(it, page, x, y, 1, 1) }

    /** Simula il rilascio senza cambiare niente e mostra dove si sposterebbero gli altri. */
    fun showReorderPreview(dragged: HomeItem, page: Int, x: Int, y: Int, merge: Boolean = false) {
        val saved = items.toList()
        val pc = pageCount
        dryRun = true
        val moved = HashMap<String, Triple<Int, Int, Int>>()
        try {
            // icona che arriva dal cassetto o da una cartella: per la simulazione la metto in lista
            if (items.none { it.id == dragged.id }) items.add(dragged)
            dropItem(dragged, page, x, y, merge)
            val after = items.associateBy { it.id }
            for (o in saved) {
                if (o.id == dragged.id) continue
                val n = after[o.id] ?: continue
                if (n.page != o.page || n.x != o.x || n.y != o.y) moved[o.id] = Triple(n.page, n.x, n.y)
            }
        } catch (e: Exception) {
            // anteprima non possibile
        } finally {
            items.clear()
            items.addAll(saved)
            pageCount = pc
            dryRun = false
        }
        reorderPreview.clear()
        reorderPreview.putAll(moved)
    }

    fun clearReorderPreview() {
        if (reorderPreview.isNotEmpty()) reorderPreview.clear()
    }

    // ---------- icone portate fuori da una cartella aperta ----------

    /** La cartella aperta si chiude e le app trascinate passano sulla Home (il dito resta sull'icona). */
    fun startFolderDrag(folderId: String, appId: String, extras: List<String>, pos: androidx.compose.ui.geometry.Offset) {
        if (blockedByLock()) return
        drawerDrag = DrawerDrag(appId, pos, extras, folderId)
        openFolderId = null
    }

    /** Toglie le app dalla cartella senza rimetterle in Home (la cartella con una sola app si scioglie). */
    private fun takeFromFolder(folderId: String, ids: List<String>) {
        val f = items.firstOrNull { it is FolderItem && it.folderId == folderId } as? FolderItem ?: return
        val rest = f.appIds - ids.toSet()
        if (rest.size <= 1) {
            items.remove(f)
            rest.firstOrNull()?.let { items.add(AppItem(it, f.page, f.x, f.y)) }
        } else {
            replace(f, f.copy(appIds = rest))
        }
    }

    /**
     * Rilascio di app (una o più) sulla cella indicata: sopra una cartella entrano tutte,
     * sopra un'app nasce una cartella, in un posto libero va la prima e le altre vicino.
     * [page] null = nessuna cella (finiscono nella pagina attuale).
     */
    fun dropAppsAt(ids0: List<String>, page: Int?, x: Int, y: Int, fromFolder: String? = null, merge: Boolean = true) {
        val ids = ids0.distinct().filter { it in apps }
        if (ids.isEmpty()) return
        if (fromFolder != null) takeFromFolder(fromFolder, ids)
        // le app eventualmente già sciolte in Home si spostano (niente doppioni)
        items.removeAll { it is AppItem && it.appId in ids }
        val hit = if (page == null) null else items.firstOrNull { overlaps(it, page, x, y, 1, 1) }
        when {
            page == null -> ids.forEach { addApp(it, maxOf(currentPage, 0)) }
            !merge && hit != null && hit.spanX == 1 && hit.spanY == 1 &&
                insertShift(AppItem(ids.first(), -3, 0, 0), page, x, y) ->
                ids.drop(1).forEach { addApp(it, maxOf(page, 0)) }
            hit is FolderItem && hit.spanX == 1 -> replace(hit, hit.copy(appIds = hit.appIds + ids.filter { it !in hit.appIds }))
            hit is AppItem && hit.appId !in ids -> {
                items.remove(hit)
                items.add(FolderItem(UUID.randomUUID().toString(), "Cartella", listOf(hit.appId) + ids, page, hit.x, hit.y))
            }
            hit == null && areaFree(page, x, y, 1, 1) -> {
                items.add(AppItem(ids.first(), page, x, y))
                if (page >= 0) ensurePages(page)
                ids.drop(1).forEach { addApp(it, maxOf(page, 0)) }
            }
            else -> ids.forEach { addApp(it, maxOf(page, 0)) }
        }
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

    fun startDrawerDrag(appId: String, pos: androidx.compose.ui.geometry.Offset) {
        if (blockedByLock()) return
        openFolderId = null
        drawerDrag = DrawerDrag(appId, pos)
    }

    fun endDrawerDrag() {
        drawerDropSignal += 1
    }

    fun cancelDrawerDrag() {
        drawerDrag = null
        overlay = Overlay.NONE
    }

    /** Rilascio di un'app presa dal cassetto: cella libera, sopra un'app (cartella) o dentro una cartella. */
    fun dropFromDrawer(appId: String, page: Int, x: Int, y: Int, merge: Boolean = true) {
        val existing = items.firstOrNull { it is AppItem && it.appId == appId }
        if (existing != null) {
            dropItem(existing, page, x, y, merge)
            return
        }
        val hits = items.filter { overlaps(it, page, x, y, 1, 1) }
        val t = hits.singleOrNull()
        when {
            !merge && t != null && t.spanX == 1 && t.spanY == 1 &&
                insertShift(AppItem(appId, -3, 0, 0), page, x, y) -> {}
            hits.isEmpty() && areaFree(page, x, y, 1, 1) -> {
                items.add(AppItem(appId, page, x, y))
                if (page >= 0) ensurePages(page)
            }
            t is AppItem && page >= 0 && t.appId != appId -> {
                items.remove(t)
                items.add(FolderItem(UUID.randomUUID().toString(), "Cartella", listOf(t.appId, appId), page, t.x, t.y))
            }
            t is FolderItem && t.spanX == 1 && appId !in t.appIds -> replace(t, t.copy(appIds = t.appIds + appId))
            else -> addApp(appId, maxOf(page, 0))
        }
        saveLayout()
    }

    fun isOnHome(appId: String) = appId in placedAppIds()

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

    /** pagina principale (dove porta il tasto Home), sempre tra quelle esistenti */
    val homePageIdx: Int get() = settings.homePage.coerceIn(0, maxOf(0, pageCount - 1))

    fun setHomePage(p: Int) {
        settings.set("homePage", p.coerceIn(0, maxOf(0, pageCount - 1)))
    }

    fun pageEmpty(p: Int) = items.none { it.page == p }

    /** Elimina una pagina con tutto quello che c'è (le app restano nel cassetto). */
    fun deletePage(p: Int) {
        if (blockedByLock()) return
        if (pageCount <= 1) return
        items.filter { it.page == p }.forEach { if (it is WidgetItem || it is StackItem) removeWidgetItem(it) }
        items.removeAll { it.page == p }
        for (it in items.toList()) {
            if (it.page > p) replace(it, it.at(it.page - 1, it.x, it.y))
        }
        pageCount -= 1
        val hp = settings.homePage
        val newHp = (if (hp > p) hp - 1 else hp).coerceIn(0, maxOf(0, pageCount - 1))
        if (newHp != hp) settings.set("homePage", newHp)
        saveLayout()
    }

    /** Sposta e ridimensiona insieme (pallini dei widget a sinistra e in alto). */
    fun moveResize(item: HomeItem, x: Int, y: Int, sx: Int, sy: Int) {
        val cur = items.firstOrNull { it.id == item.id } ?: return
        if (x == cur.x && y == cur.y && sx == cur.spanX && sy == cur.spanY) return
        if (!areaFree(cur.page, x, y, sx, sy, cur.id)) return
        replace(cur, cur.at(cur.page, x, y).sized(sx, sy))
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
        // la pagina principale segue le pagine che restano
        val hp = settings.homePage
        val newHp = map[hp] ?: used.count { it < hp }.coerceAtMost(maxOf(used.size - 1, 0))
        if (newHp != hp) settings.set("homePage", newHp)
        pageCount = maxOf(used.size, 1)
        saveLayout()
    }

    /** [alreadyHome]: il tasto Home è stato premuto mentre la Home era già in primo piano. */
    /** Indietro sulla Home: torna alla prima pagina (e chiude menu aperti), senza chiudere il Launcher. */
    fun backOnHome() {
        if (currentPage != homePageIdx) homeSignal += 1
    }

    fun onHomePressed(alreadyHome: Boolean = false) {
        // pagina −1 aperta: il tasto Home la chiude
        if (minusP > 0f) {
            closeMinus()
            overlay = Overlay.NONE
            openFolderId = null
            return
        }
        // già sulla prima pagina, senza nulla di aperto: il tasto Home apre il cassetto
        if (alreadyHome && settings.homeOpensDrawer && overlay == Overlay.NONE && openFolderId == null &&
            !editMode && currentPage == homePageIdx
        ) {
            overlay = Overlay.DRAWER
            return
        }
        overlay = Overlay.NONE
        openFolderId = null
        if (editMode) exitEdit()
        homeSignal += 1
    }

    fun setSetting(key: String, value: Any) {
        settings.set(key, value)
        when (key) {
            "homeMode" -> {
                if (value == "drawer") {
                    // con il cassetto in Home restano solo le icone predefinite
                    resetHome()
                } else if (value == "all") {
                    arrangeHomeApps()
                    dropOtherLayout()
                }
            }
            "homeOrder", "autoFolders", "smartMode" -> if (settings.homeMode == "all") {
                arrangeHomeApps()
                dropOtherLayout()
            }
            "cols", "rows", "dockSlots" -> if (settings.landscape) {
                // griglia orizzontale cambiata: la Home orizzontale si ricava di nuovo dalla verticale
                if (layoutLand) {
                    items.clear()
                    buildFromOther()
                    takeLandSnapshot()
                    homeSignal += 1
                }
            } else inOrientation(false) { fit() }
            "iconPack" -> reloadIcons()
        }
    }

    /** Esegue [block] sul layout dell'orientamento [land] (anche se non è quello attivo). */
    private fun inOrientation(land: Boolean, block: () -> Unit) {
        if (land == layoutLand) {
            block()
            return
        }
        val back = layoutLand
        swapTo(land)
        block()
        saveLayout()
        swapTo(back)
        homeSignal += 1
    }

    private fun swapTo(land: Boolean) {
        if (land == layoutLand) return
        saveLayout()
        items.clear()
        layoutLand = land
        if (land) {
            // l'orizzontale non ha una disposizione sua: la ricavo ogni volta da quella verticale
            dropLegacyLand()
            buildFromOther()
            takeLandSnapshot()
        } else if (!loadLayout()) buildFromOther()
        if (apps.isNotEmpty()) {
            items.removeAll { it is AppItem && it.appId !in apps }
            // app installate mentre era attivo l'altro orientamento
            if (settings.homeMode == "all") {
                val placed = placedAppIds()
                sortedApps.filter { it.id !in placed && it.id !in hidden }.forEach { addApp(it.id, 1) }
            }
        }
        if (hasLayout && apps.isNotEmpty()) migrateSearchBar()
        if (hasLayout) saveLayout()
    }

    /**
     * Il layout di questo orientamento non esiste ancora: copio icone, cartelle e widget interni
     * dall'altro (i widget esterni no, non si possono duplicare) mantenendo la posizione relativa:
     * ciò che stava in basso resta in basso, a destra resta a destra.
     */
    private fun buildFromOther() {
        items.clear()
        pageCount = sp.getInt(otherKey("pages"), 1).coerceAtLeast(1)
        val str = sp.getString(otherKey("layout"), null) ?: return
        val src = try {
            // anche i widget delle altre app: è lo stesso widget, se ne vede uno alla volta
            decodeItems(JSONArray(str), false)
        } catch (e: Exception) {
            return
        }
        val oc = settings.colsOf(!layoutLand)
        val orr = settings.rowsOf(!layoutLand)
        val cols = curCols
        val rows = curRows
        val overflow = ArrayList<HomeItem>()
        for (d in src.filter { it.page == -1 }.sortedBy { it.x }) {
            if (d.x < curSlots && areaFree(-1, d.x, 0, 1, 1)) items.add(d) else overflow.add(d)
        }
        val grid = src.filter { it.page >= 0 }.sortedWith(
            compareByDescending<HomeItem> { it.spanX * it.spanY }.thenBy { it.page }.thenBy { it.y }.thenBy { it.x }
        )
        for (it in grid) {
            val n = it.sized(minOf(it.spanX, cols), minOf(it.spanY, rows))
            val fx = if (oc - it.spanX > 0) it.x.toFloat() / (oc - it.spanX) else 0f
            val fy = if (orr - it.spanY > 0) it.y.toFloat() / (orr - it.spanY) else 0f
            val tx = (fx * (cols - n.spanX)).roundToInt().coerceIn(0, cols - n.spanX)
            val ty = (fy * (rows - n.spanY)).roundToInt().coerceIn(0, rows - n.spanY)
            val spot = nearestFree(n.page, tx, ty, n.spanX, n.spanY)
            if (spot != null) {
                items.add(n.at(n.page, spot.first, spot.second))
                ensurePages(n.page)
            } else {
                overflow.add(n)
            }
        }
        for (o in overflow) {
            val (p, x, y) = findFree(maxOf(o.page, 0), o.spanX, o.spanY)
            items.add(o.at(p, x, y))
            ensurePages(p)
        }
    }

    private fun nearestFree(page: Int, tx: Int, ty: Int, sx: Int, sy: Int): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var bd = Int.MAX_VALUE
        for (y in 0..(curRows - sy)) for (x in 0..(curCols - sx)) {
            if (!areaFree(page, x, y, sx, sy)) continue
            val d = (x - tx) * (x - tx) + 2 * (y - ty) * (y - ty)
            if (d < bd) {
                bd = d
                best = x to y
            }
        }
        return best
    }

    /** La barra di ricerca fissa è diventata un widget: chi la usava la ritrova in Home, una volta sola per layout. */
    private fun migrateSearchBar() {
        val flag = lk("searchWidgetTop")
        if (sp.getBoolean(flag, false)) return
        // la versione precedente la metteva nel primo posto libero: la riporto in cima
        val prev = items.firstOrNull { it is WidgetItem && it.ref.kind == "search" && it.page == 0 && it.y > 0 && sp.getBoolean(lk("searchWidget"), false) }
        if (prev != null) items.remove(prev)
        val has = items.any { i -> refsOf(i).any { it.kind == "search" } }
        if (!has && (prev != null || settings.searchBar != "none")) placeSearchOnTop()
        sp.edit().putBoolean(flag, true).apply()
    }

    /** Chiamato quando cambia l'orientamento reale del telefono. */
    fun syncOrientation(land: Boolean) {
        if (land == layoutLand) return
        AppLog.i("Orientamento: " + if (land) "orizzontale" else "verticale")
        // ruotando si resta dove si era: stessa pagina, cartella aperta e modifica restano
        val keep = currentPage
        val t0 = System.currentTimeMillis()
        swapTo(land)
        AppLog.i("Home ridisposta in ${System.currentTimeMillis() - t0} ms")
        val sel = selectedWidget
        if (sel != null && items.none { it.id == sel }) selectedWidget = null
        val folder = openFolderId
        if (folder != null && items.none { it is FolderItem && it.folderId == folder }) openFolderId = null
        goToPage(keep.coerceIn(0, maxOf(0, pageCount - 1)))
    }

    /** Adatta il layout attivo alla griglia e al numero di slot della dock. */
    private fun fit() {
        val n = curSlots
        val out = items.filter { it.page == -1 && it.x >= n }
        items.removeAll(out)
        for (o in out) {
            val (p, x, y) = findFree(0, o.spanX, o.spanY)
            items.add(o.at(p, x, y))
            ensurePages(p)
        }
        reflow()
    }

    private fun reflow() {
        val cols = curCols
        val rows = curRows
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
        sortedApps = map.values.filter { !it.isAction }.sortedBy { it.label.lowercase() }
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
        if (!weatherReady || weatherBusy) return
        val w = weather
        if (!force && w != null && System.currentTimeMillis() - w.time < 20 * 60 * 1000L) return
        weatherBusy = true
        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) { fetchWeather() }
                if (data != null) {
                    weather = data.first
                    weatherDetail = data.second
                    sp.edit().putString("weatherRaw", data.third).apply()
                    val wd = data.first
                    sp.edit().putString(
                        "weather",
                        JSONObject().put("t", wd.temp).put("c", wd.code).put("d", wd.isDay)
                            .put("h", wd.hi).put("l", wd.lo).put("p", wd.place).put("time", wd.time).toString()
                    ).apply()
                }
            } finally {
                weatherBusy = false
            }
        }
    }

    private suspend fun fetchWeather(): Triple<WeatherData, WeatherDetail?, String>? {
        return try {
            val city = weatherCity
            val lat: Double
            val lon: Double
            if (city != null) {
                lat = city.lat
                lon = city.lon
            } else {
                val loc = findLocation() ?: return null
                lat = loc.latitude
                lon = loc.longitude
            }
            val url = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code,is_day,apparent_temperature,relative_humidity_2m,wind_speed_10m" +
                    "&hourly=temperature_2m,weather_code,is_day,precipitation_probability" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset" +
                    "&timezone=auto&forecast_days=7"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val txt = conn.inputStream.bufferedReader().use { it.readText() }
            val j = JSONObject(txt)
            val cur = j.getJSONObject("current")
            val daily = j.getJSONObject("daily")
            var place = city?.name ?: ""
            if (city == null) try {
                @Suppress("DEPRECATION")
                val list = Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lon, 1)
                val a = list?.firstOrNull()
                place = a?.locality ?: a?.subAdminArea ?: a?.adminArea ?: ""
            } catch (e: Exception) {
                // senza nome del luogo
            }
            Triple(WeatherData(
                temp = cur.getDouble("temperature_2m").roundToInt(),
                code = cur.getInt("weather_code"),
                isDay = cur.optInt("is_day", 1) == 1,
                hi = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                lo = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
                place = place,
                time = System.currentTimeMillis()
            ), parseDetail(j), txt)
        } catch (e: Exception) {
            AppLog.w("Meteo non disponibile: ${e.javaClass.simpleName} ${e.message ?: ""}")
            null
        }
    }

    private fun parseDetail(j: JSONObject): WeatherDetail? = try {
        val cur = j.getJSONObject("current")
        val h = j.getJSONObject("hourly")
        val d = j.getJSONObject("daily")
        val ht = h.getJSONArray("time")
        val now = cur.getString("time").take(13)
        var start = 0
        while (start < ht.length() && ht.getString(start).take(13) < now) start++
        val hours = ArrayList<WeatherHour>()
        for (i in start until minOf(start + 24, ht.length())) {
            hours.add(
                WeatherHour(
                    if (i == start) "Ora" else ht.getString(i).substring(11, 16),
                    h.getJSONArray("weather_code").optInt(i),
                    h.getJSONArray("is_day").optInt(i, 1) == 1,
                    h.getJSONArray("temperature_2m").optDouble(i).roundToInt(),
                    h.getJSONArray("precipitation_probability").optInt(i, 0)
                )
            )
        }
        val dt = d.getJSONArray("time")
        val days = ArrayList<WeatherDay>()
        for (i in 0 until dt.length()) {
            val date = java.time.LocalDate.parse(dt.getString(i))
            val label = when (i) {
                0 -> "Oggi"
                1 -> "Domani"
                else -> date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ITALIAN)
                    .replaceFirstChar { it.uppercase() }
            }
            days.add(
                WeatherDay(
                    label,
                    d.getJSONArray("weather_code").optInt(i),
                    d.getJSONArray("temperature_2m_max").optDouble(i).roundToInt(),
                    d.getJSONArray("temperature_2m_min").optDouble(i).roundToInt(),
                    d.getJSONArray("precipitation_probability_max").optInt(i, 0)
                )
            )
        }
        WeatherDetail(
            feels = cur.optDouble("apparent_temperature").roundToInt(),
            humidity = cur.optInt("relative_humidity_2m"),
            wind = cur.optDouble("wind_speed_10m").roundToInt(),
            pop = hours.firstOrNull()?.pop ?: 0,
            sunrise = d.getJSONArray("sunrise").optString(0).drop(11).take(5),
            sunset = d.getJSONArray("sunset").optString(0).drop(11).take(5),
            hours = hours,
            days = days
        )
    } catch (e: Exception) {
        null
    }

    // ---------- aggiornamenti ----------

    /** testo dell'ultimo errore che ha chiuso l'app (null = nessuno) */
    var lastCrash by mutableStateOf<String?>(null)

    fun loadCrash() {
        val f = MLApp.crashFile(ctx)
        lastCrash = if (f.exists()) try { f.readText() } catch (e: Exception) { null } else null
    }

    fun clearCrash() {
        try { MLApp.crashFile(ctx).delete() } catch (e: Exception) { }
        lastCrash = null
    }

    var updateInfo by mutableStateOf<UpdateInfo?>(null)
        private set
    var updateChecking by mutableStateOf(false)
        private set
    /** null = mai controllato; true = ultima versione; false = errore di rete */
    var updateUpToDate by mutableStateOf<Boolean?>(null)
        private set

    val currentVersionCode: Int by lazy {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode.toInt()
        } catch (e: Exception) {
            0
        }
    }
    val currentVersionName: String by lazy {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /** Legge l'ultima release su GitHub; [auto] = controllo silenzioso all'avvio (al massimo ogni 6 ore). */
    fun checkUpdate(auto: Boolean = false) {
        if (updateChecking) return
        if (auto) {
            if (!settings.autoUpdateCheck) return
            if (System.currentTimeMillis() - sp.getLong("updateCheckTime", 0L) < 6 * 3600_000L) {
                restoreUpdateCache()
                return
            }
        }
        updateChecking = true
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                try {
                    val conn = URL("https://api.github.com/repos/marco90-cyber/Mlauncher/releases/latest")
                        .openConnection() as HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.setRequestProperty("Accept", "application/vnd.github+json")
                    val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val tag = j.getString("tag_name").removePrefix("v")
                    val code = tag.substringAfterLast('.').toIntOrNull() ?: 0
                    val assets = j.optJSONArray("assets")
                    var url = ""
                    if (assets != null) for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name").endsWith(".apk")) url = a.optString("browser_download_url")
                    }
                    UpdateInfo(tag, code, url, j.optString("html_url"))
                } catch (e: Exception) {
                    null
                }
            }
            updateChecking = false
            AppLog.i(if (res == null) "Controllo aggiornamenti non riuscito" else "Ultima versione su GitHub: ${res.version}")
            if (res == null) {
                updateUpToDate = false
                return@launch
            }
            sp.edit().putLong("updateCheckTime", System.currentTimeMillis())
                .putString("updateCache", JSONObject().put("v", res.version).put("c", res.code).put("u", res.url).put("p", res.page).toString())
                .apply()
            applyUpdate(res)
        }
    }

    private fun applyUpdate(res: UpdateInfo) {
        if (res.code > currentVersionCode) {
            updateInfo = res
            updateUpToDate = null
        } else {
            updateInfo = null
            updateUpToDate = true
        }
    }

    private fun restoreUpdateCache() {
        val str = sp.getString("updateCache", null) ?: return
        try {
            val o = JSONObject(str)
            applyUpdate(UpdateInfo(o.getString("v"), o.getInt("c"), o.optString("u"), o.optString("p")))
        } catch (e: Exception) {
            // cache non valida
        }
    }

    /** Cerca una città per nome (geocoding di Open-Meteo). */
    suspend fun searchCity(q: String): List<City> = withContext(Dispatchers.IO) {
        try {
            val url = URL(
                "https://geocoding-api.open-meteo.com/v1/search?name=" +
                    java.net.URLEncoder.encode(q.trim(), "UTF-8") + "&count=8&language=it&format=json"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val arr = j.optJSONArray("results") ?: return@withContext emptyList()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                City(
                    o.getString("name"), o.getDouble("latitude"), o.getDouble("longitude"),
                    listOf(o.optString("admin1"), o.optString("country")).filter { it.isNotBlank() }.joinToString(", ")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** null = torna alla posizione del telefono */
    fun chooseWeatherCity(c: City?) {
        weatherCity = c
        val e = sp.edit()
        if (c == null) e.remove("weatherCity")
        else e.putString("weatherCity", JSONObject().put("n", c.name).put("la", c.lat).put("lo", c.lon).put("a", c.area).toString())
        e.apply()
        weather = null
        weatherDetail = null
        refreshWeather(true)
    }

    private fun loadWeatherCache() {
        sp.getString("weatherCity", null)?.let {
            try {
                val o = JSONObject(it)
                weatherCity = City(o.getString("n"), o.getDouble("la"), o.getDouble("lo"), o.optString("a"))
            } catch (e: Exception) {
                // città non valida
            }
        }
        sp.getString("weatherRaw", null)?.let {
            try {
                weatherDetail = parseDetail(JSONObject(it))
            } catch (e: Exception) {
                // cache non valida
            }
        }
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
        sp.getStringSet("newApps", null)?.let { newApps.addAll(it) }
        loadNotes()
        loadWallpaper()
        loadMinus()
        loadAutoName()
        // le vecchie voci "cartelle per lettera/categoria" ora sono modalità delle cartelle intelligenti
        when (settings.homeOrder) {
            "letter_folders", "cat_folders" -> {
                settings.set("smartMode", if (settings.homeOrder == "letter_folders") "letter" else "genre")
                settings.set("autoFolders", true)
                settings.set("homeOrder", "name")
            }
        }
        hasLayout = loadLayout()
        // benvenuto solo alla prima installazione: chi ha già una Home non lo vede
        if (!sp.getBoolean("welcomeDone", false)) {
            if (hasLayout) sp.edit().putBoolean("welcomeDone", true).apply() else showWelcome = true
        }
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
        loadFromCache()
        readWallpaperHint()
        try {
            android.app.WallpaperManager.getInstance(ctx).addOnColorsChangedListener(
                { _, _ -> readWallpaperHint() }, android.os.Handler(android.os.Looper.getMainLooper())
            )
        } catch (e: Exception) { }
        refresh(null)
    }
}
