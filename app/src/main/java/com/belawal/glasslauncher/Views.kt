package com.belawal.glasslauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** ওয়ালপেপার (ধারালো) — সবার পেছনে */
class WallView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.FILTER_BITMAP_FLAG)
    override fun onDraw(c: Canvas) {
        val b = Wall.sharp(context)
        c.save()
        c.scale(width.toFloat() / b.width, height.toFloat() / b.height)
        c.drawBitmap(b, 0f, 0f, p)
        c.restore()
    }
}

/** Liquid Glass প্যানেল: পেছনের ঝাপসা ওয়ালপেপার + সাদা টিন্ট + চকচকে কিনারা */
open class GlassView(ctx: Context) : FrameLayout(ctx) {
    var radiusPx: Float = ctx.dpf(24f)
    var circle: Boolean = false
    var tintColor: Int = Color.argb(52, 255, 255, 255)

    private val clip = Path()
    private val border = Path()
    private val rect = RectF()
    private val bp = Paint(Paint.FILTER_BITMAP_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val loc = IntArray(2)
    private var lastX = Int.MIN_VALUE
    private var lastY = Int.MIN_VALUE

    private val pre = ViewTreeObserver.OnPreDrawListener {
        getLocationInWindow(loc)
        if (loc[0] != lastX || loc[1] != lastY) invalidate()
        true
    }

    init {
        setWillNotDraw(false)
        line.style = Paint.Style.STROKE
        line.strokeWidth = ctx.dpf(1f)
        tp.style = Paint.Style.FILL
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        rect.set(0f, 0f, w.toFloat(), h.toFloat())
        val r = if (circle) minOf(w, h) / 2f else radiusPx
        clip.reset()
        clip.addRoundRect(rect, r, r, Path.Direction.CW)
        val inset = line.strokeWidth / 2f
        val br = RectF(inset, inset, w - inset, h - inset)
        border.reset()
        border.addRoundRect(br, r - inset, r - inset, Path.Direction.CW)
        line.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            Color.argb(150, 255, 255, 255), Color.argb(28, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(pre)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        val o = viewTreeObserver
        if (o.isAlive) o.removeOnPreDrawListener(pre)
    }

    override fun onDraw(canvas: Canvas) {
        getLocationInWindow(loc)
        lastX = loc[0]
        lastY = loc[1]
        val root = rootView
        canvas.save()
        canvas.clipPath(clip)
        if (root.width > 0 && root.height > 0) {
            val bmp = Wall.blur(context)
            canvas.save()
            canvas.translate(-loc[0].toFloat(), -loc[1].toFloat())
            canvas.scale(root.width.toFloat() / bmp.width, root.height.toFloat() / bmp.height)
            canvas.drawBitmap(bmp, 0f, 0f, bp)
            canvas.restore()
        }
        tp.color = tintColor
        canvas.drawRect(rect, tp)
        canvas.restore()
        canvas.drawPath(border, line)
    }
}

/** iOS-স্টাইল গোল-কোণা অ্যাপ আইকন */
class IconView(ctx: Context, private val d: Drawable?) : View(ctx) {
    private val path = Path()
    private val bgp = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        bgp.color = Color.rgb(244, 244, 247)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val r = w * 0.225f
        path.reset()
        path.addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Path.Direction.CW)
    }

    override fun onDraw(c: Canvas) {
        val dr = d ?: return
        val w = width
        val h = height
        c.save()
        c.clipPath(path)
        if (dr is AdaptiveIconDrawable) {
            val o = (w * 0.25f).toInt()
            val bg = dr.background
            val fg = dr.foreground
            if (bg != null) {
                bg.setBounds(-o, -o, w + o, h + o)
                bg.draw(c)
            }
            if (fg != null) {
                fg.setBounds(-o, -o, w + o, h + o)
                fg.draw(c)
            }
        } else {
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgp)
            val i = (w * 0.12f).toInt()
            dr.setBounds(i, i, w - i, h - i)
            dr.draw(c)
        }
        c.restore()
    }
}

/** লক স্ক্রিনের গোল গ্লাস উইজেট ও বাটন */
class WidgetCircle(ctx: Context, private val kind: Int) : GlassView(ctx) {
    companion object {
        const val BATTERY = 0
        const val NOTIF = 1
        const val MUSIC = 2
        const val DAY = 3
        const val TORCH = 4
        const val CAMERA = 5
    }

    var value: Int = 0
        set(v) {
            field = v
            invalidate()
        }

