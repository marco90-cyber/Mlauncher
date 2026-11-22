package com.marco.mlauncher

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MLNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        recount()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = recount()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recount()

    private fun recount() {
        val map = HashMap<String, Int>()
        try {
            val ranking = currentRanking
            val tmp = Ranking()
            for (n in activeNotifications ?: emptyArray()) {
                if (n.isOngoing) continue
                if ((n.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0) continue
                // come il launcher di Android (idea da ARK-launcher): niente puntino se il canale lo vieta
                // (notifiche silenziose, es. aggiornamenti di notizie) o se la notifica non ha né titolo né testo
                if (ranking != null && ranking.getRanking(n.key, tmp) && !tmp.canShowBadge()) continue
                val ex = n.notification.extras
                if (ex?.getCharSequence(android.app.Notification.EXTRA_TITLE).isNullOrEmpty() &&
                    ex?.getCharSequence(android.app.Notification.EXTRA_TEXT).isNullOrEmpty()
                ) continue
                map[n.packageName] = (map[n.packageName] ?: 0) + 1
            }
        } catch (e: Exception) {
            AppLog.quiet("lettura delle notifiche", e)
            // servizio non pronto
        }
        android.os.Handler(mainLooper).post {
            BadgeStore.counts.keys.toList().filter { it !in map }.forEach { BadgeStore.counts.remove(it) }
            for ((k, v) in map) BadgeStore.counts[k] = v
        }
    }
}
