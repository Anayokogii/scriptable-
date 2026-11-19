package com.scriptwidgets

import android.graphics.Bitmap
import kotlin.math.roundToInt
import org.mozilla.javascript.Context as JsContext

/** Helpers shared by the scripting API classes. */
object JsUtil {
    fun toMillis(v: Any?): Long? = try {
        when (v) {
            null -> null
            is Number -> v.toLong()
            is java.util.Date -> v.time
            else -> (JsContext.jsToJava(v, java.util.Date::class.java) as java.util.Date).time
        }
    } catch (e: Exception) {
        null
    }
}

/** Remembers the widget a script passed to presentSmall/Medium/Large as a fallback. */
object Capture {
    @JvmField
    var last: ListWidget? = null
}

// ---------------------------------------------------------------- value types

class Color @JvmOverloads constructor(hexStr: String, a: Double = 1.0) {
    var argb: Int = parse(hexStr, a)
        private set
    var darkVariant: Color? = null
        private set

    val hex: String get() = String.format("%06X", argb and 0xFFFFFF)
    val alpha: Double get() = ((argb ushr 24) and 0xFF) / 255.0

    fun resolve(dark: Boolean): Int = if (dark) (darkVariant?.argb ?: argb) else argb

    companion object {
        private fun parse(h: String, a: Double): Int {
            var s = h.trim().removePrefix("#")
            if (s.length == 3) s = s.map { "$it$it" }.joinToString("")
            val v = s.toLongOrNull(16) ?: 0L
            val rgb: Long
            val alphaByte: Int
            if (s.length == 8) {
                rgb = (v shr 8) and 0xFFFFFF
                alphaByte = (v and 0xFF).toInt()
            } else {
                rgb = v and 0xFFFFFF
                alphaByte = 255
            }
            val ab = (alphaByte * a.coerceIn(0.0, 1.0)).roundToInt()
            return (ab shl 24) or rgb.toInt()
        }

        @JvmStatic fun white() = Color("FFFFFF")
        @JvmStatic fun black() = Color("000000")
        @JvmStatic fun red() = Color("FF3B30")
        @JvmStatic fun green() = Color("34C759")
        @JvmStatic fun blue() = Color("007AFF")
        @JvmStatic fun orange() = Color("FF9500")
        @JvmStatic fun yellow() = Color("FFCC00")
        @JvmStatic fun purple() = Color("AF52DE")
        @JvmStatic fun gray() = Color("8E8E93")
        @JvmStatic fun lightGray() = Color("D3D3D3")
        @JvmStatic fun darkGray() = Color("555555")
        @JvmStatic fun brown() = Color("A2845E")
        @JvmStatic fun clear() = Color("000000", 0.0)

        @JvmStatic
        fun dynamic(light: Color, dark: Color): Color {
            val c = Color("000000")
            c.argb = light.argb
            c.darkVariant = dark
            return c
        }
    }
}

class Font(val name: String, val size: Double) {
    var weight: Int = when {
        Regex("black|heavy", RegexOption.IGNORE_CASE).containsMatchIn(name) -> 800
        Regex("demi|semi", RegexOption.IGNORE_CASE).containsMatchIn(name) -> 600
        Regex("bold", RegexOption.IGNORE_CASE).containsMatchIn(name) -> 700
        Regex("medium", RegexOption.IGNORE_CASE).containsMatchIn(name) -> 500
        Regex("light|thin", RegexOption.IGNORE_CASE).containsMatchIn(name) -> 300
        else -> 400
    }
    var italic: Boolean = false
    var mono: Boolean =
        Regex("menlo|courier|mono|monaco|consolas", RegexOption.IGNORE_CASE).containsMatchIn(name)
    var serif: Boolean =
        Regex("times|georgia|palatino|baskerville|didot", RegexOption.IGNORE_CASE).containsMatchIn(name)

    companion object {
        private fun mk(size: Double, weight: Int, italic: Boolean = false, mono: Boolean = false): Font =
            Font("system", size).also {
                it.weight = weight
                it.italic = italic
                it.mono = mono
            }

        @JvmStatic fun systemFont(size: Double) = mk(size, 400)
        @JvmStatic fun ultraLightSystemFont(size: Double) = mk(size, 100)
        @JvmStatic fun thinSystemFont(size: Double) = mk(size, 200)
        @JvmStatic fun lightSystemFont(size: Double) = mk(size, 300)
        @JvmStatic fun regularSystemFont(size: Double) = mk(size, 400)
        @JvmStatic fun mediumSystemFont(size: Double) = mk(size, 500)
        @JvmStatic fun semiboldSystemFont(size: Double) = mk(size, 600)
        @JvmStatic fun boldSystemFont(size: Double) = mk(size, 700)
        @JvmStatic fun heavySystemFont(size: Double) = mk(size, 800)
        @JvmStatic fun blackSystemFont(size: Double) = mk(size, 900)
        @JvmStatic fun italicSystemFont(size: Double) = mk(size, 400, italic = true)
        @JvmStatic fun regularMonospacedSystemFont(size: Double) = mk(size, 400, mono = true)
        @JvmStatic fun boldMonospacedSystemFont(size: Double) = mk(size, 700, mono = true)

        @JvmStatic fun largeTitle() = mk(34.0, 400)
        @JvmStatic fun title1() = mk(28.0, 400)
        @JvmStatic fun title2() = mk(22.0, 400)
        @JvmStatic fun title3() = mk(20.0, 400)
        @JvmStatic fun headline() = mk(17.0, 600)
        @JvmStatic fun subheadline() = mk(15.0, 400)
        @JvmStatic fun body() = mk(17.0, 400)
        @JvmStatic fun callout() = mk(16.0, 400)
        @JvmStatic fun footnote() = mk(13.0, 400)
        @JvmStatic fun caption1() = mk(12.0, 400)
        @JvmStatic fun caption2() = mk(11.0, 400)
    }
}

