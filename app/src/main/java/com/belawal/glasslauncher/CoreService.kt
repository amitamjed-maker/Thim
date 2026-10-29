package com.belawal.glasslauncher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager

/** ব্যাকগ্রাউন্ড সার্ভিস: Dynamic Island ওভারলে চালায় + স্ক্রিন অফ হলে লক স্ক্রিন প্রস্তুত করে */
class CoreService : Service() {

    companion object {
        const val CH = "core"

        fun start(c: Context) {
            val i = Intent(c, CoreService::class.java)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }
    }

    private var wm: WindowManager? = null
    private var island: IslandView? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> openLock(c)
                Intent.ACTION_POWER_CONNECTED -> {
                    val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
                    val lv = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    island?.showEvent(null, Color.rgb(48, 209, 88), "চার্জ হচ্ছে", "ব্যাটারি $lv%")
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH, "Glass Launcher", NotificationManager.IMPORTANCE_MIN)
        )
        val n = Notification.Builder(this, CH)
            .setContentTitle("Glass Launcher চালু আছে")
            .setContentText("Dynamic Island ও লক স্ক্রিন সক্রিয়")
            .setSmallIcon(R.drawable.ic_stat)
            .setOngoing(true)
            .build()
        startForeground(1, n)

        val f = IntentFilter()
        f.addAction(Intent.ACTION_SCREEN_OFF)
        f.addAction(Intent.ACTION_POWER_CONNECTED)
        registerReceiver(receiver, f)

        Bus.islandCb = { item -> island?.showNotif(item) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        applyPrefs()
        return START_STICKY
    }

    private fun applyPrefs() {
        val can = Settings.canDrawOverlays(this)
        if (can && Prefs.islandOn(this)) {
            if (island == null) addIsland()
            island?.setAlways(Prefs.islandAlways(this))
        } else {
            removeIsland()
        }
    }

    private fun addIsland() {
        try {
            val v = IslandView(this)
            val lp = WindowManager.LayoutParams(
                dp(360), dp(100),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            if (Build.VERSION.SDK_INT >= 28) {
                lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            wm?.addView(v, lp)
            island = v
        } catch (e: Throwable) {
            island = null
        }
    }

    private fun removeIsland() {
        try {
            island?.let { wm?.removeView(it) }
        } catch (e: Throwable) {
        }
        island = null
    }

    private fun openLock(c: Context) {
        if (!Prefs.lockOn(c) || !Settings.canDrawOverlays(c)) return
        if (LockActivity.showing) return
        try {
            val i = Intent(c, LockActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            c.startActivity(i)
        } catch (e: Throwable) {
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(receiver)
        } catch (e: Throwable) {
        }
        Bus.islandCb = null
        removeIsland()
        super.onDestroy()
    }
}
