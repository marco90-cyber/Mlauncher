package com.marco.mlauncher

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Misure di velocità per il registro: avvio della Home, ritorno alla Home, caricamento icone, widget e
 * scatti nelle animazioni (cartelle, cassetto, pagine). Scrive solo quando qualcosa è lento o scatta,
 * più un riepilogo al giorno con le medie.
 */
object Perf {
    private val lock = Any()
    private var sp: android.content.SharedPreferences? = null
    private val dayFmt = SimpleDateFormat("dd/MM", Locale.ITALY)

    // ---------- tempi (avvio, ritorno, icone, widget) ----------

    private class Timing(var n: Int = 0, var sum: Long = 0, var max: Long = 0)
    private class Scene(var n: Int = 0, var withJank: Int = 0, var frames: Int = 0, var jank: Int = 0)

    private val timings = LinkedHashMap<String, Timing>()
    private val scenes = LinkedHashMap<String, Scene>()
    private var day = ""
    /** versione di MLauncher a cui si riferiscono le statistiche (si riparte da zero a ogni aggiornamento) */
    private var ver = ""
    private var curVer = ""

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("mlauncher_perf", Context.MODE_PRIVATE)
        curVer = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
        load()
        rollDay()
    }

    /** Un tempo misurato: entra nel riepilogo; se supera [slowMs] viene scritto subito. */
    fun time(name: String, ms: Long, slowMs: Long = Long.MAX_VALUE, detail: String = "") {
        synchronized(lock) {
            rollDay()
            val t = timings.getOrPut(name) { Timing() }
            t.n++
            t.sum += ms
            if (ms > t.max) t.max = ms
        }
        if (ms >= slowMs) AppLog.i("Lento — $name: $ms ms" + if (detail.isNotEmpty()) " ($detail)" else "")
    }

    // ---------- avvio e ritorno alla Home ----------

    private var coldDone = false
    private var resumeAt = 0L

    /** La Home è comparsa con le icone per la prima volta in questo processo. */
    fun homeReady() {
        if (coldDone) return
        coldDone = true
        val ms = SystemClock.uptimeMillis() - android.os.Process.getStartUptimeMillis()
        // processo avviato da tempo (es. servizio in background): non è un avvio vero
        if (ms > 60_000) return
        AppLog.i("Avvio della Home: pronta in $ms ms")
        time("Avvio della Home (processo chiuso)", ms)
    }

    fun onResume(activity: Activity) {
        resumeAt = SystemClock.uptimeMillis()
        SlowSpot.watch(3000, "ritorno alla Home")
        val start = resumeAt
        try {
            activity.window.decorView.viewTreeObserver.registerFrameCommitCallback {
                if (!coldDone) return@registerFrameCommitCallback
                val ms = SystemClock.uptimeMillis() - start
                time("Ritorno alla Home", ms, slowMs = 400)
            }
        } catch (e: Exception) {
            // non disponibile
        }
    }

    fun onPause() {
        synchronized(lock) { flushScene() }
        save()
    }

    // ---------- scatti nelle animazioni ----------

    private var scene: String? = null
    private var sceneUntil = 0L
    private var frames = 0
    private var jank = 0
    private var worst = 0L

    /** Osserva i fotogrammi per [ms] millisecondi (animazione di durata nota: cartella, cassetto…). */
    fun watch(name: String, ms: Long = 700) = synchronized(lock) {
        SlowSpot.watch(ms + 300, name)
        flushScene()
        scene = name
        sceneUntil = SystemClock.uptimeMillis() + ms
    }

    /** Osserva i fotogrammi finché non si chiama [end] (es. scorrimento tra le pagine). */
    fun begin(name: String) = synchronized(lock) {
        SlowSpot.watch(1000, name)
        flushScene()
        scene = name
        sceneUntil = Long.MAX_VALUE
    }

    fun end(name: String) = synchronized(lock) { if (scene == name) flushScene() }

    private fun onFrame(totalNs: Long, deadlineNs: Long) = synchronized(lock) {
        val s = scene ?: return@synchronized
        if (SystemClock.uptimeMillis() > sceneUntil) {
            flushScene()
            return@synchronized
        }
        frames++
        if (deadlineNs > 0 && totalNs > deadlineNs) jank++
        val ms = totalNs / 1_000_000
        if (ms > worst) worst = ms
    }

    private fun flushScene() {
        val s = scene ?: return
        scene = null
        if (frames > 0) {
            if (jank > 0) AppLog.i("Scatti — $s: $jank fotogrammi persi su $frames (il più lento $worst ms)")
            rollDay()
            val sc = scenes.getOrPut(s) { Scene() }
            sc.n++
            if (jank > 0) sc.withJank++
            sc.frames += frames
            sc.jank += jank
        }
        frames = 0
        jank = 0
        worst = 0
    }

    private var metricsThread: HandlerThread? = null

    /** Ascolta la durata di ogni fotogramma della Home (costa pochissimo). */
    fun attach(window: Window) {
        try {
            val th = metricsThread ?: HandlerThread("MLauncher-frames").also { it.start(); metricsThread = it }
            window.addOnFrameMetricsAvailableListener({ _, m, _ ->
                onFrame(m.getMetric(FrameMetrics.TOTAL_DURATION), m.getMetric(FrameMetrics.DEADLINE))
            }, Handler(th.looper))
        } catch (e: Exception) {
            AppLog.e("Misura dei fotogrammi non disponibile", e)
        }
    }

    // ---------- riepilogo giornaliero ----------

    /** Nuovo giorno o nuova versione: scrivo il riepilogo di prima e riparto, così i confronti sono puliti. */
    private fun rollDay() {
        val today = dayFmt.format(Date())
        if (day == today && ver == curVer) return
        if (day.isNotEmpty() && (timings.isNotEmpty() || scenes.isNotEmpty())) AppLog.i(summary())
        timings.clear()
        scenes.clear()
        day = today
        ver = curVer
    }

    private fun summary(): String {
        val sb = StringBuilder("Riepilogo velocità del $day" + (if (ver.isNotEmpty()) " (versione $ver)" else "") + ":")
        for ((k, t) in timings) {
            if (t.n == 0) continue
            sb.append("\n  • $k: ${t.n} volte, media ${(t.sum.toDouble() / t.n).roundToInt()} ms, peggiore ${t.max} ms")
        }
        for ((k, s) in scenes) {
            if (s.n == 0) continue
            val pct = if (s.frames > 0) (s.jank * 100.0 / s.frames) else 0.0
            sb.append("\n  • $k: ${s.n} volte, ${s.withJank} con scatti (${s.jank} fotogrammi persi su ${s.frames}, ${"%.1f".format(Locale.ITALY, pct)}%)")
        }
        return sb.toString()
    }

    /** Riepilogo di oggi fino ad ora (per il registro copiato). */
    fun todaySummary(): String = synchronized(lock) { summary() }

    private fun save() {
        val p = sp ?: return
        try {
            val o = JSONObject().put("day", day).put("ver", ver)
            val ti = JSONObject()
            synchronized(lock) {
                timings.forEach { (k, t) -> ti.put(k, JSONObject().put("n", t.n).put("s", t.sum).put("m", t.max)) }
                val sc = JSONObject()
                scenes.forEach { (k, s) -> sc.put(k, JSONObject().put("n", s.n).put("w", s.withJank).put("f", s.frames).put("j", s.jank)) }
                o.put("t", ti).put("sc", sc)
            }
            p.edit().putString("stats", o.toString()).apply()
        } catch (e: Exception) {
            // niente
        }
    }

    private fun load() {
        val str = sp?.getString("stats", null) ?: return
        try {
            val o = JSONObject(str)
            day = o.optString("day", "")
            ver = o.optString("ver", "")
            o.optJSONObject("t")?.let { ti ->
                for (k in ti.keys()) ti.getJSONObject(k).let { timings[k] = Timing(it.optInt("n"), it.optLong("s"), it.optLong("m")) }
            }
            o.optJSONObject("sc")?.let { sc ->
                for (k in sc.keys()) sc.getJSONObject(k).let { scenes[k] = Scene(it.optInt("n"), it.optInt("w"), it.optInt("f"), it.optInt("j")) }
            }
        } catch (e: Exception) {
            // statistiche illeggibili: si riparte
        }
    }
}
