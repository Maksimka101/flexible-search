package com.zemlianikin.flexiblesearch.plugins

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.zemlianikin.flexiblesearch.core.ActionIcon
import com.zemlianikin.flexiblesearch.core.Plugin
import com.zemlianikin.flexiblesearch.core.PluginScope

/**
 * Ряд «искать в…»: строка уходит в веб-поиск, Maps или Play. Кнопка
 * показывается, только если приложение установлено.
 */
class SearchInAppsPlugin : Plugin {
    override val id = "search-in-apps"
    override val name = "Search in apps"
    override val description = "Send the query to web search, Maps or Play"

    private class Target(
        val label: String,
        val packageName: String,
        val intent: (query: String) -> Intent,
    )

    private val targets =
        listOf(
            Target("Web", "com.google.android.googlequicksearchbox") {
                Intent(Intent.ACTION_WEB_SEARCH)
                    .putExtra(SearchManager.QUERY, it)
            },
            Target("Maps", "com.google.android.apps.maps") {
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("geo:0,0?q=${Uri.encode(it)}"),
                )
            },
            Target("Play", "com.android.vending") {
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://search?q=${Uri.encode(it)}"),
                )
            },
        )

    override fun install(scope: PluginScope) {
        val packageManager = scope.context.packageManager
        val iconSize =
            (28 * scope.context.resources.displayMetrics.density).toInt()
        for (target in targets) {
            // Иконки грузятся один раз при установке плагина.
            val icon =
                try {
                    packageManager.getApplicationIcon(target.packageName)
                } catch (_: PackageManager.NameNotFoundException) {
                    continue
                }
            scope.quickAction(
                target.label,
                ActionIcon.Bitmap(
                    icon.toBitmap(iconSize, iconSize).asImageBitmap()
                ),
            ) {
                val intent =
                    target
                        .intent(query.text.trim())
                        .setPackage(target.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (start(context, intent)) close()
            }
        }
    }

    private fun start(context: Context, intent: Intent): Boolean =
        try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("SearchInAppsPlugin", "Can't start $intent", e)
            false
        }
}
