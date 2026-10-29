package com.belawal.glasslauncher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotifListener : NotificationListenerService() {

    private fun toItem(sbn: StatusBarNotification): NotifItem? {
        if (sbn.packageName == packageName) return null
        val n = sbn.notification ?: return null
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val ex = n.extras
        val title = ex?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = ex?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (title.isEmpty() && text.isEmpty()) return null
        val media = n.category == Notification.CATEGORY_TRANSPORT
        val ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0
        return NotifItem(sbn.key, sbn.packageName, title, text, sbn.postTime, media, ongoing)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Bus.clear()
        val arr = try {
            activeNotifications
        } catch (e: Exception) {
            null
        }
        if (arr != null) {
            for (s in arr) {
                val item = toItem(s)
                if (item != null) Bus.post(item, false)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val item = toItem(sbn) ?: return
        Bus.post(item, true)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Bus.remove(sbn.key)
    }
}
