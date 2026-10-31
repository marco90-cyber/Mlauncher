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

class DrawerDrag(val appId: String, start: androidx.compose.ui.geometry.Offset) {
    var pos by mutableStateOf(start)
}

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
    private var showLabelsL by mutableStateOf(sp.getBoolean("showLabels_l", true))
    val showLabels: Boolean get() = showLabelsP
    var themedIcons by mutableStateOf(sp.getBoolean("themedIcons", false))
        private set
    private var pageEffectP by mutableStateOf(sp.getString("pageEffect", "slide") ?: "slide")
    private var pageEffectL by mutableStateOf(sp.getString("pageEffect_l", sp.getString("pageEffect", "slide")) ?: "slide")
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
    private fun dockStyleOk(it: String) = if (it in listOf("liquid", "glass", "tinted", "dark", "pill", "bar", "tiles", "none")) it else "glass"
    private var dockStyleP by mutableStateOf(dockStyleOk(sp.getString("dockStyle", "glass") ?: "glass"))
    private var dockStyleL by mutableStateOf(dockStyleOk(sp.getString("dockStyle_l", sp.getString("dockStyle", "glass")) ?: "glass"))
    val dockStyle: String get() = dockStyleP
    private var dockOpacityP by mutableStateOf(sp.getInt("dockOpacity", 60))
    private var dockOpacityL by mutableStateOf(sp.getInt("dockOpacity_l", sp.getInt("dockOpacity", 60)))
    val dockOpacity: Int get() = dockOpacityP
    private var dockGlassP by mutableStateOf(sp.getBoolean("dockGlass", true))
    private var dockGlassL by mutableStateOf(sp.getBoolean("dockGlass_l", sp.getBoolean("dockGlass", true)))
    val dockGlass: Boolean get() = dockGlassP
    private var dockSizeP by mutableStateOf(sp.getFloat("dockSize", 1f))
    private var dockSizeL by mutableStateOf(sp.getFloat("dockSize_l", sp.getFloat("dockSize", 1f)))
    val dockSize: Float get() = if (landscape) dockSizeL else dockSizeP
    private var dockCornerP by mutableStateOf(sp.getInt("dockCorner", 30))
    private var dockCornerL by mutableStateOf(sp.getInt("dockCorner_l", sp.getInt("dockCorner", 30)))
    val dockCorner: Int get() = dockCornerP
    var haptics by mutableStateOf(sp.getBoolean("haptics", true))
    var hapPress by mutableStateOf(sp.getBoolean("hapPress", true))
    var hapDrop by mutableStateOf(sp.getBoolean("hapDrop", true))
    var hapTick by mutableStateOf(sp.getBoolean("hapTick", true))
    var hapPage by mutableStateOf(sp.getBoolean("hapPage", true))
    var shakeTidy by mutableStateOf(sp.getBoolean("shakeTidy", true))
    var accent by mutableStateOf(sp.getString("accent", "auto") ?: "auto")
    var lockHome by mutableStateOf(sp.getBoolean("lockHome", false))
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
    private var drLabelsL by mutableStateOf(sp.getBoolean("drLabels_l", sp.getBoolean("drLabels", true)))
    val drLabels: Boolean get() = drLabelsP
    private var drLabelSizeP by mutableStateOf(sp.getInt("drLabelSize", 11))
    private var drLabelSizeL by mutableStateOf(sp.getInt("drLabelSize_l", sp.getInt("drLabelSize", 11)))
    val drLabelSize: Int get() = drLabelSizeP
    private var drTwoLineP by mutableStateOf(sp.getBoolean("drTwoLine", false))
    private var drTwoLineL by mutableStateOf(sp.getBoolean("drTwoLine_l", sp.getBoolean("drTwoLine", false)))
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
    var folderPreview by mutableStateOf(sp.getString("folderPreview", "grid") ?: "grid")
    var folderBg by mutableStateOf(sp.getString("folderBg", "tonal") ?: "tonal")
    var folderShape by mutableStateOf(sp.getString("folderShape", "squircle") ?: "squircle")
    var folderCols by mutableStateOf(sp.getInt("folderCols", 3))
        private set
    private var pageIndicatorP by mutableStateOf(sp.getString("pageIndicator", "pill") ?: "pill")
    private var pageIndicatorL by mutableStateOf(sp.getString("pageIndicator_l", sp.getString("pageIndicator", "pill")) ?: "pill")
    val pageIndicator: String get() = pageIndicatorP
    private var searchBarP by mutableStateOf(sp.getString("searchBar", "home") ?: "home")
    private var searchBarL by mutableStateOf(sp.getString("searchBar_l", sp.getString("searchBar", "home")) ?: "home")
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
            "lockHome" -> lockHome = value as Boolean
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
