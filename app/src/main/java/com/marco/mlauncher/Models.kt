package com.marco.mlauncher

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import org.json.JSONObject

enum class Overlay { NONE, DRAWER, SETTINGS, WIDGETS }

class AppEntry(
    val pkg: String,
    val cls: String,
    val label: String,
    val icon: ImageBitmap,
    val mono: ImageBitmap?,
    val removable: Boolean,
    /** icona senza maschera del sistema, per applicare una forma uniforme */
    val raw: ImageBitmap? = null
) {
    val id: String get() = "$pkg/$cls"
}

/** Un widget: kind = "clock" | "weather" | "ext" (widget Android vero). */
data class WidgetRef(val kind: String, val widgetId: Int = -1, val provider: String = "")

/** griglia della pagina −1: colonne e righe massime di un widget */
const val MINUS_COLS = 4
const val MINUS_ROWS = 5

/** Widget della pagina −1: larghezza [sx] e altezza [sy] in celle, come in Home. */
data class MinusW(val uid: String, val ref: WidgetRef, val sx: Int, val sy: Int)

sealed class HomeItem {
    abstract val id: String
    abstract val page: Int // -1 = dock
    abstract val x: Int
    abstract val y: Int
    open val spanX: Int get() = 1
    open val spanY: Int get() = 1
    abstract fun at(page: Int, x: Int, y: Int): HomeItem
    open fun sized(sx: Int, sy: Int): HomeItem = this
}

data class AppItem(
    val appId: String,
    override val page: Int,
    override val x: Int,
    override val y: Int
) : HomeItem() {
    override val id: String get() = "app:$appId"
    override fun at(page: Int, x: Int, y: Int): HomeItem = copy(page = page, x = x, y = y)
}

data class FolderItem(
    val folderId: String,
    val name: String,
    val appIds: List<String>,
    override val page: Int,
    override val x: Int,
    override val y: Int,
    val big: Boolean = false
) : HomeItem() {
    override val id: String get() = "folder:$folderId"
    override val spanX: Int get() = if (big) 2 else 1
    override val spanY: Int get() = if (big) 2 else 1
    override fun at(page: Int, x: Int, y: Int): HomeItem = copy(page = page, x = x, y = y)
}

data class WidgetItem(
    val uid: String,
    val ref: WidgetRef,
    override val page: Int,
    override val x: Int,
    override val y: Int,
    override val spanX: Int,
    override val spanY: Int
) : HomeItem() {
    override val id: String get() = "widget:$uid"
    override fun at(page: Int, x: Int, y: Int): HomeItem = copy(page = page, x = x, y = y)
    override fun sized(sx: Int, sy: Int): HomeItem = copy(spanX = sx, spanY = sy)
}

data class StackItem(
    val uid: String,
    val refs: List<WidgetRef>,
    override val page: Int,
    override val x: Int,
    override val y: Int,
    override val spanX: Int,
    override val spanY: Int
) : HomeItem() {
    override val id: String get() = "stack:$uid"
    override fun at(page: Int, x: Int, y: Int): HomeItem = copy(page = page, x = x, y = y)
    override fun sized(sx: Int, sy: Int): HomeItem = copy(spanX = sx, spanY = sy)
}

class UpdateInfo(val version: String, val code: Int, val url: String, val page: String)

/** Nota del widget "note veloci": testo, colore del foglio (chiave) e grandezza del testo (sp). */
data class NoteData(val text: String = "", val color: String = "yellow", val size: Int = 15)

class City(val name: String, val lat: Double, val lon: Double, val area: String = "")

class WeatherHour(val label: String, val code: Int, val isDay: Boolean, val temp: Int, val pop: Int)
class WeatherDay(val label: String, val code: Int, val max: Int, val min: Int, val pop: Int)
class WeatherDetail(
    val feels: Int,
    val humidity: Int,
    val wind: Int,
    val pop: Int,
    val sunrise: String,
    val sunset: String,
    val hours: List<WeatherHour>,
    val days: List<WeatherDay>
)

class WeatherData(
    val temp: Int,
    val code: Int,
    val isDay: Boolean,
    val hi: Int,
    val lo: Int,
    val place: String,
    val time: Long
)

/**
 * Icona trascinata da fuori della Home (cassetto o cartella aperta) verso la Home.
 * [extras]: altre app raccolte insieme; [fromFolder]: cartella da cui escono.
 */
