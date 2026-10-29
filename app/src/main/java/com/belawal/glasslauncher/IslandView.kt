package com.belawal.glasslauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** স্ক্রিনের উপরে ভাসমান Dynamic Island */
class IslandView(private val c: Context) : FrameLayout(c) {
    private val idleW = c.dp(104)
    private val idleH = c.dp(30)
    private val openW = c.dp(336)
    private val openH = c.dp(68)

    private val bg = GradientDrawable()
    private val pill = FrameLayout(c)
    private val content = LinearLayout(c)
    private val icon = ImageView(c)
    private val title = TextView(c)
    private val sub = TextView(c)
    private val main = Handler(Looper.getMainLooper())
    private val closeRun = Runnable { animateTo(0f) }
    private var anim: ValueAnimator? = null
    private var frac = 0f
    private var always = true

    init {
        bg.setColor(Color.BLACK)
        pill.background = bg

        content.orientation = LinearLayout.HORIZONTAL
        content.gravity = Gravity.CENTER_VERTICAL
        content.setPadding(c.dp(16), 0, c.dp(18), 0)
        content.alpha = 0f

        icon.scaleType = ImageView.ScaleType.CENTER_CROP
        icon.clipToOutline = true
        icon.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                o.setRoundRect(0, 0, v.width, v.height, v.width * 0.25f)
            }
        }
        content.addView(icon, LinearLayout.LayoutParams(c.dp(38), c.dp(38)))

        title.setTextColor(Color.WHITE)
        title.textSize = 14f
        title.typeface = Typeface.DEFAULT_BOLD
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        sub.setTextColor(Color.argb(190, 255, 255, 255))
        sub.textSize = 12f
        sub.maxLines = 1
        sub.ellipsize = TextUtils.TruncateAt.END

        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        col.addView(title)
        col.addView(sub)
        val cl = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        cl.leftMargin = c.dp(12)
        content.addView(col, cl)

        pill.addView(
            content,
            LayoutParams(openW, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER)
        )
        val plp = LayoutParams(idleW, idleH, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        plp.topMargin = c.dp(8)
        addView(pill, plp)
        applyFrac(0f)
    }

    fun setAlways(v: Boolean) {
        always = v
        applyFrac(frac)
    }

    private fun applyFrac(f: Float) {
        frac = f
        val lp = pill.layoutParams as LayoutParams
        lp.width = (idleW + (openW - idleW) * f).toInt()
        lp.height = (idleH + (openH - idleH) * f).toInt()
        pill.layoutParams = lp
        bg.cornerRadius = lp.height / 2f
        content.alpha = ((f - 0.55f) * 2.4f).coerceIn(0f, 1f)
        pill.alpha = if (always) 1f else (f * 3f).coerceIn(0f, 1f)
    }

    private fun animateTo(target: Float) {
        anim?.cancel()
        val a = ValueAnimator.ofFloat(frac, target)
        a.duration = if (target > 0f) 480L else 320L
        a.interpolator = if (target > 0f) OvershootInterpolator(1.1f) else AccelerateDecelerateInterpolator()
        a.addUpdateListener { applyFrac(it.animatedValue as Float) }
        anim = a
        a.start()
    }

    fun showEvent(d: Drawable?, accent: Int, t: String, s: String) {
        main.removeCallbacks(closeRun)
        icon.setImageDrawable(d)
        icon.background = ColorDrawable(accent)
        title.text = t
        sub.text = s
        sub.visibility = if (s.isEmpty()) View.GONE else View.VISIBLE
        animateTo(1f)
        main.postDelayed(closeRun, 4200L)
    }

    fun showNotif(n: NotifItem) {
        val pm = c.packageManager
        val d: Drawable? = try {
            pm.getApplicationIcon(n.pkg)
        } catch (e: Exception) {
            null
        }
        val t = if (n.title.isEmpty()) n.pkg else n.title
        showEvent(d, Color.TRANSPARENT, if (n.media) "\u266A $t" else t, n.text)
    }
}
