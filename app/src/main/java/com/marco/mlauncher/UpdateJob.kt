package com.marco.mlauncher

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Lettura dell'ultima release su GitHub, condivisa dal controllo manuale e da quello in background. */
object UpdateFetcher {
    fun fetch(): UpdateInfo? = try {
        val conn = URL("https://api.github.com/repos/marco90-cyber/Mlauncher/releases/latest")
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val tag = j.getString("tag_name").removePrefix("v")
        val code = tag.substringAfterLast('.').toIntOrNull() ?: 0
        val assets = j.optJSONArray("assets")
        var url = ""
        if (assets != null) for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) url = a.optString("browser_download_url")
        }
        UpdateInfo(tag, code, url, j.optString("html_url"))
    } catch (e: Exception) {
        null
    }

    /** Ricorda l'ultima versione trovata e quando: la Home la legge senza collegarsi di nuovo. */
    fun save(ctx: Context, res: UpdateInfo) {
        ctx.getSharedPreferences("mlauncher_state", Context.MODE_PRIVATE).edit()
            .putLong("updateCheckTime", System.currentTimeMillis())
            .putString("updateCache", JSONObject().put("v", res.version).put("c", res.code).put("u", res.url).put("p", res.page).toString())
            .apply()
    }
}

/**
 * Controllo aggiornamenti in background: una volta al giorno, solo con la rete, anche a Home aperta da giorni
 * (il controllo all'avvio parte solo quando il launcher viene creato, cioè dopo un riavvio).
 */
class UpdateJob : JobService() {
    @Volatile private var worker: Thread? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val enabled = getSharedPreferences("mlauncher_settings", Context.MODE_PRIVATE).getBoolean("autoUpdateCheck", true)
        if (!enabled) return false
        worker = Thread {
            val res = UpdateFetcher.fetch()
            if (res != null) UpdateFetcher.save(applicationContext, res)
            AppLog.i(if (res == null) "Controllo in background non riuscito" else "Controllo in background: ultima versione ${res.version}")
            // senza rete o con errore si riprova con la regola del sistema
            jobFinished(params, res == null)
        }.also { it.start() }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val ID = 4721

        /** Attiva o spegne il controllo giornaliero; da richiamare a ogni avvio (il sistema può azzerarlo dopo un aggiornamento). */
        fun schedule(ctx: Context, enabled: Boolean) {
            try {
                val js = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                if (!enabled) {
                    js.cancel(ID)
                    return
                }
                if (js.getPendingJob(ID) != null) return
                js.schedule(
                    JobInfo.Builder(ID, ComponentName(ctx, UpdateJob::class.java))
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPeriodic(24L * 3600_000L)
                        .build()
                )
            } catch (e: Exception) {
                AppLog.quiet("pianificazione del controllo aggiornamenti", e)
            }
        }
    }
}