class DrawerDrag(
    val appId: String,
    start: androidx.compose.ui.geometry.Offset,
    val extras: List<String> = emptyList(),
    val fromFolder: String? = null
) {
    var pos by mutableStateOf(start)
}

/** Aspetto di un elemento: materiale, trasparenza (opacità 0-100), riflesso glass e arrotondamento. */
data class Mat(val style: String, val opacity: Int, val glass: Boolean, val corner: Int) {
    /** "standard" = l'aspetto proprio dell'elemento; "none" = niente fondo */
    val plain get() = style == "standard" || style == "none"
}

/** Materiali che si possono scegliere. */
val MATERIALS = listOf(
    "liquid" to "Liquid Glass",
    "glass" to "Vetro",
    "pill" to "Tonale",
    "fluent" to "Fluent",
    "none" to "Nessuno"
)

/** Elementi della Home che prendono il materiale. */
val MAT_ELEMENTS = listOf(
    "dock" to "Dock",
    "folder" to "Cartelle",
    "indicator" to "Indicatore di pagina",
    "widgets" to "Widget di MLauncher",
    "panels" to "Pagina \u22121 e ricerca",
    "menus" to "Menu, pulsanti e cartelle aperte",
    "drawer" to "Cassetto app"
)

class LauncherSettings(private val sp: SharedPreferences) {
    /** orientazione reale del telefono (aggiornata da Root) */
    var actualLand by mutableStateOf(false)
    /** orientazione scelta nelle impostazioni per modificare i valori dell'altra (null = quella reale) */
    var previewLand by mutableStateOf<Boolean?>(null)
    val landscape: Boolean get() = previewLand ?: actualLand
    private val oriented = setOf("iconScale", "dockSize", "drCols", "drRowH", "drIcon", "drTop", "drIndent", "cols", "rows", "dockSlots")

    var homeMode by mutableStateOf(sp.getString("homeMode", "drawer") ?: "drawer")
        private set
    private var colsP by mutableIntStateOf(sp.getInt("cols", 4))
    private var colsL by mutableIntStateOf(sp.getInt("cols_l", 8))
    val cols: Int get() = if (landscape) colsL else colsP
    private var rowsP by mutableIntStateOf(sp.getInt("rows", 6))
    private var rowsL by mutableIntStateOf(sp.getInt("rows_l", 3))
    val rows: Int get() = if (landscape) rowsL else rowsP
    fun colsOf(l: Boolean) = if (l) colsL else colsP
    fun rowsOf(l: Boolean) = if (l) rowsL else rowsP
    private var iconScaleP by mutableStateOf(sp.getFloat("iconScale", 1f))
    private var iconScaleL by mutableStateOf(sp.getFloat("iconScale_l", sp.getFloat("iconScale", 1f)))
    val iconScale: Float get() = if (landscape) iconScaleL else iconScaleP
    private var showLabelsP by mutableStateOf(sp.getBoolean("showLabels", true))
    val showLabels: Boolean get() = showLabelsP
    var themedIcons by mutableStateOf(sp.getBoolean("themedIcons", false))
        private set
    // l'effetto "Rotazione" è stato tolto: chi l'aveva passa a Scorrimento
    private var pageEffectP by mutableStateOf((sp.getString("pageEffect", "slide") ?: "slide").let { if (it == "rotate") "slide" else it })
    val pageEffect: String get() = pageEffectP
    var animSpeed by mutableIntStateOf(sp.getInt("animSpeed", 1))
        private set
    var uninstallAnim by mutableStateOf(sp.getString("uninstallAnim", "boom") ?: "boom")
        private set
    var theme by mutableStateOf(sp.getString("theme", "auto") ?: "auto")
        private set
    var swipeDown by mutableStateOf(sp.getString("swipeDown", "notifications") ?: "notifications")
        private set
    var autoAddNew by mutableStateOf(sp.getBoolean("autoAddNew", false))
        private set
    private var dockSlotsP by mutableIntStateOf(sp.getInt("dockSlots", 4))
    private var dockSlotsL by mutableIntStateOf(sp.getInt("dockSlots_l", sp.getInt("dockSlots", 4)))
    val dockSlots: Int get() = if (landscape) dockSlotsL else dockSlotsP
    fun dockSlotsOf(l: Boolean) = if (l) dockSlotsL else dockSlotsP
    private fun dockStyleOk(it: String) = if (it in listOf("liquid", "glass", "pill", "fluent", "bar", "tiles", "none")) it else "glass"
    private var dockStyleP by mutableStateOf(dockStyleOk(sp.getString("dockStyle", "glass") ?: "glass"))
    val dockStyle: String get() = dockStyleP
    private var dockOpacityP by mutableStateOf(sp.getInt("dockOpacity", 60))
    val dockOpacity: Int get() = dockOpacityP
    private var dockGlassP by mutableStateOf(sp.getBoolean("dockGlass", true))
    val dockGlass: Boolean get() = dockGlassP
    private var dockSizeP by mutableStateOf(sp.getFloat("dockSize", 1f))
    private var dockSizeL by mutableStateOf(sp.getFloat("dockSize_l", sp.getFloat("dockSize", 1f)))
    val dockSize: Float get() = if (landscape) dockSizeL else dockSizeP
    private var dockCornerP by mutableStateOf(sp.getInt("dockCorner", 30))
    val dockCorner: Int get() = dockCornerP
    var haptics by mutableStateOf(sp.getBoolean("haptics", true))
    var hapPress by mutableStateOf(sp.getBoolean("hapPress", true))
    var hapDrop by mutableStateOf(sp.getBoolean("hapDrop", true))
    var hapTick by mutableStateOf(sp.getBoolean("hapTick", true))
    var hapPage by mutableStateOf(sp.getBoolean("hapPage", true))
    var shakeTidy by mutableStateOf(sp.getBoolean("shakeTidy", true))
    var accent by mutableStateOf(sp.getString("accent", "system") ?: "system")

