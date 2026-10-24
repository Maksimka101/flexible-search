package com.zemlianikin.flexiblesearch.core.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import com.zemlianikin.flexiblesearch.core.buttons.ActionIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Картинка для виджетов и ярлыков: RemoteViews и ShortcutInfo не рисуют
 * [ImageVector]. Вектор — белая маска [maskSize]×[maskSize] px, цвет даёт
 * тот, кто её показывает; остальное — как есть. Не загрузилась — `null`.
 */
internal suspend fun ActionIcon.toBitmap(maskSize: Int): Bitmap? =
    when (this) {
        is ActionIcon.Vector ->
            withContext(Dispatchers.Default) { image.toMask(maskSize) }
        is ActionIcon.Bitmap -> image.asAndroidBitmap()
        is ActionIcon.Lazy ->
            try {
                withContext(Dispatchers.IO) { load() }?.asAndroidBitmap()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("IconBitmaps", "Can't load $key", e)
                null
            }
    }

/** Обрезка групп (`clipPathData`) не поддерживается. */
private fun ImageVector.toMask(size: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(size / viewportWidth, size / viewportHeight)
    drawGroup(root, canvas, Paint(Paint.ANTI_ALIAS_FLAG))
    return bitmap
}

private fun drawGroup(group: VectorGroup, canvas: Canvas, paint: Paint) {
    canvas.save()
    canvas.translate(
        group.translationX + group.pivotX,
        group.translationY + group.pivotY,
    )
    canvas.rotate(group.rotation)
    canvas.scale(group.scaleX, group.scaleY)
    canvas.translate(-group.pivotX, -group.pivotY)
    for (node in group) {
        when (node) {
            is VectorGroup -> drawGroup(node, canvas, paint)
            is VectorPath -> drawPath(node, canvas, paint)
        }
    }
    canvas.restore()
}

private fun drawPath(node: VectorPath, canvas: Canvas, paint: Paint) {
    val path =
        node.pathData.toPath().asAndroidPath().apply {
            fillType =
                if (node.pathFillType == PathFillType.EvenOdd)
                    android.graphics.Path.FillType.EVEN_ODD
                else android.graphics.Path.FillType.WINDING
        }
    paint.color = android.graphics.Color.WHITE
    if (node.fill != null) {
        paint.style = Paint.Style.FILL
        paint.alpha = (node.fillAlpha * 255).toInt()
        canvas.drawPath(path, paint)
    }
    if (node.stroke != null && node.strokeLineWidth > 0) {
        paint.style = Paint.Style.STROKE
        paint.alpha = (node.strokeAlpha * 255).toInt()
        paint.strokeWidth = node.strokeLineWidth
        canvas.drawPath(path, paint)
    }
}
