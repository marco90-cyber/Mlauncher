package com.marco.mlauncher

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap

enum class Overlay { NONE, DRAWER, SETTINGS }

class AppEntry(
    val pkg: String,
    val cls: String,
    val label: String,
    val icon: ImageBitmap,
    val mono: ImageBitmap?,
    val removable: Boolean
) {
    val id: String get() = "$pkg/$cls"
}

sealed class HomeItem {
    abstract val id: String
    abstract val page: Int // -1 = dock
    abstract val x: Int
    abstract val y: Int
    abstract fun at(page: Int, x: Int, y: Int): HomeItem
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
    override val y: Int
) : HomeItem() {
    override val id: String get() = "folder:$folderId"
    override fun at(page: Int, x: Int, y: Int): HomeItem = copy(page = page, x = x, y = y)
}

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
        }
    }
}
