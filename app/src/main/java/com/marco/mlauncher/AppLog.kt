package com.marco.mlauncher

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Registro degli eventi dell'app (avvii, caricamenti, errori), consultabile dalle impostazioni. */
object AppLog {
    private var file: File? = null
    private val io = Executors.newSingleThreadExecutor()
    private val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.ITALY)
    private const val MAX_BYTES = 256 * 1024L

    /** giorni tenuti nel registro: le righe più vecchie si cancellano da sole */
    const val KEEP_DAYS = 7
    private var prunedDay = -1

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "app_log.txt")
        io.execute { prune() }
    }

    /** Divide il registro in eventi (un evento può avere più righe, es. una traccia d'errore). */
    fun entries(text: String): List<String> =
        text.split(Regex("\n(?=\\d\\d/\\d\\d \\d\\d:)")).filter { it.isNotBlank() }

    /** Data di un evento ("dd/MM ...") come millisecondi, nell'anno giusto. */
    private fun entryTime(e: String, now: java.util.Calendar): Long? {
        val d = e.take(2).toIntOrNull() ?: return null
        val m = e.drop(3).take(2).toIntOrNull() ?: return null
        val c = java.util.Calendar.getInstance()
        c.timeInMillis = now.timeInMillis
        c.set(java.util.Calendar.MONTH, m - 1)
        c.set(java.util.Calendar.DAY_OF_MONTH, d)
        // evento di dicembre letto a gennaio: è dell'anno prima
        if (c.after(now)) c.add(java.util.Calendar.YEAR, -1)
        return c.timeInMillis
    }

    /** Tiene solo gli ultimi [KEEP_DAYS] giorni. */
    @Synchronized
    private fun prune() {
        val f = file ?: return
        try {
            if (!f.exists()) return
            val now = java.util.Calendar.getInstance()
            prunedDay = now.get(java.util.Calendar.DAY_OF_YEAR)
            val limit = now.timeInMillis - KEEP_DAYS * 24L * 3600_000L
            val all = entries(f.readText())
            val keep = all.filter { (entryTime(it, now) ?: Long.MAX_VALUE) >= limit }
            if (keep.size != all.size) f.writeText(keep.joinToString("\n") { it.trimEnd('\n') } + "\n")
        } catch (e: Exception) {
            // registro non disponibile
        }
    }

    fun i(msg: String) = write("I", msg)
    fun w(msg: String) = write("W", msg)
    fun e(msg: String, t: Throwable? = null) =
        write("E", msg + (t?.let { "\n" + android.util.Log.getStackTraceString(it).take(4000) } ?: ""))

    private fun write(level: String, msg: String) {
        val line = "${fmt.format(Date())}  $level  $msg\n"
        android.util.Log.println(if (level == "E") android.util.Log.ERROR else android.util.Log.INFO, "MLauncher", msg)
        io.execute { append(line) }
    }

    /** Scrittura immediata: usata dal gestore dei crash, quando il processo sta per chiudersi. */
    fun writeNow(level: String, msg: String) {
        append("${fmt.format(Date())}  $level  $msg\n")
    }

    @Synchronized
    private fun append(line: String) {
        val f = file ?: return
        // una volta al giorno tolgo i giorni troppo vecchi
        if (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR) != prunedDay) prune()
        try {
            if (f.exists() && f.length() > MAX_BYTES) {
                // tengo l'ultima metà
                val keep = f.readText().takeLast((MAX_BYTES / 2).toInt())
                f.writeText(keep.substringAfter('\n'))
            }
            f.appendText(line)
        } catch (e: Exception) {
            // registro non disponibile
        }
    }

    fun read(): String = try {
        file?.takeIf { it.exists() }?.readText() ?: ""
    } catch (e: Exception) {
        ""
    }

    fun clear() {
        io.execute {
            try { file?.writeText("") } catch (e: Exception) { }
        }
    }
}
