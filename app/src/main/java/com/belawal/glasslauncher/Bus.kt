package com.belawal.glasslauncher

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

data class NotifItem(
    val key: String,
    val pkg: String,
    val title: String,
    val text: String,
    val time: Long,
    val media: Boolean,
    val ongoing: Boolean
)

/** নোটিফিকেশন লিসেনার ↔ আইল্যান্ড ↔ লক স্ক্রিনের মধ্যে যোগাযোগ */
object Bus {
    private val list = ArrayList<NotifItem>()
    private val main = Handler(Looper.getMainLooper())
    val observers = CopyOnWriteArrayList<Runnable>()

    @Volatile
    var islandCb: ((NotifItem) -> Unit)? = null

    fun snapshot(): List<NotifItem> {
        synchronized(list) {
            return list.filter { !it.ongoing }.sortedByDescending { it.time }
        }
    }

    fun clear() {
        synchronized(list) { list.clear() }
        notifyObservers()
    }

    fun post(n: NotifItem, announce: Boolean) {
        var changed = true
        synchronized(list) {
            val idx = list.indexOfFirst { it.key == n.key }
            if (idx >= 0) {
                val o = list[idx]
                if (o.title == n.title && o.text == n.text) changed = false
                list[idx] = n
            } else {
                list.add(n)
            }
        }
        main.post {
            if (changed && announce && (!n.ongoing || n.media)) islandCb?.invoke(n)
            for (r in observers) r.run()
        }
    }

    fun remove(key: String) {
        synchronized(list) { list.removeAll { it.key == key } }
        notifyObservers()
    }

    private fun notifyObservers() {
        main.post { for (r in observers) r.run() }
    }
}
