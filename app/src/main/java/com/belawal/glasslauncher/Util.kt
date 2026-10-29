package com.belawal.glasslauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import java.io.File

fun Context.dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dp(v: Int): Int = dp(v.toFloat())
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density
fun Context.dpf(v: Int): Float = dpf(v.toFloat())

fun Context.sysDim(name: String): Int {
    val id = resources.getIdentifier(name, "dimen", "android")
    return if (id > 0) resources.getDimensionPixelSize(id) else 0
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)
    fun islandOn(c: Context): Boolean = sp(c).getBoolean("island_on", true)
    fun islandAlways(c: Context): Boolean = sp(c).getBoolean("island_always", true)
    fun lockOn(c: Context): Boolean = sp(c).getBoolean("lock_on", true)
    fun set(c: Context, k: String, v: Boolean) {
        sp(c).edit().putBoolean(k, v).apply()
    }
}

/** ওয়ালপেপার: নিজের বানানো ডিফল্ট ওয়েভ, অথবা ইউজারের বাছাই করা ছবি। blur() গ্লাস ইফেক্টের জন্য। */
object Wall {
    private var sharpBmp: Bitmap? = null
    private var blurBmp: Bitmap? = null

    fun reset() {
        sharpBmp = null
        blurBmp = null
    }

    private fun file(c: Context) = File(c.filesDir, "wall.jpg")

    fun importUri(c: Context, u: Uri): Boolean {
        return try {
            val ins = c.contentResolver.openInputStream(u) ?: return false
            ins.use { input ->
                file(c).outputStream().use { out -> input.copyTo(out) }
            }
            reset()
            true
        } catch (e: Throwable) {
            false
        }
    }

    fun sharp(c: Context): Bitmap {
        sharpBmp?.let { return it }
        val dm = c.resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        var result: Bitmap? = null
        val f = file(c)
        if (f.exists()) {
            try {
                val o = BitmapFactory.Options()
                o.inJustDecodeBounds = true
                BitmapFactory.decodeFile(f.path, o)
                var s = 1
                while (o.outWidth / (s * 2) >= w && o.outHeight / (s * 2) >= h) s *= 2
                val o2 = BitmapFactory.Options()
                o2.inSampleSize = s
                val src = BitmapFactory.decodeFile(f.path, o2)
                if (src != null) result = cropFill(src, w, h)
            } catch (e: Throwable) {
                result = null
            }
        }
        val out = result ?: generate(w, h)
        sharpBmp = out
        return out
    }

    fun blur(c: Context): Bitmap {
        blurBmp?.let { return it }
        val s = sharp(c)
        val w = s.width
        val h = s.height
        var b = Bitmap.createScaledBitmap(s, maxOf(8, w / 6), maxOf(8, h / 6), true)
        b = Bitmap.createScaledBitmap(b, maxOf(6, w / 16), maxOf(6, h / 16), true)
        b = Bitmap.createScaledBitmap(b, maxOf(4, w / 32), maxOf(4, h / 32), true)
        b = Bitmap.createScaledBitmap(b, maxOf(8, w / 8), maxOf(8, h / 8), true)
        b = Bitmap.createScaledBitmap(b, w, h, true)
        blurBmp = b
        return b
    }

    private fun cropFill(src: Bitmap, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val sc = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val dw = src.width * sc
        val dh = src.height * sc
        val m = Matrix()
        m.postScale(sc, sc)
        m.postTranslate((w - dw) / 2f, (h - dh) / 2f)
        c.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun generate(w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.rgb(18, 32, 63))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cols = intArrayOf(
            Color.rgb(74, 86, 110),
            Color.rgb(140, 147, 160),
            Color.rgb(230, 231, 234),
            Color.rgb(235, 192, 140),
            Color.rgb(245, 174, 85),
            Color.rgb(247, 158, 27)
        )
        val wf = w.toFloat()
        val hf = h.toFloat()
        for (i in cols.indices) {
            val y0 = hf * (0.10f + i * 0.15f)
            val path = Path()
            path.moveTo(0f, y0)
            path.cubicTo(wf * 0.30f, y0 - hf * 0.08f, wf * 0.55f, y0 + hf * 0.16f, wf, y0 + hf * 0.04f)
            path.lineTo(wf, hf)
            path.lineTo(0f, hf)
            path.close()
            p.color = cols[i]
            c.drawPath(path, p)
        }
        return b
    }
}
