package com.zemlianikin.flexiblesearch

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import java.util.function.Consumer

/**
 * Фон окна поиска: размытые обои и затемнение поверх. Выставлять до первого
 * кадра, тогда фон виден сразу.
 *
 * Есть картинка из [BlurredWallpaper] — она фон окна. Нет — окно прозрачное
 * над системными обоями (`windowShowWallpaper` в теме) и размывает их: в
 * многозадачности и в анимациях их тогда не видно. Android 12+ размывает
 * через `setBackgroundBlurRadius`, но Samsung это выключает
 * (`ro.surface_flinger.supports_background_blur=0`) и размывает своим
 * `SemBlurInfo`. Без размытия (энергосбережение, Android 11 и ниже)
 * затемнение плотнее, чтобы текст читался.
 */
class WallpaperBackground(
    private val window: Window,
    private val windowManager: WindowManager,
) {
    private val blurListener = Consumer<Boolean>(::setScrim)
    private var systemBlur = false

    fun show(bitmap: Bitmap?) {
        stopSystemBlur()
        if (bitmap != null) {
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER
            )
            window.setBackgroundDrawable(
                WallpaperDrawable(
                    bitmap,
                    window.context.getColor(
                        R.color.wallpaper_scrim_blurred
                    ),
                )
            )
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            startSystemBlur()
        }
    }

    /** Окно закрывается: перестать слушать систему. */
    fun dispose() = stopSystemBlur()

    private fun startSystemBlur() {
        systemBlur = true
        when {
            setSamsungBlur(radius()) -> setScrim(blurred = true)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                window.setBackgroundBlurRadius(radius())
                windowManager.addCrossWindowBlurEnabledListener(
                    blurListener
                )
            }
            else -> setScrim(blurred = false)
        }
    }

    private fun stopSystemBlur() {
        if (!systemBlur) return
        systemBlur = false
        setSamsungBlur(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setBackgroundBlurRadius(0)
            windowManager.removeCrossWindowBlurEnabledListener(blurListener)
        }
    }

    private fun radius() =
        window.context.resources.getDimensionPixelSize(
            R.dimen.wallpaper_blur_radius
        )

    private fun setScrim(blurred: Boolean) {
        window.setBackgroundDrawableResource(
            if (blurred) R.color.wallpaper_scrim_blurred
            else R.color.wallpaper_scrim
        )
    }

    /** Скрытое API One UI, на других прошивках классов нет. */
    private fun setSamsungBlur(radius: Int): Boolean =
        try {
            val info = Class.forName("android.view.SemBlurInfo")
            val mode =
                info.getField(
                    if (radius > 0) "BLUR_MODE_WINDOW" else "BLUR_MODE_NONE"
                )
            val builder =
                Class.forName("android.view.SemBlurInfo\$Builder")
                    .getConstructor(Int::class.javaPrimitiveType)
                    .newInstance(mode.getInt(null))
            builder.javaClass
                .getMethod("setRadius", Int::class.javaPrimitiveType)
                .invoke(builder, radius)
            View::class
                .java
                .getMethod("semSetBlurInfo", info)
                .invoke(
                    window.decorView,
                    builder.javaClass.getMethod("build").invoke(builder),
                )
            true
        } catch (e: ReflectiveOperationException) {
            false
        } catch (e: RuntimeException) {
            Log.w(TAG, "Samsung blur failed", e)
            false
        }

    private companion object {
        const val TAG = "WallpaperBackground"
    }
}

/**
 * Картинка по центру с обрезкой, как `ContentScale.Crop`, и цвет поверх.
 */
private class WallpaperDrawable(
    private val bitmap: Bitmap,
    private val scrim: Int,
) : Drawable() {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val source = Rect()

    override fun onBoundsChange(bounds: Rect) {
        val scale =
            maxOf(
                bounds.width().toFloat() / bitmap.width,
                bounds.height().toFloat() / bitmap.height,
            )
        val width = (bounds.width() / scale).toInt()
        val height = (bounds.height() / scale).toInt()
        val left = (bitmap.width - width) / 2
        val top = (bitmap.height - height) / 2
        source.set(left, top, left + width, top + height)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawBitmap(bitmap, source, bounds, paint)
        canvas.drawColor(scrim)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}
