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

class WeatherData(
    val temp: Int,
    val code: Int,
    val isDay: Boolean,
    val hi: Int,
    val lo: Int,
    val place: String,
    val time: Long
)

class LauncherSettings(private val sp: SharedPreferences) {
    var homeMode by mutableStateOf(sp.getString("homeMode", "drawer") ?: "drawer")
        private set
    var cols by mutableIntStateOf(sp.getInt("cols", 4))
        private set
    var rows by mutableIntStateOf(sp.getInt("rows", 6))
        private set
    var iconScale by mutableFloatStateOf(sp.getFloat("iconScale", 1f))
        private set
    var showLabels by mutableStateOf(sp.getBoolean("showLabels", true))
        private set
    var themedIcons by mutableStateOf(sp.getBoolean("themedIcons", false))
        private set
    var pageEffect by mutableStateOf(sp.getString("pageEffect", "slide") ?: "slide")
        private set
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
    var dockSlots by mutableIntStateOf(sp.getInt("dockSlots", 4))
        private set
    var dockStyle by mutableStateOf(
        (sp.getString("dockStyle", "glass") ?: "glass").let { if (it in listOf("glass", "tinted", "dark", "pill", "bar", "tiles", "none")) it else "glass" }
    )
    var dockOpacity by mutableStateOf(sp.getInt("dockOpacity", 60))
    var dockGlass by mutableStateOf(sp.getBoolean("dockGlass", true))
    var dockSize by mutableStateOf(sp.getFloat("dockSize", 1f))
    var dockCorner by mutableStateOf(sp.getInt("dockCorner", 30))
        private set
    var haptics by mutableStateOf(sp.getBoolean("haptics", true))
    var hapPress by mutableStateOf(sp.getBoolean("hapPress", true))
    var hapDrop by mutableStateOf(sp.getBoolean("hapDrop", true))
    var hapTick by mutableStateOf(sp.getBoolean("hapTick", true))
        private set
    var homeOrder by mutableStateOf(sp.getString("homeOrder", "name") ?: "name")
    var iconShape by mutableStateOf(sp.getString("iconShape", "original") ?: "original")
    var folderPreview by mutableStateOf(sp.getString("folderPreview", "grid") ?: "grid")
    var folderBg by mutableStateOf(sp.getString("folderBg", "tonal") ?: "tonal")
    var folderCols by mutableStateOf(sp.getInt("folderCols", 3))
        private set
    var pageIndicator by mutableStateOf(sp.getString("pageIndicator", "pill") ?: "pill")
        private set
    var searchBar by mutableStateOf(sp.getString("searchBar", "home") ?: "home")
        private set
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
        when (value) {
            is Int -> e.putInt(key, value)
            is Float -> e.putFloat(key, value)
            is Boolean -> e.putBoolean(key, value)
            is String -> e.putString(key, value)
        }
        e.apply()
        when (key) {
            "homeMode" -> homeMode = value as String
            "cols" -> cols = value as Int
            "rows" -> rows = value as Int
            "iconScale" -> iconScale = value as Float
            "showLabels" -> showLabels = value as Boolean
            "themedIcons" -> themedIcons = value as Boolean
            "pageEffect" -> pageEffect = value as String
            "animSpeed" -> animSpeed = value as Int
            "uninstallAnim" -> uninstallAnim = value as String
            "theme" -> theme = value as String
            "swipeDown" -> swipeDown = value as String
            "autoAddNew" -> autoAddNew = value as Boolean
            "dockSlots" -> dockSlots = value as Int
            "dockStyle" -> dockStyle = value as String
            "dockOpacity" -> dockOpacity = value as Int
            "dockGlass" -> dockGlass = value as Boolean
            "dockSize" -> dockSize = value as Float
            "dockCorner" -> dockCorner = value as Int
            "haptics" -> haptics = value as Boolean
            "hapPress" -> hapPress = value as Boolean
            "hapDrop" -> hapDrop = value as Boolean
            "hapTick" -> hapTick = value as Boolean
            "homeOrder" -> homeOrder = value as String
            "iconShape" -> iconShape = value as String
            "folderPreview" -> folderPreview = value as String
            "folderBg" -> folderBg = value as String
            "folderCols" -> folderCols = value as Int
            "pageIndicator" -> pageIndicator = value as String
            "searchBar" -> searchBar = value as String
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
