package com.song.Song

import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Image loader — Kotlin + coroutines version (replaces the old AsyncTask-based
 * loader). Same public API (load / loadCircular) so every Java call site
 * (ImageLoader.load(view, url)) keeps working unchanged.
 */
object ImageLoader {

    private const val CORNER_RADIUS_PX = 20f

    private val memoryCache: LruCache<String, Bitmap> = run {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSizeKb = maxMemoryKb / 8
        object : LruCache<String, Bitmap>(cacheSizeKb) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount / 1024
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main)

    @JvmStatic
    fun load(imageView: ImageView, url: String?) {
        loadInternal(imageView, url, circular = false)
    }

    /** Same as load(), but crops the image to a full circle — for the player's album art */
    @JvmStatic
    fun loadCircular(imageView: ImageView, url: String?) {
        loadInternal(imageView, url, circular = true)
    }

    private fun loadInternal(imageView: ImageView, url: String?, circular: Boolean) {
        if (url.isNullOrEmpty()) return

        val cacheKey = (if (circular) "circ:" else "rect:") + url
        imageView.tag = cacheKey

        val cached = memoryCache.get(cacheKey)
        if (cached != null) {
            imageView.setImageBitmap(cached)
            imageView.alpha = 1f
            return
        }

        imageView.alpha = 0f

        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { downloadAndProcess(url, cacheKey, circular) }
            if (bitmap != null && cacheKey == imageView.tag) {
                imageView.setImageBitmap(bitmap)
                ObjectAnimator.ofFloat(imageView, "alpha", 0f, 1f).setDuration(220).start()
            }
        }
    }

    private fun downloadAndProcess(url: String, cacheKey: String, circular: Boolean): Bitmap? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
            }
            val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it) } ?: return null
            val processed = if (circular) circleCrop(bmp) else roundCorners(bmp)
            memoryCache.put(cacheKey, processed)
            processed
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Rounds the corners of a bitmap using a BitmapShader clip */
    private fun roundCorners(source: Bitmap): Bitmap {
        return try {
            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            }
            val rect = RectF(0f, 0f, source.width.toFloat(), source.height.toFloat())
            canvas.drawRoundRect(rect, CORNER_RADIUS_PX, CORNER_RADIUS_PX, paint)
            output
        } catch (e: Exception) {
            source
        }
    }

    /** Crops a bitmap to a full circle — used for the player screen's album art */
    private fun circleCrop(source: Bitmap): Bitmap {
        return try {
            val size = minOf(source.width, source.height)
            val x = (source.width - size) / 2
            val y = (source.height - size) / 2

            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply { setTranslate(-x.toFloat(), -y.toFloat()) })
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            output
        } catch (e: Exception) {
            source
        }
    }
}
