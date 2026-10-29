package com.belawal.glasslauncher

import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** iPhone-স্টাইল লক স্ক্রিন */
class LockActivity : Activity() {

    companion object {
        @Volatile
        var showing = false
    }

    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var content: LinearLayout
    private lateinit var clock: GlassClockView
    private lateinit var dateTv: TextView
    private lateinit var notifBox: LinearLayout
    private lateinit var wBattery: WidgetCircle
    private lateinit var wNotif: WidgetCircle
    private lateinit var wDay: WidgetCircle
    private lateinit var torchBtn: WidgetCircle

    private var torchOn = false
    private var downY = 0f
    private var dragging = false
    private var vt: VelocityTracker? = null

    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            refresh()
        }
    }
    private val busObs = Runnable { refresh() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        buildUi()

        registerReceiver(tick, IntentFilter(Intent.ACTION_TIME_TICK))
        Bus.observers.add(busObs)
    }

    private fun buildUi() {
        val root = FrameLayout(this)
        root.addView(WallView(this), FrameLayout.LayoutParams(MP, MP))

        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(0, sysDim("status_bar_height") + dp(40), 0, dp(22))

        dateTv = TextView(this)
        dateTv.setTextColor(Color.argb(225, 255, 255, 255))
        dateTv.textSize = 20f
        dateTv.typeface = Typeface.DEFAULT_BOLD
        dateTv.gravity = Gravity.CENTER
        content.addView(dateTv, LinearLayout.LayoutParams(MP, WC))

        clock = GlassClockView(this)
        content.addView(clock, LinearLayout.LayoutParams(MP, WC))

        notifBox = LinearLayout(this)
        notifBox.orientation = LinearLayout.VERTICAL
        notifBox.setPadding(dp(20), 0, dp(20), 0)
        val nlp = LinearLayout.LayoutParams(MP, WC)
        nlp.topMargin = dp(10)
        content.addView(notifBox, nlp)

        content.addView(View(this), LinearLayout.LayoutParams(MP, 0, 1f))

        // ৪টা উইজেট
        val wRow = LinearLayout(this)
        wRow.orientation = LinearLayout.HORIZONTAL
        wRow.gravity = Gravity.CENTER
        wBattery = WidgetCircle(this, WidgetCircle.BATTERY)
        wNotif = WidgetCircle(this, WidgetCircle.NOTIF)
        val wMusic = WidgetCircle(this, WidgetCircle.MUSIC)
        wDay = WidgetCircle(this, WidgetCircle.DAY)
        for (w in listOf(wBattery, wNotif, wMusic, wDay)) {
            val lp = LinearLayout.LayoutParams(dp(62), dp(62))
            lp.setMargins(dp(7), 0, dp(7), 0)
            wRow.addView(w, lp)
        }
        wMusic.setOnClickListener {
            try {
                val i = Intent(Intent.ACTION_MAIN)
                i.addCategory(Intent.CATEGORY_APP_MUSIC)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                finish()
            } catch (e: Throwable) {
            }
        }
        content.addView(wRow, LinearLayout.LayoutParams(MP, WC))

        // টর্চ ও ক্যামেরা
        val bRow = FrameLayout(this)
        torchBtn = WidgetCircle(this, WidgetCircle.TORCH)
        val camBtn = WidgetCircle(this, WidgetCircle.CAMERA)
        val tl = FrameLayout.LayoutParams(dp(60), dp(60), Gravity.LEFT or Gravity.CENTER_VERTICAL)
        tl.leftMargin = dp(44)
        val cl = FrameLayout.LayoutParams(dp(60), dp(60), Gravity.RIGHT or Gravity.CENTER_VERTICAL)
        cl.rightMargin = dp(44)
        bRow.addView(torchBtn, tl)
        bRow.addView(camBtn, cl)
        torchBtn.setOnClickListener { toggleTorch() }
        camBtn.setOnClickListener { openCamera() }
        val blp = LinearLayout.LayoutParams(MP, dp(70))
        blp.topMargin = dp(14)
        content.addView(bRow, blp)

        // নিচের সাদা বার
        val bar = View(this)
        val g = GradientDrawable()
        g.setColor(Color.argb(235, 255, 255, 255))
        g.cornerRadius = dpf(3f)
        bar.background = g
        val barLp = LinearLayout.LayoutParams(dp(134), dp(5))
        barLp.gravity = Gravity.CENTER_HORIZONTAL
        barLp.topMargin = dp(16)
        content.addView(bar, barLp)

        root.addView(content, FrameLayout.LayoutParams(MP, MP))
        setContentView(root)
    }

    private fun batteryLevel(): Int {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lv = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val sc = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (sc > 0) lv * 100 / sc else lv
    }

    private fun iconOf(pkg: String): Drawable? {
        return try {
            packageManager.getApplicationIcon(pkg)
        } catch (e: Exception) {
            null
        }
    }

    private fun refresh() {
        val now = Date()
        dateTv.text = SimpleDateFormat("EEE MMM d", Locale.ENGLISH).format(now)
        clock.setTime(SimpleDateFormat("h:mm", Locale.ENGLISH).format(now))
        val list = Bus.snapshot()
        wBattery.value = batteryLevel()
        wNotif.value = list.size
        wDay.invalidate()

        notifBox.removeAllViews()
        for (n in list.take(3)) {
            val gv = GlassView(this)
            gv.radiusPx = dpf(22f)
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(12), dp(10), dp(12), dp(10))
            row.addView(IconView(this, iconOf(n.pkg)), LinearLayout.LayoutParams(dp(36), dp(36)))
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val t = TextView(this)
            t.text = if (n.title.isEmpty()) n.pkg else n.title
            t.setTextColor(Color.WHITE)
            t.textSize = 14f
            t.typeface = Typeface.DEFAULT_BOLD
            t.maxLines = 1
            t.ellipsize = TextUtils.TruncateAt.END
            val s = TextView(this)
            s.text = n.text
            s.setTextColor(Color.argb(205, 255, 255, 255))
            s.textSize = 13f
            s.maxLines = 1
            s.ellipsize = TextUtils.TruncateAt.END
            col.addView(t)
            col.addView(s)
            val clp = LinearLayout.LayoutParams(0, WC, 1f)
            clp.leftMargin = dp(12)
            row.addView(col, clp)
            gv.addView(row, FrameLayout.LayoutParams(MP, WC))
            val lp = LinearLayout.LayoutParams(MP, WC)
            lp.bottomMargin = dp(8)
            notifBox.addView(gv, lp)
        }
    }

    private fun toggleTorch() {
        try {
            val cm = getSystemService(CAMERA_SERVICE) as CameraManager
            var id: String? = null
            for (cid in cm.cameraIdList) {
                val f = cm.getCameraCharacteristics(cid).get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                if (f == true) {
                    id = cid
                    break
                }
            }
            if (id == null) return
            torchOn = !torchOn
            cm.setTorchMode(id, torchOn)
            torchBtn.on = torchOn
        } catch (e: Throwable) {
        }
    }

    private fun openCamera() {
        try {
            val i = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
            finish()
        } catch (e: Throwable) {
        }
    }

    private fun resetPos() {
        content.animate().translationY(0f).alpha(1f).setDuration(220).start()
    }

    private fun unlock() {
        val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    finishUnlocked()
                }

                override fun onDismissCancelled() {
                    resetPos()
                }

                override fun onDismissError() {
                    resetPos()
                }
            })
        } else {
            finishUnlocked()
        }
    }

    private fun finishUnlocked() {
        content.animate().translationY(-content.height.toFloat()).alpha(0f).setDuration(180)
            .withEndAction {
                finish()
                overridePendingTransition(0, 0)
            }.start()
    }

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = e.rawY
                dragging = false
                vt?.recycle()
                vt = VelocityTracker.obtain()
                vt?.addMovement(e)
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                val dy = downY - e.rawY
                if (!dragging && dy > dp(16)) {
                    dragging = true
                    val cancel = MotionEvent.obtain(e)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                }
                if (dragging) {
                    val d = maxOf(0f, dy)
                    content.translationY = -d
                    content.alpha = 1f - (d / content.height.toFloat()).coerceIn(0f, 1f) * 0.6f
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    vt?.addMovement(e)
                    vt?.computeCurrentVelocity(1000)
                    val vy = vt?.yVelocity ?: 0f
                    val dy = downY - e.rawY
                    dragging = false
                    vt?.recycle()
                    vt = null
                    if (dy > content.height * 0.2f || vy < -1500f) unlock() else resetPos()
                    return true
                }
                vt?.recycle()
                vt = null
            }
        }
        return super.dispatchTouchEvent(e)
    }

    override fun onResume() {
        super.onResume()
        showing = true
        content.translationY = 0f
        content.alpha = 1f
        refresh()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        content.translationY = 0f
        content.alpha = 1f
        refresh()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // লক স্ক্রিনে ব্যাক বন্ধ
    }

    override fun onDestroy() {
        showing = false
        try {
            unregisterReceiver(tick)
        } catch (e: Throwable) {
        }
        Bus.observers.remove(busObs)
        super.onDestroy()
    }
}
