package com.zemlianikin.flexiblesearch.plugins.mocks

import android.app.SearchManager
import android.content.Intent
import com.zemlianikin.flexiblesearch.core.ActionIcon
import com.zemlianikin.flexiblesearch.core.PluginScope
import com.zemlianikin.flexiblesearch.core.ResultsBuilder
import com.zemlianikin.flexiblesearch.core.materialIcon
import kotlinx.coroutines.delay

/**
 * Мок веб-подсказок: продолжения запроса с задержкой сети. Набранная часть
 * обычная, дополнение жирное, как в Google. `web ` — весь список.
 */
internal object WebSuggestionsMock {
    private val endings =
        listOf(
            "",
            " app",
            " meaning",
            " near me",
            " price",
            " review",
            " vs",
            " download",
        )

    fun install(scope: PluginScope) {
        val icon = ActionIcon.Vector(WebIcon)
        val web =
            scope.triggers.add(
                "Web",
                "web",
                placeholder = "Search the web",
                icon = icon,
            )
        scope.results.add(title = "Web", icon = icon, fullList = web) {
            suggestions(it)
        }
        scope.results.add(web, title = "Web", icon = icon) {
            suggestions(it)
        }
    }

    private suspend fun ResultsBuilder.suggestions(query: String) {
        val typed = query.trim()
        if (typed.isEmpty()) return
        // 150–400 мс, стабильно для одного запроса.
        delay(150L + Math.floorMod(typed.hashCode(), 250))
        for (ending in endings) {
            val text = typed + ending
            val completion = (typed.length until text.length).toList()
            suggestion(text, highlights = completion, id = text) {
                context.startActivity(
                    Intent(Intent.ACTION_WEB_SEARCH)
                        .putExtra(SearchManager.QUERY, text)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                close()
            }
        }
    }
}

// Material Symbols «language».
private val WebIcon =
    materialIcon(
        "Web",
        "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12" +
            "S17.52,2 11.99,2zM18.92,8h-2.95c-0.32,-1.25 -0.78,-2.45 -1.38,-3.56" +
            " 1.84,0.63 3.37,1.91 4.33,3.56zM12,4.04c0.83,1.2 1.48,2.53 1.91,3.96" +
            "h-3.82c0.43,-1.43 1.08,-2.76 1.91,-3.96zM4.26,14C4.1,13.36 4,12.69" +
            " 4,12s0.1,-1.36 0.26,-2h3.38c-0.08,0.66 -0.14,1.32 -0.14,2" +
            " 0,0.68 0.06,1.34 0.14,2H4.26zM5.08,16h2.95c0.32,1.25 0.78,2.45" +
            " 1.38,3.56 -1.84,-0.63 -3.37,-1.9 -4.33,-3.56zM8.03,8H5.08" +
            "c0.96,-1.66 2.49,-2.93 4.33,-3.56C8.81,5.55 8.35,6.75 8.03,8z" +
            "M12,19.96c-0.83,-1.2 -1.48,-2.53 -1.91,-3.96h3.82c-0.43,1.43" +
            " -1.08,2.76 -1.91,3.96zM14.34,14H9.66c-0.09,-0.66 -0.16,-1.32" +
            " -0.16,-2 0,-0.68 0.07,-1.35 0.16,-2h4.68c0.09,0.65 0.16,1.32" +
            " 0.16,2 0,0.68 -0.07,1.34 -0.16,2zM14.59,19.56c0.6,-1.11" +
            " 1.06,-2.31 1.38,-3.56h2.95c-0.96,1.65 -2.49,2.93 -4.33,3.56z" +
            "M16.36,14c0.08,-0.66 0.14,-1.32 0.14,-2 0,-0.68 -0.06,-1.34" +
            " -0.14,-2h3.38c0.16,0.64 0.26,1.31 0.26,2s-0.1,1.36 -0.26,2h-3.38z",
    )
