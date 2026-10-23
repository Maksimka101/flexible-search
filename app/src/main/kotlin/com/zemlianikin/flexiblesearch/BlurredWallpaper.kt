package com.zemlianikin.flexiblesearch

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.graphics.scale
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Размытые обои, уменьшенные в [DOWNSCALE] раз: фон окна поиска. Картинка
 * внутри окна, а не системные обои под ним, поэтому видна и в
 * многозадачности, и в анимации открытия и закрытия.
 *
 * Готовая лежит в файле с id обоев: при холодном старте читается за
 * миллисекунды, ещё до первого кадра. Сменились обои — пересчёт в фоне.
 *
 * Обои читаются только с доступом ко всем файлам. Без него, или если
 * система картинку не отдала, [bitmap] — null, и окно размывает системные
 * обои под собой.
 */
class BlurredWallpaper(private val context: Context) {
    private val manager = WallpaperManager.getInstance(context)
    private val dir = File(context.cacheDir, "wallpaper")
    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default.limitedParallelism(1)
        )
    @Volatile private var loadedId = 0

    private val state by lazy {
        MutableStateFlow(load(manager.systemId()))
    }

    /** Первое чтение синхронное: без кэша ждёт, пока посчитается. */
    val bitmap: StateFlow<Bitmap?>
        get() = state

    init {
        // Сигнал смены обоев без разрешений.
        manager.addOnColorsChangedListener(
            { _, which ->
                if (which and WallpaperManager.FLAG_SYSTEM != 0) refresh()
            },
            Handler(Looper.getMainLooper()),
        )
    }

    /** Обои сменились или доступ появился — пересчитать в фоне. */
    fun refresh() {
        scope.launch {
            val id = manager.systemId()
            if (id != loadedId || state.value == null) {
                state.value = load(id)
            }
        }
    }

    private fun load(id: Int): Bitmap? {
        loadedId = id
        val file = File(dir, "$id.png")
        BitmapFactory.decodeFile(file.path)?.let {
            return it
        }
        val blurred =
            try {
                blurred()
            } catch (e: SecurityException) {
                null
            } catch (e: RuntimeException) {
                Log.w(TAG, "Wallpaper blur failed", e)
                null
            }
        if (blurred != null) {
            dir.listFiles()?.forEach(File::delete)
            dir.mkdirs()
            file.outputStream().use {
                blurred.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        return blurred
    }

    private fun blurred(): Bitmap? {
        val wallpaper =
            (manager.drawable as? BitmapDrawable)?.bitmap ?: return null
        val small =
            wallpaper.scale(
                (wallpaper.width / DOWNSCALE).coerceAtLeast(1),
                (wallpaper.height / DOWNSCALE).coerceAtLeast(1),
            )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return small
        val radius =
            context.resources.getDimension(R.dimen.wallpaper_blur_radius) /
                DOWNSCALE
        return blur(small, radius)
    }

    private companion object {
        const val TAG = "BlurredWallpaper"

        /**
         * Размытой картинке хватает и малого размера, растянутая она не
         * отличается.
         */
        const val DOWNSCALE = 8
    }
}

private fun WallpaperManager.systemId() =
    getWallpaperId(WallpaperManager.FLAG_SYSTEM)

/** Размытие на GPU, без окна: RenderNode в ImageReader. */
private fun blur(source: Bitmap, radius: Float): Bitmap {
    val reader =
        ImageReader.newInstance(
            source.width,
            source.height,
            PixelFormat.RGBA_8888,
            1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or
                HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
    val node = RenderNode("wallpaper")
    val renderer = HardwareRenderer()
    try {
        node.setPosition(0, 0, source.width, source.height)
        node.setRenderEffect(
            RenderEffect.createBlurEffect(
                radius,
                radius,
                Shader.TileMode.CLAMP,
            )
        )
        node.beginRecording().drawBitmap(source, 0f, 0f, null)
        node.endRecording()
        renderer.setSurface(reader.surface)
        renderer.setContentRoot(node)
        renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
        reader.acquireNextImage().use { image ->
            image.hardwareBuffer!!.use { buffer ->
                return Bitmap.wrapHardwareBuffer(
                        buffer,
                        ColorSpace.get(ColorSpace.Named.SRGB),
                    )!!
                    .copy(Bitmap.Config.ARGB_8888, false)
            }
        }
    } finally {
        renderer.destroy()
        node.discardDisplayList()
        reader.close()
    }
}