    var on: Boolean = false
        set(v) {
            field = v
            tintColor = if (v) Color.argb(228, 255, 255, 255) else Color.argb(60, 255, 255, 255)
            invalidate()
        }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        circle = true
        tintColor = Color.argb(60, 255, 255, 255)
    }

    override fun dispatchDraw(c: Canvas) {
        super.dispatchDraw(c)
        val s = width.toFloat()
        val cx = s / 2f
        val cy = height / 2f
        val fg = if (on) Color.rgb(40, 44, 60) else Color.WHITE
        p.reset()
        p.isAntiAlias = true
        p.color = fg
        p.style = Paint.Style.FILL
        when (kind) {
            BATTERY -> {
                val r = s * 0.34f
                p.style = Paint.Style.STROKE
                p.strokeWidth = s * 0.075f
                p.color = Color.argb(70, 255, 255, 255)
                c.drawCircle(cx, cy, r, p)
                p.color = Color.WHITE
                p.strokeCap = Paint.Cap.ROUND
                c.drawArc(cx - r, cy - r, cx + r, cy + r, -90f, 360f * value / 100f, false, p)
                p.style = Paint.Style.FILL
                p.textAlign = Paint.Align.CENTER
                p.textSize = s * 0.2f
                p.typeface = Typeface.DEFAULT_BOLD
                c.drawText(value.toString(), cx, cy + s * 0.07f, p)
            }
            NOTIF -> {
                p.textAlign = Paint.Align.CENTER
                p.typeface = Typeface.DEFAULT_BOLD
                p.textSize = s * 0.32f
                c.drawText(value.toString(), cx, cy + s * 0.04f, p)
                p.textSize = s * 0.15f
                p.typeface = Typeface.DEFAULT
                c.drawText("new", cx, cy + s * 0.25f, p)
            }
            MUSIC -> {
                p.textAlign = Paint.Align.CENTER
                p.textSize = s * 0.52f
                c.drawText("\u266A", cx, cy + s * 0.18f, p)
            }
            DAY -> {
                val now = Date()
                p.textAlign = Paint.Align.CENTER
                p.typeface = Typeface.DEFAULT
                p.textSize = s * 0.15f
                c.drawText(SimpleDateFormat("EEE", Locale.ENGLISH).format(now).uppercase(), cx, cy - s * 0.12f, p)
                p.typeface = Typeface.DEFAULT_BOLD
                p.textSize = s * 0.34f
                c.drawText(SimpleDateFormat("d", Locale.ENGLISH).format(now), cx, cy + s * 0.2f, p)
            }
            TORCH -> {
                val bw = s * 0.17f
                val head = Path()
                head.moveTo(cx - bw * 0.95f, cy - s * 0.24f)
                head.lineTo(cx + bw * 0.95f, cy - s * 0.24f)
                head.lineTo(cx + bw * 0.6f, cy - s * 0.10f)
                head.lineTo(cx - bw * 0.6f, cy - s * 0.10f)
                head.close()
                c.drawPath(head, p)
                c.drawRoundRect(cx - bw * 0.6f, cy - s * 0.08f, cx + bw * 0.6f, cy + s * 0.24f, bw * 0.3f, bw * 0.3f, p)
            }
            CAMERA -> {
                c.drawRoundRect(cx - s * 0.23f, cy - s * 0.12f, cx + s * 0.23f, cy + s * 0.16f, s * 0.06f, s * 0.06f, p)
                c.drawRoundRect(cx - s * 0.09f, cy - s * 0.19f, cx + s * 0.05f, cy - s * 0.10f, s * 0.03f, s * 0.03f, p)
                p.color = Color.argb(120, 40, 50, 80)
                c.drawCircle(cx, cy + s * 0.02f, s * 0.085f, p)
            }
        }
    }
}

/** আইফোনের মতো লম্বা, সরু, আধা-স্বচ্ছ কাচের ঘড়ি */
class GlassClockView(ctx: Context) : View(ctx) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private var text = "9:41"
    private val sy = 1.75f
    private var capH = 0f

    init {
        var tf: Typeface? = null
        var variable = false
        try {
            tf = Typeface.createFromAsset(ctx.assets, "fonts/clock.ttf")
            variable = true
        } catch (e: Throwable) {
            tf = null
        }
        val face = tf ?: Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        fill.typeface = face
        stroke.typeface = face
        if (variable) {
            fill.setFontVariationSettings("'wght' 230")
            stroke.setFontVariationSettings("'wght' 230")
        }
        fill.textAlign = Paint.Align.CENTER
        stroke.textAlign = Paint.Align.CENTER
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = ctx.dpf(1.2f)
        stroke.color = Color.argb(130, 255, 255, 255)
    }

    fun setTime(s: String) {
        text = s
        requestLayout()
        invalidate()
    }

    private fun fit(w: Int) {
        val base = 100f
        fill.textSize = base
        var size = base * (w * 0.80f) / fill.measureText("0:00")
        val tw = fill.measureText(text) * size / base
        if (tw > w * 0.92f) size = size * (w * 0.92f) / tw
        fill.textSize = size
        stroke.textSize = size
        val b = Rect()
        fill.getTextBounds("0123456789", 0, 10, b)
        capH = -b.top.toFloat()
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        fit(w)
        val h = (capH * sy + context.dpf(24f)).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(c: Canvas) {
        fit(width)
        fill.shader = LinearGradient(
            0f, 0f, 0f, capH,
            Color.argb(220, 255, 255, 255), Color.argb(120, 215, 225, 255),
            Shader.TileMode.CLAMP
        )
        c.save()
        c.translate(width / 2f, context.dpf(12f))
        c.scale(1f, sy)
        c.drawText(text, 0f, capH, fill)
        c.drawText(text, 0f, capH, stroke)
        c.restore()
    }
}