    // ---------- materiale degli elementi (Stile) ----------
    // le vecchie dock "barra" e "tessere" diventano una forma della dock + un materiale
    // i materiali tolti (vetro colorato, vetro scuro, pieno) passano al più simile rimasto
    private fun legacyMat(st: String) = when (st) {
        "bar" -> "pill"; "tiles" -> "glass"; "tinted", "dark" -> "glass"; "solid" -> "fluent"; else -> st
    }
    /** materiale generale: liquid, glass, pill (tonale), fluent, none */
    var matStyle by mutableStateOf(legacyMat(sp.getString("matStyle", null) ?: dockStyleP))
    var matOpacity by mutableStateOf(sp.getInt("matOpacity", dockOpacityP))
    var matGlass by mutableStateOf(sp.getBoolean("matGlass", dockGlassP))
    var matCorner by mutableStateOf(sp.getInt("matCorner", dockCornerP))
    /** forma della dock: "float" (fluttuante), "bar" (a tutta larghezza), "tiles" (tessere) */
    var dockForm by mutableStateOf(sp.getString("dockForm", null) ?: when (dockStyleP) { "bar" -> "bar"; "tiles" -> "tiles"; else -> "float" })
    /** scelte dei singoli elementi: el_<elemento>_follow / _style / _opacity / _glass / _corner */
    private val el = androidx.compose.runtime.mutableStateMapOf<String, Any>().apply {
        sp.all.forEach { (k, v) -> if (k.startsWith("el_") && v != null) put(k, v) }
    }

    /** come si comportava prima di questa versione (così l'aggiornamento non cambia l'aspetto) */
    private fun elFollowDefault(e: String) = when (e) {
        "dock" -> true
        "folder" -> folderFollowDock
        "indicator" -> indFollowDock
        "widgets" -> liquid
        "panels" -> minusStyle == "dock"
        // pulsanti della modifica, "Crea cartella" e menu: insieme al tema scelto
        "menus" -> true
        else -> false
    }

    private fun elStyleDefault(e: String) = when (e) {
        "dock" -> legacyMat(dockStyleP)
        "panels" -> if (minusStyle == "glass") "glass" else "standard"
        else -> "standard"
    }