class Point(var x: Double, var y: Double)

class Size(var width: Double, var height: Double)

class LinearGradient {
    var colors: Array<Any?> = arrayOf()
    var locations: Array<Any?> = arrayOf()
    var startPoint: Point = Point(0.5, 0.0)
    var endPoint: Point = Point(0.5, 1.0)
}

class ScriptImage(val bitmap: Bitmap) {
    val size: Size get() = Size(bitmap.width.toDouble(), bitmap.height.toDouble())
}

// ------------------------------------------------------------------- elements

abstract class Element {
    var backgroundColor: Color? = null
    var cornerRadius: Double = 0.0
    var borderWidth: Double = 0.0
    var borderColor: Color? = null
    var size: Size? = null
    var url: String? = null

    /** top, leading, bottom, trailing; null = default for this element type. */
    @JvmField
    var _pad: DoubleArray? = null

    fun setPadding(top: Double, leading: Double, bottom: Double, trailing: Double) {
        _pad = doubleArrayOf(top, leading, bottom, trailing)
    }

    fun useDefaultPadding() {
        _pad = null
    }
}

open class WidgetText(text: String) : Element() {
    var text: String = text
    var font: Font? = null
    var textColor: Color? = null
    var textOpacity: Double = 1.0
    var lineLimit: Int = 0
    var minimumScaleFactor: Double = 1.0
    var shadowColor: Color? = null
    var shadowRadius: Double = 0.0
    var shadowOffset: Point? = null

    /** 0 = leading, 1 = center, 2 = trailing */
    @JvmField
    var _textAlign: Int = 0

    fun leftAlignText() { _textAlign = 0 }
    fun centerAlignText() { _textAlign = 1 }
    fun rightAlignText() { _textAlign = 2 }
}

class WidgetDate : WidgetText("") {
    @JvmField
    var millis: Long? = null

    var date: Any? = null
        set(v) {
            field = v
            millis = JsUtil.toMillis(v)
        }

    @JvmField
    var _style: String = "date"

    fun applyTimeStyle() { _style = "time" }
    fun applyDateStyle() { _style = "date" }
    fun applyRelativeStyle() { _style = "relative" }
    fun applyOffsetStyle() { _style = "offset" }
    fun applyTimerStyle() { _style = "timer" }
}

class WidgetSpacer : Element() {
    /** < 0 means flexible. */
    @JvmField
    var _length: Double = -1.0
}

class WidgetImage : Element() {
    var image: ScriptImage? = null
    var imageSize: Size? = null
    var imageOpacity: Double = 1.0
    var tintColor: Color? = null
    var resizable: Boolean = true

    @JvmField
    var _fill: Boolean = false

    fun applyFittingContentMode() { _fill = false }
    fun applyFillingContentMode() { _fill = true }
}

open class Container : Element() {
    @JvmField
    val children = ArrayList<Element>()

    var spacing: Double = 0.0
    var backgroundGradient: LinearGradient? = null
    var backgroundImage: ScriptImage? = null

    @JvmField
    var _horizontal: Boolean = false

    /** 0 = top/leading, 1 = center, 2 = bottom/trailing */
    @JvmField
    var _align: Int = 0

    fun layoutHorizontally() { _horizontal = true }
    fun layoutVertically() { _horizontal = false }
    fun topAlignContent() { _align = 0 }
    fun centerAlignContent() { _align = 1 }
    fun bottomAlignContent() { _align = 2 }

    fun addText(text: String?): WidgetText = WidgetText(text ?: "").also { children.add(it) }

    fun addDate(date: Any?): WidgetDate = WidgetDate().also {
        it.date = date
        children.add(it)
    }

    fun addImage(image: ScriptImage?): WidgetImage = WidgetImage().also {
        it.image = image
        children.add(it)
    }

    fun addStack(): WidgetStack = WidgetStack().also { children.add(it) }

    @JvmOverloads
    fun addSpacer(length: Any? = null): WidgetSpacer = WidgetSpacer().also {
        it._length = (length as? Number)?.toDouble() ?: -1.0
        children.add(it)
    }
}

class WidgetStack : Container() {
    init {
        _horizontal = true
    }
}

class ListWidget : Container() {
    var refreshAfterDate: Any? = null
        set(v) {
            field = v
            refreshAfterMillis = JsUtil.toMillis(v)
        }

    @JvmField
    var refreshAfterMillis: Long? = null

    fun presentSmall() { Capture.last = this }
    fun presentMedium() { Capture.last = this }
    fun presentLarge() { Capture.last = this }
    fun presentExtraLarge() { Capture.last = this }
}
