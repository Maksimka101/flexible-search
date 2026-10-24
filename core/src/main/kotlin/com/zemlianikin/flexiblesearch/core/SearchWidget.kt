package com.zemlianikin.flexiblesearch.core

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import kotlin.math.ceil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Application хоста: из него виджет поиска берёт плагины, даже когда окна
 * поиска ещё не было.
 */
interface PluginApp {
    val plugins: PluginManager
}

/**
 * Кнопка плагина справа от строки в виджете поиска на главном экране, см.
 * [PluginScope.homeScreenButton].
 */
class HomeScreenButton(
    val id: String,
    val label: String,
    val icon: ActionIcon,
    val visible: StateFlow<Boolean>,
    val action: Action,
)

/**
 * Строка поиска на главном экране: по тапу открывает поиск
 * ([SearchLinks.search]), справа — кнопки плагинов ([HomeScreenButton]).
 */
class SearchWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        val result = goAsync()
        val plugins = (context.applicationContext as PluginApp).plugins
        scope.launch {
            try {
                update(context, visibleButtons(plugins).first())
            } finally {
                result.finish()
            }
        }
    }

    internal companion object {
        // Хост собирается в главном потоке, как и в окне поиска.
        private val scope =
            CoroutineScope(SupervisorJob() + Dispatchers.Main)

        /**
         * Перерисовывать виджет, когда меняются кнопки: включили плагин,
         * плагин добавил кнопку или спрятал её из своих настроек.
         */
        fun watch(context: Context, plugins: PluginManager) {
            val app = context.applicationContext
            scope.launch {
                visibleButtons(plugins)
                    .distinctUntilChanged()
                    .collectLatest {
                        update(app, it)
                    }
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun visibleButtons(
            plugins: PluginManager
        ): Flow<List<HomeScreenButton>> =
            plugins.disabled
                .flatMapLatest { plugins.host(it).homeScreenButtons }
                .flatMapLatest { buttons ->
                    if (buttons.isEmpty()) flowOf(emptyList())
                    else
                        combine(
                            buttons.map { button ->
                                button.visible.map {
                                    if (it) button else null
                                }
                            }
                        ) {
                            it.filterNotNull()
                        }
                }

        private suspend fun update(
            context: Context,
            buttons: List<HomeScreenButton>,
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, SearchWidget::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            val views =
                RemoteViews(context.packageName, R.layout.search_widget)
            views.setOnClickPendingIntent(
                R.id.search_widget_bar,
                open(context, SearchLinks.search()),
            )
            // Лаунчер может применить новые views к старым, и addView
            // дописал бы к уже показанным кнопкам.
            views.removeAllViews(R.id.search_widget_buttons)
            for (button in buttons) {
                views.addView(
                    R.id.search_widget_buttons,
                    buttonViews(context, button) ?: continue,
                )
            }
            manager.updateAppWidget(ids, views)
        }

        private suspend fun buttonViews(
            context: Context,
            button: HomeScreenButton,
        ): RemoteViews? {
            val views =
                RemoteViews(
                    context.packageName,
                    R.layout.search_widget_button,
                )
            val density = context.resources.displayMetrics.density
            val icon = button.icon
            val (viewId, bitmap) =
                when (icon) {
                    is ActionIcon.Vector ->
                        R.id.search_widget_button_icon to
                            withContext(Dispatchers.Default) {
                                icon.image.toMask(
                                    ceil(24 * density).toInt()
                                )
                            }
                    is ActionIcon.Bitmap ->
                        R.id.search_widget_button_image to
                            icon.image.asAndroidBitmap()
                    is ActionIcon.Lazy ->
                        R.id.search_widget_button_image to
                            try {
                                withContext(Dispatchers.IO) { icon.load() }
                                    ?.asAndroidBitmap()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(TAG, "Can't load ${icon.key}", e)
                                null
                            }
                }
            if (bitmap == null) return null
            views.setImageViewBitmap(viewId, bitmap)
            views.setViewVisibility(viewId, View.VISIBLE)
            views.setContentDescription(
                R.id.search_widget_button,
                button.label,
            )
            views.setOnClickPendingIntent(
                R.id.search_widget_button,
                open(context, SearchLinks.action(button.id)),
            )
            return views
        }

        // У каждой ссылки свой PendingIntent: интенты различаются data.
        private fun open(context: Context, link: Uri): PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(Intent.ACTION_VIEW, link)
                    .setPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE,
            )

        private const val TAG = "SearchWidget"
    }
}

/**
 * Белая маска [size]×[size] px: RemoteViews не рисует [ImageVector], а цвет
 * даёт `android:tint` разметки, он и следит за тёмной темой. Обрезка групп
 * (`clipPathData`) не поддерживается.
 */
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
