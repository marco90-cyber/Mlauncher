package com.marco.mlauncher

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Registra gli errori che chiudono l'app, così al riavvio si possono leggere e inviare. */
class MLApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppLog.i("Avvio del processo")
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val ver = try {
                    packageManager.getPackageInfo(packageName, 0).versionName
                } catch (x: Exception) {
                    "?"
                }
                val head = "MLauncher $ver · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MODEL}\n" +
                    SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ITALY).format(Date()) + " · thread ${t.name}\n\n"
                val f = crashFile(this)
                // tengo solo l'ultimo errore (più eventuali ripetizioni ravvicinate)
                val old = if (f.exists() && System.currentTimeMillis() - f.lastModified() < 60_000) f.readText().take(20_000) + "\n\n----\n\n" else ""
                f.writeText(old + head + sw.toString().take(30_000))
                AppLog.writeNow("E", "CHIUSURA PER ERRORE (thread ${t.name})\n" + sw.toString().take(8000))
            } catch (x: Throwable) {
                // niente
            }
            prev?.uncaughtException(t, e)
        }
    }

    companion object {
        fun crashFile(app: android.content.Context) = File(app.filesDir, "last_crash.txt")
    }
}
