package com.zemlianikin.flexiblesearch

import android.app.Application
import com.zemlianikin.flexiblesearch.core.PluginHost
import com.zemlianikin.flexiblesearch.plugins.AppsPlugin
import com.zemlianikin.flexiblesearch.plugins.CalculatorPlugin
import com.zemlianikin.flexiblesearch.plugins.PillSearchBarPlugin
import com.zemlianikin.flexiblesearch.plugins.SearchInAppsPlugin
import com.zemlianikin.flexiblesearch.plugins.TogglesPlugin
import com.zemlianikin.flexiblesearch.plugins.mocks.MocksPlugin

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
                // действий, порядок групп и виджетов снизу вверх: первая —
                // у клавиатуры.
                SearchInAppsPlugin(),
                AppsPlugin(),
                CalculatorPlugin(),
                TogglesPlugin(),
                // Веб и файлы после калькулятора, погода после кнопок.
                MocksPlugin(),
            ),
        )
    }
}
