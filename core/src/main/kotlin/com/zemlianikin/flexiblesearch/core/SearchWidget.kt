package com.zemlianikin.flexiblesearch.core

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Строка поиска на главном экране: по тапу открывает поиск
 * ([SearchLinks.search]).
 */
class SearchWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        val intent =
            Intent(Intent.ACTION_VIEW, SearchLinks.search())
                .setPackage(context.packageName)
        val open =
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE,
            )
        val views = RemoteViews(context.packageName, R.layout.search_widget)
        views.setOnClickPendingIntent(R.id.search_widget_bar, open)
        manager.updateAppWidget(ids, views)
    }
}
