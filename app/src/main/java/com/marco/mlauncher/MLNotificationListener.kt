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
            for (n in activeNotifications ?: emptyArray()) {
                if (n.isOngoing) continue
                if ((n.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0) continue
                map[n.packageName] = (map[n.packageName] ?: 0) + 1
            }
        } catch (e: Exception) {
            // servizio non pronto
        }
        android.os.Handler(mainLooper).post {
            BadgeStore.counts.keys.toList().filter { it !in map }.forEach { BadgeStore.counts.remove(it) }
            for ((k, v) in map) BadgeStore.counts[k] = v
        }
    }
}
