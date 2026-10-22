package com.zemlianikin.flexiblesearch.poc.components

import android.app.Application
import com.zemlianikin.flexiblesearch.poc.components.core.PluginHost
import com.zemlianikin.flexiblesearch.poc.components.plugins.AppsPlugin
import com.zemlianikin.flexiblesearch.poc.components.plugins.CalculatorPlugin
import com.zemlianikin.flexiblesearch.poc.components.plugins.FilesPlugin
import com.zemlianikin.flexiblesearch.poc.components.plugins.PillSearchBarPlugin
import com.zemlianikin.flexiblesearch.poc.components.plugins.SearchInAppsPlugin
import com.zemlianikin.flexiblesearch.poc.components.plugins.TogglesPlugin
import com.zemlianikin.flexiblesearch.poc.components.plugins.WeatherPlugin
import com.zemlianikin.flexiblesearch.poc.components.plugins.WebSuggestionsPlugin

class SearchApp : Application() {
    /**
     * Плагины живут дольше окна: список приложений и кэш иконок не грузятся
     * заново при каждом открытии.
     */
    val host by lazy {
        PluginHost(
            this,
            listOf(
                PillSearchBarPlugin(),
                // Порядок плагинов — это порядок кнопок в ряду быстрых
                // действий и порядок групп снизу вверх: первая — у
                // клавиатуры.
                SearchInAppsPlugin(),
                AppsPlugin(),
                CalculatorPlugin(),
                WebSuggestionsPlugin(),
                FilesPlugin(),
                // Виджеты пустой строки, в том же порядке снизу вверх.
                TogglesPlugin(),
                WeatherPlugin(),
            ),
        )
    }
}
