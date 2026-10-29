package com.belawal.glasslauncher

import android.app.Activity
import android.app.ActivityOptions
import android.app.AlertDialog
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

class AppInfo(
    val label: String,
    val pkg: String,
    val cls: String,
    val icon: Drawable,
    val group: Int
)

/** Liquid Glass হোম স্ক্রিন */
class LauncherActivity : Activity() {

    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var root: FrameLayout
    private lateinit var pager: ViewPager2
    private lateinit var dotsBox: LinearLayout
    private lateinit var dock: GlassView

    private var apps: List<AppInfo> = emptyList()
    private var pages: List<List<AppInfo>> = emptyList()
    private var statusH = 0
    private var navH = 0
    private var dialog: Dialog? = null

    private val groupNames = listOf("সোশ্যাল", "গেমস", "মিডিয়া", "টুলস ও অন্যান্য")

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            loadApps()
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        statusH = sysDim("status_bar_height")
        navH = sysDim("navigation_bar_height")

        buildUi()

        val f = IntentFilter()
        f.addAction(Intent.ACTION_PACKAGE_ADDED)
        f.addAction(Intent.ACTION_PACKAGE_REMOVED)
        f.addAction(Intent.ACTION_PACKAGE_CHANGED)
        f.addDataScheme("package")
        registerReceiver(pkgReceiver, f)

        loadApps()
        try {
            CoreService.start(this)
        } catch (e: Throwable) {
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(pkgReceiver)
        } catch (e: Throwable) {
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        dialog?.dismiss()
        if (::pager.isInitialized) pager.setCurrentItem(0, true)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        dialog?.dismiss()
        if (::pager.isInitialized) pager.setCurrentItem(0, true)
    }

    // ---------------------------------------------------------------- UI

    private fun dockAreaH(): Int = dp(12 + 90 + 12) + navH + dp(28)

