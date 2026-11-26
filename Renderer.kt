package com.scriptwidgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.format.DateUtils
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import android.graphics.Color as AColor
import android.graphics.LinearGradient as AGradient

/**
 * Draws a widget tree onto a bitmap. All sizes are in "points" (1pt = 1 virtual unit), the canvas
 * is scaled so the layout matches iOS widget point sizes regardless of the device.
 */
class Renderer(private val isDark: Boolean) {

    private val defaultText: Int get() = if (isDark) AColor.WHITE else AColor.BLACK

    fun render(root: ListWidget, vw: Double, vh: Double, pxW: Int, pxH: Int): Bitmap {
        val bmp = Bitmap.createBitmap(pxW, pxH, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.scale((pxW / vw).toFloat(), (pxH / vh).toFloat())
        val clip = Path().apply {
            addRoundRect(RectF(0f, 0f, vw.toFloat(), vh.toFloat()), 22f, 22f, Path.Direction.CW)
        }
        cv.clipPath(clip)
        // Only paint the default backdrop when the script set no background (so transparent widgets work).
        if (root.backgroundColor == null && root.backgroundGradient == null && root.backgroundImage == null) {
            val bg = Paint().apply { color = if (isDark) AColor.BLACK else AColor.WHITE }
            cv.drawRect(0f, 0f, vw.toFloat(), vh.toFloat(), bg)
        }
        draw(root, cv, 0.0, 0.0, vw, vh)
        return bmp
    }

    // ------------------------------------------------------------------ helpers

    private fun pad(e: Element): DoubleArray =
        e._pad ?: if (e is ListWidget) doubleArrayOf(16.0, 16.0, 16.0, 16.0) else doubleArrayOf(0.0, 0.0, 0.0, 0.0)

    private fun typeface(f: Font?): Typeface {
        val base = when {
            f?.condensed == true -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            f?.mono == true -> Typeface.MONOSPACE
            f?.serif == true -> Typeface.SERIF
            else -> Typeface.DEFAULT
        }
        val w = f?.weight ?: 400
        val italic = f?.italic == true
        return if (Build.VERSION.SDK_INT >= 28) {
            Typeface.create(base, w, italic)
        } else {
            val style = when {
                w >= 600 && italic -> Typeface.BOLD_ITALIC
                w >= 600 -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            Typeface.create(base, style)
        }
    }

    private fun textPaint(e: WidgetText, scale: Float): TextPaint {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG)
        val f = e.font
        p.typeface = typeface(f)
        p.textSize = ((f?.size ?: 17.0) * scale).toFloat()
        val col = e.textColor?.resolve(isDark) ?: defaultText
        val a = (AColor.alpha(col) * e.textOpacity.coerceIn(0.0, 1.0)).toInt()
        p.color = (col and 0xFFFFFF) or (a shl 24)
        val sc = e.shadowColor
        if (sc != null && e.shadowRadius > 0) {
            val off = e.shadowOffset
            p.setShadowLayer(
                e.shadowRadius.toFloat(),
                (off?.x ?: 0.0).toFloat(),
                (off?.y ?: 0.0).toFloat(),
                sc.resolve(isDark)
            )
        }
        return p
    }

    private fun textOf(e: WidgetText): String = if (e is WidgetDate) dateText(e) else e.text

    private fun dateText(e: WidgetDate): String {
        val now = System.currentTimeMillis()
        val ms = e.millis ?: now
        val d = Date(ms)
        return when (e._style) {
            "time" -> DateFormat.getTimeInstance(DateFormat.SHORT).format(d)
            "date" -> DateFormat.getDateInstance(DateFormat.LONG).format(d)
            "relative" -> DateUtils.getRelativeTimeSpanString(
                ms, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()
            "offset" -> {
                val diff = (ms - now) / 1000
                (if (diff >= 0) "+" else "-") + duration(abs(diff))
            }
            "timer" -> clock(abs(ms - now) / 1000)
            else -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(d)
        }
    }

    private fun clock(s: Long): String =
        if (s >= 3600) String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
        else String.format("%d:%02d", s / 60, s % 60)

    private fun duration(s: Long): String = when {
        s >= 86400 -> "${s / 86400} days"
        s >= 3600 -> "${s / 3600} hours"
        s >= 60 -> "${s / 60} minutes"
        else -> "$s seconds"
    }

    private fun makeLayout(e: WidgetText, text: String, w: Int, scale: Float): StaticLayout {
        val p = textPaint(e, scale)
        val b = StaticLayout.Builder.obtain(text, 0, text.length, p, w)
            .setIncludePad(false)
            .setAlignment(
                when (e._textAlign) {
                    1 -> Layout.Alignment.ALIGN_CENTER
                    2 -> Layout.Alignment.ALIGN_OPPOSITE
                    else -> Layout.Alignment.ALIGN_NORMAL
                }
            )
        if (e.lineLimit > 0) {
            b.setMaxLines(e.lineLimit).setEllipsize(TextUtils.TruncateAt.END)
        }
        return b.build()
    }

    private fun truncated(l: StaticLayout): Boolean =
        l.lineCount > 0 && l.getEllipsisCount(l.lineCount - 1) > 0

    private fun buildLayout(e: WidgetText, width: Double): StaticLayout {
        val text = textOf(e)
        val w = max(1, ceil(width).toInt())
        var scale = 1f
        var layout = makeLayout(e, text, w, scale)
        if (e.minimumScaleFactor < 1.0 && e.lineLimit > 0) {
            val minScale = e.minimumScaleFactor.coerceIn(0.1, 1.0).toFloat()
            while (scale > minScale && truncated(layout)) {
                scale -= 0.05f
                layout = makeLayout(e, text, w, scale)
            }
        }
        return layout
    }

    // ------------------------------------------------------------------ measuring

    /** Returns the outer (width, height) of an element, including padding. */
    private fun measure(e: Element, maxW: Double, maxH: Double): DoubleArray {
        val p = pad(e)
        val iw = max(0.0, maxW - p[1] - p[3])
        val ih = max(0.0, maxH - p[0] - p[2])
        val inner: DoubleArray = when (e) {
            is WidgetText -> {
                val l = buildLayout(e, iw)
                var lw = 0f
                for (i in 0 until l.lineCount) lw = max(lw, l.getLineWidth(i))
                doubleArrayOf(
                    min(ceil(lw.toDouble()) + 1, iw),
                    l.height.toDouble() * e.verticalScale.coerceAtLeast(0.1)
                )
            }
            is WidgetImage -> measureImage(e, iw, ih)
            is Container -> measureContainer(e, iw, ih)
            else -> doubleArrayOf(0.0, 0.0)
        }
        var w = inner[0] + p[1] + p[3]
        var h = inner[1] + p[0] + p[2]
        val sz = e.size
        if (sz != null) {
            if (sz.width > 0) w = sz.width
            if (sz.height > 0) h = sz.height
        }
        return doubleArrayOf(w, h)
    }

    private fun measureImage(e: WidgetImage, iw: Double, ih: Double): DoubleArray {
        val bm = e.image?.bitmap ?: return doubleArrayOf(0.0, 0.0)
        var nw = bm.width / 3.0
        var nh = bm.height / 3.0
        val s = e.imageSize
        if (s != null && s.width > 0 && s.height > 0) {
            nw = s.width
            nh = s.height
        }
        val kw = if (iw > 0) iw / nw else 1.0
        val kh = if (ih > 0) ih / nh else 1.0
        val k = min(1.0, min(kw, kh))
        return doubleArrayOf(nw * k, nh * k)
    }

    private fun childSizes(c: Container, iw: Double, ih: Double): List<DoubleArray> {
        val horiz = c._horizontal
        val kids = c.children
        val out = ArrayList<DoubleArray>()
        for (k in kids) {
            if (k is WidgetSpacer) {
                val len = if (k._length >= 0) k._length else 0.0
                out.add(if (horiz) doubleArrayOf(len, 0.0) else doubleArrayOf(0.0, len))
            } else {
                out.add(measure(k, iw, ih))
            }
        }
        if (horiz && kids.isNotEmpty()) {
            val total = out.sumOf { it[0] } + c.spacing * (kids.size - 1)
            if (total > iw + 0.5) {
                val over = total - iw
                val idx = kids.indices.filter {
                    val k = kids[it]
                    (k is WidgetText || k is Container) && (k.size?.width ?: 0.0) <= 0.0
                }
                val base = idx.sumOf { out[it][0] }
                if (base > 0) {
                    for (i in idx) {
                        val target = max(1.0, out[i][0] - over * out[i][0] / base)
                        out[i] = measure(kids[i], target, ih)
                    }
                }
            }
        }
        return out
    }

    private fun measureContainer(c: Container, iw: Double, ih: Double): DoubleArray {
        val sizes = childSizes(c, iw, ih)
        val horiz = c._horizontal
        var main = c.spacing * max(0, sizes.size - 1)
        var cross = 0.0
        for (s in sizes) {
            if (horiz) {
                main += s[0]
                cross = max(cross, s[1])
            } else {
                main += s[1]
                cross = max(cross, s[0])
            }
        }
        val flex = c.children.any { it is WidgetSpacer && it._length < 0 }
        if (flex) main = max(main, if (horiz) iw else ih)
        return if (horiz) doubleArrayOf(main, cross) else doubleArrayOf(cross, main)
    }

    // ------------------------------------------------------------------ drawing

    private fun draw(e: Element, cv: Canvas, x: Double, y: Double, w: Double, h: Double) {
        if (w <= 0 || h <= 0) return
        val rect = RectF(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
        val r = e.cornerRadius.toFloat()
        val saved = cv.save()
        if (r > 0f) {
            cv.clipPath(Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) })
        }

        val bgc = e.backgroundColor
        if (bgc != null) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgc.resolve(isDark) }
            if (r > 0f) cv.drawRoundRect(rect, r, r, paint) else cv.drawRect(rect, paint)
        }
        if (e is Container) {
            e.backgroundGradient?.let { drawGradient(cv, rect, it) }
            e.backgroundImage?.bitmap?.let { drawFill(cv, rect, it, 255) }
        }

