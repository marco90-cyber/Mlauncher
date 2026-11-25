package com.marco.mlauncher

import org.json.JSONObject

/**
 * Il tema personale: una fotografia dell'aspetto (pannelli, forme, colori, carattere, sfondo, icone) che si salva,
 * si riapplica, si esporta in un file e torna con il backup. Non contiene la disposizione della Home.
 */
object MyTheme {
    /** Impostazioni di aspetto che fanno parte del tema (più le scelte dei singoli elementi, «el_…»). */
    val KEYS = listOf(
        "matStyle", "matOpacity", "matGlass", "matCorner", "iconShape", "themedIcons", "themedIconColor",
        "switchStyle", "labelPills", "labelColor", "liquid",
        "dockForm", "dockStyle", "dockOpacity", "dockGlass", "dockCorner",
        "folderBg", "folderOpacity", "folderShape", "folderBlur", "folderFollowDock",
        "indFollowDock", "pageIndicator", "glassIconStyle", "glassIconHighlight", "glassHighlight",
        "minusStyle", "minusBlur", "minusDim", "searchStyle", "searchBlur", "searchDim", "searchLook",
        "drBg", "drOpacity", "drBgHue", "drBgSat", "drBgVal", "drSearchBg",
        "accent", "fontKey", "wallMode", "wallDim", "wallBlur", "wallParallax", "iconPack"
    )

    private val EL_PARTS = listOf("follow", "style", "opacity", "glass", "corner")

    private fun validKey(k: String): Boolean {
        if (k in KEYS) return true
        val p = k.split("_")
        return p.size == 3 && p[0] == "el" && MAT_ELEMENTS.any { it.first == p[1] } && p[2] in EL_PARTS
    }

    private fun entry(v: Any): JSONObject? = when (v) {
        is Int -> JSONObject().put("t", "i").put("v", v)
        is Boolean -> JSONObject().put("t", "b").put("v", v)
        is String -> JSONObject().put("t", "s").put("v", v)
        else -> null
    }

    /** Valore attuale: dalla proprietà (anche se mai scritta, quindi di default) o, in mancanza, da quello salvato. */
    private fun read(s: LauncherSettings, dump: JSONObject, key: String): Any? {
        for (n in listOf(key, key + "P")) {
            val g = "get" + n.replaceFirstChar { it.uppercase() }
            try {
                val m = s.javaClass.declaredMethods.firstOrNull { it.name == g && it.parameterCount == 0 } ?: continue
                m.isAccessible = true
                val v = m.invoke(s)
                if (v is Int || v is Boolean || v is String) return v
            } catch (e: Exception) {
                AppLog.quiet("lettura di $key per il tema", e)
            }
        }
        return dump.optJSONObject(key)?.opt("v")
    }

    /** L'aspetto di adesso. */
    fun snapshot(s: LauncherSettings): JSONObject {
        val dump = s.dump()
        val o = JSONObject()
        for (k in KEYS) read(s, dump, k)?.let { v -> entry(v)?.let { o.put(k, it) } }
        for ((e, _) in MAT_ELEMENTS) {
            o.put("el_${e}_follow", entry(s.elFollow(e)))
            o.put("el_${e}_style", entry(s.elStyle(e)))
            o.put("el_${e}_opacity", entry(s.elOpacity(e)))
            o.put("el_${e}_glass", entry(s.elGlass(e)))
            o.put("el_${e}_corner", entry(s.elCorner(e)))
        }
        return o
    }

    /** Tiene solo ciò che è un'impostazione d'aspetto valida: un file strano non può toccare altro. Null se non resta niente. */
    fun sanitize(src: JSONObject): JSONObject? {
        val out = JSONObject()
        for (k in src.keys()) {
            if (!validKey(k)) continue
            val e = src.optJSONObject(k) ?: continue
            val v: Any = when (e.optString("t")) {
                "i" -> if (e.has("v")) e.optInt("v").coerceIn(0, 1000) else continue
                "b" -> if (e.has("v")) e.optBoolean("v") else continue
                "s" -> e.optString("v").takeIf { it.length <= 120 } ?: continue
                else -> continue
            }
            if (k == "fontKey" && v != "default" && v != "custom" && APP_FONTS.none { it.key == v }) continue
            if (k == "wallMode" && v != "system" && v != "custom") continue
            entry(v)?.let { out.put(k, it) }
        }
        return if (out.length() == 0) null else out
    }
}