    fun elFollow(e: String): Boolean = (el["el_${e}_follow"] as? Boolean) ?: elFollowDefault(e)
    fun elStyle(e: String): String = (el["el_${e}_style"] as? String)?.let { legacyMat(it) } ?: elStyleDefault(e)
    fun elOpacity(e: String): Int = (el["el_${e}_opacity"] as? Int) ?: (if (e == "dock") dockOpacityP else matOpacity)
    fun elGlass(e: String): Boolean = (el["el_${e}_glass"] as? Boolean) ?: (if (e == "dock") dockGlassP else matGlass)
    fun elCorner(e: String): Int = (el["el_${e}_corner"] as? Int) ?: (if (e == "dock") dockCornerP else matCorner)

    /** Materiale da usare per un elemento della Home: quello generale o il suo. */
    fun matOf(e: String): Mat =
        if (elFollow(e)) Mat(matStyle, matOpacity, matGlass, matCorner)
        else Mat(elStyle(e), elOpacity(e), elGlass(e), elCorner(e))
    // pagina −1 (widget a sinistra della prima pagina)
    var minusOn by mutableStateOf(sp.getBoolean("minusOn", true))
    var minusGreet by mutableStateOf(sp.getBoolean("minusGreet", true))
    var minusWeather by mutableStateOf(sp.getBoolean("minusWeather", true))
    var minusName by mutableStateOf(sp.getString("minusName", "") ?: "")
    /** contenitore dei widget: "dock" (come la dock), "tonal", "glass" */
    var minusStyle by mutableStateOf(sp.getString("minusStyle", "dock") ?: "dock")
    var minusBlur by mutableStateOf(sp.getInt("minusBlur", 24))
    var minusDim by mutableStateOf(sp.getInt("minusDim", 30))
    var minusWidgets by mutableStateOf(sp.getString("minusWidgets", "[]") ?: "[]")
    // azioni: barra di stato e dock nascoste
    var hideStatusBar by mutableStateOf(sp.getBoolean("hideStatusBar", false))
    var hideDock by mutableStateOf(sp.getBoolean("hideDock", false))
    // schermata di ricerca
    /** "minus" (come la pagina −1) o "classic" */
    var searchLook by mutableStateOf(sp.getString("searchLook", "minus") ?: "minus")
    /** usa contenitore, sfocatura e velo della pagina −1 */
    var searchSame by mutableStateOf(sp.getBoolean("searchSame", true))
    var searchStyle by mutableStateOf(sp.getString("searchStyle", "dock") ?: "dock")
    var searchBlur by mutableStateOf(sp.getInt("searchBlur", 24))
    var searchDim by mutableStateOf(sp.getInt("searchDim", 30))
    var searchSuggest by mutableStateOf(sp.getBoolean("searchSuggest", true))
    // widget orologio
    /** "tonal", "glass", "text", "filled" */
    var clockStyle by mutableStateOf(sp.getString("clockStyle", "tonal") ?: "tonal")
    /** "thin", "normal", "bold" */
    var clockWeight by mutableStateOf(sp.getString("clockWeight", "bold") ?: "bold")
    /** minuti: "accent" (colore d'accento) o "same" (come le ore) */
    var clockMinutes by mutableStateOf(sp.getString("clockMinutes", "accent") ?: "accent")
    /** data: "right", "below", "none" */
    var clockDate by mutableStateOf(sp.getString("clockDate", "right") ?: "right")
    var clockSeconds by mutableStateOf(sp.getBoolean("clockSeconds", false))
    /** sfondi creati con "Crea il tuo sfondo" (regole in JSON) */
    var myWalls by mutableStateOf(sp.getString("myWalls", "[]") ?: "[]")
    /** stile degli interruttori: android16 (predefinito), material, ios, hyperos */
    var switchStyle by mutableStateOf((sp.getString("switchStyle", "android16") ?: "android16").also { SwitchLook.style = it })
    var lockHome by mutableStateOf(sp.getBoolean("lockHome", false))
    /** pagina principale della Home (dove porta il tasto Home) */
    var homePage by mutableIntStateOf(sp.getInt("homePage", 0))
    /** cartella aperta: "center" (al centro dello schermo) o "icon" (vicino all'icona) */
    var folderPos by mutableStateOf(sp.getString("folderPos", "center") ?: "center")
    /** cartella aperta: "compact" o "large" (quasi tutta la larghezza, stile HyperOS) */
    var folderSize by mutableStateOf(sp.getString("folderSize", "compact") ?: "compact")
    var returnAnim by mutableStateOf(sp.getString("returnAnim", "settle") ?: "settle")
    var returnOnApp by mutableStateOf(sp.getBoolean("returnOnApp", true))
    var returnOnUnlock by mutableStateOf(sp.getBoolean("returnOnUnlock", true))
    var returnBounce by mutableStateOf(sp.getBoolean("returnBounce", true))
    var returnWallZoom by mutableStateOf(sp.getBoolean("returnWallZoom", true))
    /** sfondo: "system" (quello di Android) o "custom" (scelto e disegnato da MLauncher) */
    var wallMode by mutableStateOf(sp.getString("wallMode", "system") ?: "system")
    var wallDim by mutableIntStateOf(sp.getInt("wallDim", 0))
    var wallBlur by mutableIntStateOf(sp.getInt("wallBlur", 0))
    var wallParallax by mutableStateOf(sp.getBoolean("wallParallax", true))
    /** tema Liquid Glass: dock, cartelle, indicatore e widget in vetro liquido */
    var liquid by mutableStateOf(sp.getBoolean("liquid", false))
    var autoBackup by mutableStateOf(sp.getBoolean("autoBackup", false))
    /** ogni quanti giorni */
    var backupEvery by mutableIntStateOf(sp.getInt("backupEvery", 7))
    /** quanti backup tenere */
    var backupKeep by mutableIntStateOf(sp.getInt("backupKeep", 5))
    var backupDir by mutableStateOf(sp.getString("backupDir", "") ?: "")
    /** colore dei nomi in Home: auto (dallo sfondo), white, dark */
    var labelColor by mutableStateOf(sp.getString("labelColor", "auto") ?: "auto")
    var homeOpensDrawer by mutableStateOf(sp.getBoolean("homeOpensDrawer", true))
    var newAppDot by mutableStateOf(sp.getBoolean("newAppDot", true))
    var autoUpdateCheck by mutableStateOf(sp.getBoolean("autoUpdateCheck", true))
    var indFollowDock by mutableStateOf(sp.getBoolean("indFollowDock", true))
        private set
    var drLayout by mutableStateOf(sp.getString("drLayout", "grid") ?: "grid")
    private var drColsP by mutableStateOf(sp.getInt("drCols", 4))
    private var drColsL by mutableStateOf(sp.getInt("drCols_l", 6))
    val drCols: Int get() = if (landscape) drColsL else drColsP
    private var drRowHP by mutableStateOf(sp.getFloat("drRowH", 1f))
    private var drRowHL by mutableStateOf(sp.getFloat("drRowH_l", sp.getFloat("drRowH", 1f)))
    val drRowH: Float get() = if (landscape) drRowHL else drRowHP
    private var drIconP by mutableStateOf(sp.getFloat("drIcon", 1f))
    private var drIconL by mutableStateOf(sp.getFloat("drIcon_l", sp.getFloat("drIcon", 1f)))
    val drIcon: Float get() = if (landscape) drIconL else drIconP
    private var drLabelsP by mutableStateOf(sp.getBoolean("drLabels", true))
    val drLabels: Boolean get() = drLabelsP
    private var drLabelSizeP by mutableStateOf(sp.getInt("drLabelSize", 11))
    val drLabelSize: Int get() = drLabelSizeP
    private var drTwoLineP by mutableStateOf(sp.getBoolean("drTwoLine", false))
    val drTwoLine: Boolean get() = drTwoLineP
    var drBg by mutableStateOf(sp.getString("drBg", "surface") ?: "surface")
    var drOpacity by mutableStateOf(sp.getInt("drOpacity", 97))
    var drSearchBar by mutableStateOf(sp.getBoolean("drSearchBar", true))
    var drSearchBg by mutableStateOf(sp.getBoolean("drSearchBg", true))
    var drSuggest by mutableStateOf(sp.getBoolean("drSuggest", false))
    var drSort by mutableStateOf(sp.getString("drSort", "name") ?: "name")
    var drScrollbar by mutableStateOf(sp.getBoolean("drScrollbar", true))
    private var drTopP by mutableStateOf(sp.getInt("drTop", 0))
    private var drTopL by mutableStateOf(sp.getInt("drTop_l", sp.getInt("drTop", 0)))
    val drTop: Int get() = if (landscape) drTopL else drTopP
    private var drIndentP by mutableStateOf(sp.getInt("drIndent", 8))
    private var drIndentL by mutableStateOf(sp.getInt("drIndent_l", sp.getInt("drIndent", 8)))
    val drIndent: Int get() = if (landscape) drIndentL else drIndentP
    var drRemember by mutableStateOf(sp.getBoolean("drRemember", false))
    var folderBlur by mutableStateOf(sp.getInt("folderBlur", 18))
    var folderFollowDock by mutableStateOf(sp.getBoolean("folderFollowDock", true))
    var allowRotation by mutableStateOf(sp.getBoolean("allowRotation", true))
    var autoFolders by mutableStateOf(sp.getBoolean("autoFolders", false))
    /** come raggruppano le cartelle intelligenti: genre_plus, genre, color, letter */
    var smartMode by mutableStateOf(sp.getString("smartMode", "genre_plus") ?: "genre_plus")
    var homeOrder by mutableStateOf(sp.getString("homeOrder", "name") ?: "name")
    var iconShape by mutableStateOf(sp.getString("iconShape", "original") ?: "original")
    /** tema (Stile → Tema): "liquid", "glass", "expressive", "tonal", "lite", "fluent", "none" o "custom" (scelte fatte a mano) */
    var launcherTheme by mutableStateOf(sp.getString("launcherTheme", "custom") ?: "custom")
    /** barra "Cerca nelle impostazioni" in cima all'elenco delle impostazioni */
    var settingsSearch by mutableStateOf(sp.getBoolean("settingsSearch", true))
    /** colori delle icone Material You: "primary", "secondary", "tertiary" */
    var themedIconColor by mutableStateOf(sp.getString("themedIconColor", "secondary") ?: "secondary")
    /** icone nel tema Liquid Glass: "normal", "clear" (logo su vetro), "tinted" (glifo colorato su vetro scuro) */
    var glassIconStyle by mutableStateOf(sp.getString("glassIconStyle", "normal") ?: "normal")
    /** riflessi sul bordo delle icone (Liquid Glass): "off", "corners", "full" */
    var glassIconHighlight by mutableStateOf(sp.getString("glassIconHighlight", "corners") ?: "corners")
    /** forza dei riflessi del vetro, in percentuale */
    var glassHighlight by mutableIntStateOf(sp.getInt("glassHighlight", 70))
    var folderPreview by mutableStateOf(sp.getString("folderPreview", "grid") ?: "grid")
    var folderBg by mutableStateOf(sp.getString("folderBg", "tonal") ?: "tonal")
    var folderShape by mutableStateOf(sp.getString("folderShape", "squircle") ?: "squircle")
    var folderCols by mutableStateOf(sp.getInt("folderCols", 3))
        private set
    private var pageIndicatorP by mutableStateOf(sp.getString("pageIndicator", "pill") ?: "pill")
    val pageIndicator: String get() = pageIndicatorP
    private var searchBarP by mutableStateOf(sp.getString("searchBar", "home") ?: "home")
    val searchBar: String get() = searchBarP
    var badges by mutableStateOf(sp.getString("badges", "off") ?: "off")
        private set
    var stackAutoRotate by mutableStateOf(sp.getBoolean("stackAutoRotate", true))
        private set
    var iconPack by mutableStateOf(sp.getString("iconPack", "") ?: "")
        private set
    var doubleTapLock by mutableStateOf(sp.getBoolean("doubleTapLock", false))
        private set
    var edgePanel by mutableStateOf(sp.getBoolean("edgePanel", false))
        private set
    var edgeSide by mutableStateOf(sp.getString("edgeSide", "right") ?: "right")
        private set
    var edgeHandleSize by mutableIntStateOf(sp.getInt("edgeHandleSize", 3))
        private set
    var edgeHandleY by mutableIntStateOf(sp.getInt("edgeHandleY", 40))
        private set
    var edgePanelApps by mutableStateOf(sp.getString("edgePanelApps", "") ?: "")
        private set
    var edgeShowApps by mutableStateOf(sp.getBoolean("edgeShowApps", true))
        private set
    var edgeShowTools by mutableStateOf(sp.getBoolean("edgeShowTools", true))
        private set
    var edgeShowContacts by mutableStateOf(sp.getBoolean("edgeShowContacts", false))
        private set