        val p = pad(e)
        val ix = x + p[1]
        val iy = y + p[0]
        val iw = max(0.0, w - p[1] - p[3])
        val ih = max(0.0, h - p[0] - p[2])
        when (e) {
            is WidgetText -> drawText(e, cv, ix, iy, iw, ih)
            is WidgetImage -> drawImage(e, cv, ix, iy, iw, ih)
            is Container -> drawChildren(e, cv, ix, iy, iw, ih)
            else -> {}
        }

        val bc = e.borderColor
        if (e.borderWidth > 0 && bc != null) {
            val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = e.borderWidth.toFloat()
                color = bc.resolve(isDark)
            }
            val half = (e.borderWidth / 2).toFloat()
            cv.drawRoundRect(
                RectF(rect.left + half, rect.top + half, rect.right - half, rect.bottom - half),
                r, r, bp
            )
        }
        cv.restoreToCount(saved)
    }

    private fun drawText(e: WidgetText, cv: Canvas, x: Double, y: Double, w: Double, h: Double) {
        if (w <= 0) return
        val layout = buildLayout(e, w)
        val vs = e.verticalScale.coerceAtLeast(0.1)
        val dy = max(0.0, (h - layout.height * vs) / 2.0)
        cv.save()
        cv.translate(x.toFloat(), (y + dy).toFloat())
        if (vs != 1.0) cv.scale(1f, vs.toFloat())
        val oc = e.outlineColor
        if (oc != null && e.outlineWidth > 0) strokeOf(e, layout, oc).draw(cv)
        layout.draw(cv)
        cv.restore()
    }

    /** Same text as [base], drawn as an outline only. */
    private fun strokeOf(e: WidgetText, base: StaticLayout, oc: Color): StaticLayout {
        val sp = TextPaint(base.paint)
        sp.clearShadowLayer()
        sp.style = Paint.Style.STROKE
        sp.strokeWidth = e.outlineWidth.toFloat()
        sp.color = oc.resolve(isDark)
        val b = StaticLayout.Builder.obtain(base.text, 0, base.text.length, sp, base.width)
            .setIncludePad(false)
            .setAlignment(base.alignment)
        if (e.lineLimit > 0) b.setMaxLines(e.lineLimit).setEllipsize(TextUtils.TruncateAt.END)
        return b.build()
    }

    private fun drawImage(e: WidgetImage, cv: Canvas, x: Double, y: Double, w: Double, h: Double) {
        val bm = e.image?.bitmap ?: return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (255 * e.imageOpacity.coerceIn(0.0, 1.0)).toInt()
            val t = e.tintColor
            if (t != null) colorFilter = PorterDuffColorFilter(t.resolve(isDark), PorterDuff.Mode.SRC_IN)
        }
        val bw = bm.width.toDouble()
        val bh = bm.height.toDouble()
        val s = if (e._fill) max(w / bw, h / bh) else min(w / bw, h / bh)
        val dw = bw * s
        val dh = bh * s
        val dx = x + (w - dw) / 2
        val dy = y + (h - dh) / 2
        cv.save()
        cv.clipRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
        cv.drawBitmap(
            bm, null,
            RectF(dx.toFloat(), dy.toFloat(), (dx + dw).toFloat(), (dy + dh).toFloat()),
            paint
        )
        cv.restore()
    }

    private fun drawFill(cv: Canvas, rect: RectF, bm: Bitmap, alpha: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha }
        val s = max(rect.width() / bm.width, rect.height() / bm.height)
        val dw = bm.width * s
        val dh = bm.height * s
        val dx = rect.left + (rect.width() - dw) / 2
        val dy = rect.top + (rect.height() - dh) / 2
        cv.save()
        cv.clipRect(rect)
        cv.drawBitmap(bm, null, RectF(dx, dy, dx + dw, dy + dh), paint)
        cv.restore()
    }

    private fun drawGradient(cv: Canvas, rect: RectF, g: LinearGradient) {
        val cols = g.colors.mapNotNull { (it as? Color)?.resolve(isDark) }.toIntArray()
        if (cols.isEmpty()) return
        val p = Paint()
        if (cols.size == 1) {
            p.color = cols[0]
        } else {
            val locs = g.locations.mapNotNull { (it as? Number)?.toFloat() }
            val pos = if (locs.size == cols.size) locs.toFloatArray() else null
            p.shader = AGradient(
                rect.left + g.startPoint.x.toFloat() * rect.width(),
                rect.top + g.startPoint.y.toFloat() * rect.height(),
                rect.left + g.endPoint.x.toFloat() * rect.width(),
                rect.top + g.endPoint.y.toFloat() * rect.height(),
                cols, pos, Shader.TileMode.CLAMP
            )
        }
        cv.drawRect(rect, p)
    }

    private fun drawChildren(c: Container, cv: Canvas, ix: Double, iy: Double, iw: Double, ih: Double) {
        val kids = c.children
        if (kids.isEmpty()) return
        val horiz = c._horizontal
        val sizes = childSizes(c, iw, ih)
        val fixedMain = sizes.sumOf { if (horiz) it[0] else it[1] } + c.spacing * (kids.size - 1)
        val availMain = if (horiz) iw else ih
        val flexCount = kids.count { it is WidgetSpacer && it._length < 0 }
        val extra = if (flexCount > 0) max(0.0, availMain - fixedMain) / flexCount else 0.0
        val availCross = if (horiz) ih else iw

        var pos = 0.0
        for (i in kids.indices) {
            val k = kids[i]
            val s = sizes[i]
            val mainLen = if (k is WidgetSpacer && k._length < 0) extra else if (horiz) s[0] else s[1]
            val crossLen = if (horiz) s[1] else s[0]
            val crossOff = when (c._align) {
                1 -> (availCross - crossLen) / 2
                2 -> availCross - crossLen
                else -> 0.0
            }
            val cx = if (horiz) ix + pos else ix + crossOff
            val cy = if (horiz) iy + crossOff else iy + pos
            if (k !is WidgetSpacer) {
                draw(k, cv, cx, cy, if (horiz) mainLen else crossLen, if (horiz) crossLen else mainLen)
            }
            pos += mainLen + c.spacing
        }
    }
}
