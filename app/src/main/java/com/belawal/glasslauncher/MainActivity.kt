package com.belawal.glasslauncher

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** সেটিংস ও অনুমতি স্ক্রিন */
class MainActivity : Activity() {

    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var box: LinearLayout
    private val checks = ArrayList<Pair<TextView, () -> Boolean>>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Color.rgb(14, 18, 32)
        window.navigationBarColor = Color.rgb(14, 18, 32)

        val sv = ScrollView(this)
        sv.setBackgroundColor(Color.rgb(14, 18, 32))
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(18), dp(28), dp(18), dp(28))
        sv.addView(box, ViewGroup.LayoutParams(MP, WC))
        setContentView(sv)

        text("Glass Launcher", 28f, Color.WHITE, true)
        text("iPhone-স্টাইল হোম স্ক্রিন, লক স্ক্রিন ও Dynamic Island", 14f, Color.argb(190, 255, 255, 255), false)

        section("আগে এই ধাপগুলো সেরে নিন")
        step(
            "১. ডিফল্ট লঞ্চার করুন",
            "খুললে লিস্ট আসবে, সেখান থেকে Glass Launcher বাছুন।",
            { isDefaultLauncher() }
        ) {
            try {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            } catch (e: Throwable) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
        step(
            "২. অন্য অ্যাপের উপরে দেখানোর অনুমতি",
            "Dynamic Island ও লক স্ক্রিনের জন্য লাগবে।",
            { Settings.canDrawOverlays(this) }
        ) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        step(
            "৩. নোটিফিকেশন অ্যাক্সেস",
            "লক স্ক্রিনে ও Island-এ নোটিফিকেশন দেখানোর জন্য।",
            { hasListener() }
        ) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        step(
            "৪. ব্যাটারি সীমা তুলে দিন",
            "না দিলে Vivo ব্যাকগ্রাউন্ড সার্ভিস বন্ধ করে দেবে।",
            { isIgnoringBattery() }
        ) {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                )
            } catch (e: Throwable) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        step(
            "৫. Vivo: অটো-স্টার্ট ও ব্যাকগ্রাউন্ড পপ-আপ",
            "অ্যাপ তথ্য খুলবে। সেখানে ব্যাটারি → \"No restriction\", আর পারমিশনে \"ব্যাকগ্রাউন্ডে পপ-আপ\" ও অটো-স্টার্ট চালু করুন। না করলে লক স্ক্রিন আসবে না।",
            { true }
        ) {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            i.data = Uri.parse("package:$packageName")
            startActivity(i)
        }

        section("ফিচার চালু/বন্ধ")
        toggle("Dynamic Island", "island_on", Prefs.islandOn(this))
        toggle("Island সবসময় দেখাও (বন্ধ থাকলে শুধু ইভেন্টে আসবে)", "island_always", Prefs.islandAlways(this))
        toggle("কাস্টম লক স্ক্রিন", "lock_on", Prefs.lockOn(this))

        section("অন্যান্য")
        button("লক স্ক্রিন টেস্ট করুন") {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "আগে ধাপ ২ করুন", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, LockActivity::class.java))
            }
        }
        button("ওয়ালপেপার বদলান") {
            val i = Intent(Intent.ACTION_GET_CONTENT)
            i.type = "image/*"
            startActivityForResult(Intent.createChooser(i, "ওয়ালপেপার বাছুন"), 78)
        }
        button("সার্ভিস রিস্টার্ট করুন") {
            CoreService.start(this)
            Toast.makeText(this, "রিস্টার্ট হয়েছে", Toast.LENGTH_SHORT).show()
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }
        try {
            CoreService.start(this)
        } catch (e: Throwable) {
        }
    }

    override fun onResume() {
        super.onResume()
        for (p in checks) {
            val ok = p.second()
            p.first.text = if (ok) "✅" else "⚠️"
        }
        try {
            CoreService.start(this)
        } catch (e: Throwable) {
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        super.onActivityResult(rc, res, data)
        val u = data?.data
        if (rc == 78 && res == RESULT_OK && u != null) {
            val ok = Wall.importUri(this, u)
            Toast.makeText(this, if (ok) "ওয়ালপেপার বদলেছে" else "ছবি নেওয়া যায়নি", Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------ checks

    private fun isDefaultLauncher(): Boolean {
        val i = Intent(Intent.ACTION_MAIN)
        i.addCategory(Intent.CATEGORY_HOME)
        val ri = packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        return ri?.activityInfo?.packageName == packageName
    }

    private fun hasListener(): Boolean {
        val s = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return s.contains(packageName)
    }

    private fun isIgnoringBattery(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    // ------------------------------------------------------------ UI helpers

    private fun text(t: String, size: Float, color: Int, bold: Boolean): TextView {
        val tv = TextView(this)
        tv.text = t
        tv.textSize = size
        tv.setTextColor(color)
        if (bold) tv.typeface = Typeface.DEFAULT_BOLD
        val lp = LinearLayout.LayoutParams(MP, WC)
        lp.bottomMargin = dp(6)
        box.addView(tv, lp)
        return tv
    }

    private fun section(t: String) {
        val tv = TextView(this)
        tv.text = t
        tv.textSize = 16f
        tv.setTextColor(Color.rgb(245, 174, 85))
        tv.typeface = Typeface.DEFAULT_BOLD
        val lp = LinearLayout.LayoutParams(MP, WC)
        lp.topMargin = dp(22)
        lp.bottomMargin = dp(8)
        box.addView(tv, lp)
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(14), dp(12), dp(14), dp(12))
        val g = GradientDrawable()
        g.setColor(Color.rgb(27, 33, 64))
        g.cornerRadius = dpf(18f)
        c.background = g
        val lp = LinearLayout.LayoutParams(MP, WC)
        lp.bottomMargin = dp(10)
        box.addView(c, lp)
        return c
    }

    private fun smallButton(t: String, onClick: () -> Unit): TextView {
        val b = TextView(this)
        b.text = t
        b.textSize = 14f
        b.setTextColor(Color.WHITE)
        b.typeface = Typeface.DEFAULT_BOLD
        b.gravity = Gravity.CENTER
        b.setPadding(dp(16), dp(10), dp(16), dp(10))
        val g = GradientDrawable()
        g.setColor(Color.rgb(64, 96, 220))
        g.cornerRadius = dpf(14f)
        b.background = g
        b.setOnClickListener { onClick() }
        return b
    }

    private fun step(title: String, desc: String, check: () -> Boolean, action: () -> Unit) {
        val c = card()
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val st = TextView(this)
        st.textSize = 18f
        st.text = "⚠️"
        row.addView(st, LinearLayout.LayoutParams(WC, WC))
        val tv = TextView(this)
        tv.text = title
        tv.textSize = 15f
        tv.setTextColor(Color.WHITE)
        tv.typeface = Typeface.DEFAULT_BOLD
        val tlp = LinearLayout.LayoutParams(0, WC, 1f)
        tlp.leftMargin = dp(10)
        row.addView(tv, tlp)
        c.addView(row, LinearLayout.LayoutParams(MP, WC))

        val d = TextView(this)
        d.text = desc
        d.textSize = 13f
        d.setTextColor(Color.argb(190, 255, 255, 255))
        val dlp = LinearLayout.LayoutParams(MP, WC)
        dlp.topMargin = dp(6)
        dlp.bottomMargin = dp(10)
        c.addView(d, dlp)

        c.addView(smallButton("খুলুন") {
            try {
                action()
            } catch (e: Throwable) {
                Toast.makeText(this, "সেটিংস খোলা যায়নি", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(MP, WC))
        checks.add(Pair(st, check))
    }

    private fun toggle(label: String, key: String, initial: Boolean) {
        val c = card()
        val sw = Switch(this)
        sw.text = label
        sw.textSize = 14f
        sw.setTextColor(Color.WHITE)
        sw.isChecked = initial
        sw.setOnCheckedChangeListener { _, v ->
            Prefs.set(this, key, v)
            CoreService.start(this)
        }
        c.addView(sw, LinearLayout.LayoutParams(MP, WC))
    }

    private fun button(t: String, onClick: () -> Unit) {
        val b = smallButton(t, onClick)
        val lp = LinearLayout.LayoutParams(MP, WC)
        lp.bottomMargin = dp(10)
        box.addView(b, lp)
    }
}
