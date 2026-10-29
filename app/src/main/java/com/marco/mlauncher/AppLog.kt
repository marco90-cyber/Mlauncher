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

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "app_log.txt")
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
