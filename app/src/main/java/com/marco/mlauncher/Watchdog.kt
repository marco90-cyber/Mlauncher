package com.marco.mlauncher

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sorveglia il thread principale mentre la Home è in primo piano: se non risponde per più di 2 secondi
 * (Home bloccata) scrive nel registro cosa sta facendo in quel momento, la memoria, e quanto è durato il blocco.
 */
object MainWatchdog {
    @Volatile private var tick = SystemClock.uptimeMillis()
    @Volatile var active = false
        set(v) {
            field = v
            tick = SystemClock.uptimeMillis()
        }
    private var started = false
    private var app: Context? = null

    fun start(ctx: Context) {
        if (started) return
        started = true
        app = ctx.applicationContext
        val h = Handler(Looper.getMainLooper())
        val ping = Runnable { tick = SystemClock.uptimeMillis() }
        Thread({
            var blockedSince = 0L
            var reports = 0
            while (true) {
                try {
                    if (!active) {
                        blockedSince = 0L
                        Thread.sleep(1000)
                        continue
                    }
                    h.post(ping)
                    Thread.sleep(500)
                } catch (e: InterruptedException) {
                    return@Thread
                }
                val lag = SystemClock.uptimeMillis() - tick
                if (lag > 2000) {
                    if (blockedSince == 0L) {
                        blockedSince = tick
                        reports = 0
                    }
                    // una prima foto subito, poi un'altra se il blocco continua (per vedere se è fermo nello stesso punto)
                    if ((reports == 0) || (reports == 1 && lag > 6000) || (reports == 2 && lag > 15000)) {
                        reports++
                        AppLog.w("HOME BLOCCATA da $lag ms (foto $reports) — il thread principale sta facendo:\n" + mainStack() + "\n" + memInfo())
                    }
                } else if (blockedSince != 0L) {
                    AppLog.w("Home sbloccata: il blocco è durato circa ${tick - blockedSince} ms")
                    blockedSince = 0L
                }
            }
        }, "MLauncher-watchdog").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    private fun mainStack(): String {
        val st = Looper.getMainLooper().thread.stackTrace
        return st.take(40).joinToString("\n") { "    at $it" }
    }

    fun memInfo(): String {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val max = rt.maxMemory() / (1024 * 1024)
        val sys = try {
            val am = app?.getSystemService(ActivityManager::class.java)
            val mi = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(mi)
            " · RAM del telefono libera ${mi.availMem / (1024 * 1024)} MB su ${mi.totalMem / (1024 * 1024)} MB" + if (mi.lowMemory) " (MEMORIA BASSA)" else ""
        } catch (e: Exception) {
            ""
        }
        return "    memoria MLauncher: $used MB usati su $max MB$sys"
    }

    /** Intestazione del registro: telefono, Android, versione e memoria. */
    fun deviceInfo(ctx: Context): String {
        val ver = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { "?" }
        if (app == null) app = ctx.applicationContext
        return "MLauncher $ver · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) · " +
            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" + memInfo().trim()
    }

    /**
     * Android ricorda perché il processo è stato chiuso le ultime volte (blocco ANR, errore, memoria…).
     * Le nuove le scrivo nel registro; per un ANR anche cosa stava facendo il thread principale.
     */
    fun logExitReasons(ctx: Context) {
        try {
            val am = ctx.getSystemService(ActivityManager::class.java) ?: return
            val sp = ctx.getSharedPreferences("mlauncher_log", Context.MODE_PRIVATE)
            val last = sp.getLong("exitTs", 0L)
            val list = am.getHistoricalProcessExitReasons(ctx.packageName, 0, 8)
                .filter { it.timestamp > last }
                .sortedBy { it.timestamp }
            val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.ITALY)
            for (r in list) {
                var msg = "Chiusura precedente del ${fmt.format(Date(r.timestamp))}: ${reasonName(r.reason)}" +
                    (r.description?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: "") +
                    " · memoria ${r.pss / 1024} MB"
                if (r.reason == ApplicationExitInfo.REASON_ANR) {
                    val trace = try { r.traceInputStream?.bufferedReader()?.use { it.readText() } } catch (e: Exception) { null }
                    if (trace != null) msg += "\n" + mainThreadOf(trace)
                }
                val bad = r.reason in setOf(
                    ApplicationExitInfo.REASON_ANR, ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE,
                    ApplicationExitInfo.REASON_LOW_MEMORY, ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE
                )
                if (bad) AppLog.w(msg) else AppLog.i(msg)
            }
            list.maxOfOrNull { it.timestamp }?.let { sp.edit().putLong("exitTs", it).apply() }
        } catch (e: Exception) {
            AppLog.e("Motivi delle chiusure precedenti non leggibili", e)
        }
    }

    private fun mainThreadOf(trace: String): String {
        val i = trace.indexOf("\"main\"")
        if (i < 0) return trace.take(3000)
        val end = trace.indexOf("\n\n", i).let { if (it < 0) trace.length else it }
        return trace.substring(i, end).take(5000)
    }

    private fun reasonName(r: Int): String = when (r) {
        ApplicationExitInfo.REASON_ANR -> "BLOCCO (ANR: l'app non rispondeva)"
        ApplicationExitInfo.REASON_CRASH -> "ERRORE (crash)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "ERRORE NATIVO"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "chiusa dal sistema per memoria bassa"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "chiusa per uso eccessivo di risorse"
        ApplicationExitInfo.REASON_EXIT_SELF -> "chiusa da sé"
        ApplicationExitInfo.REASON_SIGNALED -> "chiusa dal sistema (segnale)"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "chiusa dall'utente"
        ApplicationExitInfo.REASON_USER_STOPPED -> "arresto forzato"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permessi cambiati"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "è morto un processo da cui dipendeva"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "avvio non riuscito"
        ApplicationExitInfo.REASON_FREEZER -> "congelata dal sistema"
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "stato del pacchetto cambiato"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "aggiornamento di MLauncher"
        else -> "altro motivo ($r)"
    }
}