    val animFactor: Float
        get() = when (animSpeed) {
            0 -> 1.4f
            2 -> 0.7f
            else -> 1f
        }

    fun set(key: String, value: Any) {
        if (key.startsWith("el_")) el[key] = value
        val e = sp.edit()
        val wk = if (key in oriented && landscape) key + "_l" else key
        when (value) {
            is Int -> e.putInt(wk, value)
            is Float -> e.putFloat(wk, value)
            is Boolean -> e.putBoolean(wk, value)
            is String -> e.putString(wk, value)
        }
        e.apply()
        when (key) {
            "homeMode" -> homeMode = value as String
            "cols" -> if (landscape) colsL = value as Int else colsP = value as Int
            "rows" -> if (landscape) rowsL = value as Int else rowsP = value as Int
            "iconScale" -> if (landscape) iconScaleL = value as Float else iconScaleP = value as Float
            "showLabels" -> showLabelsP = value as Boolean
            "themedIcons" -> themedIcons = value as Boolean
            "pageEffect" -> pageEffectP = value as String
            "animSpeed" -> animSpeed = value as Int
            "uninstallAnim" -> uninstallAnim = value as String
            "theme" -> theme = value as String
            "swipeDown" -> swipeDown = value as String
            "autoAddNew" -> autoAddNew = value as Boolean
            "dockSlots" -> if (landscape) dockSlotsL = value as Int else dockSlotsP = value as Int
            "dockStyle" -> dockStyleP = value as String
            "dockOpacity" -> dockOpacityP = value as Int
            "dockGlass" -> dockGlassP = value as Boolean
            "dockSize" -> if (landscape) dockSizeL = value as Float else dockSizeP = value as Float
            "dockCorner" -> dockCornerP = value as Int
            "haptics" -> haptics = value as Boolean
            "hapPress" -> hapPress = value as Boolean
            "hapDrop" -> hapDrop = value as Boolean
            "hapTick" -> hapTick = value as Boolean
            "hapPage" -> hapPage = value as Boolean
            "shakeTidy" -> shakeTidy = value as Boolean
            "accent" -> accent = value as String
            "matStyle" -> matStyle = value as String
            "matOpacity" -> matOpacity = value as Int
            "matGlass" -> matGlass = value as Boolean
            "matCorner" -> matCorner = value as Int
            "dockForm" -> dockForm = value as String
            "minusOn" -> minusOn = value as Boolean
            "minusGreet" -> minusGreet = value as Boolean
            "minusWeather" -> minusWeather = value as Boolean
            "minusName" -> minusName = value as String
            "minusStyle" -> minusStyle = value as String
            "minusBlur" -> minusBlur = value as Int
            "minusDim" -> minusDim = value as Int
            "minusWidgets" -> minusWidgets = value as String
            "myWalls" -> myWalls = value as String
            "clockStyle" -> clockStyle = value as String
            "searchLook" -> searchLook = value as String
            "hideStatusBar" -> hideStatusBar = value as Boolean
            "hideDock" -> hideDock = value as Boolean
            "searchSame" -> searchSame = value as Boolean
            "searchStyle" -> searchStyle = value as String
            "searchBlur" -> searchBlur = value as Int
            "searchDim" -> searchDim = value as Int
            "searchSuggest" -> searchSuggest = value as Boolean
            "clockWeight" -> clockWeight = value as String
            "clockMinutes" -> clockMinutes = value as String
            "clockDate" -> clockDate = value as String
            "clockSeconds" -> clockSeconds = value as Boolean
            "switchStyle" -> { switchStyle = value as String; SwitchLook.style = switchStyle }
            "lockHome" -> lockHome = value as Boolean
            "homePage" -> homePage = value as Int
            "folderPos" -> folderPos = value as String
            "folderSize" -> folderSize = value as String
            "returnAnim" -> returnAnim = value as String
            "returnOnApp" -> returnOnApp = value as Boolean
            "returnOnUnlock" -> returnOnUnlock = value as Boolean
            "returnBounce" -> returnBounce = value as Boolean
            "returnWallZoom" -> returnWallZoom = value as Boolean
            "wallMode" -> wallMode = value as String
            "wallDim" -> wallDim = value as Int
            "wallBlur" -> wallBlur = value as Int
            "wallParallax" -> wallParallax = value as Boolean
            "liquid" -> liquid = value as Boolean
            "autoBackup" -> autoBackup = value as Boolean
            "backupEvery" -> backupEvery = value as Int
            "backupKeep" -> backupKeep = value as Int
            "backupDir" -> backupDir = value as String
            "labelColor" -> labelColor = value as String
            "homeOpensDrawer" -> homeOpensDrawer = value as Boolean
            "newAppDot" -> newAppDot = value as Boolean
            "autoUpdateCheck" -> autoUpdateCheck = value as Boolean
            "indFollowDock" -> indFollowDock = value as Boolean
            "drLayout" -> drLayout = value as String
            "drCols" -> if (landscape) drColsL = value as Int else drColsP = value as Int
            "drRowH" -> if (landscape) drRowHL = value as Float else drRowHP = value as Float
            "drIcon" -> if (landscape) drIconL = value as Float else drIconP = value as Float
            "drLabels" -> drLabelsP = value as Boolean
            "drLabelSize" -> drLabelSizeP = value as Int
            "drTwoLine" -> drTwoLineP = value as Boolean
            "drBg" -> drBg = value as String
            "drOpacity" -> drOpacity = value as Int
            "drSearchBar" -> drSearchBar = value as Boolean
            "drSearchBg" -> drSearchBg = value as Boolean
            "drSuggest" -> drSuggest = value as Boolean
            "drSort" -> drSort = value as String
            "drScrollbar" -> drScrollbar = value as Boolean
            "drTop" -> if (landscape) drTopL = value as Int else drTopP = value as Int
            "drIndent" -> if (landscape) drIndentL = value as Int else drIndentP = value as Int
            "drRemember" -> drRemember = value as Boolean
            "folderBlur" -> folderBlur = value as Int
            "folderFollowDock" -> folderFollowDock = value as Boolean
            "allowRotation" -> allowRotation = value as Boolean
            "autoFolders" -> autoFolders = value as Boolean
            "smartMode" -> smartMode = value as String
            "homeOrder" -> homeOrder = value as String
            "iconShape" -> iconShape = value as String
            "launcherTheme" -> launcherTheme = value as String
            "settingsSearch" -> settingsSearch = value as Boolean
            "themedIconColor" -> themedIconColor = value as String
            "glassIconStyle" -> glassIconStyle = value as String
            "glassIconHighlight" -> glassIconHighlight = value as String
            "glassHighlight" -> glassHighlight = value as Int
            "folderPreview" -> folderPreview = value as String
            "folderBg" -> folderBg = value as String
            "folderShape" -> folderShape = value as String
            "folderCols" -> folderCols = value as Int
            "pageIndicator" -> pageIndicatorP = value as String
            "searchBar" -> searchBarP = value as String
            "badges" -> badges = value as String
            "stackAutoRotate" -> stackAutoRotate = value as Boolean
            "iconPack" -> iconPack = value as String
            "doubleTapLock" -> doubleTapLock = value as Boolean
            "edgePanel" -> edgePanel = value as Boolean
            "edgeSide" -> edgeSide = value as String
            "edgeHandleSize" -> edgeHandleSize = value as Int
            "edgeHandleY" -> edgeHandleY = value as Int
            "edgePanelApps" -> edgePanelApps = value as String
            "edgeShowApps" -> edgeShowApps = value as Boolean
            "edgeShowTools" -> edgeShowTools = value as Boolean
            "edgeShowContacts" -> edgeShowContacts = value as Boolean
        }
    }

    /** Esporta tutte le impostazioni (per il backup). */
    fun dump(): JSONObject {
        val o = JSONObject()
        for ((k, v) in sp.all) {
            val e = JSONObject()
            when (v) {
                is Int -> e.put("t", "i").put("v", v)
                is Float -> e.put("t", "f").put("v", v.toDouble())
                is Boolean -> e.put("t", "b").put("v", v)
                is String -> e.put("t", "s").put("v", v)
                else -> continue
            }
            o.put(k, e)
        }
        return o
    }

    fun load(o: JSONObject) {
        for (k in o.keys()) {
            val e = o.optJSONObject(k) ?: continue
            when (e.optString("t")) {
                "i" -> set(k, e.getInt("v"))
                "f" -> set(k, e.getDouble("v").toFloat())
                "b" -> set(k, e.getBoolean("v"))
                "s" -> set(k, e.getString("v"))
            }
        }
    }
}