    private fun buildUi() {
        root = FrameLayout(this)
        root.addView(WallView(this), FrameLayout.LayoutParams(MP, MP))

        pager = ViewPager2(this)
        pager.adapter = PageAdapter()
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
            }
        })
        root.addView(pager, FrameLayout.LayoutParams(MP, MP))

        dotsBox = LinearLayout(this)
        dotsBox.orientation = LinearLayout.HORIZONTAL
        dotsBox.gravity = Gravity.CENTER
        val dlp = FrameLayout.LayoutParams(MP, WC, Gravity.BOTTOM)
        dlp.bottomMargin = navH + dp(12 + 90 + 10)
        root.addView(dotsBox, dlp)

        buildDock()

        root.setOnApplyWindowInsetsListener { _, ins ->
            val t = ins.systemWindowInsetTop
            val bt = ins.systemWindowInsetBottom
            if (t != statusH || bt != navH) {
                statusH = t
                navH = bt
                applyInsets()
            }
            ins
        }
        setContentView(root)
    }

    private fun applyInsets() {
        val dl = dock.layoutParams as FrameLayout.LayoutParams
        dl.bottomMargin = navH + dp(12)
        dock.layoutParams = dl
        val bl = dotsBox.layoutParams as FrameLayout.LayoutParams
        bl.bottomMargin = navH + dp(12 + 90 + 10)
        dotsBox.layoutParams = bl
        pager.adapter?.notifyDataSetChanged()
    }

    private fun buildDock() {
        dock = GlassView(this)
        dock.radiusPx = dpf(32f)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL

        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_VIEW, Uri.parse("sms:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        )
        for (intent in intents) {
            val cell = FrameLayout(this)
            val ri = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (ri != null) {
                val ic = IconView(this, ri.loadIcon(packageManager))
                cell.addView(ic, FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER))
                cell.setOnClickListener {
                    try {
                        val i = Intent(intent)
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(i)
                    } catch (e: Throwable) {
                        Toast.makeText(this, "খোলা যাচ্ছে না", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            row.addView(cell, LinearLayout.LayoutParams(0, MP, 1f))
        }
        dock.addView(row, FrameLayout.LayoutParams(MP, MP))
        val lp = FrameLayout.LayoutParams(MP, dp(90), Gravity.BOTTOM)
        lp.leftMargin = dp(14)
        lp.rightMargin = dp(14)
        lp.bottomMargin = navH + dp(12)
        root.addView(dock, lp)
    }

    private fun rebuildDots() {
        dotsBox.removeAllViews()
        val n = pages.size + 1
        for (i in 0 until n) {
            val v = View(this)
            val g = GradientDrawable()
            g.shape = GradientDrawable.OVAL
            g.setColor(Color.WHITE)
            v.background = g
            v.alpha = if (i == pager.currentItem) 1f else 0.4f
            val lp = LinearLayout.LayoutParams(dp(7), dp(7))
            lp.setMargins(dp(4), 0, dp(4), 0)
            dotsBox.addView(v, lp)
        }
    }

    private fun updateDots(pos: Int) {
        for (i in 0 until dotsBox.childCount) {
            dotsBox.getChildAt(i).alpha = if (i == pos) 1f else 0.4f
        }
    }

    // ---------------------------------------------------------------- Pager

    inner class PageAdapter : RecyclerView.Adapter<PageAdapter.VH>() {
        inner class VH(val box: FrameLayout) : RecyclerView.ViewHolder(box)

        override fun getItemCount(): Int = pages.size + 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val f = FrameLayout(parent.context)
            f.layoutParams = ViewGroup.LayoutParams(MP, MP)
            return VH(f)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.box.removeAllViews()
            val v = if (position < pages.size) buildGridPage(position) else buildLibraryPage()
            holder.box.addView(v, FrameLayout.LayoutParams(MP, MP))
        }
    }

    private fun buildGridPage(pos: Int): View {
        val list = pages[pos]
        val hasWidget = pos == 0
        val rows = if (hasWidget) 4 else 6
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(dp(10), statusH + dp(34), dp(10), dockAreaH())
        if (hasWidget) page.addView(buildClockWidget())
        for (r in 0 until rows) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (c in 0 until 4) {
                val i = r * 4 + c
                val cell: View = if (i < list.size) buildCell(list[i]) else View(this)
                row.addView(cell, LinearLayout.LayoutParams(0, MP, 1f))
            }
            page.addView(row, LinearLayout.LayoutParams(MP, 0, 1f))
        }
        page.setOnLongClickListener {
            pickWallpaper()
            true
        }
        return page
    }

    private fun buildClockWidget(): View {
        val g = GlassView(this)
        g.radiusPx = dpf(26f)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER

        val t = TextClock(this)
        t.format12Hour = "h:mm"
        t.format24Hour = "H:mm"
        t.textSize = 56f
        t.setTextColor(Color.WHITE)
        t.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        t.gravity = Gravity.CENTER
        val d = TextClock(this)
        d.format12Hour = "EEEE, d MMMM"
        d.format24Hour = "EEEE, d MMMM"
        d.textSize = 15f
        d.setTextColor(Color.argb(220, 255, 255, 255))
        d.gravity = Gravity.CENTER
        col.addView(t, LinearLayout.LayoutParams(WC, WC))
        col.addView(d, LinearLayout.LayoutParams(WC, WC))
        g.addView(col, FrameLayout.LayoutParams(MP, MP))

        val lp = LinearLayout.LayoutParams(MP, 0, 2f)
        lp.setMargins(dp(8), dp(4), dp(8), dp(10))
        g.layoutParams = lp
        return g
    }

    private fun buildCell(a: AppInfo): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        box.addView(IconView(this, a.icon), LinearLayout.LayoutParams(dp(58), dp(58)))
        val tv = TextView(this)
        tv.text = a.label
        tv.setTextColor(Color.WHITE)
        tv.textSize = 11f
        tv.maxLines = 1
        tv.ellipsize = TextUtils.TruncateAt.END
        tv.gravity = Gravity.CENTER
        tv.setShadowLayer(4f, 0f, 1f, Color.argb(120, 0, 0, 0))
        val lp = LinearLayout.LayoutParams(MP, WC)
        lp.topMargin = dp(5)
        lp.leftMargin = dp(2)
        lp.rightMargin = dp(2)
        box.addView(tv, lp)
        box.setOnClickListener { launchApp(box, a) }
        box.setOnLongClickListener {
            showMenu(a)
            true
        }
        return box
    }

    private fun buildLibraryPage(): View {
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(dp(18), statusH + dp(34), dp(18), dockAreaH())

        val title = TextView(this)
        title.text = "অ্যাপ লাইব্রেরি"
        title.setTextColor(Color.WHITE)
        title.textSize = 22f
        title.typeface = Typeface.DEFAULT_BOLD
        title.setShadowLayer(6f, 0f, 1f, Color.argb(120, 0, 0, 0))
        val tlp = LinearLayout.LayoutParams(MP, WC)
        tlp.bottomMargin = dp(10)
        page.addView(title, tlp)

        for (r in 0 until 2) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (c in 0 until 2) {
                val gi = r * 2 + c
                row.addView(buildCategoryCard(gi), LinearLayout.LayoutParams(0, MP, 1f))
            }
            page.addView(row, LinearLayout.LayoutParams(MP, 0, 1f))
        }
        page.setOnLongClickListener {
            pickWallpaper()
            true
        }
        return page
    }

    private fun buildCategoryCard(gi: Int): View {
        val list = apps.filter { it.group == gi }
        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.VERTICAL
        val lpOuter = LinearLayout.LayoutParams(0, MP, 1f)
        lpOuter.setMargins(dp(7), dp(7), dp(7), dp(7))
        outer.layoutParams = lpOuter

        val g = GlassView(this)
        g.radiusPx = dpf(26f)
        val grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        grid.setPadding(dp(12), dp(12), dp(12), dp(12))
        val minis = list.take(4)
        for (r in 0 until 2) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (c in 0 until 2) {
                val i = r * 2 + c
                val cell = FrameLayout(this)
                if (i < minis.size) {
                    cell.addView(IconView(this, minis[i].icon), FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER))
                }
                row.addView(cell, LinearLayout.LayoutParams(0, MP, 1f))
            }
            grid.addView(row, LinearLayout.LayoutParams(MP, 0, 1f))
        }
        g.addView(grid, FrameLayout.LayoutParams(MP, MP))
        g.setOnClickListener { showGroup(groupNames[gi], list) }
        outer.addView(g, LinearLayout.LayoutParams(MP, 0, 1f))

        val tv = TextView(this)
        tv.text = groupNames[gi]
        tv.setTextColor(Color.WHITE)
        tv.textSize = 13f
        tv.gravity = Gravity.CENTER
        tv.setShadowLayer(4f, 0f, 1f, Color.argb(120, 0, 0, 0))
        val tlp = LinearLayout.LayoutParams(MP, WC)
        tlp.topMargin = dp(6)
        outer.addView(tv, tlp)
        return outer
    }

    private fun showGroup(title: String, list: List<AppInfo>) {
        dialog?.dismiss()
        val d = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        val wrapper = FrameLayout(this)
        wrapper.setPadding(dp(18), dp(60), dp(18), dp(60))
        wrapper.setOnClickListener { d.dismiss() }

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(16), dp(14), dp(10))
        val bg = GradientDrawable()
        bg.setColor(Color.argb(238, 22, 24, 36))
        bg.cornerRadius = dpf(28f)
        box.background = bg
        box.setOnClickListener { }

        val tv = TextView(this)
        tv.text = title
        tv.setTextColor(Color.WHITE)
        tv.textSize = 18f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.setPadding(dp(8), 0, 0, dp(8))
        box.addView(tv)

        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        for (chunk in list.chunked(4)) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (c in 0 until 4) {
                val cell: View = if (c < chunk.size) buildCell(chunk[c]) else View(this)
                row.addView(cell, LinearLayout.LayoutParams(0, dp(92), 1f))
            }
            col.addView(row, LinearLayout.LayoutParams(MP, WC))
        }
        if (list.isEmpty()) {
            val e = TextView(this)
            e.text = "এই ক্যাটাগরিতে কোনো অ্যাপ নেই"
            e.setTextColor(Color.argb(180, 255, 255, 255))
            e.setPadding(dp(8), dp(12), dp(8), dp(12))
            col.addView(e)
        }
        scroll.addView(col)
        box.addView(scroll, LinearLayout.LayoutParams(MP, WC))

        wrapper.addView(box, FrameLayout.LayoutParams(MP, WC, Gravity.CENTER))
        d.setContentView(wrapper, ViewGroup.LayoutParams(MP, MP))
        d.window?.setBackgroundDrawable(ColorDrawable(Color.argb(110, 0, 0, 0)))
        d.window?.setLayout(MP, MP)
        d.show()
        dialog = d
    }

    // ---------------------------------------------------------------- Actions

    private fun launchApp(v: View, a: AppInfo) {
        dialog?.dismiss()
        try {
            val i = Intent(Intent.ACTION_MAIN)
            i.addCategory(Intent.CATEGORY_LAUNCHER)
            i.setClassName(a.pkg, a.cls)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            val opt = ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.width, v.height)
            startActivity(i, opt.toBundle())
        } catch (e: Throwable) {
            Toast.makeText(this, "অ্যাপ খোলা যাচ্ছে না", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMenu(a: AppInfo) {
        AlertDialog.Builder(this)
            .setTitle(a.label)
            .setItems(arrayOf("অ্যাপ তথ্য", "আনইনস্টল")) { _, which ->
                try {
                    if (which == 0) {
                        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        i.data = Uri.parse("package:" + a.pkg)
                        startActivity(i)
                    } else {
                        val i = Intent(Intent.ACTION_DELETE)
                        i.data = Uri.parse("package:" + a.pkg)
                        startActivity(i)
                    }
                } catch (e: Throwable) {
                }
            }
            .show()
    }

    private fun pickWallpaper() {
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.type = "image/*"
        startActivityForResult(Intent.createChooser(i, "ওয়ালপেপার বাছুন"), 77)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        super.onActivityResult(rc, res, data)
        val u = data?.data
        if (rc == 77 && res == RESULT_OK && u != null) {
            Wall.importUri(this, u)
            recreate()
        }
    }

    // ---------------------------------------------------------------- Apps

    private fun groupOf(ai: ApplicationInfo): Int {
        val pkg = ai.packageName.lowercase()
        val cat = ai.category
        if (cat == ApplicationInfo.CATEGORY_SOCIAL) return 0
        if (cat == ApplicationInfo.CATEGORY_GAME) return 1
        if (cat == ApplicationInfo.CATEGORY_AUDIO || cat == ApplicationInfo.CATEGORY_VIDEO ||
            cat == ApplicationInfo.CATEGORY_IMAGE
        ) return 2
        val social = listOf(
            "facebook", "whatsapp", "instagram", "messenger", "telegram", "twitter", "snapchat",
            "imo", "tiktok", "linkedin", "viber", "discord", "pinterest", "reddit"
        )
        val games = listOf("game", "pubg", "freefire", "garena", "supercell", "bussid", "maleo", "roblox", "minecraft")
        val media = listOf("youtube", "music", "video", "spotify", "netflix", "photos", "gallery", "camera", "vlc", "player", "radio")
        if (social.any { pkg.contains(it) }) return 0
        if (games.any { pkg.contains(it) }) return 1
        if (media.any { pkg.contains(it) }) return 2
        return 3
    }

    private fun loadApps() {
        Thread {
            val pm = packageManager
            val q = Intent(Intent.ACTION_MAIN)
            q.addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(q, 0)
            val out = ArrayList<AppInfo>()
            for (ri in list) {
                val ai = ri.activityInfo ?: continue
                try {
                    out.add(
                        AppInfo(
                            ri.loadLabel(pm).toString(),
                            ai.packageName,
                            ai.name,
                            ri.loadIcon(pm),
                            groupOf(ai.applicationInfo)
                        )
                    )
                } catch (e: Throwable) {
                }
            }
            out.sortBy { it.label.lowercase() }
            runOnUiThread {
                apps = out
                val first = out.take(16)
                val rest = out.drop(16).chunked(24)
                pages = listOf(first) + rest
                pager.adapter?.notifyDataSetChanged()
                rebuildDots()
            }
        }.start()
    }
}
