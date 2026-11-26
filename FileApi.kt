package com.scriptwidgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.io.File
import kotlin.math.max

/** Gives the file/drawing API access to the app context while a script runs. */
object ScriptEnv {
    @JvmField
    var app: Context? = null

    /** True while a live (once-a-second) tick runs; lets network results be cached briefly. */
    @Volatile
    @JvmField
    var live: Boolean = false
}

class Rect(var x: Double, var y: Double, var width: Double, var height: Double)

/** Off-screen drawing surface, like Scriptable's DrawContext. */
class DrawContext {
    var size: Size = Size(100.0, 100.0)
    var respectScreenScale: Boolean = false

    private var bmp: Bitmap? = null
    private var cv: Canvas? = null
    private var fill: Int = 0xFF000000.toInt()

    private fun canvas(): Canvas {
        var c = cv
        if (c == null) {
            val b = Bitmap.createBitmap(
                max(1, size.width.toInt()), max(1, size.height.toInt()), Bitmap.Config.ARGB_8888
            )
            bmp = b
            c = Canvas(b)
            cv = c
        }
        return c
    }

    private fun rectF(r: Rect) =
        RectF(r.x.toFloat(), r.y.toFloat(), (r.x + r.width).toFloat(), (r.y + r.height).toFloat())

    fun drawImageInRect(img: ScriptImage?, r: Rect) {
        val b = img?.bitmap ?: return
        canvas().drawBitmap(b, null, rectF(r), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    fun setFillColor(c: Color) {
        fill = c.resolve(false)
    }

    fun fillRect(r: Rect) {
        canvas().drawRect(rectF(r), Paint().apply { color = fill })
    }

    fun getImage(): ScriptImage {
        canvas()
        return ScriptImage(bmp!!)
    }
}

/** Sandboxed file access. "Documents" is the app's private folder; use the editor's Import menu to add files. */
class FileManager {

    companion object {
        @JvmStatic fun local() = FileManager()
        @JvmStatic fun iCloud() = FileManager()

        fun docsDir(ctx: Context): File = File(ctx.filesDir, "documents").apply { mkdirs() }
    }

    private fun ctx(): Context = ScriptEnv.app ?: throw IllegalStateException("No app context")

    fun documentsDirectory(): String = docsDir(ctx()).absolutePath
    fun libraryDirectory(): String = ctx().filesDir.absolutePath
    fun temporaryDirectory(): String = ctx().cacheDir.absolutePath

    fun joinPath(a: String, b: String): String = File(a, b).path
    fun fileExists(p: String): Boolean = File(p).exists()
    fun remove(p: String) {
        File(p).delete()
    }

    fun readString(p: String): String = File(p).readText()
    fun writeString(p: String, s: String) {
        File(p).writeText(s)
    }

    private object ImageCache {
        var key: String? = null
        var bmp: Bitmap? = null
    }

    /** Decoded images are cached (by path + modified time) so once-a-second live updates stay cheap. */
    fun readImage(p: String): ScriptImage? {
        val f = File(p)
        if (!f.exists()) return null
        val key = p + "|" + f.lastModified()
        synchronized(ImageCache) {
            val c = ImageCache.bmp
            if (c != null && ImageCache.key == key && !c.isRecycled) return ScriptImage(c)
        }
        val img = readImageUncached(p)
        if (img != null) synchronized(ImageCache) {
            ImageCache.key = key
            ImageCache.bmp = img.bitmap
        }
        return img
    }

    private fun readImageUncached(p: String): ScriptImage? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(p, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 2400) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(p, opts)?.let { ScriptImage(it) }
    }

    fun writeImage(p: String, img: ScriptImage?) {
        val b = img?.bitmap ?: return
        File(p).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
