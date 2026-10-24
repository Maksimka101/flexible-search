package com.zemlianikin.flexiblesearch.core

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import kotlin.math.ceil
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

/**
 * Application хоста: из него виджет поиска берёт плагины, даже когда окна
 * поиска ещё не было.
 */
interface PluginApp {
    val plugins: PluginManager
}

/**
 * Кнопка плагина справа от строки в виджете поиска на главном экране, см.
 * [PluginLauncher.widgetButton].
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
            val bitmap =
                button.icon.toBitmap(ceil(24 * density).toInt())
                    ?: return null
            val viewId =
                if (button.icon is ActionIcon.Vector)
                    R.id.search_widget_button_icon
                else R.id.search_widget_button_image
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
    }
}
